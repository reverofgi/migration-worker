package com.migration.util;

import com.migration.config.MigrationParameter;
import com.migration.config.MigrationProperties;
import com.migration.exception.MetadataException;
import com.migration.metadata.MigrationTableInfo;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 로깅 유틸리티 확장 지점.
 *
 */
public final class LogUtil {

    public static final String WORKER_BATCH_LOG_PATH = "WORKER_BATCH_LOG_PATH";
    private static final String LOG_FILE_MDC_KEY = "migrationLogFile";
    private static final DateTimeFormatter LOG_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private LogUtil() {
    }

    public static String tableName(String tableSchema, String tableName) {
        return tableSchema + "." + tableName;
    }

    /** 현재 TASK_ID 실행 로그를 별도 파일로 분리하기 위한 MDC를 초기화한다. */
    public static void initializeTaskFileLogging(
            MigrationParameter parameter,
            MigrationTableInfo table) throws MetadataException {
        try {
            Path configuredLogDirectory = Path.of(
                    MigrationProperties.getRequired(WORKER_BATCH_LOG_PATH));
            initializeTaskFileLogging(parameter, table, configuredLogDirectory);
        } catch (SQLException | IOException | InvalidPathException e) {
            throw new MetadataException(
                    WORKER_BATCH_LOG_PATH + " 설정 또는 로그 디렉터리가 유효하지 않습니다.",
                    e);
        }
    }

    static void initializeTaskFileLogging(
            MigrationParameter parameter,
            MigrationTableInfo table,
            Path configuredLogDirectory) throws IOException {
        Path logDirectory = prepareLogDirectory(configuredLogDirectory);

        String fileName = migrationLogFileName(parameter, table, LocalDateTime.now());
        Path logFileBase = logDirectory.resolve(fileName).normalize();
        if (!logFileBase.startsWith(logDirectory)) {
            throw new IOException("Worker batch log file escapes its configured root: "
                    + logFileBase);
        }

        MDC.put(LOG_FILE_MDC_KEY, logFileBase.toString());
        MDC.put("execOrd", Integer.toString(parameter.getExecOrd()));
        MDC.put("taskId", sanitize(parameter.getTaskId()));
        MDC.put("tableOwner", sanitize(table.getTableOwner()));
        MDC.put("tableId", sanitize(table.getTableId()));
        MDC.put("procOrd", Integer.toString(table.getProcOrd()));
    }

    private static Path prepareLogDirectory(Path configuredLogDirectory)
            throws IOException {
        Path logDirectory = configuredLogDirectory.toAbsolutePath().normalize();
        if (logDirectory.getParent() == null) {
            throw new IOException(
                    WORKER_BATCH_LOG_PATH + " must not be the filesystem root: "
                            + configuredLogDirectory);
        }
        Files.createDirectories(logDirectory);
        if (!Files.isDirectory(logDirectory)) {
            throw new IOException("Worker batch log path is not a directory: "
                    + logDirectory);
        }

        /*********************************************
        ① [Files.isWritable() 사용 하지 않는 이유]
        Java NIO의 Files.isWritable(Path) 메서드는
        해당 경로에 실제로 파일이나 데이터를 써보는 방식이 아니라,
        운영체제(OS)에 파일/디렉터리의 메타데이터 및 ACL 속성을 조회하여 권한 여부를 추정 판단합니다.
        Linux 기반 Samba 서버를 Windows 네트워크(UNC 경로)로 연결하거나
        심볼릭 링크(Symbolic Link)를 거쳐 접근하는 환경에서는
        Linux의 파일 권한(UID/GID)과 Windows의 SID 기반 ACL 간의 매핑 과정에서
        정보가 제대로 전달되지 않거나 권한 조회가 거부되는 일이 흔히 발생합니다.

        이로 인해 실제로는 물리적 쓰기 권한이 정상 제공되고 있음에도 불구하고,
        Java의 ACL 메타데이터 조회 결과 실패로 인해
        Files.isWritable()이 오탐(false)을 반환하게 됩니다.

        ② 적용 방법 (생성/삭제 권한 직접 검증)
        API의 메타데이터 조회 결과 오차를 우회하기 위해, 권한 검사 시 단순 속성 조회 함수에 의존하지 않습니다.
        대상 경로에 테스트용 임시 파일(예: UUID 기반 임시 파일)을 직접 생성(Create)하고 즉시 삭제(Delete)하는 실체적 검증 로직을 적용합니다.

        이를 통해 Java 프로세스가 해당 네트워크/심볼릭 링크 디렉터리에 대해
        실제 물리적인 파일 생성, 쓰기, 삭제 작업을 정상 수행할 수 있는지 가장 확실하게 검증할 수 있습니다.
        ********************************************/
        Path probeFile;
        try {
            probeFile = Files.createTempFile(
                    logDirectory, ".migration-write-test-", ".tmp");
        } catch (IOException e) {
            throw new IOException("Worker batch log directory is not writable: "
                    + logDirectory, e);
        }
        try {
            Files.delete(probeFile);
        } catch (IOException e) {
            throw new IOException("Worker batch log write test file cannot be deleted: "
                    + probeFile, e);
        }
        return logDirectory;
    }

    /** 실행 종료 후 현재 Thread의 로그 식별자를 제거한다. */
    public static void clearTaskFileLogging() {
        MDC.remove(LOG_FILE_MDC_KEY);
        MDC.remove("execOrd");
        MDC.remove("taskId");
        MDC.remove("tableOwner");
        MDC.remove("tableId");
        MDC.remove("procOrd");
    }

    static String migrationLogFileName(
            MigrationParameter parameter,
            MigrationTableInfo table,
            LocalDateTime now) {
        return parameter.getExecOrd()
                + "_" + sanitize(table.getTableOwner())
                + "_" + sanitize(table.getTableId())
                + "_" + table.getProcOrd()
                + "_" + LOG_TIMESTAMP.format(now);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    }
}

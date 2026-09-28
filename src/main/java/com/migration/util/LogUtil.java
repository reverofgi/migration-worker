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

        // Files.isWritable()은 Samba/UNC Symbolic Link에서 실제 쓰기가 가능해도
        // SID 기반 ACL 조회 결과 때문에 false를 반환할 수 있다. 실제 파일을
        // 생성하고 삭제하여 Java 프로세스의 생성/삭제 권한을 함께 검증한다.
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

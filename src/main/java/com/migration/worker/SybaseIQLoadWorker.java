package com.migration.worker;

import com.migration.config.MigrationFileFormat;
import com.migration.config.MigrationStoragePaths;
import com.migration.exception.LoadException;
import com.migration.metadata.MigrationTableInfo;
import com.migration.metadata.ValidationTarget;
import com.migration.util.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** 공유 볼륨의 Native Extract 결과를 TO-BE Sybase IQ에 적재한다. */
public final class SybaseIQLoadWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(SybaseIQLoadWorker.class);
    private static final Set<String> CHARACTER_LOB_TYPES = Set.of(
            "CLOB", "LONG VARCHAR", "LONG NVARCHAR", "TEXT");
    private static final Set<String> BINARY_LOB_TYPES = Set.of(
            "BLOB", "LONG BINARY", "IMAGE");
    private static final Set<String> UNSUPPORTED_FILE_BACKED_TYPES = Set.of(
            "VARBINARY", "BINARY");

    private final Connection connection;

    public SybaseIQLoadWorker(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    public void execute(MigrationTableInfo table) throws LoadException {
        Objects.requireNonNull(table, "table");
        try {
            MigrationFileFormat format = MigrationFileFormat.load();
            MigrationStoragePaths paths = MigrationStoragePaths.load();
            List<ValidationTarget> columns = validateAndOrderColumns(table);
            String directoryName = buildExtractDirectoryName(table);
            String fileName = buildExtractFileName(table);
            Path workerDataFile = paths.workerDataFile(directoryName, fileName);
            validateInputFile(workerDataFile);

            String databaseDataFile = joinPath(
                    paths.databaseDataDirectory(directoryName), fileName);
            String targetTable = qualifiedTargetTable(table);
            String loadSql = buildLoadSql(
                    targetTable, columns, databaseDataFile, format);
            executeLoad(targetTable, table.getMigLoadMode(), loadSql);
            cleanupExtractFiles(table, paths);
        } catch (SQLException | RuntimeException e) {
            throw new LoadException(
                    "TO-BE 테이블 적재 실패: " + table.getTableNm(), e);
        }
    }

    String buildLoadSql(
            MigrationTableInfo table,
            List<ValidationTarget> columns,
            String databaseDataFile,
            MigrationFileFormat format) throws LoadException {
        return buildLoadSql(
                qualifiedTargetTable(table),
                validateAndOrderColumns(table, columns),
                databaseDataFile,
                format);
    }

    private String buildLoadSql(
            String targetTable,
            List<ValidationTarget> columns,
            String databaseDataFile,
            MigrationFileFormat format) throws LoadException {
        if (databaseDataFile == null || databaseDataFile.isBlank()) {
            throw new LoadException("LOAD TABLE 입력 파일 경로가 없습니다.");
        }

        String columnDelimiter = MigrationFileFormat.toSqlDelimiter(
                format.columnDelimiter());
        String rowDelimiter = MigrationFileFormat.toSqlDelimiter(format.rowDelimiter());
        String nullToken = SqlUtil.escapeLiteral(format.nullToken());
        List<String> specifications = new ArrayList<>(columns.size());
        for (ValidationTarget column : columns) {
            String columnName = validateIdentifier(column.getColNm(), "column name");
            String baseType = baseType(column.getDataType());
            StringBuilder specification = new StringBuilder(columnName);
            if (CHARACTER_LOB_TYPES.contains(baseType)) {
                specification.append(" ASCII FILE ('").append(columnDelimiter).append("')");
            } else if (BINARY_LOB_TYPES.contains(baseType)) {
                specification.append(" BINARY FILE ('").append(columnDelimiter).append("')");
            } else if (UNSUPPORTED_FILE_BACKED_TYPES.contains(baseType)) {
                throw new LoadException(
                        "FILE 방식 LOAD TABLE을 지원하지 않는 DATA_TYPE입니다: "
                                + column.getDataType());
            }
            if (column.isNullYn()) {
                specification.append(" NULL ('").append(nullToken).append("')");
            }
            specifications.add(specification.toString());
        }

        return "LOAD TABLE " + targetTable + " ("
                + String.join(", ", specifications) + ") FROM '"
                + SqlUtil.escapeLiteral(databaseDataFile) + "' "
                + "QUOTES ON ESCAPES OFF FORMAT ASCII DELIMITED BY '"
                + columnDelimiter + "' ROW DELIMITED BY '" + rowDelimiter
                + "' ON FILE ERROR ROLLBACK";
    }

    private void executeLoad(String targetTable, String loadMode, String loadSql)
            throws SQLException, LoadException {
        String normalizedMode = requireLoadMode(loadMode);
        boolean originalAutoCommit = connection.getAutoCommit();
        SQLException failure = null;
        try {
            if (originalAutoCommit) {
                connection.setAutoCommit(false);
            }
            try (Statement statement = connection.createStatement()) {
                if ("T".equals(normalizedMode)) {
                    LOGGER.info("TO-BE table truncate started: table={}", targetTable);
                    statement.executeUpdate("TRUNCATE TABLE " + targetTable);
                }
                LOGGER.info("Sybase IQ load started: table={}", targetTable);
                statement.execute(loadSql);
                connection.commit();
                LOGGER.info("Sybase IQ load completed: table={}", targetTable);
            }
        } catch (SQLException e) {
            failure = e;
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            if (originalAutoCommit) {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException restoreFailure) {
                    if (failure != null) {
                        failure.addSuppressed(restoreFailure);
                    } else {
                        throw restoreFailure;
                    }
                }
            }
        }
    }

    private List<ValidationTarget> validateAndOrderColumns(MigrationTableInfo table)
            throws LoadException {
        return validateAndOrderColumns(table, table.getValidationTargets());
    }

    private List<ValidationTarget> validateAndOrderColumns(
            MigrationTableInfo table,
            List<ValidationTarget> targets) throws LoadException {
        if (targets == null || targets.isEmpty()) {
            throw new LoadException("적재 대상 컬럼 정보가 없습니다: " + table.getTableId());
        }
        List<ValidationTarget> ordered = new ArrayList<>(targets);
        ordered.sort(Comparator.comparingInt(ValidationTarget::getColOrd));
        Set<Integer> ordinals = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (ValidationTarget target : ordered) {
            if (target == null
                    || !SqlUtil.identifiersEqual(
                        table.getTableOwner(), target.getTableOwner())
                    || !SqlUtil.identifiersEqual(
                        table.getTableId(), target.getTableId())) {
                throw new LoadException(
                        "TABLE_OWNER/TABLE_ID가 일치하지 않는 적재 컬럼이 있습니다.");
            }
            if (target.getColOrd() <= 0 || !ordinals.add(target.getColOrd())) {
                throw new LoadException("잘못되거나 중복된 COL_ORD: " + target.getColOrd());
            }
            String columnName = validateIdentifier(target.getColNm(), "column name");
            if (!names.add(columnName.toUpperCase(Locale.ROOT))) {
                throw new LoadException("중복된 COL_NM: " + columnName);
            }
            baseType(target.getDataType());
        }
        return List.copyOf(ordered);
    }

    private String qualifiedTargetTable(MigrationTableInfo table) throws LoadException {
        String owner = validateIdentifier(table.getTgtDbPrefix(), "target table owner");
        String tableName = validateIdentifier(table.getTableNm(), "target table name");
        return owner + "." + tableName;
    }

    private String buildExtractDirectoryName(MigrationTableInfo table) throws LoadException {
        String owner = validateIdentifier(table.getTableOwner(), "source table owner");
        String tableName = validateIdentifier(table.getTableNm(), "source table name");
        if (table.getProcOrd() < 0) {
            throw new LoadException("PROC_ORD는 0 이상이어야 합니다: " + table.getProcOrd());
        }
        return owner + "_" + tableName + "_" + table.getProcOrd();
    }

    private String buildExtractFileName(MigrationTableInfo table) throws LoadException {
        String tableId = validateIdentifier(table.getTableId(), "table ID");
        return tableId + "_" + table.getProcOrd() + "_data.dat";
    }

    private void validateInputFile(Path inputFile) throws LoadException {
        if (!Files.isRegularFile(inputFile) || !Files.isReadable(inputFile)) {
            throw new LoadException("적재 입력 파일을 읽을 수 없습니다: " + inputFile);
        }
    }

    /** LOAD 커밋 후 현재 TABLE_ID/PROC_ORD가 생성한 DAT/BIN 파일만 삭제한다. */
    void cleanupExtractFiles(MigrationTableInfo table, MigrationStoragePaths paths)
            throws LoadException {
        String directoryName = buildExtractDirectoryName(table);
        String dataFileName = buildExtractFileName(table);
        String binaryFilePrefix = validateIdentifier(table.getTableId(), "table ID")
                + "_" + table.getProcOrd() + "_";

        try {
            long totalBytes = 0;
            long deletedFiles = 0;
            Path dataDirectory = resolveExistingDirectory(
                    paths.workerDataDirectory(directoryName), "data");
            if (dataDirectory != null) {
                long deletedBytes = deleteRegularFileIfPresent(
                        dataDirectory.resolve(dataFileName), "DAT");
                if (deletedBytes >= 0) {
                    totalBytes += deletedBytes;
                    deletedFiles++;
                } else {
                    LOGGER.warn("Extract DAT file was already absent: table={}.{}, procOrd={}",
                            table.getTableOwner(), table.getTableId(), table.getProcOrd());
                }
            }

            Path blobDirectory = resolveExistingDirectory(
                    paths.workerBlobDirectory(directoryName), "BLOB");
            if (blobDirectory != null) {
                DeletedFiles binaryFiles = deleteBinaryFiles(
                        blobDirectory, binaryFilePrefix);
                totalBytes += binaryFiles.bytes();
                deletedFiles += binaryFiles.count();
            }

            deleteDirectoryIfEmpty(dataDirectory);
            deleteDirectoryIfEmpty(blobDirectory);
            LOGGER.info(
                    "Extract file cleanup completed: table={}.{}, procOrd={}, files={}, bytes={}",
                    table.getTableOwner(), table.getTableId(), table.getProcOrd(),
                    deletedFiles, totalBytes);
        } catch (IOException e) {
            LOGGER.error(
                    "Extract file cleanup failed after LOAD commit: table={}.{}, procOrd={}",
                    table.getTableOwner(), table.getTableId(), table.getProcOrd(), e);
            throw new LoadException(
                    "TO-BE 적재는 성공했으나 추출 파일 정리에 실패했습니다: "
                            + table.getTableId() + ", PROC_ORD=" + table.getProcOrd(),
                    e);
        }
    }

    /**
     * Windows/SMB에서는 디렉터리를 열거하면서 파일을 삭제하면 일부 항목이
     * 건너뛰어질 수 있으므로, 일치 파일이 없는 순회가 나올 때까지 반복한다.
     */
    private DeletedFiles deleteBinaryFiles(Path directory, String filePrefix)
            throws IOException {
        long totalBytes = 0;
        long deletedFiles = 0;
        long deletedInPass;
        do {
            deletedInPass = 0;
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
                for (Path entry : entries) {
                    String name = entry.getFileName().toString();
                    if (!name.startsWith(filePrefix) || !name.endsWith(".bin")) {
                        continue;
                    }
                    long deletedBytes = deleteRegularFileIfPresent(entry, "BIN");
                    if (deletedBytes >= 0) {
                        totalBytes += deletedBytes;
                        deletedFiles++;
                        deletedInPass++;
                    }
                }
            }
        } while (deletedInPass > 0);
        return new DeletedFiles(deletedFiles, totalBytes);
    }

    private Path resolveExistingDirectory(Path directory, String description)
            throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(description + " 작업 경로가 디렉터리가 아닙니다: " + directory);
        }
        Path realRoot = directory.getParent().toRealPath();
        Path realDirectory = directory.toRealPath();
        if (!realDirectory.startsWith(realRoot)) {
            throw new IOException(description + " 작업 경로가 설정 루트를 벗어납니다: "
                    + realDirectory);
        }
        return realDirectory;
    }

    // 적재완료된 파일들 삭제
    private long deleteRegularFileIfPresent(Path file, String description) throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return -1;
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(description + " 정리 대상이 일반 파일이 아닙니다: " + file);
        }
        long size = Files.size(file);
        LOGGER.debug("Extract file cleanup: file={}, bytes={}", file, size);
        Files.delete(file);
        return size;
    }

    private void deleteDirectoryIfEmpty(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.deleteIfExists(directory);
        } catch (DirectoryNotEmptyException e) {
            LOGGER.debug("Extract directory is not empty and will be retained: {}", directory);
        }
    }

    private record DeletedFiles(long count, long bytes) {
    }

    private String requireLoadMode(String loadMode) throws LoadException {
        String normalized = loadMode == null ? "" : loadMode.trim().toUpperCase(Locale.ROOT);
        if (!"T".equals(normalized) && !"A".equals(normalized)) {
            throw new LoadException("지원하지 않는 MIG_LOAD_MODE입니다: " + loadMode);
        }
        return normalized;
    }

    private String baseType(String dataType) throws LoadException {
        if (dataType == null || dataType.isBlank()) {
            throw new LoadException("DATA_TYPE이 없습니다.");
        }
        String normalized = dataType.trim().toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        int detailStart = normalized.indexOf('(');
        return detailStart < 0 ? normalized : normalized.substring(0, detailStart).trim();
    }

    private String validateIdentifier(String value, String description) throws LoadException {
        try {
            return SqlUtil.validateIdentifier(value);
        } catch (IllegalArgumentException e) {
            throw new LoadException("Invalid " + description + ": " + value, e);
        }
    }

    private static String joinPath(String directory, String fileName) {
        return directory.endsWith("/") ? directory + fileName : directory + "/" + fileName;
    }
}

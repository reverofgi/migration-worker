package com.migration.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;

/** Sybase IQ 서버 경로와 Migration Worker 파일 시스템 경로의 매핑을 관리한다. */
public final class MigrationStoragePaths {
    public static final String DB_EXPORT_DATA_PATH = "DB_EXPORT_DATA_PATH";
    public static final String DB_EXPORT_BLOB_PATH = "DB_EXPORT_BLOB_PATH";
    public static final String WORKER_EXPORT_DATA_PATH = "WORKER_EXPORT_DATA_PATH";
    public static final String WORKER_EXPORT_BLOB_PATH = "WORKER_EXPORT_BLOB_PATH";

    private final String dbDataRoot;
    private final String dbBlobRoot;
    private final Path workerDataRoot;
    private final Path workerBlobRoot;

    public MigrationStoragePaths(
            String dbDataRoot,
            String dbBlobRoot,
            Path workerDataRoot,
            Path workerBlobRoot) {
        this.dbDataRoot = validateDatabaseRoot(dbDataRoot, DB_EXPORT_DATA_PATH);
        this.dbBlobRoot = validateDatabaseRoot(dbBlobRoot, DB_EXPORT_BLOB_PATH);
        this.workerDataRoot = normalizeWorkerRoot(workerDataRoot, WORKER_EXPORT_DATA_PATH);
        this.workerBlobRoot = normalizeWorkerRoot(workerBlobRoot, WORKER_EXPORT_BLOB_PATH);
    }

    public static MigrationStoragePaths load() throws SQLException {
        try {
            return new MigrationStoragePaths(
                    MigrationProperties.getRequired(DB_EXPORT_DATA_PATH),
                    MigrationProperties.getRequired(DB_EXPORT_BLOB_PATH),
                    Path.of(MigrationProperties.getRequired(WORKER_EXPORT_DATA_PATH)),
                    Path.of(MigrationProperties.getRequired(WORKER_EXPORT_BLOB_PATH)));
        } catch (RuntimeException e) {
            throw new SQLException("Migration storage path configuration is invalid.", e);
        }
    }

    public String databaseDataDirectory(String directoryName) {
        return resolveDatabaseDirectory(dbDataRoot, directoryName);
    }

    public String databaseBlobDirectory(String directoryName) {
        return resolveDatabaseDirectory(dbBlobRoot, directoryName);
    }

    public Path createWorkerDataDirectory(String directoryName) throws IOException {
        return createWorkerDirectory(workerDataRoot, directoryName);
    }

    public Path createWorkerBlobDirectory(String directoryName) throws IOException {
        return createWorkerDirectory(workerBlobRoot, directoryName);
    }

    private static String validateDatabaseRoot(String value, String propertyName) {
        Objects.requireNonNull(value, propertyName);
        String normalized = value.trim().replaceAll("/+$", "");
        if (!normalized.startsWith("/") || normalized.contains("\\")) {
            throw new IllegalArgumentException(
                    propertyName + " must be a Unix absolute path: " + value);
        }
        if (normalized.isEmpty() || "/".equals(normalized)) {
            throw new IllegalArgumentException(
                    propertyName + " must not be the filesystem root directory.");
        }
        return normalized;
    }

    private static Path normalizeWorkerRoot(Path value, String propertyName) {
        Objects.requireNonNull(value, propertyName);
        Path normalized = value.toAbsolutePath().normalize();
        if (normalized.getParent() == null) {
            throw new IllegalArgumentException(
                    propertyName + " must not be a filesystem root directory: " + value);
        }
        return normalized;
    }

    private static String resolveDatabaseDirectory(String root, String directoryName) {
        validateDirectoryName(directoryName);
        return root + "/" + directoryName;
    }

    private static Path createWorkerDirectory(Path root, String directoryName)
            throws IOException {
        validateDirectoryName(directoryName);
        if (!Files.isDirectory(root)) {
            throw new IOException("Worker export root directory is not accessible: " + root);
        }

        Path realRoot = root.toRealPath();
        Path directory = root.resolve(directoryName).normalize();
        if (!directory.startsWith(root)) {
            throw new IOException("Worker export directory escapes its configured root: "
                    + directory);
        }

        Files.createDirectories(directory);
        Path realDirectory = directory.toRealPath();
        if (!realDirectory.startsWith(realRoot)) {
            throw new IOException("Worker export directory resolves outside its configured root: "
                    + realDirectory);
        }
        return directory;
    }

    private static void validateDirectoryName(String directoryName) {
        if (directoryName == null || !directoryName.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException(
                    "Invalid export directory name: " + directoryName);
        }
    }
}

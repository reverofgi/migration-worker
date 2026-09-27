package com.migration.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationStoragePathsTest {
    @TempDir
    Path tempDirectory;

    @Test
    void mapsOneRelativeDirectoryToDatabaseAndWorkerRoots() throws Exception {
        Path workerDataRoot = tempDirectory.resolve("data");
        Path workerBlobRoot = tempDirectory.resolve("blob");
        Files.createDirectories(workerDataRoot);
        Files.createDirectories(workerBlobRoot);

        MigrationStoragePaths paths = new MigrationStoragePaths(
                "/opt/sap/iq161/IQ-16_1/demo/data/",
                "/opt/sap/iq161/IQ-16_1/demo/blob/",
                workerDataRoot,
                workerBlobRoot);

        assertEquals("/opt/sap/iq161/IQ-16_1/demo/data/EMP_1",
                paths.databaseDataDirectory("EMP_1"));
        assertEquals("/opt/sap/iq161/IQ-16_1/demo/blob/EMP_1",
                paths.databaseBlobDirectory("EMP_1"));
        assertTrue(Files.isDirectory(paths.createWorkerDataDirectory("EMP_1")));
        assertTrue(Files.isDirectory(paths.createWorkerBlobDirectory("EMP_1")));
        assertEquals(workerDataRoot.resolve("EMP_1").toAbsolutePath().normalize(),
                paths.workerDataDirectory("EMP_1"));
        assertEquals(workerBlobRoot.resolve("EMP_1").toAbsolutePath().normalize(),
                paths.workerBlobDirectory("EMP_1"));
    }

    @Test
    void rejectsNonUnixDatabasePath() {
        assertThrows(IllegalArgumentException.class, () -> new MigrationStoragePaths(
                "C:\\migration\\data",
                "/opt/migration/blob",
                tempDirectory.resolve("data"),
                tempDirectory.resolve("blob")));
    }

    @Test
    void rejectsDirectoryTraversal() {
        MigrationStoragePaths paths = new MigrationStoragePaths(
                "/opt/migration/data",
                "/opt/migration/blob",
                tempDirectory.resolve("data"),
                tempDirectory.resolve("blob"));

        assertThrows(IllegalArgumentException.class,
                () -> paths.databaseDataDirectory("../outside"));
        assertThrows(IllegalArgumentException.class,
                () -> paths.workerDataFile("safe", "../outside.dat"));
    }
}

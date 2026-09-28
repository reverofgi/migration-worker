package com.migration.util;

import com.migration.config.MigrationParameter;
import com.migration.metadata.MigrationTableInfo;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogUtilTest {
    @Test
    void createsPortableTaskLogFileNameFromExecutionAndTableKey() throws Exception {
        MigrationParameter parameter = MigrationParameter.fromArguments(
                new String[] {"1", "TASK-10", "tester"});
        MigrationTableInfo table = new MigrationTableInfo();
        setField(table, "tableOwner", "DWDB");
        setField(table, "tableId", "TB_LOB_007");
        setField(table, "procOrd", 10);

        String fileName = LogUtil.migrationLogFileName(
                parameter, table, LocalDateTime.of(2026, 9, 28, 14, 30, 5, 123_000_000));

        assertEquals(
                "1_DWDB_TB_LOB_007_10_20260928_143005_123",
                fileName);
    }

    @Test
    void writesTaskLogUnderConfiguredWorkerBatchLogPath() throws Exception {
        Path testLogDirectory = Path.of(
                "target", "test-logs", UUID.randomUUID().toString());
        MigrationParameter parameter = MigrationParameter.fromArguments(
                new String[] {"1", "TASK-10", "tester"});
        MigrationTableInfo table = new MigrationTableInfo();
        setField(table, "tableOwner", "DWDB");
        setField(table, "tableId", "TB_LOB_007");
        setField(table, "procOrd", 10);

        try {
            LogUtil.initializeTaskFileLogging(parameter, table, testLogDirectory);
            LoggerFactory.getLogger(LogUtilTest.class).info("task log path test");

            try (var files = Files.list(testLogDirectory)) {
                List<Path> createdFiles = files.toList();
                assertTrue(createdFiles.stream().anyMatch(path -> path.getFileName().toString()
                        .matches("1_DWDB_TB_LOB_007_10_\\d{8}_\\d{6}_\\d{3}\\.log")));
                assertFalse(createdFiles.stream().anyMatch(path -> path.getFileName().toString()
                        .startsWith(".migration-write-test-")));
            }
        } finally {
            LogUtil.clearTaskFileLogging();
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

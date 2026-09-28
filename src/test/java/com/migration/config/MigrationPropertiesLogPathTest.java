package com.migration.config;

import com.migration.util.LogUtil;
import com.migration.metadata.MigrationTableInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MigrationPropertiesLogPathTest {
    @TempDir
    Path tempDirectory;

    @AfterEach
    void clearGlobalSettings() {
        LogUtil.clearTaskFileLogging();
        MigrationProperties.resetForTest();
    }

    @Test
    void initializesTaskLogPathFromCommonCodeProperty() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:log_property;MODE=MySQL")) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE GPCL_CM_CD_VAL (
                            GRP_CD_ID VARCHAR(30),
                            CD_VAL VARCHAR(100),
                            CD_ADD_INFO_VAL1 VARCHAR(500)
                        )
                        """);
                try (var insert = connection.prepareStatement("""
                        INSERT INTO GPCL_CM_CD_VAL (
                            GRP_CD_ID, CD_VAL, CD_ADD_INFO_VAL1
                        ) VALUES ('MIGRATION', 'WORKER_BATCH_LOG_PATH', ?)
                        """)) {
                    insert.setString(1, tempDirectory.toString());
                    insert.executeUpdate();
                }
            }

            MigrationProperties.initialize(connection);
            MigrationParameter parameter = MigrationParameter.fromArguments(
                    new String[] {"1", "TASK-10", "tester"});
            MigrationTableInfo table = new MigrationTableInfo();
            setField(table, "tableOwner", "DWDB");
            setField(table, "tableId", "TB_LOB_007");
            setField(table, "procOrd", 10);
            LogUtil.initializeTaskFileLogging(parameter, table);

            Path logFileBase = Path.of(MDC.get("migrationLogFile"));
            assertEquals(tempDirectory.toAbsolutePath().normalize(),
                    logFileBase.getParent());
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

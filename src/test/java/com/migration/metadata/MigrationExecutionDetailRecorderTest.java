package com.migration.metadata;

import com.migration.config.MigrationParameter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MigrationExecutionDetailRecorderTest {

    @Test
    void recordsStartCompletionAndFailureForEachMigrationStage() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:execution_detail;MODE=MySQL")) {
            createExecutionDetailTable(connection);
            MigrationParameter parameter = MigrationParameter.fromArguments(
                    new String[] {"1", "TASK-10", "tester"});
            MigrationTableInfo table = tableInfo();
            MigrationExecutionDetailRecorder recorder =
                    new MigrationExecutionDetailRecorder(connection, parameter);

            recorder.start(table, MigrationJobType.UNLOAD);
            assertExecutionState(connection, "UNLOAD", "10", false);

            recorder.complete(table, MigrationJobType.UNLOAD);
            assertExecutionState(connection, "UNLOAD", "20", true);

            recorder.start(table, MigrationJobType.LOAD);
            recorder.fail(table, MigrationJobType.LOAD);
            assertExecutionState(connection, "LOAD", "30", true);
        }
    }

    private static void assertExecutionState(
            java.sql.Connection connection,
            String jobType,
            String expectedStatus,
            boolean endDateExpected) throws Exception {
        try (var statement = connection.prepareStatement("""
                SELECT STRT_DTM, END_DTM, EXEC_STAT_CD, REG_ID, LST_ADJPRN_ID
                  FROM MIG_EXEC_DTL
                 WHERE EXE_ORD = 1
                   AND TABLE_OWNER = 'DWDB'
                   AND TABLE_ID = 'TB_LOB_007'
                   AND PROC_ORD = 10
                   AND JOB_TP_CD = ?
                """)) {
            statement.setString(1, jobType);
            try (var result = statement.executeQuery()) {
                result.next();
                assertNotNull(result.getTimestamp("STRT_DTM"));
                if (endDateExpected) {
                    assertNotNull(result.getTimestamp("END_DTM"));
                } else {
                    assertNull(result.getTimestamp("END_DTM"));
                }
                assertEquals(expectedStatus, result.getString("EXEC_STAT_CD"));
                assertEquals("tester", result.getString("REG_ID"));
                assertEquals("tester", result.getString("LST_ADJPRN_ID"));
            }
        }
    }

    private static void createExecutionDetailTable(java.sql.Connection connection)
            throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE MIG_EXEC_DTL (
                        EXE_ORD DECIMAL(3,0) NOT NULL,
                        TABLE_OWNER VARCHAR(30) NOT NULL,
                        TABLE_ID VARCHAR(30) NOT NULL,
                        PROC_ORD DECIMAL(5,0) NOT NULL,
                        JOB_TP_CD VARCHAR(10) NOT NULL,
                        STRT_DTM TIMESTAMP,
                        END_DTM TIMESTAMP,
                        TOTAL_CNT DECIMAL(15,0),
                        EXEC_STAT_CD CHAR(2),
                        ERR_CD VARCHAR(20),
                        REG_ID VARCHAR(20),
                        REG_DTM TIMESTAMP,
                        LST_ADJPRN_ID VARCHAR(20),
                        LST_ADJ_DTM TIMESTAMP,
                        PRIMARY KEY (EXE_ORD, TABLE_OWNER, TABLE_ID, PROC_ORD, JOB_TP_CD)
                    )
                    """);
        }
    }

    private static MigrationTableInfo tableInfo() throws Exception {
        MigrationTableInfo table = new MigrationTableInfo();
        setField(table, "tableOwner", "DWDB");
        setField(table, "tableId", "TB_LOB_007");
        setField(table, "procOrd", 10);
        return table;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

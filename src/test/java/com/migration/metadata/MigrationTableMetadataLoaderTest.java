package com.migration.metadata;

import com.migration.exception.MetadataException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationTableMetadataLoaderTest {
    private Connection connection;

    @BeforeEach
    void createMetadataTables() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:table_metadata;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS MIG_COL_INFO");
            statement.execute("DROP TABLE IF EXISTS MIG_TBL_INFO");
            statement.execute("""
                    CREATE TABLE MIG_TBL_INFO (
                        TABLE_OWNER VARCHAR(30), TABLE_ID VARCHAR(30),
                        PROC_ORD INT, TASK_ID VARCHAR(30),
                        SRC_DB_PREFIX VARCHAR(30), TGT_DB_PREFIX VARCHAR(30),
                        TABLE_NM VARCHAR(30), MIG_COND VARCHAR(500),
                        MIG_LOAD_MODE VARCHAR(10), LOB_YN CHAR(1), MNGR_ID VARCHAR(20),
                        BIZ_AREA_CD VARCHAR(20), SUBJ_AREA_CD VARCHAR(20),
                        GRP1_CD VARCHAR(20), GRP2_CD VARCHAR(20), GRP3_CD VARCHAR(20),
                        GRP4_CD VARCHAR(20), GRP5_CD VARCHAR(20), USE_YN CHAR(1),
                        REG_ID VARCHAR(20), REG_DTM TIMESTAMP,
                        LST_ADJPRN_ID VARCHAR(20), LST_ADJ_DTM TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE MIG_COL_INFO (
                        TABLE_OWNER VARCHAR(30), TABLE_ID VARCHAR(30),
                        COL_ID VARCHAR(30), COL_ORD INT, COL_NM VARCHAR(30),
                        DATA_TYPE VARCHAR(100), PK_YN CHAR(1), NULL_YN CHAR(1),
                        SUM_YN CHAR(1), MIN_YN CHAR(1), MAX_YN CHAR(1), AVG_YN CHAR(1),
                        HASH_YN CHAR(1), DIST_CNT_YN CHAR(1), NULL_CNT_YN CHAR(1),
                        REG_ID VARCHAR(20), REG_DTM TIMESTAMP,
                        LST_ADJPRN_ID VARCHAR(20), LST_ADJ_DTM TIMESTAMP
                    )
                    """);
        }
    }

    @AfterEach
    void closeConnection() throws Exception {
        connection.close();
    }

    @Test
    void loadsOneMigrationTableBeforeWorkerExecution() throws Exception {
        insertTable("TASK-10", 10);
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO MIG_COL_INFO (
                        TABLE_OWNER, TABLE_ID, COL_ID, COL_ORD, COL_NM, DATA_TYPE,
                        PK_YN, NULL_YN, SUM_YN, MIN_YN, MAX_YN, AVG_YN,
                        HASH_YN, DIST_CNT_YN, NULL_CNT_YN
                    ) VALUES (
                        'DWDB', 'TB_LOB_007', 'ID', 1, 'ID', 'INTEGER',
                        'Y', 'N', 'Y', 'N', 'N', 'N', 'N', 'N', 'N'
                    )
                    """);
        }

        MigrationTableInfo table = new MigrationMetadataLoader()
                .loadMigrationTable(connection, "TASK-10");

        assertEquals("DWDB", table.getTableOwner());
        assertEquals("TB_LOB_007", table.getTableId());
        assertEquals(10, table.getProcOrd());
        assertEquals(1, table.getValidationTargets().size());
    }

    @Test
    void rejectsTaskMappedToMoreThanOneMigrationTable() throws Exception {
        insertTable("TASK-10", 10);
        insertTable("TASK-10", 20);

        assertThrows(MetadataException.class,
                () -> new MigrationMetadataLoader()
                        .loadMigrationTable(connection, "TASK-10"));
    }

    private void insertTable(String taskId, int processOrder) throws Exception {
        try (var insert = connection.prepareStatement("""
                INSERT INTO MIG_TBL_INFO (
                    TABLE_OWNER, TABLE_ID, PROC_ORD, TASK_ID,
                    SRC_DB_PREFIX, TGT_DB_PREFIX, TABLE_NM,
                    MIG_LOAD_MODE, LOB_YN, USE_YN
                ) VALUES (
                    'DWDB', 'TB_LOB_007', ?, ?,
                    'DWDB', 'MIGTGT', 'TB_LOB_007',
                    'T', 'N', 'Y'
                )
                """)) {
            insert.setInt(1, processOrder);
            insert.setString(2, taskId);
            insert.executeUpdate();
        }
    }
}

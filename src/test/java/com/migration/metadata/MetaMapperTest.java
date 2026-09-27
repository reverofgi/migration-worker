package com.migration.metadata;

import com.migration.util.ConnectionUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetaMapperTest {

    @Test
    void deletesResultsOnlyForExecutionTableOwnerTableAndProcessOrder() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:validation_delete;MODE=MySQL")) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE MIG_VRF_RESULT (
                            EXE_ORD DECIMAL(3,0) NOT NULL,
                            TABLE_OWNER VARCHAR(30) NOT NULL,
                            TABLE_ID VARCHAR(30) NOT NULL,
                            PROC_ORD DECIMAL(5,0) NOT NULL,
                            COL_ID VARCHAR(128) NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                        INSERT INTO MIG_VRF_RESULT VALUES
                        (1, 'DWDB', 'TB_SAMPLE', 10, 'COL_A'),
                        (2, 'DWDB', 'TB_SAMPLE', 10, 'COL_B'),
                        (1, 'DWDB', 'TB_SAMPLE', 20, 'COL_A'),
                        (1, 'OTHER', 'TB_SAMPLE', 10, 'COL_A')
                        """);
            }

            try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
                SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(reader);
                try (SqlSession session = factory.openSession(
                        ConnectionUtil.nonClosing(connection))) {
                    int deleted = session.getMapper(MetaMapper.class)
                            .deleteValidationResults(1, "DWDB", "TB_SAMPLE", 10);
                    session.commit();
                    assertEquals(1, deleted);
                }
            }

            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM MIG_VRF_RESULT")) {
                result.next();
                assertEquals(3, result.getInt(1));
            }
        }
    }

    @Test
    void readsSourceValuesAndUpdatesTargetValuesAndVerificationStatus() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:target_results;MODE=MySQL")) {
            createCompleteValidationResultTable(connection);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO MIG_VRF_RESULT (
                            EXE_ORD, TABLE_OWNER, TABLE_ID, PROC_ORD, COL_ID,
                            SRC_ROW_CNT, SRC_SUM_VAL, VRF_STAT_CD
                        ) VALUES (1, 'DWDB', 'TB_SAMPLE', 10, 'COL_A', '5', '15.0', '30')
                        """);
            }

            try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
                SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(reader);
                try (SqlSession session = factory.openSession(
                        ConnectionUtil.nonClosing(connection))) {
                    MetaMapper mapper = session.getMapper(MetaMapper.class);
                    var source = mapper.selectSourceValidationResults(
                            1, "DWDB", "TB_SAMPLE", 10);
                    assertEquals("15.0", source.getFirst().getSum());

                    TargetValidationResult target = new TargetValidationResult(
                            new SourceValidationResult(
                                    "COL_A", "5", "16", null, null, null, null, null),
                            false);
                    mapper.upsertTargetValidationResults(
                            1, "DWDB", "TB_SAMPLE", 10,
                            "manager", "audit", java.util.List.of(target));
                    session.commit();
                }
            }

            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         SELECT SRC_SUM_VAL, TGT_SUM_VAL, VRF_STAT_CD
                           FROM MIG_VRF_RESULT
                          WHERE EXE_ORD = 1 AND TABLE_OWNER = 'DWDB'
                            AND TABLE_ID = 'TB_SAMPLE' AND PROC_ORD = 10
                            AND COL_ID = 'COL_A'
                         """)) {
                result.next();
                assertEquals("15.0", result.getString("SRC_SUM_VAL"));
                assertEquals("16", result.getString("TGT_SUM_VAL"));
                assertEquals("20", result.getString("VRF_STAT_CD"));
            }
        }
    }

    private static void createCompleteValidationResultTable(java.sql.Connection connection)
            throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE MIG_VRF_RESULT (
                        EXE_ORD DECIMAL(3,0) NOT NULL,
                        TABLE_OWNER VARCHAR(30) NOT NULL,
                        TABLE_ID VARCHAR(30) NOT NULL,
                        PROC_ORD DECIMAL(5,0) NOT NULL,
                        COL_ID VARCHAR(128) NOT NULL,
                        MNGR_ID VARCHAR(20),
                        SRC_ROW_CNT VARCHAR(38), SRC_SUM_VAL VARCHAR(38),
                        SRC_MIN_VAL VARCHAR(38), SRC_MAX_VAL VARCHAR(38),
                        SRC_AVG_VAL VARCHAR(38), SRC_HASH_VAL VARCHAR(38),
                        SRC_DIST_CNT_VAL VARCHAR(38), SRC_NULL_CNT_VAL VARCHAR(38),
                        TGT_ROW_CNT VARCHAR(38), TGT_SUM_VAL VARCHAR(38),
                        TGT_MIN_VAL VARCHAR(38), TGT_MAX_VAL VARCHAR(38),
                        TGT_AVG_VAL VARCHAR(38), TGT_HASH_VAL VARCHAR(38),
                        TGT_DIST_CNT_VAL VARCHAR(38), TGT_NULL_CNT_VAL VARCHAR(38),
                        CONF_DTM TIMESTAMP, VRF_STAT_CD CHAR(2), CONF_STAT_CD CHAR(2),
                        REG_ID VARCHAR(20), REG_DTM TIMESTAMP,
                        LST_ADJPRN_ID VARCHAR(20), LST_ADJ_DTM TIMESTAMP,
                        PRIMARY KEY (EXE_ORD, TABLE_OWNER, TABLE_ID, PROC_ORD, COL_ID)
                    )
                    """);
        }
    }
}

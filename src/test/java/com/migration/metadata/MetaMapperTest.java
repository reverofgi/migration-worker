package com.migration.metadata;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaMapperTest {
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:metadata;MODE=MySQL;DB_CLOSE_DELAY=-1");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("""
                    CREATE TABLE GPCL_MIG_TABLE (
                      TABLE_SCHEMA VARCHAR(64), TABLE_NAME VARCHAR(64), PROC_ORD DECIMAL(5,0),
                      TABLE_COMMENT VARCHAR(2048), JOB_ID VARCHAR(30), SPLIT_YN CHAR(1),
                      MIG_COND VARCHAR(4000), MNGR_ID VARCHAR(20))
                    """);
            statement.execute("""
                    CREATE TABLE GPCL_MIG_VRF_TARGET (
                      TABLE_SCHEMA VARCHAR(64), TABLE_NAME VARCHAR(64), COLUMN_NAME VARCHAR(64),
                      ORDINAL_POSITION INTEGER, DATA_TYPE VARCHAR(100), SUM_YN CHAR(1),
                      MIN_YN CHAR(1), MAX_YN CHAR(1), AVG_YN CHAR(1))
                    """);
            statement.executeUpdate("""
                    INSERT INTO GPCL_MIG_TABLE VALUES
                    ('HANKUKERP','TBL_PROJECT',10,'project','100','N',NULL,NULL),
                    ('HANKUKERP','TBL_DEPARTMENT',10,'department','200','N',NULL,NULL),
                    ('HANKUKERP','TBL_CERT_WAY',10,'cert','300','N',NULL,NULL)
                    """);
            statement.executeUpdate("""
                    INSERT INTO GPCL_MIG_VRF_TARGET VALUES
                    ('HANKUKERP','TBL_PROJECT','CODE',3,'VARCHAR(30)','N','N','N','N'),
                    ('HANKUKERP','TBL_PROJECT','CONTRACT_YEAR',2,'INTEGER','Y','Y','Y','Y'),
                    ('HANKUKERP','TBL_PROJECT','ID',1,'BIGINT','N','N','N','N')
                    """);
        }

        try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
        }
        sqlSessionFactory.getConfiguration().setEnvironment(
                new org.apache.ibatis.mapping.Environment(
                        "test",
                        new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),
                        dataSource));
    }

    @Test
    void selectsEachRequiredJobId() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            MetaMapper mapper = session.getMapper(MetaMapper.class);
            assertTable(mapper.selectMigrationTables("100"), "TBL_PROJECT");
            assertTable(mapper.selectMigrationTables("200"), "TBL_DEPARTMENT");
            assertTable(mapper.selectMigrationTables("300"), "TBL_CERT_WAY");
        }
    }

    @Test
    void ordersValidationTargetsAndMapsYnFlags() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            List<ValidationTarget> targets = session.getMapper(MetaMapper.class)
                    .selectValidationTargets("HANKUKERP", "TBL_PROJECT");

            assertEquals(List.of("ID", "CONTRACT_YEAR", "CODE"),
                    targets.stream().map(ValidationTarget::getColumnName).toList());
            assertTrue(targets.get(1).isSumYn());
            assertTrue(targets.get(1).isAvgYn());
            assertFalse(targets.get(0).isSumYn());
        }
    }

    private void assertTable(List<TableInfo> tables, String expectedName) {
        assertEquals(1, tables.size());
        assertEquals("HANKUKERP", tables.get(0).getTableSchema());
        assertEquals(expectedName, tables.get(0).getTableName());
        assertEquals(10, tables.get(0).getProcOrd());
    }
}

package com.migration.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationPropertiesTest {

    @BeforeEach
    void resetProperties() {
        MigrationProperties.resetForTest();
    }

    @Test
    void loadsMigrationGroupOnlyAndInitializesOnce() throws Exception {
        try (Connection connection = metadataConnection("properties_once")) {
            insert(connection, """
                    INSERT INTO GPCL_CM_CD_VAL VALUES
                    ('MIGRATION','01','LOG_LEVEL','INFO'),
                    ('MIGRATION','02','EXPORT_DATA_PATH','/migration/data'),
                    ('OTHER','01','LOG_LEVEL','DEBUG')
                    """);

            assertEquals(2, MigrationProperties.initialize(connection));
            assertEquals("INFO", MigrationProperties.getRequired("LOG_LEVEL"));

            connection.createStatement().executeUpdate("""
                    UPDATE GPCL_CM_CD_VAL
                       SET CD_VAL_ENG_NM = 'ERROR'
                     WHERE GRP_CD_ID = 'MIGRATION'
                       AND CD_VAL_NM = 'LOG_LEVEL'
                    """);
            assertEquals(2, MigrationProperties.initialize(connection));
            assertEquals("INFO", MigrationProperties.getRequired("LOG_LEVEL"));
        }
    }

    @Test
    void rejectsBlankAndDuplicateProperties() throws Exception {
        try (Connection blank = metadataConnection("properties_blank")) {
            insert(blank, """
                    INSERT INTO GPCL_CM_CD_VAL VALUES
                    ('MIGRATION','01','LOG_LEVEL',' ')
                    """);
            SQLException error = assertThrows(
                    SQLException.class,
                    () -> MigrationProperties.initialize(blank));
            assertTrue(error.getMessage().contains("LOG_LEVEL"));
        }

        MigrationProperties.resetForTest();
        try (Connection duplicate = metadataConnection("properties_duplicate")) {
            insert(duplicate, """
                    INSERT INTO GPCL_CM_CD_VAL VALUES
                    ('MIGRATION','01','LOG_LEVEL','INFO'),
                    ('MIGRATION','02','LOG_LEVEL','DEBUG')
                    """);
            SQLException error = assertThrows(
                    SQLException.class,
                    () -> MigrationProperties.initialize(duplicate));
            assertTrue(error.getMessage().contains("Duplicate"));
        }
    }

    private Connection metadataConnection(String databaseName) throws Exception {
        Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "meta-secret");
        connection.createStatement().execute("""
                CREATE TABLE GPCL_CM_CD_VAL (
                    GRP_CD_ID VARCHAR(30) NOT NULL,
                    CD_VAL VARCHAR(10) NOT NULL,
                    CD_VAL_NM VARCHAR(100) NOT NULL,
                    CD_VAL_ENG_NM VARCHAR(100),
                    PRIMARY KEY (GRP_CD_ID, CD_VAL)
                )
                """);
        return connection;
    }

    private void insert(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}

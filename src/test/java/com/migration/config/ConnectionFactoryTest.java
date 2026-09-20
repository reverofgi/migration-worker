package com.migration.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionFactoryTest {

    @BeforeEach
    void resetProperties() {
        MigrationProperties.resetForTest();
    }

    @Test
    void createsConnectionsFromInitializedMigrationProperties() throws Exception {
        String metaUrl = "jdbc:h2:mem:factory_meta;MODE=MySQL;DB_CLOSE_DELAY=-1";
        initializeMetadata(metaUrl, """
                INSERT INTO GPCL_CM_CD_VAL VALUES
                ('MIGRATION','01','ASIS_JDBC_URL','jdbc:h2:mem:factory_asis'),
                ('MIGRATION','02','ASIS_USER','sa'),
                ('MIGRATION','03','ASIS_PWD','asis-secret'),
                ('MIGRATION','04','TOBE_JDBC_URL','jdbc:h2:mem:factory_tobe'),
                ('MIGRATION','05','TOBE_USER','sa'),
                ('MIGRATION','06','TOBE_PWD','tobe-secret'),
                ('OTHER','01','ASIS_USER','ignored')
                """);
        MigrationConfig config = MigrationConfig.fromArguments(new String[]{"1", "100"});
        ConnectionFactory factory = new ConnectionFactory(
                config, metaUrl, "sa", "meta-secret");

        try (Connection meta = factory.createMetaConnection()) {
            assertEquals(6, MigrationProperties.initialize(meta));
        }
        try (Connection asis = factory.createAsisConnection();
             Connection tobe = factory.createTobeConnection()) {
            assertTrue(asis.isValid(1));
            assertTrue(tobe.isValid(1));
            assertEquals(config, factory.getConfig());
        }
    }

    @Test
    void rejectsConnectionCreationBeforePropertiesInitialization() throws Exception {
        MigrationConfig config = MigrationConfig.fromArguments(new String[]{"1", "100"});
        ConnectionFactory factory = new ConnectionFactory(
                config, "jdbc:h2:mem:unused", "sa", "password");

        SQLException error = assertThrows(
                SQLException.class,
                factory::createAsisConnection);
        assertTrue(error.getMessage().contains("not initialized"));
    }

    @Test
    void rejectsMissingRequiredConnectionProperty() throws Exception {
        String metaUrl = "jdbc:h2:mem:factory_missing;MODE=MySQL;DB_CLOSE_DELAY=-1";
        initializeMetadata(metaUrl, "");
        MigrationConfig config = MigrationConfig.fromArguments(new String[]{"1", "100"});
        ConnectionFactory factory = new ConnectionFactory(
                config, metaUrl, "sa", "meta-secret");

        try (Connection meta = factory.createMetaConnection()) {
            assertEquals(0, MigrationProperties.initialize(meta));
        }
        SQLException error = assertThrows(
                SQLException.class,
                factory::createAsisConnection);
        assertTrue(error.getMessage().contains("ASIS_JDBC_URL"));
    }

    @Test
    void validatesMetaBootstrapConstants() throws Exception {
        MigrationConfig config = MigrationConfig.fromArguments(new String[]{"1", "100"});
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ConnectionFactory(config, " ", "sa", "password"));
        assertTrue(error.getMessage().contains("META_JDBC_URL"));
    }

    private void initializeMetadata(String url, String insertSql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "meta-secret");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE GPCL_CM_CD_VAL (
                        GRP_CD_ID VARCHAR(30) NOT NULL,
                        CD_VAL VARCHAR(10) NOT NULL,
                        CD_VAL_NM VARCHAR(100) NOT NULL,
                        CD_VAL_ENG_NM VARCHAR(100),
                        PRIMARY KEY (GRP_CD_ID, CD_VAL)
                    )
                    """);
            if (!insertSql.isBlank()) {
                statement.executeUpdate(insertSql);
            }
        }
    }
}

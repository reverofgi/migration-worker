package com.migration.metadata;

import com.migration.exception.MetadataException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationMetadataLoaderTest {
    private Connection connection;

    @BeforeEach
    void createMetadataTable() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:prefixes;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS MIG_TBL_INFO");
            statement.execute("""
                    CREATE TABLE MIG_TBL_INFO (
                        TASK_ID VARCHAR(30),
                        SRC_DB_PREFIX VARCHAR(10),
                        TGT_DB_PREFIX VARCHAR(10)
                    )
                    """);
        }
    }

    @AfterEach
    void closeConnection() throws Exception {
        connection.close();
    }

    @Test
    void loadsOneDistinctPrefixPairForTask() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO MIG_TBL_INFO VALUES
                    ('100', 'DWDB   ', 'MIGTGT   '),
                    ('100', 'DWDB', 'MIGTGT'),
                    ('200', 'OTHER', 'OTHER_TGT')
                    """);
        }

        MigrationDatabasePrefixes prefixes = new MigrationMetadataLoader()
                .loadDatabasePrefixes(connection, "100");

        assertEquals("DWDB", prefixes.getSourcePrefix());
        assertEquals("MIGTGT", prefixes.getTargetPrefix());
    }

    @Test
    void rejectsTaskThatDoesNotExist() {
        assertThrows(MetadataException.class,
                () -> new MigrationMetadataLoader()
                        .loadDatabasePrefixes(connection, "300"));
    }

    @Test
    void rejectsConflictingPrefixPairsForOneTask() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO MIG_TBL_INFO VALUES
                    ('100', 'DWDB', 'MIGTGT'),
                    ('100', 'DWDB2', 'MIGTGT')
                    """);
        }

        assertThrows(MetadataException.class,
                () -> new MigrationMetadataLoader()
                        .loadDatabasePrefixes(connection, "100"));
    }
}

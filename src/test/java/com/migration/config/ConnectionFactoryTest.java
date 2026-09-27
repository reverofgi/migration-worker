package com.migration.config;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionFactoryTest {

    @Test
    void combinesDatabasePrefixAndConnectionPropertySuffix() throws Exception {
        assertEquals("DWDB_SOURCE_JDBC_URL",
                ConnectionFactory.propertyKey("DWDB", "SOURCE_JDBC_URL"));
        assertEquals("DWDB_SOURCE_USER",
                ConnectionFactory.propertyKey("DWDB", "SOURCE_USER"));
        assertEquals("DWDB_SOURCE_PWD",
                ConnectionFactory.propertyKey("DWDB", "SOURCE_PWD"));
        assertEquals("MIGTGT_TARGET_JDBC_URL",
                ConnectionFactory.propertyKey("MIGTGT", "TARGET_JDBC_URL"));
        assertEquals("MIGTGT_TARGET_USER",
                ConnectionFactory.propertyKey("MIGTGT", "TARGET_USER"));
        assertEquals("MIGTGT_TARGET_PWD",
                ConnectionFactory.propertyKey(" MIGTGT ", "TARGET_PWD"));
    }

    @Test
    void rejectsBlankDatabasePrefix() {
        assertThrows(SQLException.class,
                () -> ConnectionFactory.propertyKey(" ", "SOURCE_USER"));
    }
}

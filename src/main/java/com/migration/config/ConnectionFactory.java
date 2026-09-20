package com.migration.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Creates the three logical Batch Group connections.
 *
 * Phase 1 intentionally does not read credentials from a real metadata table
 * because GPCL_CM_CD_VAL and its exact column/value conventions are to be
 * implemented in Phase 2.
 *
 * Connection creation is therefore an explicit extension point.
 */
public final class ConnectionFactory {

    private final MigrationConfig config;

    public ConnectionFactory(MigrationConfig config) {
        this.config = config;
    }

    public Connection createMetaConnection() throws SQLException {
        return createConfiguredConnection(
                "META_JDBC_URL",
                "META_USER",
                "META_PWD");
    }

    public Connection createAsisConnection() throws SQLException {
        return createConfiguredConnection(
                "ASIS_JDBC_URL",
                "ASIS_USER",
                "ASIS_PWD");
    }

    public Connection createTobeConnection() throws SQLException {
        return createConfiguredConnection(
                "TOBE_JDBC_URL",
                "TOBE_USER",
                "TOBE_PWD");
    }

    /**
     * Phase 1 placeholder.
     *
     * The Master Prompt requires connection settings to be obtained from
     * MariaDB common-code metadata, but its exact GPCL_CM_CD_VAL schema/value
     * convention is not provided sufficiently for a real implementation here.
     */
    private Connection createConfiguredConnection(
            String urlKey,
            String userKey,
            String passwordKey) throws SQLException {

        throw new SQLException(
                "Connection configuration is not implemented in Phase 1. "
                        + "TODO Phase 2: load "
                        + urlKey + ", " + userKey + ", " + passwordKey
                        + " from the confirmed GPCL_CM_CD_VAL schema.");
    }

    public MigrationConfig getConfig() {
        return config;
    }
}

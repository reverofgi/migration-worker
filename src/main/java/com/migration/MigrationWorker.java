package com.migration;

import com.migration.config.ConnectionFactory;
import com.migration.config.MigrationConfig;
import com.migration.config.ExitCode;
import com.migration.exception.InitializationException;
import com.migration.exception.MigrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Batch Group orchestration skeleton.
 *
 * Phase 1 deliberately does not implement:
 * - Metadata query
 * - Physical schema query
 * - Extract
 * - Load
 * - Validation
 * - Result persistence
 *
 * One JOB_ID is one Batch Group.
 * Connections are owned by this Worker instance and live for the Batch Group.
 */
public final class MigrationWorker {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MigrationWorker.class);

    private final MigrationConfig config;
    private final ConnectionFactory connectionFactory;

    /*
     * Batch Group-scoped connections.
     * They are intentionally instance fields, not static global connections.
     */
    private Connection metaConnection;
    private Connection asisConnection;
    private Connection tobeConnection;

    private MigrationWorker(
            MigrationConfig config,
            ConnectionFactory connectionFactory) {
        this.config = config;
        this.connectionFactory = connectionFactory;
    }

    /**
     * Creates a Worker from the required command-line arguments.
     *
     * Expected:
     *   args[0] = EXEC_SEQ
     *   args[1] = JOB_ID
     *   args[2] = EXEC_USER (optional, default SYSTEM)
     */
    public static MigrationWorker fromArguments(String[] args)
            throws MigrationException {

        MigrationConfig config = MigrationConfig.fromArguments(args);

        return new MigrationWorker(
                config,
                new ConnectionFactory(config));
    }

    /**
     * Executes one Batch Group.
     */
    public void execute() throws MigrationException {
        LOGGER.info(
                "Migration Worker started. EXEC_SEQ={}, JOB_ID={}, EXEC_USER={}",
                config.getExecSeq(),
                config.getJobId(),
                config.getExecUser());

        try {
            initializeConnections();

            /*
             * Phase 1 stops here.
             *
             * Phase 2 will:
             *   1. query GPCL_MIG_TABLE by JOB_ID
             *   2. order by PROC_ORD, TABLE_SCHEMA, TABLE_NAME
             *   3. process tables sequentially
             */
            LOGGER.info(
                    "Phase 1 skeleton initialized successfully. "
                            + "Table processing is intentionally not implemented yet.");

        } finally {
            closeConnections();
        }

        LOGGER.info("Migration Worker finished successfully.");
    }

    private void initializeConnections() throws InitializationException {
        try {
            metaConnection = connectionFactory.createMetaConnection();
            asisConnection = connectionFactory.createAsisConnection();
            tobeConnection = connectionFactory.createTobeConnection();

            LOGGER.info("Three Batch Group-scoped connections initialized.");
        } catch (SQLException e) {
            closeConnections();
            throw new InitializationException(
                    "Failed to initialize Batch Group database connections.",
                    ExitCode.INITIALIZATION_ERROR.getCode(),
                    e);
        }
    }

    private void closeConnections() {
        closeConnection("META", metaConnection);
        closeConnection("ASIS", asisConnection);
        closeConnection("TOBE", tobeConnection);

        metaConnection = null;
        asisConnection = null;
        tobeConnection = null;
    }

    private void closeConnection(String name, Connection connection) {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
            LOGGER.info("{} connection closed.", name);
        } catch (SQLException e) {
            LOGGER.warn("{} connection close failed.", name, e);
        }
    }

    /*
     * Package-private accessors are intentionally provided only for the
     * upcoming phases/tests. They avoid static/global connection access.
     */
    Connection getMetaConnection() {
        return metaConnection;
    }

    Connection getAsisConnection() {
        return asisConnection;
    }

    Connection getTobeConnection() {
        return tobeConnection;
    }

    MigrationConfig getConfig() {
        return config;
    }
}

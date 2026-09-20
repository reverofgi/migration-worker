package com.migration;

import com.migration.config.ExitCode;
import com.migration.exception.MigrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point.
 *
 * Phase 1 responsibility:
 * - Parse CLI arguments.
 * - Create and execute MigrationWorker.
 * - Convert the result/exception into an exit code.
 *
 * Business migration logic must not be implemented here.
 */
public final class MigrationWorkerApplication {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MigrationWorkerApplication.class);

    private MigrationWorkerApplication() {
        // Utility class.
    }

    public static void main(String[] args) {
        int exitCode = ExitCode.SUCCESS.getCode();

        try {
            MigrationWorker worker = MigrationWorker.fromArguments(args);
            worker.execute();
        } catch (MigrationException e) {
            exitCode = e.getExitCode();
            LOGGER.error("Migration Worker failed. exitCode={}", exitCode, e);
        } catch (Exception e) {
            exitCode = ExitCode.SYSTEM_ERROR.getCode();
            LOGGER.error("Unexpected Migration Worker failure.", e);
        }

        System.exit(exitCode);
    }
}

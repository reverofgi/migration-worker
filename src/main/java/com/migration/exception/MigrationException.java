package com.migration.exception;

/**
 * Base checked exception for Migration Worker failures.
 */
public class MigrationException extends Exception {

    private final int exitCode;

    public MigrationException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    public MigrationException(String message, int exitCode, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    public int getExitCode() {
        return exitCode;
    }
}

package com.migration.exception;

public final class InitializationException extends MigrationException {

    public InitializationException(
            String message,
            int exitCode,
            Throwable cause) {
        super(message, exitCode, cause);
    }
}

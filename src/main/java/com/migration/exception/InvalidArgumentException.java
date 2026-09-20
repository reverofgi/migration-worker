package com.migration.exception;

import com.migration.config.ExitCode;

public final class InvalidArgumentException extends MigrationException {

    public InvalidArgumentException(String message) {
        super(message, ExitCode.INVALID_ARGUMENT.getCode());
    }

    public InvalidArgumentException(String message, Throwable cause) {
        super(message, ExitCode.INVALID_ARGUMENT.getCode(), cause);
    }
}

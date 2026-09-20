package com.migration.exception;

import com.migration.config.ExitCode;

public class ValidationException extends MigrationException {

    public ValidationException(String message) {
        super(message, ExitCode.VALIDATION_ERROR.getCode());
    }

    public ValidationException(String message, Throwable cause) {
        super(message, ExitCode.VALIDATION_ERROR.getCode(), cause);
    }
}

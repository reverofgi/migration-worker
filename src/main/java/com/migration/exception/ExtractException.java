package com.migration.exception;

import com.migration.config.ExitCode;

public class ExtractException extends MigrationException {

    public ExtractException(String message) {
        super(message, ExitCode.EXTRACT_ERROR.getCode());
    }

    public ExtractException(String message, Throwable cause) {
        super(message, ExitCode.EXTRACT_ERROR.getCode(), cause);
    }
}

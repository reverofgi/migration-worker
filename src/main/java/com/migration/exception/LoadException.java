package com.migration.exception;

import com.migration.config.ExitCode;

public class LoadException extends MigrationException {

    public LoadException(String message) {
        super(message, ExitCode.LOAD_ERROR.getCode());
    }

    public LoadException(String message, Throwable cause) {
        super(message, ExitCode.LOAD_ERROR.getCode(), cause);
    }
}

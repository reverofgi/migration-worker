package com.migration.exception;

import com.migration.config.ExitCode;

public class SchemaException extends MigrationException {

    public SchemaException(String message) {
        super(message, ExitCode.SCHEMA_ERROR.getCode());
    }

    public SchemaException(String message, Throwable cause) {
        super(message, ExitCode.SCHEMA_ERROR.getCode(), cause);
    }
}

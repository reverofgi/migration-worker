package com.migration.exception;

import com.migration.config.ExitCode;

public class MetadataException extends MigrationException {

    public MetadataException(String message) {
        super(message, ExitCode.METADATA_ERROR.getCode());
    }

    public MetadataException(String message, Throwable cause) {
        super(message, ExitCode.METADATA_ERROR.getCode(), cause);
    }
}

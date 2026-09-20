package com.migration.config;

/**
 * 요구사항에 정의된 프로세스 종료 코드.
 */
public enum ExitCode {

    SUCCESS(0),
    INVALID_ARGUMENT(1),
    INITIALIZATION_ERROR(2),
    EXTRACT_ERROR(3),
    LOAD_ERROR(4),
    VALIDATION_MISMATCH(5),
    VALIDATION_ERROR(6),
    SCHEMA_ERROR(7),
    METADATA_ERROR(8),
    SYSTEM_ERROR(9);

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

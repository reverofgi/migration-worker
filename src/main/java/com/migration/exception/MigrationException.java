package com.migration.exception;

/**
 * Migration Worker 오류에 사용하는 최상위 검사 예외.
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

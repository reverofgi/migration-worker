package com.migration.util;

/**
 * Logging utility extension point.
 *
 * Phase 1 keeps logging through SLF4J directly in the application classes.
 * Structured MDC/context helpers can be added in a later phase once the
 * execution context fields are finalized.
 */
public final class LogUtil {

    private LogUtil() {
    }

    public static String tableName(String tableSchema, String tableName) {
        return tableSchema + "." + tableName;
    }
}

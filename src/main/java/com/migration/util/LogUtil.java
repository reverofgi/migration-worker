package com.migration.util;

/**
 * 로깅 유틸리티 확장 지점.
 *
 */
public final class LogUtil {

    private LogUtil() {
    }

    public static String tableName(String tableSchema, String tableName) {
        return tableSchema + "." + tableName;
    }
}

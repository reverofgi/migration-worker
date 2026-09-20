package com.migration.util;

/**
 * SQL utility extension point.
 *
 * Phase 1 intentionally does not build Sybase IQ SQL.
 * Dynamic Identifier validation and SQL generation belong to later phases.
 */
public final class SqlUtil {

    private SqlUtil() {
    }

    /**
     * Minimal identifier check for future SQL-builder use.
     * This is not a complete Sybase IQ identifier policy.
     */
    public static boolean isSimpleIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        if (!Character.isLetter(value.charAt(0))
                && value.charAt(0) != '_') {
            return false;
        }

        for (int i = 1; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return false;
            }
        }

        return true;
    }
}

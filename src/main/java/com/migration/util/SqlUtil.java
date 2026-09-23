package com.migration.util;

/**
 * SQL 유틸리티 확장 지점.
 *
 */
public final class SqlUtil {

    private SqlUtil() {
    }

    public static String escapeLiteral(
            String strValue) {

        if (strValue == null) {
            return "";
        }

        return strValue.replace("'", "''");
    }

    public static String validateIdentifier(
            String strIdentifier) {

        if (strIdentifier == null
                || strIdentifier.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "SQL identifier is empty.");
        }

        String strValue =
                strIdentifier.trim();

        if (!strValue.matches(
                "[A-Za-z_][A-Za-z0-9_]*"
                + "(\\.[A-Za-z_][A-Za-z0-9_]*)?")) {

            throw new IllegalArgumentException(
                    "Invalid SQL identifier: "
                    + strIdentifier);
        }

        return strValue;
    }
    
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

package com.migration.config;

import com.migration.util.SqlUtil;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/** Native Extract와 LOAD TABLE이 공유하는 구분자 및 NULL 설정. */
public record MigrationFileFormat(
        String nullToken,
        String bfileErrorToken,
        String columnDelimiter,
        String rowDelimiter) {

    private static final int MAX_ROW_DELIMITER_BYTES = 4;

    public MigrationFileFormat {
        requireText(nullToken, "NULL_TOKEN");
        requireText(bfileErrorToken, "BFILE_ERROR_TOKEN");
        validateDelimiter(columnDelimiter, "COLUMN_DELIMITER", 1);
        validateDelimiter(rowDelimiter, "ROW_DELIMITER", MAX_ROW_DELIMITER_BYTES);
        if (columnDelimiter.startsWith(rowDelimiter)
                || rowDelimiter.startsWith(columnDelimiter)) {
            throw new IllegalArgumentException(
                    "COLUMN_DELIMITER and ROW_DELIMITER must not share a prefix.");
        }
    }

    public static MigrationFileFormat load() throws SQLException {
        try {
            return new MigrationFileFormat(
                    MigrationProperties.getRequired("NULL_TOKEN"),
                    MigrationProperties.getRequired("BFILE_ERROR_TOKEN"),
                    decodeDelimiter(MigrationProperties.getRequired("COLUMN_DELIMITER")),
                    decodeDelimiter(MigrationProperties.getRequired("ROW_DELIMITER")));
        } catch (IllegalArgumentException e) {
            throw new SQLException("Migration file format configuration is invalid.", e);
        }
    }

    /** LOAD TABLE delimiter 문자열 리터럴에 넣을 실제 구분자를 반환한다. */
    public static String toSqlDelimiter(String delimiter) {
        return SqlUtil.escapeLiteral(delimiter);
    }

    static String decodeDelimiter(String configured) {
        if (configured == null) {
            throw new IllegalArgumentException("Delimiter must not be null.");
        }
        StringBuilder decoded = new StringBuilder();
        for (int index = 0; index < configured.length(); index++) {
            char current = configured.charAt(index);
            if (current != '\\') {
                decoded.append(current);
                continue;
            }
            if (++index >= configured.length()) {
                throw new IllegalArgumentException("Incomplete delimiter escape sequence.");
            }
            char escaped = configured.charAt(index);
            switch (escaped) {
                case 'n' -> decoded.append('\n');
                case 'r' -> decoded.append('\r');
                case 't' -> decoded.append('\t');
                case '\\' -> decoded.append('\\');
                case 'x' -> {
                    if (index + 2 >= configured.length()) {
                        throw new IllegalArgumentException(
                                "Incomplete hexadecimal delimiter escape sequence.");
                    }
                    String hex = configured.substring(index + 1, index + 3);
                    try {
                        decoded.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "Invalid hexadecimal delimiter escape sequence: " + hex, e);
                    }
                    index += 2;
                }
                default -> throw new IllegalArgumentException(
                        "Unsupported delimiter escape sequence: \\" + escaped);
            }
        }
        return decoded.toString();
    }

    private static void validateDelimiter(String value, String name, int maxBytes) {
        requireText(value, name);
        byte[] ascii = value.getBytes(StandardCharsets.US_ASCII);
        if (ascii.length > maxBytes || !value.equals(new String(ascii, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException(
                    name + " must be 1 to " + maxBytes + " ASCII bytes.");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty.");
        }
    }
}

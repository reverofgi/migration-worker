package com.migration.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationFileFormatTest {

    @Test
    void decodesConfiguredEscapeSequencesToActualDelimiters() {
        assertEquals("\n", MigrationFileFormat.decodeDelimiter("\\n"));
        assertEquals("\r\n", MigrationFileFormat.decodeDelimiter("\\r\\n"));
        assertEquals("\t", MigrationFileFormat.decodeDelimiter("\\x09"));
    }

    @Test
    void preservesActualControlDelimiterForLoadTableLiteral() {
        assertEquals("|", MigrationFileFormat.toSqlDelimiter("|"));
        assertEquals("\n", MigrationFileFormat.toSqlDelimiter("\n"));
        assertEquals("\r\n", MigrationFileFormat.toSqlDelimiter("\r\n"));
        assertEquals("''", MigrationFileFormat.toSqlDelimiter("'"));
    }

    @Test
    void rejectsUnsupportedEscapeSequence() {
        assertThrows(IllegalArgumentException.class,
                () -> MigrationFileFormat.decodeDelimiter("\\u000a"));
    }

    @Test
    void rejectsDelimitersThatSharePrefix() {
        assertThrows(IllegalArgumentException.class,
                () -> new MigrationFileFormat("NULL", "ERROR", "|", "|#"));
    }
}

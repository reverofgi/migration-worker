package com.migration.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlUtilTest {

    @Test
    void comparesMetadataIdentifiersIgnoringPaddingAndCase() {
        assertTrue(SqlUtil.identifiersEqual("DWDB", "dwdb          "));
        assertTrue(SqlUtil.identifiersEqual("TB_LOB_007", "TB_LOB_007   "));
    }

    @Test
    void rejectsDifferentOrMissingIdentifiers() {
        assertFalse(SqlUtil.identifiersEqual("DWDB", "MIGTGT"));
        assertFalse(SqlUtil.identifiersEqual(null, "DWDB"));
        assertFalse(SqlUtil.identifiersEqual("DWDB", null));
    }
}

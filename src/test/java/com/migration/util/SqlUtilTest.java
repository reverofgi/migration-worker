package com.migration.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlUtilTest {

    @Test
    void acceptsSimpleIdentifier() {
        assertTrue(SqlUtil.isSimpleIdentifier("TBL_PROJECT"));
        assertTrue(SqlUtil.isSimpleIdentifier("_TABLE1"));
    }

    @Test
    void rejectsUnsafeIdentifier() {
        assertFalse(SqlUtil.isSimpleIdentifier("TBL_PROJECT;DROP TABLE X"));
        assertFalse(SqlUtil.isSimpleIdentifier("A.B"));
        assertFalse(SqlUtil.isSimpleIdentifier(""));
        assertFalse(SqlUtil.isSimpleIdentifier(null));
    }
}

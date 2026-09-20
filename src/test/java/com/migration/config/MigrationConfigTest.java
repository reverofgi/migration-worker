package com.migration.config;

import com.migration.exception.InvalidArgumentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationConfigTest {

    @Test
    void parsesRequiredArgumentsAndDefaultUser() throws Exception {
        MigrationConfig config =
                MigrationConfig.fromArguments(new String[]{"1001", "100"});

        assertEquals(1001L, config.getExecSeq());
        assertEquals("100", config.getJobId());
        assertEquals("SYSTEM", config.getExecUser());
    }

    @Test
    void parsesExplicitUser() throws Exception {
        MigrationConfig config =
                MigrationConfig.fromArguments(
                        new String[]{"1001", "100", "HONG"});

        assertEquals("HONG", config.getExecUser());
    }

    @Test
    void rejectsInvalidArgumentCount() {
        assertThrows(
                InvalidArgumentException.class,
                () -> MigrationConfig.fromArguments(new String[]{"1001"}));
    }

    @Test
    void rejectsNonNumericExecSeq() {
        assertThrows(
                InvalidArgumentException.class,
                () -> MigrationConfig.fromArguments(
                        new String[]{"ABC", "100", "SYSTEM"}));
    }

    @Test
    void rejectsEmptyJobId() {
        assertThrows(
                InvalidArgumentException.class,
                () -> MigrationConfig.fromArguments(
                        new String[]{"1001", "  ", "SYSTEM"}));
    }
}

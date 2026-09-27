package com.migration.config;

import com.migration.exception.InvalidArgumentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationParameterTest {

    @Test
    void parsesExecutionOrderTaskAndManager() throws Exception {
        MigrationParameter parameter = MigrationParameter.fromArguments(
                new String[] {"1", "100", "operator"});

        assertEquals(1, parameter.getExecOrd());
        assertEquals("100", parameter.getTaskId());
        assertEquals("operator", parameter.getMngrId());
    }

    @Test
    void usesSystemAsDefaultManager() throws Exception {
        MigrationParameter parameter = MigrationParameter.fromArguments(
                new String[] {"2", "200"});

        assertEquals("SYSTEM", parameter.getMngrId());
    }

    @Test
    void rejectsInvalidExecutionOrderBeforeDatabaseAccess() {
        assertThrows(InvalidArgumentException.class,
                () -> MigrationParameter.fromArguments(
                        new String[] {"not-a-number", "300"}));
    }
}

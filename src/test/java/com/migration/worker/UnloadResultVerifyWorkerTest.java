package com.migration.worker;

import com.migration.exception.ValidationException;
import com.migration.metadata.ValidationTarget;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnloadResultVerifyWorkerTest {

    @Test
    void convertsNumericStringAndTimestampAggregatesToText() throws Exception {
        assertEquals("123.4500", UnloadResultVerifyWorker.toText(
                new BigDecimal("123.4500"), "C0_SUM"));
        assertEquals("SRC007", UnloadResultVerifyWorker.toText(
                "SRC007", "C25_MIN"));
        assertEquals("2026-09-27 17:51:40.0", UnloadResultVerifyWorker.toText(
                Timestamp.valueOf("2026-09-27 17:51:40"), "C26_MIN"));
        assertNull(UnloadResultVerifyWorker.toText(null, "C0_MIN"));
    }

    @Test
    void rejectsAggregateThatExceedsVarcharLength() {
        assertThrows(ValidationException.class,
                () -> UnloadResultVerifyWorker.toText("X".repeat(39), "C0_MIN"));
    }

    @Test
    void selectsColumnsWithAnyValidationFlagEnabled() throws Exception {
        ValidationTarget sum = target("SUM_COL", "sumYn");
        ValidationTarget min = target("MIN_COL", "minYn");
        ValidationTarget max = target("MAX_COL", "maxYn");
        ValidationTarget avg = target("AVG_COL", "avgYn");
        ValidationTarget distinctOnly = target("DIST_COL", "distCntYn");
        ValidationTarget nullOnly = target("NULL_COL", "nullCntYn");
        ValidationTarget hashOnly = target("HASH_COL", "hashYn");
        ValidationTarget disabled = target("DISABLED_COL", null);

        List<ValidationTarget> selected =
                UnloadResultVerifyWorker.enabledValidationTargets(List.of(
                        sum, distinctOnly, min, nullOnly, max, disabled, avg, hashOnly));

        assertEquals(List.of(
                        "SUM_COL", "DIST_COL", "MIN_COL", "NULL_COL",
                        "MAX_COL", "AVG_COL", "HASH_COL"),
                selected.stream().map(ValidationTarget::getColId).toList());
    }

    private static ValidationTarget target(String colId, String enabledField)
            throws Exception {
        ValidationTarget target = new ValidationTarget();
        set(target, "colId", colId);
        if (enabledField != null) {
            set(target, enabledField, true);
        }
        return target;
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

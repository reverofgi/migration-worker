package com.migration.worker;

import com.migration.metadata.SourceValidationResult;
import com.migration.metadata.ValidationTarget;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationResultComparatorTest {

    @Test
    void comparesNumericValuesByMagnitudeInsteadOfTextScale() throws Exception {
        ValidationTarget target = target("DECIMAL(20, 4)", "sumYn", "avgYn");
        SourceValidationResult source = values("10", "1.0", null, null, "2.500");
        SourceValidationResult loaded = values("10.0", "1.00", null, null, "2.5");

        assertTrue(ValidationResultComparator.mismatchedMetrics(
                target, source, loaded).isEmpty());
    }

    @Test
    void reportsRowCountAndTextMinimumMismatches() throws Exception {
        ValidationTarget target = target("VARCHAR(30)", "minYn");
        SourceValidationResult source = values("10", null, "AAA", null, null);
        SourceValidationResult loaded = values("9", null, "AAB", null, null);

        assertEquals(List.of("ROW_COUNT", "MIN"),
                ValidationResultComparator.mismatchedMetrics(target, source, loaded));
    }

    @Test
    void treatsTwoNullAggregatesAsEqualAndOneNullAsMismatch() throws Exception {
        ValidationTarget target = target("INTEGER", "maxYn");
        SourceValidationResult source = values("0", null, null, null, null);
        SourceValidationResult bothNull = values("0", null, null, null, null);
        SourceValidationResult targetHasValue = values("0", null, null, "1", null);

        assertTrue(ValidationResultComparator.mismatchedMetrics(
                target, source, bothNull).isEmpty());
        assertEquals(List.of("MAX"), ValidationResultComparator.mismatchedMetrics(
                target, source, targetHasValue));
    }

    private static ValidationTarget target(String dataType, String... flags) throws Exception {
        ValidationTarget target = new ValidationTarget();
        set(target, "dataType", dataType);
        for (String flag : flags) {
            set(target, flag, true);
        }
        return target;
    }

    private static SourceValidationResult values(
            String rowCount, String sum, String min, String max, String avg) {
        return new SourceValidationResult(
                "COL_A", rowCount, sum, min, max, avg, null, null);
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

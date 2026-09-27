package com.migration.worker;

import com.migration.exception.ValidationException;
import com.migration.metadata.SourceValidationResult;
import com.migration.metadata.ValidationTarget;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** AS-IS와 TO-BE의 컬럼별 집계값을 데이터 성격에 맞게 비교한다. */
final class ValidationResultComparator {
    private static final Set<String> NUMERIC_TYPES = Set.of(
            "TINYINT", "SMALLINT", "INTEGER", "INT", "BIGINT",
            "UNSIGNED TINYINT", "TINYINT UNSIGNED",
            "UNSIGNED SMALLINT", "SMALLINT UNSIGNED",
            "UNSIGNED INTEGER", "UNSIGNED INT", "INTEGER UNSIGNED", "INT UNSIGNED",
            "UNSIGNED BIGINT", "BIGINT UNSIGNED",
            "DECIMAL", "DEC", "NUMERIC", "REAL", "FLOAT", "DOUBLE",
            "DOUBLE PRECISION");

    private ValidationResultComparator() {
    }

    static List<String> mismatchedMetrics(
            ValidationTarget target,
            SourceValidationResult source,
            SourceValidationResult targetValues) throws ValidationException {
        List<String> mismatches = new ArrayList<>();
        compareNumeric("ROW_COUNT", source.getRowCount(), targetValues.getRowCount(), mismatches);
        if (target.isSumYn()) {
            compareNumeric("SUM", source.getSum(), targetValues.getSum(), mismatches);
        }
        if (target.isMinYn()) {
            compareByType("MIN", target.getDataType(),
                    source.getMin(), targetValues.getMin(), mismatches);
        }
        if (target.isMaxYn()) {
            compareByType("MAX", target.getDataType(),
                    source.getMax(), targetValues.getMax(), mismatches);
        }
        if (target.isAvgYn()) {
            compareNumeric("AVG", source.getAvg(), targetValues.getAvg(), mismatches);
        }
        if (target.isDistCntYn()) {
            compareNumeric("DIST_CNT", source.getDistinctCount(),
                    targetValues.getDistinctCount(), mismatches);
        }
        if (target.isNullCntYn()) {
            compareNumeric("NULL_CNT", source.getNullCount(),
                    targetValues.getNullCount(), mismatches);
        }
        return List.copyOf(mismatches);
    }

    private static void compareByType(
            String metric,
            String dataType,
            String source,
            String target,
            List<String> mismatches) throws ValidationException {
        if (NUMERIC_TYPES.contains(baseType(dataType))) {
            compareNumeric(metric, source, target, mismatches);
        } else if (!Objects.equals(source, target)) {
            mismatches.add(metric);
        }
    }

    private static void compareNumeric(
            String metric,
            String source,
            String target,
            List<String> mismatches) throws ValidationException {
        if (source == null || target == null) {
            if (!Objects.equals(source, target)) {
                mismatches.add(metric);
            }
            return;
        }
        try {
            if (new BigDecimal(source).compareTo(new BigDecimal(target)) != 0) {
                mismatches.add(metric);
            }
        } catch (NumberFormatException e) {
            throw new ValidationException(
                    "숫자 검증값을 비교할 수 없습니다: metric=" + metric
                            + ", source=" + source + ", target=" + target,
                    e);
        }
    }

    private static String baseType(String dataType) {
        String normalized = dataType == null
                ? ""
                : dataType.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        int detailStart = normalized.indexOf('(');
        return detailStart < 0 ? normalized : normalized.substring(0, detailStart).trim();
    }
}

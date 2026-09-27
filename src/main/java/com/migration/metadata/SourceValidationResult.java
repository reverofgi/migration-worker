package com.migration.metadata;

/** MIG_VRF_RESULT에 저장하거나 비교할 컬럼별 검증 집계값. */
public final class SourceValidationResult {
    private final String colId;
    private final String rowCount;
    private final String sum;
    private final String min;
    private final String max;
    private final String avg;
    private final String distinctCount;
    private final String nullCount;

    public SourceValidationResult(
            String colId,
            String rowCount,
            String sum,
            String min,
            String max,
            String avg,
            String distinctCount,
            String nullCount) {
        this.colId = colId;
        this.rowCount = rowCount;
        this.sum = sum;
        this.min = min;
        this.max = max;
        this.avg = avg;
        this.distinctCount = distinctCount;
        this.nullCount = nullCount;
    }

    public String getColId() { return colId; }
    public String getRowCount() { return rowCount; }
    public String getSum() { return sum; }
    public String getMin() { return min; }
    public String getMax() { return max; }
    public String getAvg() { return avg; }
    public String getDistinctCount() { return distinctCount; }
    public String getNullCount() { return nullCount; }
}

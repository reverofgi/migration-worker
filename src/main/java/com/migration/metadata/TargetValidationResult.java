package com.migration.metadata;

/** MIG_VRF_RESULT에 저장할 TO-BE 집계값과 AS-IS 비교 결과. */
public final class TargetValidationResult {
    private final String colId;
    private final String rowCount;
    private final String sum;
    private final String min;
    private final String max;
    private final String avg;
    private final String distinctCount;
    private final String nullCount;
    private final String verificationStatusCode;

    public TargetValidationResult(
            SourceValidationResult values,
            boolean matches) {
        this.colId = values.getColId();
        this.rowCount = values.getRowCount();
        this.sum = values.getSum();
        this.min = values.getMin();
        this.max = values.getMax();
        this.avg = values.getAvg();
        this.distinctCount = values.getDistinctCount();
        this.nullCount = values.getNullCount();
        this.verificationStatusCode = matches ? "10" : "20";
    }

    public String getColId() { return colId; }
    public String getRowCount() { return rowCount; }
    public String getSum() { return sum; }
    public String getMin() { return min; }
    public String getMax() { return max; }
    public String getAvg() { return avg; }
    public String getDistinctCount() { return distinctCount; }
    public String getNullCount() { return nullCount; }
    public String getVerificationStatusCode() { return verificationStatusCode; }
}

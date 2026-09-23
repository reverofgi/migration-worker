package com.migration.metadata;

import java.math.BigDecimal;

/** GPCL_MIG_VRF_RESULT에 저장할 AS-IS 컬럼 검증 결과. */
public final class SourceValidationResult {
    private final String colId;
    private final BigDecimal rowCount;
    private final BigDecimal sum;
    private final BigDecimal min;
    private final BigDecimal max;
    private final BigDecimal avg;
    private final BigDecimal byteLength;
    private final BigDecimal distinctCount;
    private final BigDecimal nullCount;

    public SourceValidationResult(
            String colId,
            BigDecimal rowCount,
            BigDecimal sum,
            BigDecimal min,
            BigDecimal max,
            BigDecimal avg,
            BigDecimal byteLength,
            BigDecimal distinctCount,
            BigDecimal nullCount) {
        this.colId = colId;
        this.rowCount = rowCount;
        this.sum = sum;
        this.min = min;
        this.max = max;
        this.avg = avg;
        this.byteLength = byteLength;
        this.distinctCount = distinctCount;
        this.nullCount = nullCount;
    }

    public String getColId() { return colId; }
    public BigDecimal getRowCount() { return rowCount; }
    public BigDecimal getSum() { return sum; }
    public BigDecimal getMin() { return min; }
    public BigDecimal getMax() { return max; }
    public BigDecimal getAvg() { return avg; }
    public BigDecimal getByteLength() { return byteLength; }
    public BigDecimal getDistinctCount() { return distinctCount; }
    public BigDecimal getNullCount() { return nullCount; }
}

package com.migration.metadata;

import java.time.LocalDateTime;

/** GPCL_MIG_VRF_TARGET에서 조회한 컬럼별 검증 대상 메타데이터. */
public class ValidationTarget {
    private String tableId;
    private String colId;
    private int colOrd;
    private String tableNm;
    private String colNm;
    private String dataType;
    private boolean pkYn;
    private boolean nullYn;
    private boolean sumYn;
    private boolean minYn;
    private boolean maxYn;
    private boolean avgYn;
    private boolean hashYn;
    private boolean byteLenYn;
    private boolean distCntYn;
    private boolean nullCntYn;
    private String regId;
    private LocalDateTime regDtm;
    private String lstAdjprnId;
    private LocalDateTime lstAdjDtm;

    public String getTableId() { return tableId; }
    // public void setTableId(String tableId) { this.tableId = tableId; }

    public String getColId() { return colId; }
    // public void setColId(String colId) { this.colId = colId; }

    public int getColOrd() { return colOrd; }
    // public void setColOrd(int colOrd) { this.colOrd = colOrd; }

    public String getTableNm() { return tableNm; }
    // public void setTableNm(String tableNm) { this.tableNm = tableNm; }

    public String getColNm() { return colNm; }
    // public void setColNm(String colNm) { this.colNm = colNm; }

    public String getDataType() { return dataType; }
    // public void setDataType(String dataType) { this.dataType = dataType; }

    public boolean isPkYn() { return pkYn; }
    // public void setPkYn(boolean pkYn) { this.pkYn = pkYn; }

    public boolean isNullYn() { return nullYn; }
    // public void setNullYn(boolean nullYn) { this.nullYn = nullYn; }

    public boolean isSumYn() { return sumYn; }
    // public void setSumYn(boolean sumYn) { this.sumYn = sumYn; }

    public boolean isMinYn() { return minYn; }
    // public void setMinYn(boolean minYn) { this.minYn = minYn; }

    public boolean isMaxYn() { return maxYn; }
    // public void setMaxYn(boolean maxYn) { this.maxYn = maxYn; }

    public boolean isAvgYn() { return avgYn; }
    // public void setAvgYn(boolean avgYn) { this.avgYn = avgYn; }

    public boolean isHashYn() { return hashYn; }
    // public void setHashYn(boolean hashYn) { this.hashYn = hashYn; }

    public boolean isByteLenYn() { return byteLenYn; }
    // public void setByteLenYn(boolean byteLenYn) { this.byteLenYn = byteLenYn; }

    public boolean isDistCntYn() { return distCntYn; }
    // public void setDistCntYn(boolean distCntYn) { this.distCntYn = distCntYn; }

    public boolean isNullCntYn() { return nullCntYn; }
    // public void setNullCntYn(boolean nullCntYn) { this.nullCntYn = nullCntYn; }

    public String getRegId() { return regId; }
    // public void setRegId(String regId) { this.regId = regId; }

    public LocalDateTime getRegDtm() { return regDtm; }
    // public void setRegDtm(LocalDateTime regDtm) { this.regDtm = regDtm; }

    public String getLstAdjprnId() { return lstAdjprnId; }
    // public void setLstAdjprnId(String lstAdjprnId) { this.lstAdjprnId = lstAdjprnId; }

    public LocalDateTime getLstAdjDtm() { return lstAdjDtm; }
    // public void setLstAdjDtm(LocalDateTime lstAdjDtm) { this.lstAdjDtm = lstAdjDtm; }
}

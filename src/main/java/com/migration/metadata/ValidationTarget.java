package com.migration.metadata;

/** Column and aggregate metadata read from GPCL_MIG_VRF_TARGET. */
public class ValidationTarget {
    private String tableSchema;
    private String tableName;
    private String columnName;
    private int ordinalPosition;
    private String dataType;
    private boolean sumYn;
    private boolean minYn;
    private boolean maxYn;
    private boolean avgYn;

    public String getTableSchema() { return tableSchema; }
    public void setTableSchema(String tableSchema) { this.tableSchema = tableSchema; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public int getOrdinalPosition() { return ordinalPosition; }
    public void setOrdinalPosition(int ordinalPosition) { this.ordinalPosition = ordinalPosition; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public boolean isSumYn() { return sumYn; }
    public void setSumYn(boolean sumYn) { this.sumYn = sumYn; }
    public boolean isMinYn() { return minYn; }
    public void setMinYn(boolean minYn) { this.minYn = minYn; }
    public boolean isMaxYn() { return maxYn; }
    public void setMaxYn(boolean maxYn) { this.maxYn = maxYn; }
    public boolean isAvgYn() { return avgYn; }
    public void setAvgYn(boolean avgYn) { this.avgYn = avgYn; }
}

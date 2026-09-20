package com.migration.metadata;

/** 확인된 이관 메타데이터로 구성한 물리 컬럼 정의. */
public final class ColumnSchema {
    private final String tableSchema;
    private final String tableName;
    private final String columnName;
    private final int ordinalPosition;
    private final String dataType;
    private final Integer columnSize;
    private final Integer decimalDigits;
    private final Boolean nullable;

    public ColumnSchema(String tableSchema, String tableName, String columnName,
                        int ordinalPosition, String dataType, Integer columnSize,
                        Integer decimalDigits, Boolean nullable) {
        this.tableSchema = requireText(tableSchema, "tableSchema");
        this.tableName = requireText(tableName, "tableName");
        this.columnName = requireText(columnName, "columnName");
        if (ordinalPosition <= 0) {
            throw new IllegalArgumentException("ordinalPosition must be greater than zero");
        }
        this.ordinalPosition = ordinalPosition;
        this.dataType = requireText(dataType, "dataType");
        this.columnSize = nonNegative(columnSize, "columnSize");
        this.decimalDigits = nonNegative(decimalDigits, "decimalDigits");
        this.nullable = nullable;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static Integer nonNegative(Integer value, String name) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    public String getTableSchema() { return tableSchema; }
    public String getTableName() { return tableName; }
    public String getColumnName() { return columnName; }
    public int getOrdinalPosition() { return ordinalPosition; }
    public String getDataType() { return dataType; }
    public Integer getColumnSize() { return columnSize; }
    public Integer getDecimalDigits() { return decimalDigits; }
    public Boolean getNullable() { return nullable; }
}

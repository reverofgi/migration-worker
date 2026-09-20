package com.migration.metadata;

import java.util.List;
import java.util.Objects;

/** 하나의 이관 테이블에 대한 정렬된 물리 스키마. */
public final class TableSchema {
    private final String tableSchema;
    private final String tableName;
    private final List<ColumnSchema> columns;

    public TableSchema(String tableSchema, String tableName, List<ColumnSchema> columns) {
        if (tableSchema == null || tableSchema.isBlank()) {
            throw new IllegalArgumentException("tableSchema must not be blank");
        }
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        this.tableSchema = tableSchema.trim();
        this.tableName = tableName.trim();
        this.columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
    }

    public String getTableSchema() { return tableSchema; }
    public String getTableName() { return tableName; }
    public List<ColumnSchema> getColumns() { return columns; }
    public boolean exists() { return !columns.isEmpty(); }
}

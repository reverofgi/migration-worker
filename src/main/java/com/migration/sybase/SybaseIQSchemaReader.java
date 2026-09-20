package com.migration.sybase;

import com.migration.exception.SchemaException;
import com.migration.metadata.ColumnSchema;
import com.migration.metadata.TableSchema;
import com.migration.metadata.ValidationTarget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GPCL_MIG_VRF_TARGET 메타데이터로 물리 스키마를 구성하고 비교한다. */
public final class SybaseIQSchemaReader {
    private static final Pattern TYPE_PATTERN = Pattern.compile(
            "^\\s*([A-Z][A-Z0-9 ]*?)(?:\\s*\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\))?\\s*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Sybase 카탈로그나 JDBC DatabaseMetaData 대신 메타데이터를 사용한다.
     * 프로젝트 요구사항에 따라 nullable 여부는 GPCL_MIG_VRF_TARGET에서
     * 제공하지 않으므로 알 수 없는 값으로 유지한다.
     */
    public TableSchema read(String tableSchema, String tableName,
                            List<ValidationTarget> targets) throws SchemaException {
        requireText(tableSchema, "TABLE_SCHEMA");
        requireText(tableName, "TABLE_NAME");
        if (targets == null) {
            throw new SchemaException("Physical schema targets must not be null.");
        }

        List<ValidationTarget> ordered = new ArrayList<>(targets);
        ordered.sort(Comparator.comparingInt(ValidationTarget::getOrdinalPosition));
        List<ColumnSchema> columns = new ArrayList<>(ordered.size());
        Set<String> names = new HashSet<>();
        Set<Integer> ordinals = new HashSet<>();

        for (ValidationTarget target : ordered) {
            if (target == null) {
                throw new SchemaException("Physical schema contains a null column target.");
            }
            validateOwner(tableSchema, tableName, target);
            if (!names.add(requireText(target.getColumnName(), "COLUMN_NAME")
                    .toUpperCase(Locale.ROOT))) {
                throw new SchemaException("Duplicate physical column: " + target.getColumnName());
            }
            if (!ordinals.add(target.getOrdinalPosition())) {
                throw new SchemaException("Duplicate physical column ordinal: "
                        + target.getOrdinalPosition());
            }
            columns.add(toColumnSchema(target));
        }
        return new TableSchema(tableSchema, tableName, columns);
    }

    /** 스키마가 다르면 Phase 3 규칙에 따라 SCHEMA_MISMATCH로 실패한다. */
    public void validateCompatible(TableSchema asis, TableSchema tobe)
            throws SchemaException {
        if (asis == null || tobe == null) {
            throw mismatch("AS-IS and TO-BE schemas are required");
        }
        if (!asis.exists()) throw mismatch("AS-IS table does not exist: " + qualified(asis));
        if (!tobe.exists()) throw mismatch("TO-BE table does not exist: " + qualified(tobe));
        if (!same(asis.getTableSchema(), tobe.getTableSchema())
                || !same(asis.getTableName(), tobe.getTableName())) {
            throw mismatch("table identity differs: " + qualified(asis) + " vs " + qualified(tobe));
        }
        if (asis.getColumns().size() != tobe.getColumns().size()) {
            throw mismatch("column count differs: " + asis.getColumns().size()
                    + " vs " + tobe.getColumns().size());
        }
        for (int i = 0; i < asis.getColumns().size(); i++) {
            compareColumn(asis.getColumns().get(i), tobe.getColumns().get(i));
        }
    }

    private ColumnSchema toColumnSchema(ValidationTarget target) throws SchemaException {
        String rawType = requireText(target.getDataType(), "DATA_TYPE");
        Matcher matcher = TYPE_PATTERN.matcher(rawType);
        if (!matcher.matches()) {
            throw new SchemaException("Unsupported DATA_TYPE metadata: " + rawType);
        }
        String baseType = matcher.group(1).trim().replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
        Integer size = parseInteger(matcher.group(2), rawType);
        boolean numeric = baseType.equals("DECIMAL") || baseType.equals("NUMERIC");
        Integer scale = numeric ? parseInteger(matcher.group(3), rawType) : null;
        return new ColumnSchema(target.getTableSchema(), target.getTableName(),
                target.getColumnName(), target.getOrdinalPosition(), baseType,
                size, scale, null);
    }

    private void validateOwner(String schema, String table, ValidationTarget target)
            throws SchemaException {
        if (!same(schema, target.getTableSchema()) || !same(table, target.getTableName())) {
            throw new SchemaException("Physical column belongs to another table: "
                    + target.getTableSchema() + "." + target.getTableName());
        }
        requireText(target.getColumnName(), "COLUMN_NAME");
        if (target.getOrdinalPosition() <= 0) {
            throw new SchemaException("ORDINAL_POSITION must be greater than zero: "
                    + target.getColumnName());
        }
    }

    private void compareColumn(ColumnSchema asis, ColumnSchema tobe) throws SchemaException {
        String column = asis.getColumnName();
        if (!same(column, tobe.getColumnName())) {
            throw mismatch("column name differs at ordinal " + asis.getOrdinalPosition()
                    + ": " + column + " vs " + tobe.getColumnName());
        }
        if (asis.getOrdinalPosition() != tobe.getOrdinalPosition()) {
            throw mismatch("ordinal differs for column " + column);
        }
        if (!same(asis.getDataType(), tobe.getDataType())) {
            throw mismatch("data type differs for column " + column + ": "
                    + asis.getDataType() + " vs " + tobe.getDataType());
        }
        compareKnown("length/precision", column, asis.getColumnSize(), tobe.getColumnSize());
        compareKnown("scale", column, asis.getDecimalDigits(), tobe.getDecimalDigits());
        compareKnown("nullable", column, asis.getNullable(), tobe.getNullable());
    }

    private void compareKnown(String field, String column, Object asis, Object tobe)
            throws SchemaException {
        if (asis != null && tobe != null && !Objects.equals(asis, tobe)) {
            throw mismatch(field + " differs for column " + column + ": " + asis + " vs " + tobe);
        }
    }

    private static Integer parseInteger(String value, String rawType) throws SchemaException {
        if (value == null) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new SchemaException("Invalid DATA_TYPE size: " + rawType, e);
        }
    }

    private static String requireText(String value, String field) throws SchemaException {
        if (value == null || value.isBlank()) {
            throw new SchemaException(field + " must not be blank.");
        }
        return value.trim();
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static String qualified(TableSchema schema) {
        return schema.getTableSchema() + "." + schema.getTableName();
    }

    private static SchemaException mismatch(String detail) {
        return new SchemaException("SCHEMA_MISMATCH: " + detail);
    }
}

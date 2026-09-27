package com.migration.sybase;

import com.migration.exception.SchemaException;
import com.migration.metadata.ColumnSchema;
import com.migration.metadata.TableSchema;
import com.migration.metadata.ValidationTarget;
import com.migration.util.SqlUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MIG_COL_INFO 메타데이터로 물리 스키마를 구성하고 비교한다. */
public final class SybaseIQSchemaReader {
    private static final Pattern TYPE_PATTERN = Pattern.compile(
            "^\\s*([A-Z][A-Z0-9 ]*?)(?:\\s*\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\))?\\s*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Sybase 카탈로그나 JDBC DatabaseMetaData 대신 메타데이터를 사용한다.
     * TABLE_SCHEMA는 검증 대상 테이블 인자로 받고, 컬럼 속성은
     * MIG_COL_INFO 메타데이터를 사용한다.
     */
    public TableSchema read(String tableSchema, String tableName,
                            List<ValidationTarget> targets) throws SchemaException {
        requireText(tableSchema, "TABLE_SCHEMA");
        requireText(tableName, "TABLE_NAME");
        if (targets == null) {
            throw new SchemaException("Physical schema targets must not be null.");
        }

        List<ValidationTarget> ordered = new ArrayList<>(targets);
        ordered.sort(Comparator.comparingInt(ValidationTarget::getColOrd));
        List<ColumnSchema> columns = new ArrayList<>(ordered.size());
        Set<String> names = new HashSet<>();
        Set<Integer> ordinals = new HashSet<>();

        for (ValidationTarget target : ordered) {
            if (target == null) {
                throw new SchemaException("Physical schema contains a null column target.");
            }
            validateOwner(tableSchema, tableName, target);
            if (!names.add(requireText(target.getColNm(), "COL_NM")
                    .toUpperCase(Locale.ROOT))) {
                throw new SchemaException("Duplicate physical column: " + target.getColNm());
            }
            if (!ordinals.add(target.getColOrd())) {
                throw new SchemaException("Duplicate physical column ordinal: "
                        + target.getColOrd());
            }
            columns.add(toColumnSchema(tableSchema, tableName, target));
        }
        return new TableSchema(tableSchema, tableName, columns);
    }

        public void validateCompatible(TableSchema source, TableSchema target)
            throws SchemaException {
        if (source == null || target == null) {
            throw mismatch("AS-IS and TO-BE schemas are required");
        }
        if (!source.exists()) throw mismatch("AS-IS table does not exist: " + qualified(source));
        if (!target.exists()) throw mismatch("TO-BE table does not exist: " + qualified(target));
        if (!same(source.getTableSchema(), target.getTableSchema())
                || !same(source.getTableName(), target.getTableName())) {
            throw mismatch("table identity differs: " + qualified(source) + " vs " + qualified(target));
        }
        if (source.getColumns().size() != target.getColumns().size()) {
            throw mismatch("column count differs: " + source.getColumns().size()
                    + " vs " + target.getColumns().size());
        }
        for (int i = 0; i < source.getColumns().size(); i++) {
            compareColumn(source.getColumns().get(i), target.getColumns().get(i));
        }
    }

    private ColumnSchema toColumnSchema(
            String tableSchema,
            String tableName,
            ValidationTarget target)
            throws SchemaException {
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
        return new ColumnSchema(tableSchema, tableName,
                target.getColNm(), target.getColOrd(), baseType,
                size, scale, target.isNullYn());
    }

    private void validateOwner(String schema, String table, ValidationTarget target)
            throws SchemaException {
        if (!same(schema, target.getTableOwner())) {
            throw new SchemaException("Physical column belongs to another owner: "
                    + target.getTableOwner());
        }
        requireText(target.getColNm(), "COL_NM");
        if (target.getColOrd() <= 0) {
            throw new SchemaException("COL_ORD must be greater than zero: "
                    + target.getColNm());
        }
    }

    private void compareColumn(ColumnSchema source, ColumnSchema target) throws SchemaException {
        String column = source.getColumnName();
        if (!same(column, target.getColumnName())) {
            throw mismatch("column name differs at ordinal " + source.getOrdinalPosition()
                    + ": " + column + " vs " + target.getColumnName());
        }
        if (source.getOrdinalPosition() != target.getOrdinalPosition()) {
            throw mismatch("ordinal differs for column " + column);
        }
        if (!same(source.getDataType(), target.getDataType())) {
            throw mismatch("data type differs for column " + column + ": "
                    + source.getDataType() + " vs " + target.getDataType());
        }
        compareKnown("length/precision", column, source.getColumnSize(), target.getColumnSize());
        compareKnown("scale", column, source.getDecimalDigits(), target.getDecimalDigits());
        compareKnown("nullable", column, source.getNullable(), target.getNullable());
    }

    private void compareKnown(String field, String column, Object source, Object target)
            throws SchemaException {
        if (source != null && target != null && !Objects.equals(source, target)) {
            throw mismatch(field + " differs for column " + column + ": " + source + " vs " + target);
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
        return SqlUtil.identifiersEqual(left, right);
    }

    private static String qualified(TableSchema schema) {
        return schema.getTableSchema() + "." + schema.getTableName();
    }

    private static SchemaException mismatch(String detail) {
        return new SchemaException("SCHEMA_MISMATCH: " + detail);
    }
}

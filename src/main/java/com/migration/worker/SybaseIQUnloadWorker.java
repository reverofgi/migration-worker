package com.migration.worker;

import com.migration.config.MigrationProperties;
import com.migration.config.MigrationStoragePaths;
import com.migration.exception.ExtractException;
import com.migration.metadata.MigrationTableInfo;
import com.migration.metadata.ValidationTarget;
import com.migration.util.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sybase IQ Native Extract와 BFILE을 이용하여 AS-IS 테이블을 추출한다. */
public final class SybaseIQUnloadWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(SybaseIQUnloadWorker.class);
    private static final Pattern DATA_TYPE_PATTERN = Pattern.compile(
            "^([A-Z][A-Z0-9 ]*?)(?:\\s*\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\))?$",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> BINARY_TYPES = Set.of(
            "BLOB", "LONG BINARY", "VARBINARY", "BINARY", "IMAGE",
            "CLOB", "LONG VARCHAR","LONG NVARCHAR", "TEXT"
            );
    private static final Set<String> CHARACTER_TYPES = Set.of(
            "CHAR", "CHARACTER", "VARCHAR", "CHARACTER VARYING",
            "NCHAR", "NVARCHAR", "LONG VARCHAR", "LONG NVARCHAR",
            "TEXT", "CLOB");

    private final Connection connection;

    public SybaseIQUnloadWorker(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    public void execute(MigrationTableInfo table) throws ExtractException {
        Objects.requireNonNull(table, "table");

        ExtractSettings settings = loadExtractSettings();

        String tableName = validateIdentifier(table.getTableNm(), "table name");
        List<ValidationTarget> columns = validateAndOrderColumns(table);
        validateLobMetadata(table, columns);

        try {
            MigrationStoragePaths storagePaths = MigrationStoragePaths.load();
            String extractDirectoryName = buildExtractDirectoryName(tableName, table.getProcOrd());
            createWorkerDirectories(storagePaths, extractDirectoryName);
            String dataDirectory = storagePaths.databaseDataDirectory(extractDirectoryName);
            String blobDirectory = storagePaths.databaseBlobDirectory(extractDirectoryName);
            String extractFileBaseName = buildExtractFileBaseName(table);
            String dataFile = joinPath(dataDirectory, extractFileBaseName + "_data.dat");
            String blobPrefix = joinPath(blobDirectory, extractFileBaseName + "_");
            String extractSql = buildExtractSql(table, columns, blobPrefix, settings);

            executeExtract(tableName, dataFile, extractSql, settings);
        } catch (SQLException e) {
            throw new ExtractException("AS-IS 테이블 추출 실패: " + tableName, e);
        }
    }

    String buildExtractSql(MigrationTableInfo table, List<ValidationTarget> columns,
                           String blobPrefix) throws ExtractException {
        return buildExtractSql(table, columns, blobPrefix, loadExtractSettings());
    }

    private String buildExtractSql(MigrationTableInfo table, List<ValidationTarget> columns,
                                   String blobPrefix, ExtractSettings settings)
            throws ExtractException {
        String tableName = validateIdentifier(table.getTableNm(), "table name");
        List<ValidationTarget> primaryKeys = columns.stream()
                .filter(ValidationTarget::isPkYn)
                .toList();

        if (hasBinaryColumn(columns) && primaryKeys.isEmpty()) {
            throw new ExtractException(
                    "BFILE 이름을 생성할 PK 컬럼이 없습니다: " + tableName);
        }

        List<String> selectExpressions = new ArrayList<>(columns.size());
        for (ValidationTarget column : columns) {
            String columnName = validateIdentifier(column.getColNm(), "column name");
            if (isBinaryType(column.getDataType())) {
                selectExpressions.add(buildBlobExpression(
                        column, columnName, primaryKeys, blobPrefix, settings));
            } else if (column.isNullYn()) {
                selectExpressions.add(buildNullableExpression(column, columnName, settings));
            } else {
                selectExpressions.add(columnName);
            }
        }

        StringBuilder sql = new StringBuilder("SELECT ")
                .append(String.join(", ", selectExpressions))
                .append(" FROM ")
                .append(tableName);

        if (table.getMigCond() != null && !table.getMigCond().isBlank()) {
            sql.append(" WHERE ").append(table.getMigCond().trim());
        }
        return sql.toString();
    }

    private void executeExtract(String tableName, String dataFile, String extractSql,
                                ExtractSettings settings)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            SQLException executionFailure = null;
            try {
                configureExtract(statement, dataFile, settings);
                LOGGER.info("Sybase IQ unload started: table={}, dataFile={}", tableName, dataFile);
                statement.execute(extractSql);
                LOGGER.info("Sybase IQ unload completed: table={}", tableName);
            } catch (SQLException e) {
                executionFailure = e;
                throw e;
            } finally {
                try {
                    clearExtract(statement);
                } catch (SQLException resetFailure) {
                    if (executionFailure != null) {
                        executionFailure.addSuppressed(resetFailure);
                    } else {
                        throw resetFailure;
                    }
                }
            }
        }
    }

    private void configureExtract(Statement statement, String dataFile, ExtractSettings settings)
            throws SQLException {
        statement.execute("SET TEMPORARY OPTION TEMP_EXTRACT_NAME1 = '"
                + SqlUtil.escapeLiteral(dataFile) + "'");
        statement.execute("SET TEMPORARY OPTION TEMP_EXTRACT_BINARY = 'OFF'");
        statement.execute("SET TEMPORARY OPTION TEMP_EXTRACT_NULL_AS_EMPTY = 'OFF'");
        statement.execute("SET TEMPORARY OPTION TEMP_EXTRACT_NULL_AS_ZERO = 'OFF'");
        statement.execute("SET TEMPORARY OPTION TEMP_EXTRACT_COLUMN_DELIMITER = '"
                + SqlUtil.escapeLiteral(settings.columnDelimiter()) + "'");
        statement.execute("SET TEMPORARY OPTION TEMP_EXTRACT_ROW_DELIMITER = '"
                + SqlUtil.escapeLiteral(settings.rowDelimiter()) + "'");
        statement.execute("SET TEMPORARY OPTION TEMP_EXTRACT_QUOTES = 'ON'");
    }

    private void clearExtract(Statement statement) throws SQLException {
        SQLException failure = null;
        for (String sql : List.of(
                "SET TEMPORARY OPTION TEMP_EXTRACT_NAME1 = ''",
                "SET TEMPORARY OPTION TEMP_EXTRACT_BINARY = 'OFF'")) {
            try {
                statement.execute(sql);
            } catch (SQLException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * nullable 컬럼의 NULL과 빈 문자열을 구분할 추출 표현식을 생성한다.
     *
     * <p>문자열 컬럼은 원래 타입을 유지하고, 숫자·날짜 컬럼은
     * {@link ValidationTarget#getDataType()}에 맞는 길이의 {@code VARCHAR}로 변환해
     * 문자열 NULL 토큰과 {@code CASE} 반환 타입을 일치시킨다.</p>
     *
     * @param column 컬럼 데이터 타입과 NULL 허용 여부를 포함한 메타데이터
     * @param columnName 검증이 완료된 SQL 컬럼 식별자
     * @return Native Extract SELECT 절에 사용할 nullable 컬럼 표현식
     * @throws ExtractException 지원하지 않거나 해석할 수 없는 데이터 타입일 때
     */
    private String buildNullableExpression(
            ValidationTarget column,
            String columnName,
            ExtractSettings settings) throws ExtractException {

        String valueExpression = buildTextValueExpression(columnName, column.getDataType());
        return "CASE WHEN " + columnName + " IS NULL THEN '"
                + SqlUtil.escapeLiteral(settings.nullToken()) + "' ELSE "
                + valueExpression + " END";
    }

    private String buildTextValueExpression(String columnName, String dataType)
            throws ExtractException {
        Matcher matcher = DATA_TYPE_PATTERN.matcher(
                dataType == null ? "" : dataType.trim());
        if (!matcher.matches()) {
            throw new ExtractException("해석할 수 없는 DATA_TYPE입니다: " + dataType);
        }

        String baseType = matcher.group(1).trim().replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
        if (CHARACTER_TYPES.contains(baseType)) {
            return columnName;
        }

        int varcharLength = switch (baseType) {
            case "TINYINT" -> 4;
            case "SMALLINT" -> 6;
            case "INTEGER", "INT" -> 11;
            case "BIGINT" -> 20;
            case "UNSIGNED TINYINT", "TINYINT UNSIGNED" -> 3;
            case "UNSIGNED SMALLINT", "SMALLINT UNSIGNED" -> 5;
            case "UNSIGNED INTEGER", "UNSIGNED INT",
                 "INTEGER UNSIGNED", "INT UNSIGNED" -> 10;
            case "UNSIGNED BIGINT", "BIGINT UNSIGNED" -> 20;
            case "DECIMAL", "DEC", "NUMERIC" -> decimalTextLength(
                    matcher.group(2), matcher.group(3), dataType);
            case "REAL", "FLOAT", "DOUBLE", "DOUBLE PRECISION" -> 64;
            case "DATE" -> 10;
            case "TIME" -> 18;
            case "TIMESTAMP", "DATETIME", "SMALLDATETIME" -> 32;
            case "BIT", "BOOLEAN" -> 5;
            default -> throw new ExtractException(
                    "문자열 변환 규칙이 없는 DATA_TYPE입니다: " + dataType);
        };
        return "CAST(" + columnName + " AS VARCHAR(" + varcharLength + "))";
    }

    private int decimalTextLength(String precisionText, String scaleText, String dataType)
            throws ExtractException {
        if (precisionText == null) {
            throw new ExtractException(
                    "DECIMAL/NUMERIC 타입에 precision이 없습니다: " + dataType);
        }
        try {
            int precision = Integer.parseInt(precisionText);
            int scale = scaleText == null ? 0 : Integer.parseInt(scaleText);
            if (precision <= 0 || scale < 0 || scale > precision) {
                throw new ExtractException(
                        "잘못된 DECIMAL/NUMERIC precision 또는 scale입니다: " + dataType);
            }
            return precision + 1 + (scale > 0 ? 1 : 0);
        } catch (NumberFormatException e) {
            throw new ExtractException("잘못된 DECIMAL/NUMERIC 타입입니다: " + dataType, e);
        }
    }

    private String buildBlobExpression(
            ValidationTarget column,
            String columnName,
            List<ValidationTarget> primaryKeys,
            String blobPrefix,
            ExtractSettings settings) throws ExtractException {

        List<String> keyExpressions = new ArrayList<>(primaryKeys.size());
        for (ValidationTarget primaryKey : primaryKeys) {
            String primaryKeyName = validateIdentifier(primaryKey.getColNm(), "PK column name");
            keyExpressions.add("CAST(" + primaryKeyName + " AS VARCHAR(255))");
        }
        String keyExpression = String.join(" || '_' || ", keyExpressions);
        String fileExpression = "'" + SqlUtil.escapeLiteral(blobPrefix) + "' || "
                + keyExpression + " || '_" + SqlUtil.escapeLiteral(columnName) + ".bin'";
        String bfileExpression = "CASE ";
        if (column.isNullYn()) {
            bfileExpression += "WHEN " + columnName + " IS NULL THEN '"
                    + SqlUtil.escapeLiteral(settings.nullToken()) + "' ";
        }
        return bfileExpression + "WHEN BFILE(" + fileExpression + ", " + columnName
                + ") = 1 THEN " + fileExpression + " ELSE '"
                + SqlUtil.escapeLiteral(settings.bfileErrorToken()) + "' END";
    }

    private ExtractSettings loadExtractSettings() throws ExtractException {
        try {
            return new ExtractSettings(
                    MigrationProperties.getRequired("NULL_TOKEN"),
                    MigrationProperties.getRequired("BFILE_ERROR_TOKEN"),
                    MigrationProperties.getRequired("COLUMN_DELIMITER"),
                    MigrationProperties.getRequired("ROW_DELIMITER"));
        } catch (SQLException e) {
            throw new ExtractException("추출 설정을 읽지 못했습니다.", e);
        }
    }

    private record ExtractSettings(
            String nullToken,
            String bfileErrorToken,
            String columnDelimiter,
            String rowDelimiter) {
    }

    /**
     * 이관 대상 테이블의 컬럼 정보를 검증하고 컬럼 순서대로 정렬한다.
     *
     * <p>{@link MigrationTableInfo#getValidationTargets()}에서 컬럼 목록을 가져와
     * {@code TABLE_ID}, {@code TABLE_NM}, {@code COL_ORD}, {@code COL_NM},
     * {@code DATA_TYPE}을 검증한다. 컬럼 순번과 컬럼명의 중복도 허용하지 않는다.</p>
     *
     * @param table 이관 대상 테이블과 컬럼 관계 정보
     * @return {@code COL_ORD} 오름차순으로 정렬된 변경 불가능한 컬럼 목록
     * @throws ExtractException 컬럼 정보가 없거나 식별자·순번·타입이 유효하지 않을 때
     */
    private List<ValidationTarget> validateAndOrderColumns(MigrationTableInfo table)
            throws ExtractException {
        List<ValidationTarget> targets = table.getValidationTargets();
        if (targets == null || targets.isEmpty()) {
            throw new ExtractException(
                    "ValidationTarget 컬럼 정보가 없습니다: " + table.getTableId());
        }

        List<ValidationTarget> ordered = new ArrayList<>(targets);
        ordered.sort(Comparator.comparingInt(ValidationTarget::getColOrd));
        Set<Integer> ordinals = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (ValidationTarget target : ordered) {
            if (target == null) {
                throw new ExtractException("ValidationTarget에 null 항목이 있습니다.");
            }
            if (!Objects.equals(table.getTableId(), target.getTableId())) {
                throw new ExtractException("TABLE_ID가 일치하지 않습니다: "
                        + target.getTableId());
            }
            if (!table.getTableNm().equalsIgnoreCase(target.getTableNm())) {
                throw new ExtractException("TABLE_NM이 일치하지 않습니다: "
                        + target.getTableNm());
            }
            if (target.getColOrd() <= 0 || !ordinals.add(target.getColOrd())) {
                throw new ExtractException("잘못되거나 중복된 COL_ORD: "
                        + target.getColOrd());
            }
            String columnName = validateIdentifier(target.getColNm(), "column name");
            if (!names.add(columnName.toUpperCase(Locale.ROOT))) {
                throw new ExtractException("중복된 COL_NM: " + columnName);
            }
            if (target.getDataType() == null || target.getDataType().isBlank()) {
                throw new ExtractException("DATA_TYPE이 없습니다: " + columnName);
            }
        }
        return List.copyOf(ordered);
    }

    /**
     * 이관 테이블의 LOB 설정과 실제 바이너리 컬럼 정보를 검증한다.
     *
     * <p>{@link MigrationTableInfo#getLobYn()} 값과
     * {@link ValidationTarget#getDataType()}에서 판별한 바이너리 컬럼 존재 여부가
     * 일치하지 않으면 추출을 중단한다.</p>
     *
     * @param table 이관 대상 테이블 정보
     * @param columns {@code COL_ORD} 순서로 정렬된 컬럼 정보
     * @throws ExtractException {@code LOB_YN}과 바이너리 컬럼 존재 여부가 다를 때
     */
    private void validateLobMetadata(MigrationTableInfo table, List<ValidationTarget> columns)
            throws ExtractException {
        boolean lobYn = "Y".equalsIgnoreCase(table.getLobYn());
        boolean hasBinary = hasBinaryColumn(columns);
        if (lobYn != hasBinary) {
            throw new ExtractException("LOB_YN과 바이너리 컬럼 정보가 일치하지 않습니다: table="
                    + table.getTableNm() + ", lobYn=" + table.getLobYn()
                    + ", hasBinaryColumn=" + hasBinary);
        }
    }

    private boolean hasBinaryColumn(List<ValidationTarget> columns) {
        return columns.stream().anyMatch(column -> isBinaryType(column.getDataType()));
    }

    private boolean isBinaryType(String dataType) {
        String normalized = dataType == null
                ? ""
                : dataType.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        int detailStart = normalized.indexOf('(');
        if (detailStart >= 0) {
            normalized = normalized.substring(0, detailStart).trim();
        }
        return BINARY_TYPES.contains(normalized);
    }

    private String buildExtractFileBaseName(MigrationTableInfo table) throws ExtractException {
        String value = table.getTableId() + "_" + table.getProcOrd();
        if (!value.matches("[A-Za-z0-9_.-]+")) {
            throw new ExtractException("파일명에 사용할 수 없는 TABLE_ID입니다: "
                    + table.getTableId());
        }
        return value;
    }

    /**
     * 테이블별 추출 디렉터리명을 {@code TABLE_NM_PROC_ORD} 형식으로 생성한다.
     *
     * @param tableName 검증이 완료된 테이블 식별자
     * @param procOrd 동일 테이블의 분할 처리 순서
     * @return 데이터 및 BLOB 루트 디렉터리 아래에 사용할 작업 디렉터리명
     * @throws ExtractException 처리 순서가 음수이거나 디렉터리명이 안전하지 않을 때
     */
    private String buildExtractDirectoryName(String tableName, int procOrd)
            throws ExtractException {
        if (procOrd < 0) {
            throw new ExtractException("PROC_ORD는 0 이상이어야 합니다: " + procOrd);
        }
        String directoryName = tableName.replace('.', '_') + "_" + procOrd;
        if (!directoryName.matches("[A-Za-z0-9_.-]+")) {
            throw new ExtractException(
                    "추출 디렉터리명으로 사용할 수 없는 값입니다: " + directoryName);
        }
        return directoryName;
    }

    /** Worker가 접근하는 공유 경로에 테이블별 추출 디렉터리를 생성한다. */
    private void createWorkerDirectories(
            MigrationStoragePaths storagePaths,
            String directoryName) throws ExtractException {
        try {
            LOGGER.debug("Worker data export directory ready: {}",
                    storagePaths.createWorkerDataDirectory(directoryName));
            LOGGER.debug("Worker BLOB export directory ready: {}",
                    storagePaths.createWorkerBlobDirectory(directoryName));
        } catch (IOException | RuntimeException e) {
            throw new ExtractException(
                    "Worker export directory creation failed: " + directoryName, e);
        }
    }

    /**
     * 동적 SQL에 사용할 테이블명 또는 컬럼명이 안전한 SQL 식별자인지 검증한다.
     *
     * <p>검증은 {@link SqlUtil#validateIdentifier(String)}에 위임하며,
     * 유효하지 않은 값은 현재 Unload 단계의 {@link ExtractException}으로 변환한다.</p>
     *
     * @param value 검증할 테이블명 또는 컬럼명
     * @param description 오류 메시지에 표시할 식별자 설명
     * @return 공백이 제거되고 검증이 완료된 SQL 식별자
     * @throws ExtractException 값이 비어 있거나 허용된 식별자 형식이 아닐 때
     */
    private String validateIdentifier(String value, String description)
            throws ExtractException {
        try {
            return SqlUtil.validateIdentifier(value);
        } catch (IllegalArgumentException e) {
            throw new ExtractException("Invalid " + description + ": " + value, e);
        }
    }

    private static String joinPath(String directory, String fileName) {
        return directory.endsWith("/") || directory.endsWith("\\")
                ? directory + fileName
                : directory + "/" + fileName;
    }
}

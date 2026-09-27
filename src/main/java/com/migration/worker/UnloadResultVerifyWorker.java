package com.migration.worker;

import com.migration.config.MigrationParameter;
import com.migration.exception.ValidationException;
import com.migration.metadata.MetaMapper;
import com.migration.metadata.MigrationTableInfo;
import com.migration.metadata.SourceValidationResult;
import com.migration.metadata.SybaseIQMapper;
import com.migration.metadata.ValidationTarget;
import com.migration.util.SqlUtil;
import com.migration.util.ConnectionUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** AS-IS 추출 시점의 검증값을 계산하여 MIG_VRF_RESULT에 저장한다. */
public final class UnloadResultVerifyWorker {
    private static final int MAX_VALIDATION_VALUE_LENGTH = 38;
    private static final Logger LOGGER =
            LoggerFactory.getLogger(UnloadResultVerifyWorker.class);

    private final Connection asisConnection;
    private final Connection godisConnection;
    private final MigrationParameter parameter;

    public UnloadResultVerifyWorker(
            Connection asisConnection,
            Connection godisConnection,
            MigrationParameter parameter) {
        this.asisConnection = Objects.requireNonNull(asisConnection, "asisConnection");
        this.godisConnection = Objects.requireNonNull(godisConnection, "godisConnection");
        this.parameter = Objects.requireNonNull(parameter, "parameter");
    }

    public void execute(MigrationTableInfo table) throws ValidationException {
        Objects.requireNonNull(table, "table");
        List<ValidationTarget> targets = enabledValidationTargets(
                validateAndOrderTargets(table));
        rejectUnsupportedHash(targets);

        try {
            List<SourceValidationResult> results = targets.isEmpty()
                    ? List.of()
                    : querySourceResults(table, targets);
            int deletedRows = replaceSourceResults(table, results);
            LOGGER.info(
                    "AS-IS 검증 결과를 교체했습니다: tableId={}, procOrd={}, deleted={}, columns={}",
                    table.getTableId(), table.getProcOrd(), deletedRows, results.size());
        } catch (SQLException e) {
            throw new ValidationException(
                    "AS-IS 검증 결과 처리 실패: " + table.getTableNm(), e);
        }
    }

    private List<SourceValidationResult> querySourceResults(
            MigrationTableInfo table,
            List<ValidationTarget> targets) throws SQLException, ValidationException {
        String tableName = qualifiedTableName(table);
        for (ValidationTarget target : targets) {
            validateIdentifier(target.getColNm(), "column name");
        }

        try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(reader);
            try (SqlSession session = factory.openSession(
                    ConnectionUtil.nonClosing(asisConnection))) {
                Map<String, Object> aggregate = session.getMapper(SybaseIQMapper.class)
                        .selectValidationAggregate(
                                tableName, table.getMigCond(), targets);
                if (aggregate == null || aggregate.isEmpty()) {
                    throw new ValidationException(
                            "AS-IS 집계 결과가 없습니다: " + table.getTableNm());
                }
                return toSourceValidationResults(targets, aggregate);
            }
        } catch (IOException | RuntimeException e) {
            throw new SQLException("Failed to query source validation results.", e);
        }
    }

    private List<SourceValidationResult> toSourceValidationResults(
            List<ValidationTarget> targets, Map<String, Object> aggregate) throws ValidationException {
        String rowCount = toText(getAggregateValue(aggregate, "ROW_COUNT"), "ROW_COUNT");
        List<SourceValidationResult> results = new ArrayList<>(targets.size());
        for (int index = 0; index < targets.size(); index++) {
            ValidationTarget target = targets.get(index);
            String prefix = "C" + index;
            String sum = valueWhenEnabled(aggregate, prefix + "_SUM", target.isSumYn());
            String min = valueWhenEnabled(aggregate, prefix + "_MIN", target.isMinYn());
            String max = valueWhenEnabled(aggregate, prefix + "_MAX", target.isMaxYn());
            String avg = valueWhenEnabled(aggregate, prefix + "_AVG", target.isAvgYn());
            String distinctCount = valueWhenEnabled(aggregate, prefix + "_DIST_CNT", target.isDistCntYn());
            String nullCount = valueWhenEnabled(aggregate, prefix + "_NULL_CNT", target.isNullCntYn());
            results.add(new SourceValidationResult(
                            target.getColId(), rowCount, sum, min, max, avg,
                             distinctCount, nullCount));
        }
        return List.copyOf(results);
    }

    private String valueWhenEnabled(
            Map<String, Object> aggregate,
            String key,
            boolean enabled) throws ValidationException {
        return enabled ? toText(getAggregateValue(aggregate, key), key) : null;
    }

    private Object getAggregateValue(Map<String, Object> aggregate, String key)
            throws ValidationException {
        if (aggregate.containsKey(key)) {
            return aggregate.get(key);
        }
        for (Map.Entry<String, Object> entry : aggregate.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new ValidationException("집계 결과 컬럼이 없습니다: " + key);
    }

    static String toText(Object value, String key) throws ValidationException {
        if (value == null) {
            return null;
        }
        String text = value instanceof BigDecimal decimal
                ? decimal.toPlainString()
                : value.toString();
        if (text.length() > MAX_VALIDATION_VALUE_LENGTH) {
            throw new ValidationException(
                    "집계 결과가 varchar(38) 범위를 초과합니다: "
                            + key + ", length=" + text.length());
        }
        return text;
    }

    private int replaceSourceResults(
            MigrationTableInfo table,
            List<SourceValidationResult> results) throws SQLException {
        boolean originalAutoCommit = godisConnection.getAutoCommit();
        SQLException failure = null;
        try {
            if (originalAutoCommit) {
                godisConnection.setAutoCommit(false);
            }
            try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
                SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(reader);
                try (SqlSession session = factory.openSession(
                        ConnectionUtil.nonClosing(godisConnection))) {
                    MetaMapper mapper = session.getMapper(MetaMapper.class);
                    int deletedRows = mapper.deleteValidationResults(
                            parameter.getExecOrd(),
                            table.getTableOwner(),
                            table.getTableId(),
                            table.getProcOrd());
                    if (!results.isEmpty()) {
                        mapper.upsertSourceValidationResults(
                                parameter.getExecOrd(),
                                table.getTableOwner(),
                                table.getTableId(),
                                table.getProcOrd(),
                                resultManagerId(table),
                                parameter.getMngrId(),
                                results);
                    }
                    session.commit();
                    return deletedRows;
                }
            }
        } catch (IOException | RuntimeException e) {
            failure = new SQLException("Failed to replace source validation results.", e);
            rollbackAfterFailure(failure);
            throw failure;
        } catch (SQLException e) {
            failure = e;
            rollbackAfterFailure(e);
            throw e;
        } finally {
            if (originalAutoCommit) {
                try {
                    godisConnection.setAutoCommit(true);
                } catch (SQLException restoreFailure) {
                    if (failure != null) {
                        failure.addSuppressed(restoreFailure);
                    } else {
                        throw restoreFailure;
                    }
                }
            }
        }
    }

    private void rollbackAfterFailure(SQLException failure) {
        try {
            godisConnection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private List<ValidationTarget> validateAndOrderTargets(MigrationTableInfo table)
            throws ValidationException {
        List<ValidationTarget> targets = table.getValidationTargets();
        if (targets == null || targets.isEmpty()) {
            throw new ValidationException(
                    "검증 대상 컬럼이 없습니다: " + table.getTableId());
        }

        List<ValidationTarget> ordered = new ArrayList<>(targets);
        ordered.sort(Comparator.comparingInt(ValidationTarget::getColOrd));
        for (ValidationTarget target : ordered) {
            if (target == null
                    || !SqlUtil.identifiersEqual(table.getTableOwner(), target.getTableOwner())
                    || !SqlUtil.identifiersEqual(table.getTableId(), target.getTableId())) {
                throw new ValidationException(
                        "TABLE_OWNER/TABLE_ID가 일치하지 않는 검증 대상이 있습니다: "
                                + table.getTableOwner() + "." + table.getTableId());
            }
            if (target.getColId() == null || target.getColId().isBlank()) {
                throw new ValidationException("COL_ID가 없습니다: " + table.getTableId());
            }
            validateIdentifier(target.getColNm(), "column name");
        }
        return List.copyOf(ordered);
    }

    private void rejectUnsupportedHash(List<ValidationTarget> targets)
            throws ValidationException {
        for (ValidationTarget target : targets) {
            if (target.isHashYn()) {
                throw new ValidationException(
                        "HASH_YN 검증 SQL은 아직 정의되지 않았습니다: "
                                + target.getTableId() + "." + target.getColId());
            }
        }
    }

    /** 지원하는 검증 플래그 중 하나 이상이 설정된 컬럼만 결과 저장 대상으로 선택한다. */
    static List<ValidationTarget> enabledValidationTargets(
            List<ValidationTarget> targets) {
        return targets.stream()
                .filter(target -> target.isSumYn()
                        || target.isMinYn()
                        || target.isMaxYn()
                        || target.isAvgYn()
                        || target.isDistCntYn()
                        || target.isNullCntYn()
                        || target.isHashYn())
                .toList();
    }

    private String resultManagerId(MigrationTableInfo table) {
        return table.getMngrId() == null || table.getMngrId().isBlank()
                ? parameter.getMngrId()
                : table.getMngrId();
    }

    private String validateIdentifier(String value, String description)
            throws ValidationException {
        try {
            return SqlUtil.validateIdentifier(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid " + description + ": " + value, e);
        }
    }

    private String qualifiedTableName(MigrationTableInfo table)
            throws ValidationException {
        String owner = validateIdentifier(table.getTableOwner(), "table owner");
        String tableName = validateIdentifier(table.getTableNm(), "table name");
        return owner + "." + tableName;
    }

}

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

/** AS-IS 추출 시점의 검증값을 계산하여 GPCL_MIG_VRF_RESULT에 저장한다. */
public final class UnloadResultVerifyWorker {
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
        List<ValidationTarget> targets = validateAndOrderTargets(table);
        rejectUnsupportedHash(targets);

        try {
            List<SourceValidationResult> results = querySourceResults(table, targets);
            saveSourceResults(table, results);
            LOGGER.info("AS-IS 검증 결과를 저장했습니다: tableId={}, procOrd={}, columns={}",
                    table.getTableId(), table.getProcOrd(), results.size());
        } catch (SQLException e) {
            throw new ValidationException(
                    "AS-IS 검증 결과 처리 실패: " + table.getTableNm(), e);
        }
    }

    private List<SourceValidationResult> querySourceResults(
            MigrationTableInfo table,
            List<ValidationTarget> targets) throws SQLException, ValidationException {
        String tableName = validateIdentifier(table.getTableNm(), "table name");
        for (ValidationTarget target : targets) {
            validateIdentifier(target.getColNm(), "column name");
        }

        try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(reader);
            try (SqlSession session = factory.openSession(
                    ConnectionUtil.nonClosing(asisConnection))) {
                Map<String, Object> aggregate = session.getMapper(SybaseIQMapper.class)
                        .selectSourceValidationAggregate(
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
            List<ValidationTarget> targets,
            Map<String, Object> aggregate) throws ValidationException {
        BigDecimal rowCount = toBigDecimal(getAggregateValue(aggregate, "ROW_COUNT"),
                "ROW_COUNT");
        List<SourceValidationResult> results = new ArrayList<>(targets.size());
        for (int index = 0; index < targets.size(); index++) {
            ValidationTarget target = targets.get(index);
            String prefix = "C" + index;
            BigDecimal sum = valueWhenEnabled(
                    aggregate, prefix + "_SUM", target.isSumYn());
            BigDecimal min = valueWhenEnabled(
                    aggregate, prefix + "_MIN", target.isMinYn());
            BigDecimal max = valueWhenEnabled(
                    aggregate, prefix + "_MAX", target.isMaxYn());
            BigDecimal avg = valueWhenEnabled(
                    aggregate, prefix + "_AVG", target.isAvgYn());
            BigDecimal byteLength = valueWhenEnabled(
                    aggregate, prefix + "_BYTE_LEN", target.isByteLenYn());
            BigDecimal distinctCount = valueWhenEnabled(
                    aggregate, prefix + "_DIST_CNT", target.isDistCntYn());
            BigDecimal nullCount = valueWhenEnabled(
                    aggregate, prefix + "_NULL_CNT", target.isNullCntYn());
            results.add(new SourceValidationResult(
                    target.getColId(), rowCount, sum, min, max, avg,
                    byteLength, distinctCount, nullCount));
        }
        return List.copyOf(results);
    }

    private BigDecimal valueWhenEnabled(
            Map<String, Object> aggregate,
            String key,
            boolean enabled) throws ValidationException {
        return enabled ? toBigDecimal(getAggregateValue(aggregate, key), key) : null;
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

    private BigDecimal toBigDecimal(Object value, String key) throws ValidationException {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new ValidationException(
                    "집계 결과를 숫자로 변환할 수 없습니다: " + key + "=" + value, e);
        }
    }

    private void saveSourceResults(
            MigrationTableInfo table,
            List<SourceValidationResult> results) throws SQLException {
        try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(reader);
            try (SqlSession session = factory.openSession(
                    ConnectionUtil.nonClosing(godisConnection))) {
                session.getMapper(MetaMapper.class).upsertSourceValidationResults(
                        parameter.getExecOrd(),
                        table.getTableId(),
                        table.getProcOrd(),
                        resultManagerId(table),
                        parameter.getMngrId(),
                        results);
                session.commit();
            }
        } catch (IOException | RuntimeException e) {
            throw new SQLException("Failed to save source validation results.", e);
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
                    || !Objects.equals(table.getTableId(), target.getTableId())) {
                throw new ValidationException(
                        "TABLE_ID가 일치하지 않는 검증 대상이 있습니다: "
                                + table.getTableId());
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

}

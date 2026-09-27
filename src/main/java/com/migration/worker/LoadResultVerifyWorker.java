package com.migration.worker;

import com.migration.config.MigrationParameter;
import com.migration.exception.MigrationException;
import com.migration.exception.ValidationException;
import com.migration.metadata.MetaMapper;
import com.migration.metadata.MigrationTableInfo;
import com.migration.metadata.SourceValidationResult;
import com.migration.metadata.SybaseIQMapper;
import com.migration.metadata.TargetValidationResult;
import com.migration.metadata.ValidationTarget;
import com.migration.util.ConnectionUtil;
import com.migration.util.SqlUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** TO-BE 적재 결과를 집계하고 AS-IS 결과와 비교하여 MIG_VRF_RESULT에 저장한다. */
public final class LoadResultVerifyWorker {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(LoadResultVerifyWorker.class);

    private final Connection tobeConnection;
    private final Connection godisConnection;
    private final MigrationParameter parameter;

    public LoadResultVerifyWorker(
            Connection tobeConnection,
            Connection godisConnection,
            MigrationParameter parameter) {
        this.tobeConnection = Objects.requireNonNull(tobeConnection, "tobeConnection");
        this.godisConnection = Objects.requireNonNull(godisConnection, "godisConnection");
        this.parameter = Objects.requireNonNull(parameter, "parameter");
    }

    public void execute(MigrationTableInfo table) throws MigrationException {
        Objects.requireNonNull(table, "table");
        List<ValidationTarget> targets = UnloadResultVerifyWorker.enabledValidationTargets(
                validateAndOrderTargets(table));
        rejectUnsupportedHash(targets);
        if (targets.isEmpty()) {
            LOGGER.info("TO-BE 검증 대상 컬럼이 없습니다: tableId={}, procOrd={}",
                    table.getTableId(), table.getProcOrd());
            return;
        }

        try {
            List<SourceValidationResult> targetValues = queryTargetResults(table, targets);
            Map<String, SourceValidationResult> sourceByColumn =
                    loadSourceResults(table, targets.size());
            List<TargetValidationResult> compared = new ArrayList<>(targets.size());
            int mismatchColumns = 0;
            for (int index = 0; index < targets.size(); index++) {
                ValidationTarget target = targets.get(index);
                SourceValidationResult source = sourceByColumn.get(normalize(target.getColId()));
                if (source == null) {
                    throw new ValidationException(
                            "AS-IS 검증 결과가 없습니다: " + target.getColId());
                }
                SourceValidationResult targetValue = targetValues.get(index);
                List<String> mismatches = ValidationResultComparator.mismatchedMetrics(
                        target, source, targetValue);
                boolean matches = mismatches.isEmpty();
                compared.add(new TargetValidationResult(targetValue, matches));
                if (!matches) {
                    mismatchColumns++;
                    LOGGER.warn(
                            "AS-IS/TO-BE 검증값 불일치: tableId={}, procOrd={}, colId={}, metrics={}",
                            table.getTableId(), table.getProcOrd(), target.getColId(), mismatches);
                }
            }

            saveTargetResults(table, compared);
            LOGGER.info(
                    "TO-BE 검증 결과를 저장했습니다: tableId={}, procOrd={}, columns={}, mismatches={}",
                    table.getTableId(), table.getProcOrd(), compared.size(), mismatchColumns);

            /*
             * 검증값 불일치는 프로세스 오류로 종료하지 않는다.
             * 불일치 결과를 MIG_VRF_RESULT의 VRF_STAT_CD='20'으로 기록하고
             * 관련 로그만 남긴 뒤 배치를 정상 종료
             */
        } catch (SQLException e) {
            throw new ValidationException(
                    "TO-BE 검증 결과 처리 실패: " + table.getTableNm(), e);
        }
    }

    private List<SourceValidationResult> queryTargetResults(
            MigrationTableInfo table,
            List<ValidationTarget> targets) throws SQLException, ValidationException {
        String tableName = qualifiedTargetTableName(table);
        try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(reader);
            try (SqlSession session = factory.openSession(
                    ConnectionUtil.nonClosing(tobeConnection))) {
                Map<String, Object> aggregate = session.getMapper(SybaseIQMapper.class)
                        .selectValidationAggregate(tableName, table.getMigCond(), targets);
                if (aggregate == null || aggregate.isEmpty()) {
                    throw new ValidationException(
                            "TO-BE 집계 결과가 없습니다: " + table.getTableNm());
                }
                return toValidationResults(targets, aggregate);
            }
        } catch (IOException | RuntimeException e) {
            throw new SQLException("Failed to query target validation results.", e);
        }
    }

    private List<SourceValidationResult> toValidationResults(
            List<ValidationTarget> targets,
            Map<String, Object> aggregate) throws ValidationException {
        String rowCount = UnloadResultVerifyWorker.toText(
                getAggregateValue(aggregate, "ROW_COUNT"), "ROW_COUNT");
        List<SourceValidationResult> results = new ArrayList<>(targets.size());
        for (int index = 0; index < targets.size(); index++) {
            ValidationTarget target = targets.get(index);
            String prefix = "C" + index;
            results.add(new SourceValidationResult(
                    target.getColId(),
                    rowCount,
                    valueWhenEnabled(aggregate, prefix + "_SUM", target.isSumYn()),
                    valueWhenEnabled(aggregate, prefix + "_MIN", target.isMinYn()),
                    valueWhenEnabled(aggregate, prefix + "_MAX", target.isMaxYn()),
                    valueWhenEnabled(aggregate, prefix + "_AVG", target.isAvgYn()),
                    valueWhenEnabled(
                            aggregate, prefix + "_DIST_CNT", target.isDistCntYn()),
                    valueWhenEnabled(
                            aggregate, prefix + "_NULL_CNT", target.isNullCntYn())));
        }
        return List.copyOf(results);
    }

    private String valueWhenEnabled(
            Map<String, Object> aggregate,
            String key,
            boolean enabled) throws ValidationException {
        return enabled
                ? UnloadResultVerifyWorker.toText(getAggregateValue(aggregate, key), key)
                : null;
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

    private Map<String, SourceValidationResult> loadSourceResults(
            MigrationTableInfo table,
            int expectedCount) throws SQLException, ValidationException {
        try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(reader);
            try (SqlSession session = factory.openSession(
                    ConnectionUtil.nonClosing(godisConnection))) {
                List<SourceValidationResult> results = session.getMapper(MetaMapper.class)
                        .selectSourceValidationResults(
                                parameter.getExecOrd(), table.getTableOwner(),
                                table.getTableId(), table.getProcOrd());
                Map<String, SourceValidationResult> byColumn = new HashMap<>();
                for (SourceValidationResult result : results) {
                    if (byColumn.put(normalize(result.getColId()), result) != null) {
                        throw new ValidationException(
                                "중복된 AS-IS 검증 결과입니다: " + result.getColId());
                    }
                }
                if (byColumn.size() != expectedCount) {
                    throw new ValidationException(
                            "AS-IS 검증 결과 컬럼 수가 일치하지 않습니다: expected="
                                    + expectedCount + ", actual=" + byColumn.size());
                }
                return Map.copyOf(byColumn);
            }
        } catch (IOException | RuntimeException e) {
            throw new SQLException("Failed to load source validation results.", e);
        }
    }

    private void saveTargetResults(
            MigrationTableInfo table,
            List<TargetValidationResult> results) throws SQLException {
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
                    session.getMapper(MetaMapper.class).upsertTargetValidationResults(
                            parameter.getExecOrd(), table.getTableOwner(), table.getTableId(),
                            table.getProcOrd(), resultManagerId(table), parameter.getMngrId(),
                            results);
                    session.commit();
                }
            }
        } catch (IOException | RuntimeException e) {
            failure = new SQLException("Failed to save target validation results.", e);
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
            throw new ValidationException("검증 대상 컬럼이 없습니다: " + table.getTableId());
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

    private String resultManagerId(MigrationTableInfo table) {
        return table.getMngrId() == null || table.getMngrId().isBlank()
                ? parameter.getMngrId()
                : table.getMngrId();
    }

    private String qualifiedTargetTableName(MigrationTableInfo table)
            throws ValidationException {
        return validateIdentifier(table.getTgtDbPrefix(), "target table owner")
                + "." + validateIdentifier(table.getTableNm(), "table name");
    }

    private String validateIdentifier(String value, String description)
            throws ValidationException {
        try {
            return SqlUtil.validateIdentifier(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid " + description + ": " + value, e);
        }
    }

    private String normalize(String identifier) {
        return identifier.trim().toUpperCase(Locale.ROOT);
    }
}

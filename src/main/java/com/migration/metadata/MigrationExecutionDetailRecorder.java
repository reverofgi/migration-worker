package com.migration.metadata;

import com.migration.config.MigrationParameter;
import com.migration.exception.MetadataException;
import com.migration.util.ConnectionUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** 이관 단계별 시작, 완료 및 오류 상태를 MIG_EXEC_DTL에 기록한다. */
public final class MigrationExecutionDetailRecorder {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(MigrationExecutionDetailRecorder.class);

    private final Connection godisConnection;
    private final MigrationParameter parameter;

    public MigrationExecutionDetailRecorder(
            Connection godisConnection,
            MigrationParameter parameter) {
        this.godisConnection = Objects.requireNonNull(godisConnection, "godisConnection");
        this.parameter = Objects.requireNonNull(parameter, "parameter");
    }

    public void start(MigrationTableInfo table, MigrationJobType jobType)
            throws MetadataException {
        int affected = executeUpdate(
                mapper -> mapper.upsertMigrationExecutionStart(
                        parameter.getExecOrd(), table.getTableOwner(), table.getTableId(),
                        table.getProcOrd(), jobType.name(), parameter.getMngrId()),
                "이관 단계 시작 이력 저장 실패");
        requireAffectedRow(affected, table, jobType, "시작");
        LOGGER.info("이관 단계 시작: jobType={}, table={}.{}, procOrd={}",
                jobType, table.getTableOwner(), table.getTableId(), table.getProcOrd());
    }

    public void complete(MigrationTableInfo table, MigrationJobType jobType)
            throws MetadataException {
        int affected = executeUpdate(
                mapper -> mapper.completeMigrationExecution(
                        parameter.getExecOrd(), table.getTableOwner(), table.getTableId(),
                        table.getProcOrd(), jobType.name(), parameter.getMngrId()),
                "이관 단계 완료 이력 저장 실패");
        requireAffectedRow(affected, table, jobType, "완료");
        LOGGER.info("이관 단계 완료: jobType={}, table={}.{}, procOrd={}",
                jobType, table.getTableOwner(), table.getTableId(), table.getProcOrd());
    }

    public void fail(MigrationTableInfo table, MigrationJobType jobType)
            throws MetadataException {
        int affected = executeUpdate(
                mapper -> mapper.failMigrationExecution(
                        parameter.getExecOrd(), table.getTableOwner(), table.getTableId(),
                        table.getProcOrd(), jobType.name(), parameter.getMngrId()),
                "이관 단계 오류 이력 저장 실패");
        requireAffectedRow(affected, table, jobType, "오류");
        LOGGER.info("이관 단계 오류 상태 저장: jobType={}, table={}.{}, procOrd={}",
                jobType, table.getTableOwner(), table.getTableId(), table.getProcOrd());
    }

    private int executeUpdate(
            ToIntFunction<MetaMapper> operation,
            String failureMessage) throws MetadataException {
        try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(reader);
            try (SqlSession session = factory.openSession(
                    ConnectionUtil.nonClosing(godisConnection))) {
                try {
                    int affected = operation.applyAsInt(session.getMapper(MetaMapper.class));
                    session.commit();
                    return affected;
                } catch (RuntimeException e) {
                    session.rollback();
                    throw e;
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new MetadataException(failureMessage, e);
        }
    }

    private void requireAffectedRow(
            int affected,
            MigrationTableInfo table,
            MigrationJobType jobType,
            String operation) throws MetadataException {
        if (affected < 1) {
            throw new MetadataException(
                    "MIG_EXEC_DTL " + operation + " 대상이 없습니다: EXE_ORD="
                            + parameter.getExecOrd() + ", TABLE_OWNER=" + table.getTableOwner()
                            + ", TABLE_ID=" + table.getTableId()
                            + ", PROC_ORD=" + table.getProcOrd()
                            + ", JOB_TP_CD=" + jobType);
        }
    }
}

package com.migration;

import com.migration.config.MigrationParameter;
import com.migration.config.ExitCode;
import com.migration.exception.InitializationException;
import com.migration.exception.MigrationException;
import com.migration.exception.MetadataException;
import com.migration.metadata.MigrationExecutionDetailRecorder;
import com.migration.metadata.MigrationJobType;
import com.migration.metadata.MigrationTableInfo;
import com.migration.worker.SybaseIQUnloadWorker;
import com.migration.worker.SybaseIQLoadWorker;
import com.migration.worker.LoadResultVerifyWorker;
import com.migration.worker.UnloadResultVerifyWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Batch Group 실행 흐름의 기본 구조.
 *
 * - Application에서 조회한 메타데이터 사용
 * - 물리 스키마 조회
 * - 데이터 추출
 * - 데이터 적재
 * - 데이터 검증
 * - 결과 저장
 *
 * 하나의 TASK_ID는 하나의 Batch Group에 해당한다.
 * 연결은 MigrationWorkerApplication이 소유하고 Worker는 실행 동안 빌려 쓴다.
 */
public final class MigrationWorker {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MigrationWorker.class);

    private final MigrationParameter config;
    private final Connection godisConnection;
    private final Connection asisConnection;
    private final Connection tobeConnection;
    private final MigrationTableInfo table;
    private final MigrationExecutionDetailRecorder executionDetailRecorder;

    MigrationWorker(
            MigrationParameter config,
            MigrationTableInfo table,
            Connection godisConnection,
            Connection asisConnection,
            Connection tobeConnection) {
        this.config = config;
        this.table = Objects.requireNonNull(table, "table");
        this.godisConnection = godisConnection;
        this.asisConnection = asisConnection;
        this.tobeConnection = tobeConnection;
        this.executionDetailRecorder = new MigrationExecutionDetailRecorder(
                godisConnection, config);
    }
    /**
     * 하나의 Batch Group을 실행한다.
     */
    public void execute() throws MigrationException {
        validateConnections();

        LOGGER.info(
                "Migration Worker를 시작합니다. EXEC_ORD={}, TASK_ID={}, MNGR_ID={}",
                config.getExecOrd(), config.getTaskId(), config.getMngrId());

        // 1. UNLOAD 시작일시 저장 -> AS-IS 데이터 추출 -> 종료일시 저장
        executionDetailRecorder.start(table, MigrationJobType.UNLOAD);
        try {
            unload(table);
            executionDetailRecorder.complete(table, MigrationJobType.UNLOAD);
        } catch (MigrationException | RuntimeException e) {
            recordStageFailure(table, MigrationJobType.UNLOAD, e);
            throw e;
        }

        // 2. UNLOAD_VRF 시작일시 저장 -> AS-IS 추출결과 검증 -> 종료일시 저장
        executionDetailRecorder.start(table, MigrationJobType.UNLOAD_VRF);
        try {
            unloadResultVerify(table);
            executionDetailRecorder.complete(table, MigrationJobType.UNLOAD_VRF);
        } catch (MigrationException | RuntimeException e) {
            recordStageFailure(table, MigrationJobType.UNLOAD_VRF, e);
            throw e;
        }

        // 3. LOAD 시작일시 저장 -> TO-BE 데이터 적재 -> 종료일시 저장
        executionDetailRecorder.start(table, MigrationJobType.LOAD);
        try {
            load(table);
            executionDetailRecorder.complete(table, MigrationJobType.LOAD);
        } catch (MigrationException | RuntimeException e) {
            recordStageFailure(table, MigrationJobType.LOAD, e);
            throw e;
        }

        // 4. LOAD_VRF 시작일시 저장 -> TO-BE 적재결과 검증 -> 종료일시 저장
        executionDetailRecorder.start(table, MigrationJobType.LOAD_VRF);
        try {
            loadResultVerify(table);
            executionDetailRecorder.complete(table, MigrationJobType.LOAD_VRF);
        } catch (MigrationException | RuntimeException e) {
            recordStageFailure(table, MigrationJobType.LOAD_VRF, e);
            throw e;
        }

        LOGGER.info("Migration Worker가 정상적으로 종료되었습니다.");
    }

    private void recordStageFailure(
            MigrationTableInfo table,
            MigrationJobType jobType,
            Throwable originalFailure) {
        try {
            executionDetailRecorder.fail(table, jobType);
        } catch (MetadataException historyFailure) {
            originalFailure.addSuppressed(historyFailure);
            LOGGER.error("이관 단계 오류 이력을 저장하지 못했습니다: jobType={}",
                    jobType, historyFailure);
        }
    }

    private void unload(MigrationTableInfo table) throws MigrationException {
        new SybaseIQUnloadWorker(asisConnection).execute(table);
    }

    private void unloadResultVerify(MigrationTableInfo table) throws MigrationException {
        new UnloadResultVerifyWorker(asisConnection, godisConnection, config)
                .execute(table);
    }

    private void load(MigrationTableInfo table) throws MigrationException {
        new SybaseIQLoadWorker(tobeConnection).execute(table);
    }

    private void loadResultVerify(MigrationTableInfo table) throws MigrationException {
        new LoadResultVerifyWorker(tobeConnection, godisConnection, config)
                .execute(table);
    }

    private void validateConnections() throws InitializationException {
        try {
            if (godisConnection == null || godisConnection.isClosed()) {
                throw new SQLException("GODIS connection is not available.");
            }
            if (asisConnection == null || asisConnection.isClosed()) {
                throw new SQLException("AS-IS connection is not available.");
            }
            if (tobeConnection == null || tobeConnection.isClosed()) {
                throw new SQLException("TO-BE connection is not available.");
            }
        } catch (SQLException e) {
            throw new InitializationException(
                    "Database connection validation failed.",
                    ExitCode.INITIALIZATION_ERROR.getCode(),
                    e);
        }
    }

    /*
     * package-private 접근자는 이후 단계와 테스트에서만 사용하도록
     * 제공한다. 이를 통해 static 전역 연결 사용을 방지한다.
     */
    Connection getGodisConnection() {
        return godisConnection;
    }

    Connection getAsisConnection() {
        return asisConnection;
    }

    Connection getTobeConnection() {
        return tobeConnection;
    }

    MigrationParameter getConfig() {
        return config;
    }
}

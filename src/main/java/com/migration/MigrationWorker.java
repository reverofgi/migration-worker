package com.migration;

import com.migration.config.ConnectionFactory;
import com.migration.config.MigrationConfig;
import com.migration.config.MigrationProperties;
import com.migration.config.ExitCode;
import com.migration.exception.InitializationException;
import com.migration.exception.MigrationException;
import com.migration.exception.MetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Batch Group 실행 흐름의 기본 구조.
 *
 * Phase 1에서는 다음 기능을 의도적으로 구현하지 않는다:
 * - 메타데이터 조회
 * - 물리 스키마 조회
 * - 데이터 추출
 * - 데이터 적재
 * - 데이터 검증
 * - 결과 저장
 *
 * 하나의 JOB_ID는 하나의 Batch Group에 해당한다.
 * 연결은 이 Worker 인스턴스가 소유하며 Batch Group 실행 동안 유지한다.
 */
public final class MigrationWorker {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MigrationWorker.class);

    private final MigrationConfig config;
    private final ConnectionFactory connectionFactory;

    /*
     * Batch Group 범위에서 사용하는 연결이다.
     * static 전역 연결을 피하기 위해 의도적으로 인스턴스 필드로 선언한다.
     */
    private Connection metaConnection;
    private Connection asisConnection;
    private Connection tobeConnection;

    private MigrationWorker(
            MigrationConfig config,
            ConnectionFactory connectionFactory) {
        this.config = config;
        this.connectionFactory = connectionFactory;
    }

    /**
     * 필수 명령행 인자로 Worker를 생성한다.
     *
     * 입력 형식:
     *   args[0] = EXEC_SEQ
     *   args[1] = JOB_ID
     *   args[2] = EXEC_USER (선택 사항, 기본값 SYSTEM)
     */
    public static MigrationWorker fromArguments(String[] args)
            throws MigrationException {

        MigrationConfig config = MigrationConfig.fromArguments(args);

        return new MigrationWorker(
                config,
                new ConnectionFactory(config));
    }

    /** META 연결을 생성하고 MIGRATION 그룹 설정을 전역 Properties에 적재한다. */
    public void initializeProperties() throws MigrationException {
        try {
            if (metaConnection == null || metaConnection.isClosed()) {
                metaConnection = connectionFactory.createMetaConnection();
            }
        } catch (SQLException e) {
            closeConnections();
            throw new InitializationException(
                    "Failed to initialize META database connection.",
                    ExitCode.INITIALIZATION_ERROR.getCode(),
                    e);
        }

        try {
            int propertyCount = MigrationProperties.initialize(metaConnection);
            LOGGER.info("Migration 전역 설정 {}건을 초기화했습니다.", propertyCount);
        } catch (SQLException e) {
            closeConnections();
            throw new MetadataException(
                    "Failed to initialize migration properties.", e);
        }
    }
    /**
     * 하나의 Batch Group을 실행한다.
     */
    public void execute() throws MigrationException {
        LOGGER.info(
                "Migration Worker를 시작합니다. EXEC_SEQ={}, JOB_ID={}, EXEC_USER={}",
                config.getExecSeq(),
                config.getJobId(),
                config.getExecUser());

        try {
            initializeConnections();

            /*
             * Phase 1의 처리 범위는 여기까지다.
             *
             * Phase 2에서는 다음 작업을 수행한다:
             *   1. JOB_ID로 GPCL_MIG_TABLE을 조회한다.
             *   2. PROC_ORD, TABLE_SCHEMA, TABLE_NAME 순으로 정렬한다.
             *   3. 테이블을 순차 처리한다.
             */
            LOGGER.info(
                    "Phase 1 기본 구조를 성공적으로 초기화했습니다. "
                            + "테이블 처리는 현재 단계에서 구현하지 않았습니다.");

        } finally {
            closeConnections();
        }

        LOGGER.info("Migration Worker가 정상적으로 종료되었습니다.");
    }

    private void initializeConnections() throws InitializationException {
        try {
            if (metaConnection == null || metaConnection.isClosed()
                    || !MigrationProperties.isInitialized()) {
                throw new SQLException("Migration properties are not initialized.");
            }
            asisConnection = connectionFactory.createAsisConnection();
            tobeConnection = connectionFactory.createTobeConnection();

            LOGGER.info("Batch Group 범위의 데이터베이스 연결 3개를 초기화했습니다.");
        } catch (SQLException e) {
            closeConnections();
            throw new InitializationException(
                    "Failed to initialize Batch Group database connections.",
                    ExitCode.INITIALIZATION_ERROR.getCode(),
                    e);
        }
    }

    private void closeConnections() {
        closeConnection("META", metaConnection);
        closeConnection("ASIS", asisConnection);
        closeConnection("TOBE", tobeConnection);

        metaConnection = null;
        asisConnection = null;
        tobeConnection = null;
    }

    private void closeConnection(String name, Connection connection) {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
            LOGGER.info("{} 연결을 종료했습니다.", name);
        } catch (SQLException e) {
            LOGGER.warn("{} 연결 종료에 실패했습니다.", name, e);
        }
    }

    /*
     * package-private 접근자는 이후 단계와 테스트에서만 사용하도록
     * 제공한다. 이를 통해 static 전역 연결 사용을 방지한다.
     */
    Connection getMetaConnection() {
        return metaConnection;
    }

    Connection getAsisConnection() {
        return asisConnection;
    }

    Connection getTobeConnection() {
        return tobeConnection;
    }

    MigrationConfig getConfig() {
        return config;
    }
}

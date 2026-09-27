package com.migration;

import com.migration.config.MigrationParameter;
import com.migration.config.ExitCode;
import com.migration.exception.InitializationException;
import com.migration.exception.MigrationException;
import com.migration.exception.MetadataException;
import com.migration.metadata.MetaMapper;
import com.migration.metadata.MigrationTableInfo;
import com.migration.worker.SybaseIQUnloadWorker;
import com.migration.worker.SybaseIQLoadWorker;
import com.migration.worker.LoadResultVerifyWorker;
import com.migration.worker.UnloadResultVerifyWorker;
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
import java.sql.SQLException;

/**
 * Batch Group 실행 흐름의 기본 구조.
 *
 * - 메타데이터 조회
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

    MigrationWorker(
            MigrationParameter config,
            Connection godisConnection,
            Connection asisConnection,
            Connection tobeConnection) {
        this.config = config;
        this.godisConnection = godisConnection;
        this.asisConnection = asisConnection;
        this.tobeConnection = tobeConnection;
    }
    /**
     * 하나의 Batch Group을 실행한다.
     */
    public void execute() throws MigrationException {
        LOGGER.info(
                "Migration Worker를 시작합니다. EXEC_ORD={}, TASK_ID={}, MNGR_ID={}",
                config.getExecOrd(),
                config.getTaskId(),
                config.getMngrId());

        validateConnections();

        // 마이그레이션은 테이블 1개씩 실행하므로, 처리 대상이 한개이다.
        // 대량용량 테이블의 경우, 한개 테이블을 기간등의 조건으로 여러 번 나누어 실시
        MigrationTableInfo table = loadMigrationTable();
        unload(table);
        unloadResultVerify(table);
        load(table);
        loadResultVerify(table);

        LOGGER.info("Migration Worker가 정상적으로 종료되었습니다.");
    }

    private MigrationTableInfo loadMigrationTable() throws MetadataException {
        try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(reader);
            try (SqlSession session = factory.openSession(
                    ConnectionUtil.nonClosing(godisConnection))) {
                return session.getMapper(MetaMapper.class)
                        .selectMigrationTable(config.getTaskId());
            }
        } catch (IOException | RuntimeException e) {
            throw new MetadataException("Failed to load migration table metadata.", e);
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

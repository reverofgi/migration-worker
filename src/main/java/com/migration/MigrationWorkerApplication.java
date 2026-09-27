package com.migration;

import com.migration.config.ConnectionFactory;
import com.migration.config.ExitCode;
import com.migration.config.MigrationParameter;
import com.migration.config.MigrationProperties;
import com.migration.exception.MigrationException;
import com.migration.exception.MetadataException;
import com.migration.metadata.MigrationDatabasePrefixes;
import com.migration.metadata.MigrationMetadataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 애플리케이션 진입점.
 *
 */
public final class MigrationWorkerApplication {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MigrationWorkerApplication.class);

    private MigrationWorkerApplication() {
        // 인스턴스 생성을 허용하지 않는 유틸리티 클래스다.
    }

    public static void main(String[] args) {
        int exitCode = ExitCode.SUCCESS.getCode();

        try {
            execute(args);
        } catch (MigrationException e) {
            exitCode = e.getExitCode();
            LOGGER.error("Migration Worker 실행에 실패했습니다. exitCode={}", exitCode, e);
        } catch (SQLException e) {
            exitCode = ExitCode.INITIALIZATION_ERROR.getCode();
            LOGGER.error("데이터베이스 연결 초기화에 실패했습니다. exitCode={}", exitCode, e);
        } catch (Exception e) {
            exitCode = ExitCode.SYSTEM_ERROR.getCode();
            LOGGER.error("Migration Worker 실행 중 예상하지 못한 오류가 발생했습니다.", e);
        }

        System.exit(exitCode);
    }

    private static void execute(String[] args) throws MigrationException, SQLException {
        // 1. DB 조회 전에 명령행 파라미터를 검증한다.
        MigrationParameter parameter = MigrationParameter.fromArguments(args);
        ConnectionFactory connectionFactory = new ConnectionFactory();

        // 2. GODIS 연결은 Application이 생성하고 전체 실행 동안 소유한다.
        try (Connection godisConnection = connectionFactory.createGodisConnection())
        {
            MigrationDatabasePrefixes databasePrefixes = new MigrationMetadataLoader()
                    .loadDatabasePrefixes(godisConnection, parameter.getTaskId());
            initializeProperties(godisConnection);

            // 3. TASK_ID의 PREFIX와 공통코드 키를 조합하여 AS-IS/TO-BE에 연결한다.
            try (Connection sourceConnection =
                    connectionFactory.createSourceConnection(databasePrefixes.getSourcePrefix());
                 Connection targetConnection =
                    connectionFactory.createTargetConnection(databasePrefixes.getTargetPrefix()))
            {

                // 4. 생성된 실행 문맥과 Connection을 Worker에 전달한다.
                MigrationWorker worker = new MigrationWorker(
                        parameter,
                        godisConnection,
                        sourceConnection,
                        targetConnection);
                worker.execute();
            }
        }
    }

    private static void initializeProperties(Connection godisConnection)
            throws MetadataException {
        try {
            int propertyCount = MigrationProperties.initialize(godisConnection);
            LOGGER.info("Migration 전역 설정 {}건을 초기화했습니다.", propertyCount);
        } catch (SQLException e) {
            throw new MetadataException("Failed to initialize migration properties.", e);
        }
    }
}

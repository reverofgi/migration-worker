package com.migration;

import com.migration.config.ExitCode;
import com.migration.exception.MigrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 애플리케이션 진입점.
 *
 * Phase 1의 책임:
 * - CLI 인자를 해석한다.
 * - MigrationWorker를 생성하고 실행한다.
 * - 실행 결과 또는 예외를 종료 코드로 변환한다.
 *
 * 실제 이관 업무 로직은 이 클래스에 구현하지 않는다.
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
            MigrationWorker worker = MigrationWorker.fromArguments(args);
            worker.initializeProperties();
            worker.execute();
        } catch (MigrationException e) {
            exitCode = e.getExitCode();
            LOGGER.error("Migration Worker 실행에 실패했습니다. exitCode={}", exitCode, e);
        } catch (Exception e) {
            exitCode = ExitCode.SYSTEM_ERROR.getCode();
            LOGGER.error("Migration Worker 실행 중 예상하지 못한 오류가 발생했습니다.", e);
        }

        System.exit(exitCode);
    }
}

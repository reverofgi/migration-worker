package com.migration.util;

/**
 * 로깅 유틸리티 확장 지점.
 *
 * Phase 1에서는 애플리케이션 클래스가 SLF4J를 통해 직접 로그를 기록한다.
 * 실행 컨텍스트 필드가 확정되면 이후 단계에서 구조화된 MDC 및 컨텍스트
 * 도우미를 추가할 수 있다.
 */
public final class LogUtil {

    private LogUtil() {
    }

    public static String tableName(String tableSchema, String tableName) {
        return tableSchema + "." + tableName;
    }
}

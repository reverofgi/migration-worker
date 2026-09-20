package com.migration.util;

/**
 * SQL 유틸리티 확장 지점.
 *
 * Phase 1에서는 Sybase IQ SQL을 의도적으로 생성하지 않는다.
 * 동적 식별자 검증과 SQL 생성은 이후 단계의 책임이다.
 */
public final class SqlUtil {

    private SqlUtil() {
    }

    /**
     * 이후 SQL 빌더에서 사용할 최소한의 식별자 검증이다.
     * Sybase IQ의 전체 식별자 정책을 구현한 것은 아니다.
     */
    public static boolean isSimpleIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        if (!Character.isLetter(value.charAt(0))
                && value.charAt(0) != '_') {
            return false;
        }

        for (int i = 1; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return false;
            }
        }

        return true;
    }
}

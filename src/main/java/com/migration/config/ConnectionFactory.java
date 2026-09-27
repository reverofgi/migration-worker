package com.migration.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Batch Group에서 사용할 세 종류의 논리 연결을 생성한다.
 *
 * GODIS 연결정보는 프로그램 상수로 관리한다. AS-IS와 TO-BE 연결정보는
 * TASK_ID 메타데이터의 DB PREFIX와 MigrationProperties 키를 조합해 읽는다.
 */
public final class ConnectionFactory {
    private static final String GODIS_JDBC_URL = "jdbc:mariadb://192.168.0.122:3306/KBADW";
    private static final String GODIS_USER = "iteyes";
    private static final String GODIS_PWD = "iteyes123$";

    private final String godisJdbcUrl;
    private final String godisUser;
    private final String godisPassword;

    public ConnectionFactory() {
        this(GODIS_JDBC_URL, GODIS_USER, GODIS_PWD);
    }

    ConnectionFactory(String godisJdbcUrl, String godisUser, String godisPassword) {
        this.godisJdbcUrl = requireMetaSetting(godisJdbcUrl, "GODIS_JDBC_URL");
        this.godisUser = requireMetaSetting(godisUser, "GODIS_USER");
        this.godisPassword = requireMetaSetting(godisPassword, "GODIS_PWD");
    }

    public Connection createGodisConnection() throws SQLException {
        return DriverManager.getConnection(godisJdbcUrl, godisUser, godisPassword);
    }

    public Connection createSourceConnection(String databasePrefix) throws SQLException {
        return DriverManager.getConnection(
                MigrationProperties.getRequired(propertyKey(databasePrefix, "SOURCE_JDBC_URL")),
                MigrationProperties.getRequired(propertyKey(databasePrefix, "SOURCE_USER")),
                MigrationProperties.getRequired(propertyKey(databasePrefix, "SOURCE_PWD")));
    }

    public Connection createTargetConnection(String databasePrefix) throws SQLException {
        return DriverManager.getConnection(
                MigrationProperties.getRequired(propertyKey(databasePrefix, "TARGET_JDBC_URL")),
                MigrationProperties.getRequired(propertyKey(databasePrefix, "TARGET_USER")),
                MigrationProperties.getRequired(propertyKey(databasePrefix, "TARGET_PWD")));
    }

    static String propertyKey(String databasePrefix, String suffix) throws SQLException {
        if (databasePrefix == null || databasePrefix.isBlank()) {
            throw new SQLException("Database property prefix must not be blank.");
        }
        return databasePrefix.trim() + "_" + suffix;
    }

    private static String requireMetaSetting(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank.");
        }
        return value;
    }
}

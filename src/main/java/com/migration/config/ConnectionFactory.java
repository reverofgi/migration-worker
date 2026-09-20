package com.migration.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Batch Group에서 사용할 세 종류의 논리 연결을 생성한다.
 *
 * META 연결정보는 프로그램 상수로 관리한다. AS-IS와 TO-BE 연결정보는
 * 프로그램 시작 시 초기화된 MigrationProperties에서 읽는다.
 */
public final class ConnectionFactory {
    private static final String META_JDBC_URL = "jdbc:mariadb://localhost:3316/mig_test";
    private static final String META_USER = "MigUser";
    private static final String META_PWD = "miguser1234!";

    private static final String ASIS_JDBC_URL_KEY = "ASIS_JDBC_URL";
    private static final String ASIS_USER_KEY = "ASIS_USER";
    private static final String ASIS_PWD_KEY = "ASIS_PWD";
    private static final String TOBE_JDBC_URL_KEY = "TOBE_JDBC_URL";
    private static final String TOBE_USER_KEY = "TOBE_USER";
    private static final String TOBE_PWD_KEY = "TOBE_PWD";

    private final MigrationConfig config;
    private final String metaJdbcUrl;
    private final String metaUser;
    private final String metaPassword;

    public ConnectionFactory(MigrationConfig config) {
        this(config, META_JDBC_URL, META_USER, META_PWD);
    }

    ConnectionFactory(
            MigrationConfig config,
            String metaJdbcUrl,
            String metaUser,
            String metaPassword) {
        this.config = Objects.requireNonNull(config, "config");
        this.metaJdbcUrl = requireMetaSetting(metaJdbcUrl, "META_JDBC_URL");
        this.metaUser = requireMetaSetting(metaUser, "META_USER");
        this.metaPassword = requireMetaSetting(metaPassword, "META_PWD");
    }

    public Connection createMetaConnection() throws SQLException {
        return DriverManager.getConnection(metaJdbcUrl, metaUser, metaPassword);
    }

    public Connection createAsisConnection() throws SQLException {
        return createConfiguredConnection(
                ASIS_JDBC_URL_KEY,
                ASIS_USER_KEY,
                ASIS_PWD_KEY);
    }

    public Connection createTobeConnection() throws SQLException {
        return createConfiguredConnection(
                TOBE_JDBC_URL_KEY,
                TOBE_USER_KEY,
                TOBE_PWD_KEY);
    }

    private Connection createConfiguredConnection(
            String urlKey,
            String userKey,
            String passwordKey) throws SQLException {
        String url = MigrationProperties.getRequired(urlKey);
        String user = MigrationProperties.getRequired(userKey);
        String password = MigrationProperties.getRequired(passwordKey);
        return DriverManager.getConnection(url, user, password);
    }

    private static String requireMetaSetting(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank.");
        }
        return value;
    }

    public MigrationConfig getConfig() {
        return config;
    }
}

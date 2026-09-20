package com.migration.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

/** GPCL_CM_CD_VAL에서 한 번만 읽어 전역으로 사용하는 Migration 설정. */
public final class MigrationProperties {
    private static final String SELECT_PROPERTIES_SQL = """
            SELECT CD_VAL_NM        AS PROP_ID,
                   CD_ADD_INFO_VAL1 AS PROP_VALUE
              FROM GPCL_CM_CD_VAL
             WHERE GRP_CD_ID = 'MIGRATION'
            """;

    private static final Object LOCK = new Object();
    private static volatile Properties properties;

    private MigrationProperties() {
        // 인스턴스 생성을 허용하지 않는 전역 설정 클래스다.
    }

    public static int initialize(Connection metaConnection) throws SQLException {
        Objects.requireNonNull(metaConnection, "metaConnection");
        if (properties != null) {
            return properties.size();
        }

        synchronized (LOCK) {
            if (properties != null) {
                return properties.size();
            }
            if (metaConnection.isClosed()) {
                throw new SQLException("META connection is closed.");
            }

            Properties loaded = new Properties();
            try (
                PreparedStatement statement = metaConnection.prepareStatement(SELECT_PROPERTIES_SQL);
                ResultSet resultSet = statement.executeQuery())
            {
                while (resultSet.next()) {
                    String key = resultSet.getString("PROP_ID");
                    String value = resultSet.getString("PROP_VALUE");
                    if (key == null || key.isBlank()) {
                        throw new SQLException("Migration property ID is missing or blank.");
                    }
                    if (value == null || value.isBlank()) {
                        throw new SQLException(
                                "Migration property value is missing or blank: " + key);
                    }
                    if (loaded.containsKey(key)) {
                        throw new SQLException("Duplicate migration property: " + key);
                    }
                    loaded.setProperty(key, value);
                }
            }
            properties = loaded;
            return loaded.size();
        }
    }

    public static String getRequired(String key) throws SQLException {
        Properties current = properties;
        if (current == null) {
            throw new SQLException("Migration properties are not initialized.");
        }
        String value = current.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new SQLException("Required migration property not found: " + key);
        }
        return value;
    }

    public static boolean isInitialized() {
        return properties != null;
    }

    static void resetForTest() {
        synchronized (LOCK) {
            properties = null;
        }
    }
}

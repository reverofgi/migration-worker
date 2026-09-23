package com.migration.config;

import com.migration.metadata.MetaMapper;
import com.migration.metadata.MigrationProperty;
import com.migration.util.ConnectionUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/** GPCL_CM_CD_VAL에서 한 번만 읽어 전역으로 사용하는 Migration 설정. */
public final class MigrationProperties {
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
                throw new SQLException("GODIS Web DB connection is closed.");
            }

            Properties loaded = load(metaConnection);
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

    /** Connection 생성 단계에서 전역 상태를 변경하지 않고 설정을 조회한다. */
    static Properties load(Connection connection) throws SQLException {
        Properties loaded = new Properties();
        for (MigrationProperty property : selectMigrationProperties(connection)) {
            String key = property.getKey();
            String value = property.getValue();
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
        return loaded;
    }

    private static List<MigrationProperty> selectMigrationProperties(Connection connection)
            throws SQLException {
        try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(reader);
            try (SqlSession session = factory.openSession(
                    ConnectionUtil.nonClosing(connection))) {
                return session.getMapper(MetaMapper.class).selectMigrationProperties();
            }
        } catch (IOException | RuntimeException e) {
            throw new SQLException("Failed to load migration properties.", e);
        }
    }

    static void resetForTest() {
        synchronized (LOCK) {
            properties = null;
        }
    }
}

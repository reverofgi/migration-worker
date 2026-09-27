package com.migration.metadata;

import com.migration.exception.MetadataException;
import com.migration.util.ConnectionUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;

/** GODIS 이관 메타데이터 조회와 실행 전 유효성 검사를 담당한다. */
public final class MigrationMetadataLoader {

    public MigrationDatabasePrefixes loadDatabasePrefixes(
            Connection connection,
            String taskId) throws MetadataException {
        Objects.requireNonNull(connection, "connection");
        if (taskId == null || taskId.isBlank()) {
            throw new MetadataException("TASK_ID must not be blank.");
        }

        try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(reader);
            try (SqlSession session = factory.openSession(
                    ConnectionUtil.nonClosing(connection))) {
                List<MigrationDatabasePrefixes> prefixes = session.getMapper(MetaMapper.class)
                        .selectMigrationDatabasePrefixes(taskId.trim());
                if (prefixes.isEmpty()) {
                    throw new MetadataException(
                            "MIG_TBL_INFO에 TASK_ID가 없습니다: " + taskId);
                }
                if (prefixes.size() > 1) {
                    throw new MetadataException(
                            "TASK_ID에 서로 다른 DB PREFIX가 지정되어 있습니다: " + taskId);
                }

                MigrationDatabasePrefixes result = prefixes.getFirst();
                requirePrefix(result.getSourcePrefix(), "SRC_DB_PREFIX", taskId);
                requirePrefix(result.getTargetPrefix(), "TGT_DB_PREFIX", taskId);
                return result;
            }
        } catch (IOException | RuntimeException e) {
            throw new MetadataException(
                    "Failed to load database prefixes for TASK_ID: " + taskId, e);
        }
    }

    private static void requirePrefix(String value, String column, String taskId)
            throws MetadataException {
        if (value == null || value.isBlank()) {
            throw new MetadataException(
                    column + "가 없습니다: TASK_ID=" + taskId);
        }
    }
}

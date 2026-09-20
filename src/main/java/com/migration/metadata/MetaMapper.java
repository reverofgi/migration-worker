package com.migration.metadata;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 확인된 Phase 2 메타데이터 테이블용 MyBatis 매퍼. */
public interface MetaMapper {
    List<TableInfo> selectMigrationTables(@Param("jobId") String jobId);

    List<ValidationTarget> selectValidationTargets(
            @Param("tableSchema") String tableSchema,
            @Param("tableName") String tableName);
}

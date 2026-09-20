package com.migration.metadata;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MyBatis mapper for the confirmed Phase 2 metadata tables. */
public interface MetaMapper {
    List<TableInfo> selectMigrationTables(@Param("jobId") String jobId);

    List<ValidationTarget> selectValidationTargets(
            @Param("tableSchema") String tableSchema,
            @Param("tableName") String tableName);
}

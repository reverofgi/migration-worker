package com.migration.metadata;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MetaMapper {
    List<MigrationProperty> selectMigrationProperties();

    MigrationTableInfo selectMigrationTable(
        @Param("taskId") String taskId);

    List<ValidationTarget> selectValidationTargets(
        @Param("tableId") String tableId);

    int upsertSourceValidationResults(
            @Param("execOrd") int execOrd,
            @Param("tableId") String tableId,
            @Param("procOrd") int procOrd,
            @Param("mngrId") String mngrId,
            @Param("auditId") String auditId,
            @Param("results") List<SourceValidationResult> results);
}

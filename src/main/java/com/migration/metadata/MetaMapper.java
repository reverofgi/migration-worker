package com.migration.metadata;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MetaMapper {
    List<MigrationProperty> selectMigrationProperties();

    List<MigrationDatabasePrefixes> selectMigrationDatabasePrefixes(
            @Param("taskId") String taskId);

    MigrationTableInfo selectMigrationTable(
        @Param("taskId") String taskId);

    List<ValidationTarget> selectValidationTargets(
        @Param("tableOwner") String tableOwner,
        @Param("tableId") String tableId);

    int deleteValidationResults(
            @Param("execOrd") int execOrd,
            @Param("tableOwner") String tableOwner,
            @Param("tableId") String tableId,
            @Param("procOrd") int procOrd);

    int upsertSourceValidationResults(
            @Param("execOrd") int execOrd,
            @Param("tableOwner") String tableOwner,
            @Param("tableId") String tableId,
            @Param("procOrd") int procOrd,
            @Param("mngrId") String mngrId,
            @Param("auditId") String auditId,
            @Param("results") List<SourceValidationResult> results);

    List<SourceValidationResult> selectSourceValidationResults(
            @Param("execOrd") int execOrd,
            @Param("tableOwner") String tableOwner,
            @Param("tableId") String tableId,
            @Param("procOrd") int procOrd);

    int upsertTargetValidationResults(
            @Param("execOrd") int execOrd,
            @Param("tableOwner") String tableOwner,
            @Param("tableId") String tableId,
            @Param("procOrd") int procOrd,
            @Param("mngrId") String mngrId,
            @Param("auditId") String auditId,
            @Param("results") List<TargetValidationResult> results);
}

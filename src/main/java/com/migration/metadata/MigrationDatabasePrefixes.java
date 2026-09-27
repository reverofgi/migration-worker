package com.migration.metadata;

/** MIG_TBL_INFO에서 TASK_ID별로 조회한 원천/대상 데이터베이스 접두어. */
public final class MigrationDatabasePrefixes {
    private String sourcePrefix;
    private String targetPrefix;

    public String getSourcePrefix() { return sourcePrefix; }

    public String getTargetPrefix() { return targetPrefix; }
}

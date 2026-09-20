package com.migration.metadata;

/** Migration table metadata read from GPCL_MIG_TABLE. */
public class TableInfo {
    private String tableSchema;
    private String tableName;
    private int procOrd;
    private String tableComment;
    private String jobId;
    private String splitYn;
    private String migCond;
    private String mngrId;

    public String getTableSchema() { return tableSchema; }
    public void setTableSchema(String tableSchema) { this.tableSchema = tableSchema; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public int getProcOrd() { return procOrd; }
    public void setProcOrd(int procOrd) { this.procOrd = procOrd; }
    public String getTableComment() { return tableComment; }
    public void setTableComment(String tableComment) { this.tableComment = tableComment; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getSplitYn() { return splitYn; }
    public void setSplitYn(String splitYn) { this.splitYn = splitYn; }
    public String getMigCond() { return migCond; }
    public void setMigCond(String migCond) { this.migCond = migCond; }
    public String getMngrId() { return mngrId; }
    public void setMngrId(String mngrId) { this.mngrId = mngrId; }
}

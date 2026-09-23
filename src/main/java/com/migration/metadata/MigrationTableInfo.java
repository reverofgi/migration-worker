package com.migration.metadata;

import java.time.LocalDateTime;
import java.util.List;

/** GPCL_MIG_TABLE에서 조회한 이관 테이블 메타데이터. */
public class MigrationTableInfo {
    private String tableId;
    private int procOrd;
    private String taskId;
    private String tableNm;
    private String migCond;
    private String migMode;
    private String lobYn;
    private String migModeRsn;
    private int parallelDgr;
    private String mngrId;
    private String bizAreaCd;
    private String subjAreaCd;
    private String grp1Cd;
    private String grp2Cd;
    private String grp3Cd;
    private String grp4Cd;
    private String grp5Cd;
    private String clnupTpCd;
    private String useYn;
    private String regId;
    private LocalDateTime regDtm;
    private String lstAdjprnId;
    private LocalDateTime lstAdjDtm;
    private List<ValidationTarget> validationTargets;

    public String getTableId() { return tableId; }
    // public void setTableId(String tableId) { this.tableId = tableId; }

    public int getProcOrd() { return procOrd; }
    // public void setProcOrd(int procOrd) { this.procOrd = procOrd; }

    public String getTaskId() { return taskId; }
    // public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTableNm() { return tableNm; }
    // public void setTableNm(String tableNm) { this.tableNm = tableNm; }

    public String getMigCond() { return migCond; }
    // public void setMigCond(String migCond) { this.migCond = migCond; }

    public String getMigMode() { return migMode; }
    // public void setMigMode(String migMode) { this.migMode = migMode; }

    public String getLobYn() { return lobYn; }
    // public void setLobYn(String lobYn) { this.lobYn = lobYn; }

    public String getMigModeRsn() { return migModeRsn; }
    // public void setMigModeRsn(String migModeRsn) { this.migModeRsn = migModeRsn; }

    public int getParallelDgr() { return parallelDgr; }
    // public void setParallelDgr(int parallelDgr) { this.parallelDgr = parallelDgr; }

    public String getMngrId() { return mngrId; }
    // public void setMngrId(String mngrId) { this.mngrId = mngrId; }

    public String getBizAreaCd() { return bizAreaCd; }
    // public void setBizAreaCd(String bizAreaCd) { this.bizAreaCd = bizAreaCd; }

    public String getSubjAreaCd() { return subjAreaCd; }
    // public void setSubjAreaCd(String subjAreaCd) { this.subjAreaCd = subjAreaCd; }

    public String getGrp1Cd() { return grp1Cd; }
    // public void setGrp1Cd(String grp1Cd) { this.grp1Cd = grp1Cd; }

    public String getGrp2Cd() { return grp2Cd; }
    // public void setGrp2Cd(String grp2Cd) { this.grp2Cd = grp2Cd; }

    public String getGrp3Cd() { return grp3Cd; }
    // public void setGrp3Cd(String grp3Cd) { this.grp3Cd = grp3Cd; }

    public String getGrp4Cd() { return grp4Cd; }
    // public void setGrp4Cd(String grp4Cd) { this.grp4Cd = grp4Cd; }

    public String getGrp5Cd() { return grp5Cd; }
    // public void setGrp5Cd(String grp5Cd) { this.grp5Cd = grp5Cd; }

    public String getClnupTpCd() { return clnupTpCd; }
    // public void setClnupTpCd(String clnupTpCd) { this.clnupTpCd = clnupTpCd; }

    public String getUseYn() { return useYn; }
    // public void setUseYn(String useYn) { this.useYn = useYn; }

    public String getRegId() { return regId; }
    // public void setRegId(String regId) { this.regId = regId; }

    public LocalDateTime getRegDtm() { return regDtm; }
    // public void setRegDtm(LocalDateTime regDtm) { this.regDtm = regDtm; }

    public String getLstAdjprnId() { return lstAdjprnId; }
    // public void setLstAdjprnId(String lstAdjprnId) { this.lstAdjprnId = lstAdjprnId; }

    public LocalDateTime getLstAdjDtm() { return lstAdjDtm; }
    // public void setLstAdjDtm(LocalDateTime lstAdjDtm) { this.lstAdjDtm = lstAdjDtm; }

    /** TABLE_ID로 조회한 GPCL_MIG_VRF_TARGET 컬럼 목록. */
    public List<ValidationTarget> getValidationTargets() { return validationTargets; }
    // public void setValidationTargets(List<ValidationTarget> validationTargets) {
    //     this.validationTargets = validationTargets;
    // }
}

package com.migration.metadata;

/** MIG_EXEC_DTL.JOB_TP_CD에 저장하는 이관 단계 코드. */
public enum MigrationJobType {
    UNLOAD,
    UNLOAD_VRF,
    LOAD,
    LOAD_VRF
}

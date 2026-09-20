package com.migration.config;

import com.migration.exception.InvalidArgumentException;

import java.util.Objects;

/**
 * Worker 한 번의 실행에 사용하는 불변 런타임 설정.
 *
 * 하나의 이관 배치에 필요한 CLI 및 런타임 값을 보관한다. 데이터베이스 설정은
 * GPCL_CM_CD_VAL의 컬럼 규칙이 제공될 때까지 명시적인 확장 지점으로 유지한다.
 */
public final class MigrationConfig {

    private final int execSeq;
    private final String jobId;
    private final String execUser;

    private MigrationConfig(int execSeq, String jobId, String execUser) {
        this.execSeq = execSeq;
        this.jobId = jobId;
        this.execUser = execUser;
    }

    public static MigrationConfig fromArguments(String[] args)
            throws InvalidArgumentException {

        if (args == null || (args.length != 2 && args.length != 3)) {
            throw new InvalidArgumentException(
                    "Usage: java -cp ... com.migration.MigrationWorkerApplication "
                            + "<EXEC_SEQ> <JOB_ID> [EXEC_USER]");
        }

        final int execSeq;
        try {
            execSeq = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InvalidArgumentException(
                    "EXEC_SEQ must be a numeric value: " + args[0], e);
        }

        if (execSeq < 0) {
            throw new InvalidArgumentException(
                    "EXEC_SEQ must be zero or greater.");
        }

        String jobId = Objects.requireNonNull(args[1], "JOB_ID").trim();
        if (jobId.isEmpty()) {
            throw new InvalidArgumentException("JOB_ID must not be empty.");
        }

        String execUser = args.length == 3
                ? args[2].trim()
                : "SYSTEM";

        if (execUser.isEmpty()) {
            execUser = "SYSTEM";
        }

        return new MigrationConfig(execSeq, jobId, execUser);
    }

    public int getExecSeq() {
        return execSeq;
    }

    public String getJobId() {
        return jobId;
    }

    public String getExecUser() {
        return execUser;
    }
}

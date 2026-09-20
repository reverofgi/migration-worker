package com.migration.config;

import com.migration.exception.InvalidArgumentException;

import java.util.Objects;

/**
 * Immutable runtime configuration for one Worker execution.
 *
 * CLI/runtime values for one migration batch. Database settings remain an
 * explicit extension point until the GPCL_CM_CD_VAL column contract is supplied.
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

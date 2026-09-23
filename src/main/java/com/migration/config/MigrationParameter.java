package com.migration.config;

import com.migration.exception.InvalidArgumentException;

import java.util.Objects;

/**
 * Worker 한 번의 실행에 사용하는 불변 런타임 설정.
 *
 * 하나의 이관 배치에 필요한 CLI 및 런타임 값을 보관한다. 데이터베이스 설정은
 * GPCL_CM_CD_VAL의 컬럼 규칙이 제공될 때까지 명시적인 확장 지점으로 유지한다.
 */
public final class MigrationParameter {

    private final int execOrd;
    private final String taskId;
    private final String mngrId;

    private MigrationParameter(int execOrd, String taskId, String mngrId) {
        this.execOrd = execOrd;
        this.taskId = taskId;
        this.mngrId = mngrId;
    }

    public static MigrationParameter fromArguments(String[] args)
            throws InvalidArgumentException {

        if (args == null || (args.length != 2 && args.length != 3)) {
            throw new InvalidArgumentException(
                    "Usage: java -cp ... com.migration.MigrationWorkerApplication "
                            + "<EXEC_ORD> <TASK_ID> [MNGR_ID]");
        }

        final int execOrd;
        try {
            execOrd = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InvalidArgumentException(
                    "EXEC_ORD must be a numeric value: " + args[0], e);
        }

        if (execOrd < 0) {
            throw new InvalidArgumentException(
                    "EXEC_ORD must be zero or greater.");
        }

        String taskId = Objects.requireNonNull(args[1], "TASK_ID").trim();
        if (taskId.isEmpty()) {
            throw new InvalidArgumentException("TASK_ID must not be empty.");
        }

        String execUser = args.length == 3
                ? args[2].trim()
                : "SYSTEM";

        if (execUser.isEmpty()) {
            execUser = "SYSTEM";
        }

        return new MigrationParameter(execOrd, taskId, execUser);
    }

    public int getExecOrd() {
        return execOrd;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getMngrId() {
        return mngrId;
    }
}

package com.migration.util;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/** TASK 전용 로그 파일명이 MDC에 설정된 이벤트만 파일 Appender로 전달한다. */
public final class TaskLogMdcFilter extends Filter<ILoggingEvent> {
    private static final String LOG_FILE_MDC_KEY = "migrationLogFile";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        String logFile = event.getMDCPropertyMap().get(LOG_FILE_MDC_KEY);
        return logFile == null || logFile.isBlank()
                ? FilterReply.DENY
                : FilterReply.NEUTRAL;
    }
}

package com.migration.metadata;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface SybaseIQMapper {
    Map<String, Object> selectSourceValidationAggregate(
            @Param("tableName") String tableName,
            @Param("migCond") String migCond,
            @Param("targets") List<ValidationTarget> targets);
}

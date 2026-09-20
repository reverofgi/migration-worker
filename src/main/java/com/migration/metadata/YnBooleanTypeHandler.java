package com.migration.metadata;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** JDBC 드라이버별 불리언 변환 방식에 의존하지 않고 메타데이터의 Y/N 값을 변환한다. */
public final class YnBooleanTypeHandler extends BaseTypeHandler<Boolean> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Boolean value, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, value ? "Y" : "N");
    }

    @Override public Boolean getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toBoolean(rs.getString(columnName));
    }
    @Override public Boolean getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toBoolean(rs.getString(columnIndex));
    }
    @Override public Boolean getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toBoolean(cs.getString(columnIndex));
    }

    private boolean toBoolean(String value) {
        return "Y".equalsIgnoreCase(value);
    }
}

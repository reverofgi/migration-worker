package com.migration.worker;

import com.migration.config.MigrationFileFormat;
import com.migration.metadata.MigrationTableInfo;
import com.migration.metadata.ValidationTarget;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SybaseIQUnloadWorkerTest {

    @Test
    void ordersExtractRowsByPrimaryKeysInColumnOrdinalOrder() throws Exception {
        MigrationTableInfo table = tableInfo("SEQ_NO > 0");
        List<ValidationTarget> columns = List.of(
                column("PK_SECOND", 3, true),
                column("NON_PK", 2, false),
                column("PK_FIRST", 1, true));
        MigrationFileFormat format = new MigrationFileFormat(
                "__NULL__", "__BFILE_ERROR__", "|", "\r\n");

        try (var connection = DriverManager.getConnection("jdbc:h2:mem:unload_order")) {
            String sql = buildExtractSql(
                    new SybaseIQUnloadWorker(connection), table, columns, format);

            assertEquals("SELECT PK_SECOND, NON_PK, PK_FIRST "
                    + "FROM DWDB.TB_SAMPLE WHERE SEQ_NO > 0 "
                    + "ORDER BY PK_FIRST, PK_SECOND", sql);
        }
    }

    @Test
    void leavesExtractUnorderedWhenNoPrimaryKeyIsDefined() throws Exception {
        MigrationTableInfo table = tableInfo(null);
        List<ValidationTarget> columns = List.of(
                column("COL_A", 1, false),
                column("COL_B", 2, false));
        MigrationFileFormat format = new MigrationFileFormat(
                "__NULL__", "__BFILE_ERROR__", "|", "\r\n");

        try (var connection = DriverManager.getConnection("jdbc:h2:mem:unload_no_pk")) {
            String sql = buildExtractSql(
                    new SybaseIQUnloadWorker(connection), table, columns, format);

            assertEquals("SELECT COL_A, COL_B FROM DWDB.TB_SAMPLE", sql);
        }
    }

    private static String buildExtractSql(
            SybaseIQUnloadWorker worker,
            MigrationTableInfo table,
            List<ValidationTarget> columns,
            MigrationFileFormat format) throws Exception {
        Method method = SybaseIQUnloadWorker.class.getDeclaredMethod(
                "buildExtractSql",
                MigrationTableInfo.class,
                List.class,
                String.class,
                MigrationFileFormat.class);
        method.setAccessible(true);
        return (String) method.invoke(worker, table, columns, null, format);
    }

    private static MigrationTableInfo tableInfo(String condition) throws Exception {
        MigrationTableInfo table = new MigrationTableInfo();
        set(table, "tableOwner", "DWDB");
        set(table, "tableId", "TB_SAMPLE");
        set(table, "tableNm", "TB_SAMPLE");
        set(table, "migCond", condition);
        return table;
    }

    private static ValidationTarget column(String name, int ordinal, boolean primaryKey)
            throws Exception {
        ValidationTarget column = new ValidationTarget();
        set(column, "tableOwner", "DWDB");
        set(column, "tableId", "TB_SAMPLE");
        set(column, "colId", name);
        set(column, "colNm", name);
        set(column, "colOrd", ordinal);
        set(column, "dataType", "INTEGER");
        set(column, "pkYn", primaryKey);
        return column;
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

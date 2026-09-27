package com.migration.worker;

import com.migration.config.MigrationFileFormat;
import com.migration.config.MigrationStoragePaths;
import com.migration.metadata.MigrationTableInfo;
import com.migration.metadata.ValidationTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SybaseIQLoadWorkerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void buildsAsciiLoadSqlWithTargetOwnerNullAndLobSpecifications() throws Exception {
        MigrationTableInfo table = tableInfo();
        List<ValidationTarget> columns = List.of(
                column("DWDB", "TB_LOB_007", "LOB_BIN", 4, "LONG BINARY", true),
                column("DWDB", "TB_LOB_007", "SEQ_NO", 1, "BIGINT", false),
                column("DWDB", "TB_LOB_007", "MEMO_TXT", 2, "VARCHAR(200)", true),
                column("DWDB", "TB_LOB_007", "LOB_TEXT", 3, "LONG VARCHAR", true));
        MigrationFileFormat format = new MigrationFileFormat(
                "__NULL__", "__BFILE_ERROR__", "|", "\r\n");

        try (var connection = DriverManager.getConnection("jdbc:h2:mem:load_sql")) {
            String sql = new SybaseIQLoadWorker(connection).buildLoadSql(
                    table,
                    columns,
                    "/opt/sap/iq161/IQ-16_1/demo/data/"
                            + "DWDB_TB_LOB_007_10/TB_LOB_007_10_data.dat",
                    format);

            assertEquals("LOAD TABLE MIGTGT.TB_LOB_007 ("
                    + "SEQ_NO, "
                    + "MEMO_TXT NULL ('__NULL__'), "
                    + "LOB_TEXT ASCII FILE ('|') NULL ('__NULL__'), "
                    + "LOB_BIN BINARY FILE ('|') NULL ('__NULL__')) "
                    + "FROM '/opt/sap/iq161/IQ-16_1/demo/data/"
                    + "DWDB_TB_LOB_007_10/TB_LOB_007_10_data.dat' "
                    + "QUOTES ON ESCAPES OFF FORMAT ASCII DELIMITED BY '|' "
                    + "ROW DELIMITED BY '\r\n' ON FILE ERROR ROLLBACK", sql);
        }
    }

    @Test
    void deletesOnlyCurrentTableAndProcessExtractFilesAfterLoad() throws Exception {
        MigrationTableInfo table = tableInfo();
        Path dataRoot = Files.createDirectories(tempDirectory.resolve("data"));
        Path blobRoot = Files.createDirectories(tempDirectory.resolve("blob"));
        MigrationStoragePaths paths = new MigrationStoragePaths(
                "/opt/migration/data", "/opt/migration/blob", dataRoot, blobRoot);
        Path dataDirectory = paths.createWorkerDataDirectory("DWDB_TB_LOB_007_10");
        Path blobDirectory = paths.createWorkerBlobDirectory("DWDB_TB_LOB_007_10");
        Path dataFile = Files.writeString(
                dataDirectory.resolve("TB_LOB_007_10_data.dat"), "data");
        Path firstBinary = Files.writeString(
                blobDirectory.resolve("TB_LOB_007_10_1_LOB_BIN.bin"), "blob-1");
        Path secondBinary = Files.writeString(
                blobDirectory.resolve("TB_LOB_007_10_2_LOB_BIN.bin"), "blob-2");
        Path otherProcessBinary = Files.writeString(
                blobDirectory.resolve("TB_LOB_007_11_1_LOB_BIN.bin"), "keep");
        Path unrelatedFile = Files.writeString(
                blobDirectory.resolve("notes.txt"), "keep");

        try (var connection = DriverManager.getConnection("jdbc:h2:mem:load_cleanup")) {
            new SybaseIQLoadWorker(connection).cleanupExtractFiles(table, paths);
        }

        assertFalse(Files.exists(dataFile));
        assertFalse(Files.exists(firstBinary));
        assertFalse(Files.exists(secondBinary));
        assertTrue(Files.exists(otherProcessBinary));
        assertTrue(Files.exists(unrelatedFile));
        assertFalse(Files.exists(dataDirectory));
        assertTrue(Files.exists(blobDirectory));
    }

    private static MigrationTableInfo tableInfo() throws Exception {
        MigrationTableInfo table = new MigrationTableInfo();
        set(table, "tableOwner", "DWDB");
        set(table, "tableId", "TB_LOB_007");
        set(table, "tableNm", "TB_LOB_007");
        set(table, "tgtDbPrefix", "MIGTGT");
        set(table, "procOrd", 10);
        set(table, "migLoadMode", "T");
        return table;
    }

    private static ValidationTarget column(
            String owner,
            String tableId,
            String name,
            int ordinal,
            String dataType,
            boolean nullable) throws Exception {
        ValidationTarget column = new ValidationTarget();
        set(column, "tableOwner", owner);
        set(column, "tableId", tableId);
        set(column, "colId", name);
        set(column, "colNm", name);
        set(column, "colOrd", ordinal);
        set(column, "dataType", dataType);
        set(column, "nullYn", nullable);
        return column;
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

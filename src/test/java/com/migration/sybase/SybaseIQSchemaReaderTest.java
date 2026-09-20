package com.migration.sybase;

import com.migration.exception.SchemaException;
import com.migration.metadata.ColumnSchema;
import com.migration.metadata.TableSchema;
import com.migration.metadata.ValidationTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SybaseIQSchemaReaderTest {
    private final SybaseIQSchemaReader reader = new SybaseIQSchemaReader();

    @Test
    void buildsOrderedPhysicalSchemaAndParsesTypeDetails() throws Exception {
        TableSchema schema = reader.read("HANKUKERP", "TBL_PROJECT", List.of(
                target("NOTE", 3, "VARCHAR(4000)"),
                target("AMOUNT", 2, "DECIMAL(18, 4)"),
                target("ID", 1, "BIGINT")));

        assertTrue(schema.exists());
        assertEquals(List.of("ID", "AMOUNT", "NOTE"), schema.getColumns().stream()
                .map(ColumnSchema::getColumnName).toList());
        assertEquals("DECIMAL", schema.getColumns().get(1).getDataType());
        assertEquals(18, schema.getColumns().get(1).getColumnSize());
        assertEquals(4, schema.getColumns().get(1).getDecimalDigits());
        assertEquals(4000, schema.getColumns().get(2).getColumnSize());
        assertNull(schema.getColumns().get(2).getNullable());
    }

    @Test
    void comparesAsisAndTobeSchemas() throws Exception {
        TableSchema asis = reader.read("HANKUKERP", "TBL_PROJECT",
                List.of(target("ID", 1, "BIGINT"), target("NAME", 2, "VARCHAR(100)")));
        TableSchema matching = reader.read("HANKUKERP", "TBL_PROJECT",
                List.of(target("NAME", 2, "VARCHAR(100)"), target("ID", 1, "BIGINT")));
        reader.validateCompatible(asis, matching);

        TableSchema mismatch = reader.read("HANKUKERP", "TBL_PROJECT",
                List.of(target("ID", 1, "BIGINT"), target("NAME", 2, "VARCHAR(200)")));
        SchemaException exception = assertThrows(SchemaException.class,
                () -> reader.validateCompatible(asis, mismatch));
        assertTrue(exception.getMessage().startsWith("SCHEMA_MISMATCH:"));
    }

    @Test
    void rejectsInvalidPhysicalMetadata() {
        assertThrows(SchemaException.class, () -> reader.read(
                "HANKUKERP", "TBL_PROJECT",
                List.of(target("ID", 1, "BIGINT"), target("NAME", 1, "VARCHAR(100)"))));

        ValidationTarget wrongOwner = target("ID", 1, "BIGINT");
        wrongOwner.setTableName("OTHER_TABLE");
        assertThrows(SchemaException.class,
                () -> reader.read("HANKUKERP", "TBL_PROJECT", List.of(wrongOwner)));
    }

    private ValidationTarget target(String name, int ordinal, String dataType) {
        ValidationTarget target = new ValidationTarget();
        target.setTableSchema("HANKUKERP");
        target.setTableName("TBL_PROJECT");
        target.setColumnName(name);
        target.setOrdinalPosition(ordinal);
        target.setDataType(dataType);
        return target;
    }
}

package com.npucraft.deathchest.storage;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteSchemaTest {
    @Test
    void recordUpsertMatchesSchemaAndPersistsIndependentRestoreFlags() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            SqliteDialect dialect = new SqliteDialect();
            dialect.configure(connection);
            dialect.migrate(connection);

            Set<String> columns = new HashSet<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA table_info(death_records)")) {
                while (result.next()) {
                    columns.add(result.getString("name"));
                }
            }

            assertTrue(columns.contains("items_restored"));
            assertTrue(columns.contains("experience_restored"));
            assertEquals(43, dialect.upsertRecord().chars().filter(character -> character == '?').count());
            assertEquals(43, dialect.upsertRecordPreserveItems().chars().filter(character -> character == '?').count());
        }
    }
}

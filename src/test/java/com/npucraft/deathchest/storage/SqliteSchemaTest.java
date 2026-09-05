package com.npucraft.deathchest.storage;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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
            columns.clear();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA table_info(player_settings)")) {
                while (result.next()) {
                    columns.add(result.getString("name"));
                }
            }
            assertTrue(columns.contains("player_uuid"));
            assertTrue(columns.contains("enabled"));
            assertTrue(columns.contains("updated_at"));
            try (PreparedStatement setting = connection.prepareStatement(dialect.upsertPlayerSetting())) {
                setting.setString(1, "00000000-0000-0000-0000-000000000001");
                setting.setString(2, "TestPlayer");
                setting.setInt(3, 0);
                setting.setLong(4, 123L);
                setting.executeUpdate();
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT enabled FROM player_settings")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt("enabled"));
            }
            assertEquals(43, dialect.upsertRecord().chars().filter(character -> character == '?').count());
            assertEquals(43, dialect.upsertRecordPreserveItems().chars().filter(character -> character == '?').count());
        }
    }
}

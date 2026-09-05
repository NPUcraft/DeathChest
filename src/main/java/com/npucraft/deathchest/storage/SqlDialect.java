package com.npucraft.deathchest.storage;

import com.npucraft.deathchest.DeathChestPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class SqlDialect {
    public abstract String name();

    public abstract Connection connect(DeathChestPlugin plugin) throws Exception;

    public void configure(Connection connection) throws SQLException {
    }

    public void shutdown(Connection connection) throws SQLException {
    }

    public abstract void migrate(Connection connection) throws SQLException;

    public abstract String upsertPlayerSetting();

    public abstract String upsertChest();

    public abstract String upsertRecord();

    public abstract String upsertRecordPreserveItems();

    public abstract String upsertRecovery();

    public abstract String insertAudit();

    protected static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    protected static void createIndexIfAbsent(Connection connection, String sql) {
        try {
            execute(connection, sql);
        } catch (SQLException ignored) {
        }
    }

    protected static void addColumnIfAbsent(Connection connection, String sql) {
        try {
            execute(connection, sql);
        } catch (SQLException ignored) {
        }
    }

    protected static void dropColumnIfPresent(Connection connection, String table, String column) {
        try {
            execute(connection, "ALTER TABLE " + table + " DROP COLUMN " + column);
        } catch (SQLException ignored) {
        }
    }

    protected static String insertRecordPrefix() {
        return """
                INSERT INTO death_records(id, player_uuid, player_name, death_time, world, x, y, z, yaw, pitch, death_cause,
                    killer_uuid, killer_name, player_level_before, total_experience_before, experience_progress_before,
                    experience_kept, experience_lost, balance_before, balance_after, economy_provider, currency_id,
                    calculated_price, charged_price, insufficient_balance, insufficient_balance_mode, death_chest_enabled,
                    death_chest_created, death_chest_id, death_chest_world, death_chest_x, death_chest_y,
                    death_chest_z, chest_type, is_protected, unlock_at, expire_at, status, failure_reason,
                    rollback_in_progress, items_restored, experience_restored, items)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
    }
}

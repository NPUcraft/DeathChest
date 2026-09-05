package com.npucraft.deathchest.storage;

import com.npucraft.deathchest.DeathChestPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqliteDialect extends SqlDialect {
    @Override
    public String name() {
        return "SQLITE";
    }

    @Override
    public Connection connect(DeathChestPlugin plugin) throws Exception {
        Class.forName("org.sqlite.JDBC");
        File file = new File(plugin.getDataFolder(), plugin.settings().storageFile);
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Unable to create plugin data folder");
        }
        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }

    @Override
    public void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA wal_autocheckpoint=1000");
        }
    }

    @Override
    public void shutdown(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        }
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        execute(connection, "DROP TABLE IF EXISTS player_settings");
        execute(connection, """
                CREATE TABLE IF NOT EXISTS death_chests (
                    id TEXT PRIMARY KEY,
                    record_id TEXT,
                    owner_uuid TEXT NOT NULL,
                    owner_name TEXT NOT NULL,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    second_x INTEGER,
                    second_y INTEGER,
                    second_z INTEGER,
                    chest_type TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    unlock_at INTEGER NOT NULL,
                    expire_at INTEGER NOT NULL,
                    price REAL NOT NULL,
                    currency TEXT,
                    unpaid INTEGER NOT NULL,
                    locked INTEGER NOT NULL,
                    hologram_uuid TEXT,
                    active INTEGER NOT NULL,
                    timer_paused_millis INTEGER NOT NULL DEFAULT 0
                )
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS death_records (
                    id TEXT PRIMARY KEY,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    death_time INTEGER NOT NULL,
                    world TEXT,
                    x REAL,
                    y REAL,
                    z REAL,
                    yaw REAL,
                    pitch REAL,
                    death_cause TEXT,
                    killer_uuid TEXT,
                    killer_name TEXT,
                    player_level_before INTEGER,
                    total_experience_before INTEGER,
                    experience_progress_before REAL,
                    experience_kept INTEGER,
                    experience_lost INTEGER,
                    balance_before REAL,
                    balance_after REAL,
                    economy_provider TEXT,
                    currency_id TEXT,
                    calculated_price REAL,
                    charged_price REAL,
                    insufficient_balance INTEGER,
                    insufficient_balance_mode TEXT,
                    death_chest_enabled INTEGER,
                    death_chest_created INTEGER,
                    death_chest_id TEXT,
                    death_chest_world TEXT,
                    death_chest_x INTEGER,
                    death_chest_y INTEGER,
                    death_chest_z INTEGER,
                    chest_type TEXT,
                    is_protected INTEGER,
                    unlock_at INTEGER,
                    expire_at INTEGER,
                    status TEXT NOT NULL,
                    failure_reason TEXT,
                    rollback_in_progress INTEGER NOT NULL,
                    items_restored INTEGER NOT NULL DEFAULT 0,
                    experience_restored INTEGER NOT NULL DEFAULT 0,
                    items BLOB
                )
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS recovery_storage (
                    id TEXT PRIMARY KEY,
                    player_uuid TEXT NOT NULL,
                    record_id TEXT,
                    items BLOB,
                    created_at INTEGER NOT NULL,
                    expire_at INTEGER NOT NULL
                )
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS audit_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_type TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    actor_uuid TEXT,
                    actor_name TEXT,
                    target_uuid TEXT,
                    target_name TEXT,
                    death_chest_id TEXT,
                    record_id TEXT,
                    details TEXT,
                    force INTEGER NOT NULL
                )
                """);
        createIndexIfAbsent(connection, "CREATE INDEX IF NOT EXISTS idx_records_player_time ON death_records(player_uuid, death_time)");
        createIndexIfAbsent(connection, "CREATE INDEX IF NOT EXISTS idx_records_status ON death_records(status)");
        createIndexIfAbsent(connection, "CREATE INDEX IF NOT EXISTS idx_chests_owner ON death_chests(owner_uuid)");
        createIndexIfAbsent(connection, "CREATE INDEX IF NOT EXISTS idx_chests_record ON death_chests(record_id)");
        createIndexIfAbsent(connection, "CREATE INDEX IF NOT EXISTS idx_recovery_player ON recovery_storage(player_uuid)");
        createIndexIfAbsent(connection, "CREATE INDEX IF NOT EXISTS idx_audit_time ON audit_log(timestamp)");
        addColumnIfAbsent(connection, "ALTER TABLE death_chests ADD COLUMN timer_paused_millis INTEGER NOT NULL DEFAULT 0");
        addColumnIfAbsent(connection, "ALTER TABLE death_records ADD COLUMN items_restored INTEGER NOT NULL DEFAULT 0");
        addColumnIfAbsent(connection, "ALTER TABLE death_records ADD COLUMN experience_restored INTEGER NOT NULL DEFAULT 0");
        dropColumnIfPresent(connection, "death_records", "next_death_public");
        dropColumnIfPresent(connection, "death_records", "pinned");
    }

    @Override
    public String upsertChest() {
        return """
                INSERT INTO death_chests(id, record_id, owner_uuid, owner_name, world, x, y, z, second_x, second_y, second_z,
                    chest_type, created_at, unlock_at, expire_at, price, currency, unpaid, locked, hologram_uuid, active,
                    timer_paused_millis)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    record_id=excluded.record_id,
                    owner_uuid=excluded.owner_uuid,
                    owner_name=excluded.owner_name,
                    world=excluded.world,
                    x=excluded.x, y=excluded.y, z=excluded.z,
                    second_x=excluded.second_x, second_y=excluded.second_y, second_z=excluded.second_z,
                    chest_type=excluded.chest_type,
                    created_at=excluded.created_at,
                    unlock_at=excluded.unlock_at,
                    expire_at=excluded.expire_at,
                    price=excluded.price,
                    currency=excluded.currency,
                    unpaid=excluded.unpaid,
                    locked=excluded.locked,
                    hologram_uuid=excluded.hologram_uuid,
                    active=excluded.active,
                    timer_paused_millis=excluded.timer_paused_millis
                """;
    }

    @Override
    public String upsertRecord() {
        return insertRecordPrefix() + """
                ON CONFLICT(id) DO UPDATE SET
                    player_name=excluded.player_name,
                    experience_kept=excluded.experience_kept,
                    experience_lost=excluded.experience_lost,
                    balance_after=excluded.balance_after,
                    calculated_price=excluded.calculated_price,
                    charged_price=excluded.charged_price,
                    insufficient_balance=excluded.insufficient_balance,
                    insufficient_balance_mode=excluded.insufficient_balance_mode,
                    death_chest_created=excluded.death_chest_created,
                    death_chest_id=excluded.death_chest_id,
                    death_chest_world=excluded.death_chest_world,
                    death_chest_x=excluded.death_chest_x,
                    death_chest_y=excluded.death_chest_y,
                    death_chest_z=excluded.death_chest_z,
                    chest_type=excluded.chest_type,
                    is_protected=excluded.is_protected,
                    unlock_at=excluded.unlock_at,
                    expire_at=excluded.expire_at,
                    status=excluded.status,
                    failure_reason=excluded.failure_reason,
                    rollback_in_progress=excluded.rollback_in_progress,
                    items_restored=excluded.items_restored,
                    experience_restored=excluded.experience_restored,
                    items=excluded.items
                """;
    }

    @Override
    public String upsertRecordPreserveItems() {
        return insertRecordPrefix() + """
                ON CONFLICT(id) DO UPDATE SET
                    player_name=excluded.player_name,
                    experience_kept=excluded.experience_kept,
                    experience_lost=excluded.experience_lost,
                    balance_after=excluded.balance_after,
                    calculated_price=excluded.calculated_price,
                    charged_price=excluded.charged_price,
                    insufficient_balance=excluded.insufficient_balance,
                    insufficient_balance_mode=excluded.insufficient_balance_mode,
                    death_chest_created=excluded.death_chest_created,
                    death_chest_id=excluded.death_chest_id,
                    death_chest_world=excluded.death_chest_world,
                    death_chest_x=excluded.death_chest_x,
                    death_chest_y=excluded.death_chest_y,
                    death_chest_z=excluded.death_chest_z,
                    chest_type=excluded.chest_type,
                    is_protected=excluded.is_protected,
                    unlock_at=excluded.unlock_at,
                    expire_at=excluded.expire_at,
                    status=excluded.status,
                    failure_reason=excluded.failure_reason,
                    rollback_in_progress=excluded.rollback_in_progress,
                    items_restored=excluded.items_restored,
                    experience_restored=excluded.experience_restored
                """;
    }

    @Override
    public String upsertRecovery() {
        return """
                INSERT INTO recovery_storage(id, player_uuid, record_id, items, created_at, expire_at)
                VALUES(?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET items=excluded.items, expire_at=excluded.expire_at
                """;
    }

    @Override
    public String insertAudit() {
        return """
                INSERT INTO audit_log(event_type, timestamp, actor_uuid, actor_name, target_uuid, target_name,
                    death_chest_id, record_id, details, force)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
    }
}

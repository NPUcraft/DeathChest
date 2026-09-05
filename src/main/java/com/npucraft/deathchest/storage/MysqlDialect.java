package com.npucraft.deathchest.storage;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.config.PluginSettings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class MysqlDialect extends SqlDialect {
    @Override
    public String name() {
        return "MYSQL";
    }

    @Override
    public Connection connect(DeathChestPlugin plugin) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        PluginSettings settings = plugin.settings();
        String url = settings.mysqlJdbcUrl;
        if (url == null || url.isBlank()) {
            String parameters = settings.mysqlParameters;
            if (parameters == null || parameters.isBlank()) {
                parameters = "createDatabaseIfNotExist=true&sslMode=DISABLE&allowPublicKeyRetrieval=true&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci";
            }
            url = "jdbc:mysql://" + settings.mysqlHost + ":" + settings.mysqlPort + "/" + settings.mysqlDatabase + "?" + parameters;
        }
        Properties properties = new Properties();
        properties.setProperty("user", settings.mysqlUsername == null ? "" : settings.mysqlUsername);
        properties.setProperty("password", settings.mysqlPassword == null ? "" : settings.mysqlPassword);
        plugin.getLogger().info("Connecting MySQL: " + redact(url));
        return DriverManager.getConnection(url, properties);
    }

    private String redact(String url) {
        int query = url.indexOf('?');
        return query > 0 ? url.substring(0, query) : url;
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        execute(connection, "DROP TABLE IF EXISTS player_settings");
        execute(connection, """
                CREATE TABLE IF NOT EXISTS death_chests (
                    id VARCHAR(32) NOT NULL PRIMARY KEY,
                    record_id VARCHAR(32),
                    owner_uuid VARCHAR(36) NOT NULL,
                    owner_name VARCHAR(64) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x INT NOT NULL,
                    y INT NOT NULL,
                    z INT NOT NULL,
                    second_x INT,
                    second_y INT,
                    second_z INT,
                    chest_type VARCHAR(16) NOT NULL,
                    created_at BIGINT NOT NULL,
                    unlock_at BIGINT NOT NULL,
                    expire_at BIGINT NOT NULL,
                    price DOUBLE NOT NULL,
                    currency VARCHAR(64),
                    unpaid TINYINT NOT NULL,
                    locked TINYINT NOT NULL,
                    hologram_uuid VARCHAR(36),
                    active TINYINT NOT NULL,
                    timer_paused_millis BIGINT NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS death_records (
                    id VARCHAR(32) NOT NULL PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(64) NOT NULL,
                    death_time BIGINT NOT NULL,
                    world VARCHAR(64),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    yaw FLOAT,
                    pitch FLOAT,
                    death_cause VARCHAR(64),
                    killer_uuid VARCHAR(36),
                    killer_name VARCHAR(64),
                    player_level_before INT,
                    total_experience_before INT,
                    experience_progress_before FLOAT,
                    experience_kept INT,
                    experience_lost INT,
                    balance_before DOUBLE,
                    balance_after DOUBLE,
                    economy_provider VARCHAR(32),
                    currency_id VARCHAR(64),
                    calculated_price DOUBLE,
                    charged_price DOUBLE,
                    insufficient_balance TINYINT,
                    insufficient_balance_mode VARCHAR(32),
                    death_chest_enabled TINYINT,
                    death_chest_created TINYINT,
                    death_chest_id VARCHAR(32),
                    death_chest_world VARCHAR(64),
                    death_chest_x INT,
                    death_chest_y INT,
                    death_chest_z INT,
                    chest_type VARCHAR(16),
                    is_protected TINYINT,
                    unlock_at BIGINT,
                    expire_at BIGINT,
                    status VARCHAR(32) NOT NULL,
                    failure_reason TEXT,
                    rollback_in_progress TINYINT NOT NULL,
                    items LONGBLOB
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS recovery_storage (
                    id VARCHAR(32) NOT NULL PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    record_id VARCHAR(32),
                    items LONGBLOB,
                    created_at BIGINT NOT NULL,
                    expire_at BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS audit_log (
                    id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
                    event_type VARCHAR(64) NOT NULL,
                    timestamp BIGINT NOT NULL,
                    actor_uuid VARCHAR(36),
                    actor_name VARCHAR(64),
                    target_uuid VARCHAR(36),
                    target_name VARCHAR(64),
                    death_chest_id VARCHAR(32),
                    record_id VARCHAR(32),
                    details TEXT,
                    `force` TINYINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        createIndexIfAbsent(connection, "CREATE INDEX IF NOT EXISTS idx_records_player_time ON death_records(player_uuid, death_time)");
        createIndexIfAbsent(connection, "CREATE INDEX IF NOT EXISTS idx_records_status ON death_records(status)");
        createIndexIfAbsent(connection, "CREATE INDEX IF NOT EXISTS idx_chests_owner ON death_chests(owner_uuid)");
        createIndexIfAbsent(connection, "CREATE INDEX IF NOT EXISTS idx_chests_record ON death_chests(record_id)");
        createIndexIfAbsent(connection, "CREATE INDEX IF NOT EXISTS idx_recovery_player ON recovery_storage(player_uuid)");
        createIndexIfAbsent(connection, "CREATE INDEX IF NOT EXISTS idx_audit_time ON audit_log(timestamp)");
        addColumnIfAbsent(connection, "ALTER TABLE death_chests ADD COLUMN timer_paused_millis BIGINT NOT NULL DEFAULT 0");
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
                ON DUPLICATE KEY UPDATE
                    record_id=VALUES(record_id),
                    owner_uuid=VALUES(owner_uuid),
                    owner_name=VALUES(owner_name),
                    world=VALUES(world),
                    x=VALUES(x), y=VALUES(y), z=VALUES(z),
                    second_x=VALUES(second_x), second_y=VALUES(second_y), second_z=VALUES(second_z),
                    chest_type=VALUES(chest_type),
                    created_at=VALUES(created_at),
                    unlock_at=VALUES(unlock_at),
                    expire_at=VALUES(expire_at),
                    price=VALUES(price),
                    currency=VALUES(currency),
                    unpaid=VALUES(unpaid),
                    locked=VALUES(locked),
                    hologram_uuid=VALUES(hologram_uuid),
                    active=VALUES(active),
                    timer_paused_millis=VALUES(timer_paused_millis)
                """;
    }

    @Override
    public String upsertRecord() {
        return insertRecordPrefix() + """
                ON DUPLICATE KEY UPDATE
                    player_name=VALUES(player_name),
                    experience_kept=VALUES(experience_kept),
                    experience_lost=VALUES(experience_lost),
                    balance_after=VALUES(balance_after),
                    calculated_price=VALUES(calculated_price),
                    charged_price=VALUES(charged_price),
                    insufficient_balance=VALUES(insufficient_balance),
                    insufficient_balance_mode=VALUES(insufficient_balance_mode),
                    death_chest_created=VALUES(death_chest_created),
                    death_chest_id=VALUES(death_chest_id),
                    death_chest_world=VALUES(death_chest_world),
                    death_chest_x=VALUES(death_chest_x),
                    death_chest_y=VALUES(death_chest_y),
                    death_chest_z=VALUES(death_chest_z),
                    chest_type=VALUES(chest_type),
                    is_protected=VALUES(is_protected),
                    unlock_at=VALUES(unlock_at),
                    expire_at=VALUES(expire_at),
                    status=VALUES(status),
                    failure_reason=VALUES(failure_reason),
                    rollback_in_progress=VALUES(rollback_in_progress),
                    items=VALUES(items)
                """;
    }

    @Override
    public String upsertRecordPreserveItems() {
        return insertRecordPrefix() + """
                ON DUPLICATE KEY UPDATE
                    player_name=VALUES(player_name),
                    experience_kept=VALUES(experience_kept),
                    experience_lost=VALUES(experience_lost),
                    balance_after=VALUES(balance_after),
                    calculated_price=VALUES(calculated_price),
                    charged_price=VALUES(charged_price),
                    insufficient_balance=VALUES(insufficient_balance),
                    insufficient_balance_mode=VALUES(insufficient_balance_mode),
                    death_chest_created=VALUES(death_chest_created),
                    death_chest_id=VALUES(death_chest_id),
                    death_chest_world=VALUES(death_chest_world),
                    death_chest_x=VALUES(death_chest_x),
                    death_chest_y=VALUES(death_chest_y),
                    death_chest_z=VALUES(death_chest_z),
                    chest_type=VALUES(chest_type),
                    is_protected=VALUES(is_protected),
                    unlock_at=VALUES(unlock_at),
                    expire_at=VALUES(expire_at),
                    status=VALUES(status),
                    failure_reason=VALUES(failure_reason),
                    rollback_in_progress=VALUES(rollback_in_progress)
                """;
    }

    @Override
    public String upsertRecovery() {
        return """
                INSERT INTO recovery_storage(id, player_uuid, record_id, items, created_at, expire_at)
                VALUES(?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE items=VALUES(items), expire_at=VALUES(expire_at)
                """;
    }

    @Override
    public String insertAudit() {
        return """
                INSERT INTO audit_log(event_type, timestamp, actor_uuid, actor_name, target_uuid, target_name,
                    death_chest_id, record_id, details, `force`)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
    }
}

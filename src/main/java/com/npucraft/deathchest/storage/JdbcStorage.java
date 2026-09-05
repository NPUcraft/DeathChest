package com.npucraft.deathchest.storage;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.AuditLogEntry;
import com.npucraft.deathchest.model.ChestType;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.model.DeathRecord;
import com.npucraft.deathchest.model.RecordStatus;
import com.npucraft.deathchest.model.RecoveryEntry;
import com.npucraft.deathchest.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcStorage implements PluginStorage {
    private final DeathChestPlugin plugin;
    private final SqlDialect dialect;
    private final Object lock = new Object();
    private Connection connection;

    private long lastValidityCheck;

    public JdbcStorage(DeathChestPlugin plugin, SqlDialect dialect) {
        this.plugin = plugin;
        this.dialect = dialect;
    }

    @Override
    public void open() {
        synchronized (lock) {
            try {
                connection = dialect.connect(plugin);
                dialect.configure(connection);
                dialect.migrate(connection);
                lastValidityCheck = System.currentTimeMillis();
                plugin.getLogger().info("Storage backend: " + dialect.name());
            } catch (Exception exception) {
                throw failed("Failed to open " + dialect.name() + " storage", exception);
            }
        }
    }

    private Connection conn() throws SQLException {
        if (connection != null && !connection.isClosed() && recentlyValid()) {
            return connection;
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
        }
        try {
            connection = dialect.connect(plugin);
            dialect.configure(connection);
            dialect.migrate(connection);
            lastValidityCheck = System.currentTimeMillis();
        } catch (SQLException exception) {
            lastValidityCheck = 0L;
            throw exception;
        } catch (Exception exception) {
            lastValidityCheck = 0L;
            throw new SQLException("Failed to reconnect " + dialect.name() + " storage", exception);
        }
        return connection;
    }

    private boolean recentlyValid() {
        long now = System.currentTimeMillis();
        if (now - lastValidityCheck < 60_000L) {
            return true;
        }
        lastValidityCheck = now;
        return isValid(connection);
    }

    private boolean isValid(Connection existing) {
        try {
            return existing.isValid(1);
        } catch (SQLException ignored) {
            return false;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (connection != null) {
                try {
                    dialect.shutdown(connection);
                } catch (SQLException ignored) {
                }
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
                connection = null;
            }
        }
    }

    @Override
    public void saveChest(DeathChestData chest) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement(dialect.upsertChest())) {
                statement.setString(1, chest.getId());
                statement.setString(2, chest.getRecordId());
                statement.setString(3, chest.getOwnerUuid().toString());
                statement.setString(4, chest.getOwnerName());
                statement.setString(5, chest.getWorld());
                statement.setInt(6, chest.getX());
                statement.setInt(7, chest.getY());
                statement.setInt(8, chest.getZ());
                setNullableInt(statement, 9, chest.getSecondX());
                setNullableInt(statement, 10, chest.getSecondY());
                setNullableInt(statement, 11, chest.getSecondZ());
                statement.setString(12, chest.getChestType().name());
                statement.setLong(13, chest.getCreatedAt());
                statement.setLong(14, chest.getUnlockAt());
                statement.setLong(15, chest.getExpireAt());
                statement.setDouble(16, chest.getPrice());
                statement.setString(17, chest.getCurrency());
                statement.setInt(18, chest.isUnpaid() ? 1 : 0);
                statement.setInt(19, chest.isLocked() ? 1 : 0);
                statement.setString(20, chest.getHologramId() == null ? null : chest.getHologramId().toString());
                statement.setInt(21, chest.isActive() ? 1 : 0);
                statement.setLong(22, chest.getTimerPausedMillis());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw failed("Failed to save death chest", exception);
            }
        }
    }

    @Override
    public void deleteChest(String chestId) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("DELETE FROM death_chests WHERE id=?")) {
                statement.setString(1, chestId);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw failed("Failed to delete death chest", exception);
            }
        }
    }

    @Override
    public void setChestLocked(String chestId, boolean locked) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("UPDATE death_chests SET locked=? WHERE id=?")) {
                statement.setInt(1, locked ? 1 : 0);
                statement.setString(2, chestId);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw failed("Failed to update chest lock", exception);
            }
        }
    }

    @Override
    public void setChestHologram(String chestId, UUID hologramId) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("UPDATE death_chests SET hologram_uuid=? WHERE id=?")) {
                statement.setString(1, hologramId == null ? null : hologramId.toString());
                statement.setString(2, chestId);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw failed("Failed to update hologram id", exception);
            }
        }
    }

    @Override
    public List<DeathChestData> loadActiveChests() {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("SELECT * FROM death_chests WHERE active=1")) {
                return readChests(statement.executeQuery());
            } catch (SQLException exception) {
                throw failed("Failed to load death chests", exception);
            }
        }
    }

    @Override
    public Optional<DeathChestData> loadChest(String chestId) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("SELECT * FROM death_chests WHERE id=?")) {
                statement.setString(1, chestId);
                List<DeathChestData> chests = readChests(statement.executeQuery());
                return chests.isEmpty() ? Optional.empty() : Optional.of(chests.getFirst());
            } catch (SQLException exception) {
                throw failed("Failed to load death chest", exception);
            }
        }
    }

    @Override
    public List<DeathChestData> loadChestsByOwner(UUID owner) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("SELECT * FROM death_chests WHERE owner_uuid=? AND active=1")) {
                statement.setString(1, owner.toString());
                return readChests(statement.executeQuery());
            } catch (SQLException exception) {
                throw failed("Failed to load owner death chests", exception);
            }
        }
    }

    @Override
    public List<DeathChestData> loadChestsByRecord(String recordId) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("SELECT * FROM death_chests WHERE record_id=? AND active=1")) {
                statement.setString(1, recordId);
                return readChests(statement.executeQuery());
            } catch (SQLException exception) {
                throw failed("Failed to load record death chests", exception);
            }
        }
    }

    @Override
    public void saveRecord(DeathRecord record) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement(
                    plugin.settings().saveItemSnapshot ? dialect.upsertRecord() : dialect.upsertRecordPreserveItems())) {
                statement.setString(1, record.getRecordId());
                statement.setString(2, record.getPlayerUuid().toString());
                statement.setString(3, record.getPlayerName());
                statement.setLong(4, record.getDeathTime());
                statement.setString(5, record.getWorld());
                statement.setDouble(6, record.getX());
                statement.setDouble(7, record.getY());
                statement.setDouble(8, record.getZ());
                statement.setFloat(9, record.getYaw());
                statement.setFloat(10, record.getPitch());
                statement.setString(11, record.getDeathCause());
                statement.setString(12, record.getKillerUuid() == null ? null : record.getKillerUuid().toString());
                statement.setString(13, record.getKillerName());
                statement.setInt(14, record.getPlayerLevelBefore());
                statement.setInt(15, record.getTotalExperienceBefore());
                statement.setFloat(16, record.getExperienceProgressBefore());
                statement.setInt(17, record.getExperienceKept());
                statement.setInt(18, record.getExperienceLost());
                statement.setDouble(19, record.getBalanceBefore());
                statement.setDouble(20, record.getBalanceAfter());
                statement.setString(21, record.getEconomyProvider());
                statement.setString(22, record.getCurrencyId());
                statement.setDouble(23, record.getCalculatedPrice());
                statement.setDouble(24, record.getChargedPrice());
                statement.setInt(25, record.isInsufficientBalance() ? 1 : 0);
                statement.setString(26, record.getInsufficientBalanceMode());
                statement.setInt(27, record.isDeathChestEnabled() ? 1 : 0);
                statement.setInt(28, record.isDeathChestCreated() ? 1 : 0);
                statement.setString(29, record.getDeathChestId());
                statement.setString(30, record.getDeathChestWorld());
                setNullableInt(statement, 31, record.getDeathChestX());
                setNullableInt(statement, 32, record.getDeathChestY());
                setNullableInt(statement, 33, record.getDeathChestZ());
                statement.setString(34, record.getChestType() == null ? null : record.getChestType().name());
                statement.setInt(35, record.isProtectedChest() ? 1 : 0);
                setNullableLong(statement, 36, record.getUnlockAt());
                setNullableLong(statement, 37, record.getExpireAt());
                statement.setString(38, record.getStatus().name());
                statement.setString(39, record.getFailureReason());
                statement.setInt(40, record.isRollbackInProgress() ? 1 : 0);
                statement.setInt(41, record.isItemsRestored() ? 1 : 0);
                statement.setInt(42, record.isExperienceRestored() ? 1 : 0);
                statement.setBytes(43, ItemSerializer.serialize(record.getItems()));
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw failed("Failed to save death record", exception);
            }
        }
    }

    @Override
    public Optional<DeathRecord> loadRecord(String recordId) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("SELECT * FROM death_records WHERE id=?")) {
                statement.setString(1, recordId);
                List<DeathRecord> records = readRecords(statement.executeQuery());
                return records.isEmpty() ? Optional.empty() : Optional.of(records.getFirst());
            } catch (SQLException exception) {
                throw failed("Failed to load death record", exception);
            }
        }
    }

    @Override
    public List<DeathRecord> loadRecords(UUID player, int limit) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("SELECT * FROM death_records WHERE player_uuid=? ORDER BY death_time DESC LIMIT ?")) {
                statement.setString(1, player.toString());
                statement.setInt(2, Math.max(1, limit));
                return readRecords(statement.executeQuery());
            } catch (SQLException exception) {
                throw failed("Failed to load death records", exception);
            }
        }
    }

    @Override
    public List<DeathRecord> loadInterruptedRecords() {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement(
                    "SELECT * FROM death_records WHERE rollback_in_progress=1 ORDER BY death_time")) {
                return readRecords(statement.executeQuery());
            } catch (SQLException exception) {
                throw failed("Failed to load interrupted restores", exception);
            }
        }
    }

    @Override
    public int countRecords() {
        synchronized (lock) {
            return count("SELECT COUNT(*) FROM death_records");
        }
    }

    @Override
    public int countRecords(UUID player) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("SELECT COUNT(*) FROM death_records WHERE player_uuid=?")) {
                statement.setString(1, player.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            } catch (SQLException exception) {
                throw failed("Failed to count player records", exception);
            }
        }
    }

    @Override
    public List<DeathRecord> loadOldestDeletable(UUID player, int limit, List<String> protectedRecordIds) {
        synchronized (lock) {
            StringBuilder sql = new StringBuilder("SELECT * FROM death_records WHERE 1=1");
            if (player != null) {
                sql.append(" AND player_uuid=?");
            }
            sql.append(" AND rollback_in_progress=0");
            if (protectedRecordIds != null && !protectedRecordIds.isEmpty()) {
                sql.append(" AND id NOT IN (");
                sql.append(String.join(",", protectedRecordIds.stream().map(id -> "?").toList()));
                sql.append(")");
            }
            sql.append(" ORDER BY death_time ASC LIMIT ?");
            try (PreparedStatement statement = conn().prepareStatement(sql.toString())) {
                int index = 1;
                if (player != null) {
                    statement.setString(index++, player.toString());
                }
                if (protectedRecordIds != null) {
                    for (String id : protectedRecordIds) {
                        statement.setString(index++, id);
                    }
                }
                statement.setInt(index, Math.max(1, limit));
                return readRecords(statement.executeQuery());
            } catch (SQLException exception) {
                throw failed("Failed to load deletable records", exception);
            }
        }
    }

    @Override
    public List<UUID> listRecordPlayers() {
        synchronized (lock) {
            List<UUID> players = new ArrayList<>();
            try (PreparedStatement statement = conn().prepareStatement("SELECT DISTINCT player_uuid FROM death_records");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String raw = result.getString(1);
                    if (raw == null || raw.isBlank()) {
                        continue;
                    }
                    try {
                        players.add(UUID.fromString(raw));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                return players;
            } catch (SQLException exception) {
                throw failed("Failed to list record players", exception);
            }
        }
    }

    @Override
    public void deleteRecord(String recordId) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("DELETE FROM death_records WHERE id=?")) {
                statement.setString(1, recordId);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw failed("Failed to delete death record", exception);
            }
        }
    }

    @Override
    public void saveRecovery(RecoveryEntry entry) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement(dialect.upsertRecovery())) {
                statement.setString(1, entry.getId());
                statement.setString(2, entry.getPlayerUuid().toString());
                statement.setString(3, entry.getRecordId());
                statement.setBytes(4, ItemSerializer.serialize(entry.getItems()));
                statement.setLong(5, entry.getCreatedAt());
                statement.setLong(6, entry.getExpireAt());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw failed("Failed to save recovery storage", exception);
            }
        }
    }

    @Override
    public List<RecoveryEntry> loadRecovery(UUID player) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("SELECT * FROM recovery_storage WHERE player_uuid=? ORDER BY created_at")) {
                statement.setString(1, player.toString());
                try (ResultSet result = statement.executeQuery()) {
                    List<RecoveryEntry> entries = new ArrayList<>();
                    while (result.next()) {
                        entries.add(readRecovery(result));
                    }
                    return entries;
                }
            } catch (SQLException exception) {
                throw failed("Failed to load recovery storage", exception);
            }
        }
    }

    @Override
    public Optional<RecoveryEntry> loadRecovery(String id) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("SELECT * FROM recovery_storage WHERE id=?")) {
                statement.setString(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readRecovery(result)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw failed("Failed to load recovery storage entry", exception);
            }
        }
    }

    @Override
    public void deleteRecovery(String id) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("DELETE FROM recovery_storage WHERE id=?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw failed("Failed to delete recovery storage", exception);
            }
        }
    }

    @Override
    public int deleteExpiredRecovery(long now) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("DELETE FROM recovery_storage WHERE expire_at>0 AND expire_at<=?")) {
                statement.setLong(1, now);
                return statement.executeUpdate();
            } catch (SQLException exception) {
                throw failed("Failed to cleanup recovery storage", exception);
            }
        }
    }

    @Override
    public int countRecovery() {
        synchronized (lock) {
            return count("SELECT COUNT(*) FROM recovery_storage");
        }
    }

    @Override
    public List<String> pendingRecoveryRecordIds() {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement(
                    "SELECT DISTINCT record_id FROM recovery_storage WHERE record_id IS NOT NULL AND (expire_at<=0 OR expire_at>?)")) {
                statement.setLong(1, System.currentTimeMillis());
                try (ResultSet result = statement.executeQuery()) {
                    List<String> ids = new ArrayList<>();
                    while (result.next()) {
                        String id = result.getString(1);
                        if (id != null && !id.isBlank()) {
                            ids.add(id);
                        }
                    }
                    return ids;
                }
            } catch (SQLException exception) {
                throw failed("Failed to load recovery record ids", exception);
            }
        }
    }

    @Override
    public void saveAudit(AuditLogEntry entry) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement(dialect.insertAudit())) {
                statement.setString(1, entry.getEventType());
                statement.setLong(2, entry.getTimestamp());
                statement.setString(3, entry.getActorUuid() == null ? null : entry.getActorUuid().toString());
                statement.setString(4, entry.getActorName());
                statement.setString(5, entry.getTargetUuid() == null ? null : entry.getTargetUuid().toString());
                statement.setString(6, entry.getTargetName());
                statement.setString(7, entry.getDeathChestId());
                statement.setString(8, entry.getRecordId());
                statement.setString(9, entry.getDetails());
                statement.setInt(10, entry.isForce() ? 1 : 0);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw failed("Failed to save audit log", exception);
            }
        }
    }

    @Override
    public void deleteAuditOlderThan(long timestamp) {
        synchronized (lock) {
            try (PreparedStatement statement = conn().prepareStatement("DELETE FROM audit_log WHERE timestamp < ?")) {
                statement.setLong(1, timestamp);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw failed("Failed to cleanup audit log", exception);
            }
        }
    }

    private int count(String sql) {
        try (PreparedStatement statement = conn().prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getInt(1) : 0;
        } catch (SQLException exception) {
            throw failed("Failed to count rows", exception);
        }
    }

    private RuntimeException failed(String message, Exception exception) {
        lastValidityCheck = 0L;
        return new IllegalStateException(message, exception);
    }

    private List<DeathChestData> readChests(ResultSet result) throws SQLException {
        List<DeathChestData> chests = new ArrayList<>();
        try (result) {
            while (result.next()) {
                DeathChestData chest = new DeathChestData();
                chest.setId(result.getString("id"));
                chest.setRecordId(result.getString("record_id"));
                chest.setOwnerUuid(UUID.fromString(result.getString("owner_uuid")));
                chest.setOwnerName(result.getString("owner_name"));
                chest.setWorld(result.getString("world"));
                chest.setX(result.getInt("x"));
                chest.setY(result.getInt("y"));
                chest.setZ(result.getInt("z"));
                chest.setSecondX(getInteger(result, "second_x"));
                chest.setSecondY(getInteger(result, "second_y"));
                chest.setSecondZ(getInteger(result, "second_z"));
                String type = result.getString("chest_type");
                chest.setChestType(type == null ? ChestType.SINGLE : ChestType.valueOf(type));
                chest.setCreatedAt(result.getLong("created_at"));
                chest.setUnlockAt(result.getLong("unlock_at"));
                chest.setExpireAt(result.getLong("expire_at"));
                chest.setPrice(result.getDouble("price"));
                chest.setCurrency(result.getString("currency"));
                chest.setUnpaid(result.getInt("unpaid") != 0);
                chest.setLocked(result.getInt("locked") != 0);
                String hologram = result.getString("hologram_uuid");
                chest.setHologramId(hologram == null ? null : UUID.fromString(hologram));
                chest.setActive(result.getInt("active") != 0);
                chest.setTimerPausedMillis(getLongColumn(result, "timer_paused_millis", 0L));
                chests.add(chest);
            }
        }
        return chests;
    }

    private List<DeathRecord> readRecords(ResultSet result) throws SQLException {
        List<DeathRecord> records = new ArrayList<>();
        try (result) {
            while (result.next()) {
                records.add(readRecord(result));
            }
        }
        return records;
    }

    private DeathRecord readRecord(ResultSet result) throws SQLException {
        DeathRecord record = new DeathRecord();
        record.setRecordId(result.getString("id"));
        record.setPlayerUuid(UUID.fromString(result.getString("player_uuid")));
        record.setPlayerName(result.getString("player_name"));
        record.setDeathTime(result.getLong("death_time"));
        record.setWorld(result.getString("world"));
        record.setX(result.getDouble("x"));
        record.setY(result.getDouble("y"));
        record.setZ(result.getDouble("z"));
        record.setYaw(result.getFloat("yaw"));
        record.setPitch(result.getFloat("pitch"));
        record.setDeathCause(result.getString("death_cause"));
        String killer = result.getString("killer_uuid");
        record.setKillerUuid(killer == null ? null : UUID.fromString(killer));
        record.setKillerName(result.getString("killer_name"));
        record.setPlayerLevelBefore(result.getInt("player_level_before"));
        record.setTotalExperienceBefore(result.getInt("total_experience_before"));
        record.setExperienceProgressBefore(result.getFloat("experience_progress_before"));
        record.setExperienceKept(result.getInt("experience_kept"));
        record.setExperienceLost(result.getInt("experience_lost"));
        record.setBalanceBefore(result.getDouble("balance_before"));
        record.setBalanceAfter(result.getDouble("balance_after"));
        record.setEconomyProvider(result.getString("economy_provider"));
        record.setCurrencyId(result.getString("currency_id"));
        record.setCalculatedPrice(result.getDouble("calculated_price"));
        record.setChargedPrice(result.getDouble("charged_price"));
        record.setInsufficientBalance(result.getInt("insufficient_balance") != 0);
        record.setInsufficientBalanceMode(result.getString("insufficient_balance_mode"));
        record.setDeathChestEnabled(result.getInt("death_chest_enabled") != 0);
        record.setDeathChestCreated(result.getInt("death_chest_created") != 0);
        record.setDeathChestId(result.getString("death_chest_id"));
        record.setDeathChestWorld(result.getString("death_chest_world"));
        record.setDeathChestX(getInteger(result, "death_chest_x"));
        record.setDeathChestY(getInteger(result, "death_chest_y"));
        record.setDeathChestZ(getInteger(result, "death_chest_z"));
        String type = result.getString("chest_type");
        record.setChestType(type == null || type.isBlank() ? null : ChestType.valueOf(type));
        record.setProtectedChest(result.getInt("is_protected") != 0);
        Object unlock = result.getObject("unlock_at");
        record.setUnlockAt(unlock == null ? null : result.getLong("unlock_at"));
        Object expire = result.getObject("expire_at");
        record.setExpireAt(expire == null ? null : result.getLong("expire_at"));
        record.setStatus(RecordStatus.valueOf(result.getString("status")));
        record.setFailureReason(result.getString("failure_reason"));
        record.setRollbackInProgress(result.getInt("rollback_in_progress") != 0);
        record.setItemsRestored(result.getInt("items_restored") != 0);
        record.setExperienceRestored(result.getInt("experience_restored") != 0);
        record.setItems(safeDeserialize(result.getBytes("items"), "death record " + record.getRecordId()));
        return record;
    }

    private RecoveryEntry readRecovery(ResultSet result) throws SQLException {
        RecoveryEntry entry = new RecoveryEntry();
        entry.setId(result.getString("id"));
        entry.setPlayerUuid(UUID.fromString(result.getString("player_uuid")));
        entry.setRecordId(result.getString("record_id"));
        entry.setItems(safeDeserialize(result.getBytes("items"), "recovery " + entry.getId()));
        entry.setCreatedAt(result.getLong("created_at"));
        entry.setExpireAt(result.getLong("expire_at"));
        return entry;
    }

    private List<ItemStack> safeDeserialize(byte[] items, String context) {
        try {
            return ItemSerializer.deserialize(items);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Corrupt item snapshot in " + context + ": " + exception.getMessage());
            return List.of();
        }
    }

    private Integer getInteger(ResultSet result, String column) throws SQLException {
        Object object = result.getObject(column);
        if (object instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private long getLongColumn(ResultSet result, String column, long fallback) {
        try {
            int index = result.findColumn(column);
            long value = result.getLong(index);
            return result.wasNull() ? fallback : value;
        } catch (SQLException exception) {
            return fallback;
        }
    }

    private void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }
}

package com.npucraft.deathchest.storage;

import com.npucraft.deathchest.model.AuditLogEntry;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.model.DeathRecord;
import com.npucraft.deathchest.model.RecoveryEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PluginStorage {
    void open();

    void close();

    void saveChest(DeathChestData chest);

    void deleteChest(String chestId);

    void setChestLocked(String chestId, boolean locked);

    void setChestHologram(String chestId, UUID hologramId);

    List<DeathChestData> loadActiveChests();

    Optional<DeathChestData> loadChest(String chestId);

    List<DeathChestData> loadChestsByOwner(UUID owner);

    List<DeathChestData> loadChestsByRecord(String recordId);

    void saveRecord(DeathRecord record);

    Optional<DeathRecord> loadRecord(String recordId);

    List<DeathRecord> loadRecords(UUID player, int limit);

    int countRecords();

    int countRecords(UUID player);

    List<UUID> listRecordPlayers();

    List<DeathRecord> loadOldestDeletable(UUID player, int limit, List<String> protectedRecordIds);

    void deleteRecord(String recordId);

    void saveRecovery(RecoveryEntry entry);

    List<RecoveryEntry> loadRecovery(UUID player);

    void deleteRecovery(String id);

    int deleteExpiredRecovery(long now);

    int countRecovery();

    List<String> pendingRecoveryRecordIds();

    void saveAudit(AuditLogEntry entry);

    void deleteAuditOlderThan(long timestamp);
}

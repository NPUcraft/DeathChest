package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.config.PluginSettings;
import com.npucraft.deathchest.model.DeathRecord;
import com.npucraft.deathchest.model.RecordStatus;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DeathRecordManager {
    private final DeathChestPlugin plugin;

    public DeathRecordManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public DeathRecord createPrepared(Player player, PlayerDeathEvent event, List<ItemStack> snapshot) {
        long deathTime = System.currentTimeMillis();
        DeathRecord record = new DeathRecord();
        record.setRecordId(plugin.nextRecordId(player.getName(), deathTime));
        record.setPlayerUuid(player.getUniqueId());
        record.setPlayerName(player.getName());
        record.setDeathTime(deathTime);
        if (player.getWorld() != null) {
            record.setWorld(player.getWorld().getName());
        }
        record.setX(player.getLocation().getX());
        record.setY(player.getLocation().getY());
        record.setZ(player.getLocation().getZ());
        record.setYaw(player.getLocation().getYaw());
        record.setPitch(player.getLocation().getPitch());
        var lastDamage = event.getEntity().getLastDamageCause();
        record.setDeathCause(lastDamage == null ? "UNKNOWN" : lastDamage.getCause().name());
        if (player.getKiller() != null) {
            record.setKillerUuid(player.getKiller().getUniqueId());
            record.setKillerName(player.getKiller().getName());
        }
        record.setPlayerLevelBefore(player.getLevel());
        record.setTotalExperienceBefore(com.npucraft.deathchest.util.ExperienceUtil.totalExperience(player));
        record.setExperienceProgressBefore(player.getExp());
        record.setItems(snapshot);
        record.setStatus(RecordStatus.PREPARED);
        if (plugin.settings().deathRecordsEnabled) {
            plugin.storage().saveRecord(record);
            trimPlayer(player.getUniqueId());
        }
        return record;
    }

    public void save(DeathRecord record) {
        if (!plugin.settings().deathRecordsEnabled) {
            return;
        }
        plugin.storage().saveRecord(record);
    }

    public Optional<DeathRecord> get(String id) {
        return plugin.storage().loadRecord(id);
    }

    public List<DeathRecord> history(UUID player, int limit) {
        return plugin.storage().loadRecords(player, limit);
    }

    public void trimPlayer(UUID player) {
        PluginSettings settings = plugin.settings();
        if (settings.maxRecordsPerPlayer <= 0) {
            return;
        }
        int count = plugin.storage().countRecords(player);
        int allowed = settings.maxRecordsPerPlayer;
        if (count <= allowed) {
            return;
        }
        List<String> protectedIds = protectedIds();
        int need = count - allowed;
        List<DeathRecord> deletable = plugin.storage().loadOldestDeletable(player, need + 8, protectedIds);
        int deleted = 0;
        for (DeathRecord record : deletable) {
            if (deleted >= need) {
                break;
            }
            if (!canDelete(record, protectedIds)) {
                continue;
            }
            plugin.storage().deleteRecord(record.getRecordId());
            deleted++;
        }
        if (deleted < need) {
            plugin.getLogger().warning("DeathRecord player limit exceeded for " + player + " but no safe records could be deleted. Item safety takes priority.");
        }
    }

    public void cleanupPlayerLimits() {
        PluginSettings settings = plugin.settings();
        if (settings.maxRecordsPerPlayer > 0) {
            for (UUID player : plugin.storage().listRecordPlayers()) {
                trimPlayer(player);
            }
        }
        if (plugin.settings().auditEnabled && plugin.settings().auditRetentionDays > 0) {
            long auditExpire = System.currentTimeMillis() - plugin.settings().auditRetentionDays * 24L * 60L * 60L * 1000L;
            plugin.storage().deleteAuditOlderThan(auditExpire);
        }
    }

    public List<String> protectedIds() {
        List<String> ids = new ArrayList<>();
        if (plugin.settings().protectActiveChestRecords) {
            plugin.chests().all().forEach(chest -> {
                if (chest.getRecordId() != null) {
                    ids.add(chest.getRecordId());
                }
            });
        }
        if (plugin.settings().protectPendingRecoveryRecords) {
            ids.addAll(plugin.storage().pendingRecoveryRecordIds());
        }
        return ids;
    }

    public boolean canDelete(DeathRecord record, List<String> protectedIds) {
        if (record.isRollbackInProgress()) {
            return false;
        }
        return protectedIds == null || !protectedIds.contains(record.getRecordId());
    }
}

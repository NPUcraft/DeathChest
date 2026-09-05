package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.DeathChestData;
import org.bukkit.Bukkit;

public final class CleanupManager {
    private final DeathChestPlugin plugin;
    private int chestTask = -1;
    private int recordTask = -1;

    public CleanupManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        this.chestTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::cleanupChests, 20L * 30, 20L * 30);
        if (plugin.settings().recordCleanupEnabled) {
            long interval = 20L * 60L * plugin.settings().recordCleanupIntervalMinutes;
            this.recordTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                try {
                    plugin.records().cleanupPlayerLimits();
                } catch (Exception exception) {
                    plugin.getLogger().warning("DeathRecord cleanup failed: " + exception.getMessage());
                }
            }, interval, interval);
        }
    }

    public void stop() {
        if (chestTask != -1) {
            Bukkit.getScheduler().cancelTask(chestTask);
            chestTask = -1;
        }
        if (recordTask != -1) {
            Bukkit.getScheduler().cancelTask(recordTask);
            recordTask = -1;
        }
    }

    public void runOnce() {
        cleanupChests();
    }

    private void cleanupChests() {
        try {
            if (plugin.timerClock() != null) {
                plugin.timerClock().heartbeat();
                plugin.timerClock().retryCatchUp();
            }
            long now = System.currentTimeMillis();
            for (DeathChestData chest : plugin.chests().all()) {
                try {
                    plugin.chests().cleanupIfDue(chest, now);
                } catch (Exception exception) {
                    plugin.getLogger().warning("Death chest cleanup failed for " + chest.getId() + ": " + exception.getMessage());
                }
            }
            plugin.recovery().cleanupExpired();
        } catch (Exception exception) {
            plugin.getLogger().warning("Death chest cleanup failed: " + exception.getMessage());
        }
    }
}

package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public final class TimerClock {
    private final DeathChestPlugin plugin;
    private final File file;
    private final UptimeState state = new UptimeState();

    public TimerClock(DeathChestPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "uptime.yml");
    }

    public long totalPaused() {
        return state.totalPaused();
    }

    public void applyOfflinePause() {
        load();
        long now = System.currentTimeMillis();
        long previous = state.totalPaused();
        long target = state.targetPaused(plugin.settings().timerMode, now);
        if (previous == 0L) {
            target = Math.max(target, plugin.chests().maxTimerPausedMillis());
        }
        int shifted = plugin.chests().catchUpTimers(target);
        state.markRunning(now, target);
        save();
        long additional = target - previous;
        if (additional > 0L && shifted > 0) {
            plugin.getLogger().info("Death chest timers paused while the server was offline: "
                    + (additional / 1000L) + "s, chests=" + shifted);
        }
    }

    public void retryCatchUp() {
        plugin.chests().catchUpTimers(state.totalPaused());
    }

    public void heartbeat() {
        state.heartbeat(System.currentTimeMillis());
        save();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        state.markRunning(Math.max(0L, yaml.getLong("last-seen", 0L)), Math.max(0L, yaml.getLong("total-paused", 0L)));
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("last-seen", state.lastSeen());
        yaml.set("total-paused", state.totalPaused());
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Unable to create plugin data folder for uptime.yml");
                return;
            }
            File tmp = new File(file.getAbsolutePath() + ".tmp");
            yaml.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                yaml.save(file);
                Files.deleteIfExists(tmp.toPath());
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to save uptime.yml", exception);
        }
    }
}

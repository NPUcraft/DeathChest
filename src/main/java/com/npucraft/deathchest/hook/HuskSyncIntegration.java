package com.npucraft.deathchest.hook;

import com.npucraft.deathchest.DeathChestPlugin;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

public final class HuskSyncIntegration implements Listener {
    private final DeathChestPlugin plugin;

    public HuskSyncIntegration(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Plugin huskSync = plugin.getServer().getPluginManager().getPlugin("HuskSync");
        if (huskSync == null || !huskSync.isEnabled()) {
            return;
        }
        try {
            ClassLoader loader = huskSync.getClass().getClassLoader();
            Class<? extends Event> eventType = Class.forName(
                    "net.william278.husksync.event.BukkitPreSyncEvent", true, loader).asSubclass(Event.class);
            if (!Cancellable.class.isAssignableFrom(eventType)) {
                throw new IllegalStateException("HuskSync PreSyncEvent is not cancellable");
            }
            HuskSyncHealthGuard guard = new HuskSyncHealthGuard(eventType,
                    Class.forName("net.william278.husksync.data.DataHolder", true, loader),
                    Class.forName("net.william278.husksync.data.Data$Health", true, loader));
            plugin.getServer().getPluginManager().registerEvent(eventType, this, EventPriority.HIGHEST,
                    (listener, event) -> {
                        if (eventType.isInstance(event)) {
                            handle(event, guard);
                        }
                    }, plugin, true);
            plugin.getLogger().info("HuskSync 同步死亡防护已注册，启用=" + plugin.settings().huskSyncPreventSyncDeath);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "HuskSync API 不兼容，同步死亡防护未启用。请勿视为已受保护。", exception);
        }
    }

    void handle(Event event, HuskSyncHealthGuard guard) {
        if (!plugin.settings().huskSyncPreventSyncDeath || ((Cancellable) event).isCancelled()) {
            return;
        }
        try {
            String snapshotId = guard.protect(event);
            if (snapshotId != null) {
                plugin.getLogger().warning("HuskSync 同步死亡防护：已将快照血量调整为 1（半颗心） snapshot=" + snapshotId);
            }
        } catch (Exception | LinkageError exception) {
            // Never apply a potentially lethal snapshot if the configured guard fails.
            ((Cancellable) event).setCancelled(true);
            plugin.getLogger().log(Level.SEVERE,
                    "HuskSync 同步死亡防护失败，已取消本次同步。请管理员检查错误后让玩家重新连接。", exception);
        }
    }
}

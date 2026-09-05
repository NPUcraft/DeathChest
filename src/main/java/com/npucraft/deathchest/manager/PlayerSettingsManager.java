package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.AuditEventType;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSettingsManager {
    private final DeathChestPlugin plugin;
    private final Map<UUID, Boolean> overrides = new ConcurrentHashMap<>();
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    public PlayerSettingsManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled(UUID playerId) {
        if (!loaded.contains(playerId)) {
            Optional<Boolean> stored = plugin.storage().loadPlayerEnabled(playerId);
            stored.ifPresent(value -> overrides.put(playerId, value));
            loaded.add(playerId);
        }
        return overrides.getOrDefault(playerId, plugin.settings().defaultEnabled);
    }

    public void setEnabled(Player player, boolean enabled) {
        plugin.storage().savePlayerEnabled(player.getUniqueId(), player.getName(), enabled);
        overrides.put(player.getUniqueId(), enabled);
        loaded.add(player.getUniqueId());
        plugin.audit().log(AuditEventType.PLAYER_TOGGLE, player.getUniqueId(), player.getName(),
                player.getUniqueId(), player.getName(), null, null, "enabled=" + enabled, false);
    }
}

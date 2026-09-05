package com.npucraft.deathchest.hook;

import com.npucraft.deathchest.DeathChestPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class DeathChestPlaceholderExpansion extends PlaceholderExpansion {
    private final DeathChestPlugin plugin;

    public DeathChestPlaceholderExpansion(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "deathchest";
    }

    @Override
    public @NotNull String getAuthor() {
        return "NPUcraft";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        Player online = player.getPlayer();
        if (online == null) {
            return "";
        }
        return plugin.placeholders().papiOrInternal(online, params);
    }
}

package com.npucraft.deathchest.config;

import com.npucraft.deathchest.DeathChestPlugin;

public final class ConfigManager {
    private final DeathChestPlugin plugin;
    private PluginSettings settings;

    public ConfigManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.settings = new PluginSettings(plugin);
    }

    public PluginSettings settings() {
        return settings;
    }
}

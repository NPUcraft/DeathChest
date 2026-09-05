package com.npucraft.deathchest.listener;

import com.npucraft.deathchest.DeathChestPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class DeathListener implements Listener {
    private final DeathChestPlugin plugin;

    public DeathListener(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        try {
            plugin.deaths().handle(event);
        } catch (Exception exception) {
            plugin.getLogger().severe("DeathChest transaction crashed; vanilla drops will be kept. " + exception.getMessage());
            exception.printStackTrace();
        }
    }
}

package com.npucraft.deathchest.hook;

import com.npucraft.deathchest.DeathChestPlugin;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class ProtectionManager {
    private final DeathChestPlugin plugin;
    private final List<ProtectionIntegration> integrations = new ArrayList<>();

    public ProtectionManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
        hook();
    }

    public void hook() {
        integrations.clear();
        try {
            ResidenceIntegration residence = new ResidenceIntegration(plugin);
            if (residence.isAvailable()) {
                integrations.add(residence);
            }
        } catch (LinkageError | RuntimeException exception) {
            plugin.getLogger().warning("Residence integration could not be loaded and will be skipped: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    public boolean canCreateDeathChest(Player player, Location location) {
        for (ProtectionIntegration integration : integrations) {
            if (integration.isAvailable() && !integration.canCreateDeathChest(player, location)) {
                return false;
            }
        }
        return true;
    }

    public boolean canCreateDeathChest(Player player, Block block) {
        return block != null && canCreateDeathChest(player, block.getLocation());
    }
}

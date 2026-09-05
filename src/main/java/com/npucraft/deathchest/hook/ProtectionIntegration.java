package com.npucraft.deathchest.hook;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface ProtectionIntegration {
    String getName();

    boolean isAvailable();

    boolean canCreateDeathChest(Player player, Location location);
}

package com.npucraft.deathchest.model;

public record LocationKey(String world, int x, int y, int z) {
    public static LocationKey of(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location or world is null");
        }
        return new LocationKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static LocationKey of(String world, int x, int y, int z) {
        return new LocationKey(world, x, y, z);
    }

    public String asString() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}

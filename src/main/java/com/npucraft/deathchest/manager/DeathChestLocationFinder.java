package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.config.PluginSettings;
import com.npucraft.deathchest.model.ChestPlacement;
import com.npucraft.deathchest.model.ChestType;
import com.npucraft.deathchest.model.LocationKey;
import org.bukkit.Location;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

public final class DeathChestLocationFinder {
    private enum PlacementKind {
        SURFACE,
        WATER,
        CAVE
    }

    private static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final Set<Material> DANGEROUS = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.SOUL_CAMPFIRE, Material.CAMPFIRE, Material.MAGMA_BLOCK
    );
    private static final Set<Material> IMPORTANT = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST, Material.BARREL, Material.SHULKER_BOX,
            Material.SPAWNER, Material.BEDROCK, Material.BARRIER, Material.END_PORTAL, Material.END_PORTAL_FRAME,
            Material.END_GATEWAY, Material.NETHER_PORTAL, Material.OBSIDIAN, Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK, Material.STRUCTURE_BLOCK, Material.JIGSAW,
            Material.BEACON, Material.RESPAWN_ANCHOR, Material.VAULT
    );

    private final DeathChestPlugin plugin;

    public DeathChestLocationFinder(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public ChestPlacement find(Player player, Location origin, ChestType type, Predicate<LocationKey> occupied) {
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        World world = origin.getWorld();
        PluginSettings settings = plugin.settings();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        int originX = origin.getBlockX();
        int originY = Math.max(minY, Math.min(maxY, origin.getBlockY()));
        int originZ = origin.getBlockZ();
        int radius = settings.safeLocationRadius;
        int vertical = settings.verticalSearchRadius;
        int[] checks = {0};
        int maxChecks = plugin.settings().maxBlockChecks;

        long diameter = radius * 2L + 1L;
        long columns = diameter * diameter;
        int surfaceBudget = (int) Math.min(columns, Math.max(1L, maxChecks * 2L / 5L));
        int surfaceLimit = Math.min(maxChecks, surfaceBudget);
        ChestPlacement surface = search(player, world, originX, originY, originZ, radius, minY, maxY,
                vertical, type, occupied, checks, surfaceLimit, PlacementKind.SURFACE);
        if (surface != null) {
            return surface;
        }

        int remaining = maxChecks - checks[0];
        int waterLimit = checks[0] + remaining / 2;
        ChestPlacement water = search(player, world, originX, originY, originZ, radius, minY, maxY,
                vertical, type, occupied, checks, waterLimit, PlacementKind.WATER);
        if (water != null) {
            return water;
        }

        return search(player, world, originX, originY, originZ, radius, minY, maxY,
                vertical, type, occupied, checks, maxChecks, PlacementKind.CAVE);
    }

    private ChestPlacement search(Player player, World world, int originX, int originY, int originZ, int radius,
                                  int minY, int maxY, int vertical, ChestType type,
                                  Predicate<LocationKey> occupied, int[] checks, int limit, PlacementKind kind) {
        for (int r = 0; r <= radius && checks[0] < limit; r++) {
            ChestPlacement found = searchRing(player, world, originX, originY, originZ, r,
                    minY, maxY, vertical, type, occupied, checks, limit, kind);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private ChestPlacement searchRing(Player player, World world, int originX, int originY, int originZ, int r,
                                      int minY, int maxY, int vertical, ChestType type,
                                      Predicate<LocationKey> occupied, int[] checks, int maxChecks,
                                      PlacementKind kind) {
        if (r == 0) {
            return tryColumn(player, world, originX, originY, originZ, minY, maxY, vertical,
                    type, occupied, checks, maxChecks, kind);
        }
        for (int x = originX - r; x <= originX + r; x++) {
            ChestPlacement north = tryColumn(player, world, x, originY, originZ - r, minY, maxY, vertical,
                    type, occupied, checks, maxChecks, kind);
            if (north != null) {
                return north;
            }
            ChestPlacement south = tryColumn(player, world, x, originY, originZ + r, minY, maxY, vertical,
                    type, occupied, checks, maxChecks, kind);
            if (south != null) {
                return south;
            }
            if (checks[0] >= maxChecks) {
                return null;
            }
        }
        for (int z = originZ - r + 1; z <= originZ + r - 1; z++) {
            ChestPlacement west = tryColumn(player, world, originX - r, originY, z, minY, maxY, vertical,
                    type, occupied, checks, maxChecks, kind);
            if (west != null) {
                return west;
            }
            ChestPlacement east = tryColumn(player, world, originX + r, originY, z, minY, maxY, vertical,
                    type, occupied, checks, maxChecks, kind);
            if (east != null) {
                return east;
            }
            if (checks[0] >= maxChecks) {
                return null;
            }
        }
        return null;
    }

    private ChestPlacement tryColumn(Player player, World world, int x, int originY, int z,
                                     int minY, int maxY, int vertical, ChestType type,
                                     Predicate<LocationKey> occupied, int[] checks, int maxChecks,
                                     PlacementKind kind) {
        if (kind == PlacementKind.SURFACE) {
            int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            if (surfaceY < minY || surfaceY > maxY || Math.abs(surfaceY - originY) > vertical) {
                countCheck(checks, maxChecks);
                return null;
            }
            return tryBlock(player, world.getBlockAt(x, surfaceY, z), type, occupied, checks, maxChecks, kind);
        }

        ChestPlacement exact = tryBlock(player, world.getBlockAt(x, originY, z), type, occupied,
                checks, maxChecks, kind);
        if (exact != null || checks[0] >= maxChecks) {
            return exact;
        }
        for (int distance = 1; distance <= vertical; distance++) {
            int belowY = originY - distance;
            if (belowY >= minY) {
                ChestPlacement below = tryBlock(player, world.getBlockAt(x, belowY, z),
                        type, occupied, checks, maxChecks, kind);
                if (below != null || checks[0] >= maxChecks) {
                    return below;
                }
            }
            int aboveY = originY + distance;
            if (aboveY <= maxY) {
                ChestPlacement above = tryBlock(player, world.getBlockAt(x, aboveY, z),
                        type, occupied, checks, maxChecks, kind);
                if (above != null || checks[0] >= maxChecks) {
                    return above;
                }
            }
        }
        return null;
    }

    private ChestPlacement tryBlock(Player player, Block block, ChestType type, Predicate<LocationKey> occupied,
                                    int[] checks, int maxChecks, PlacementKind kind) {
        if (checks[0] >= maxChecks) {
            return null;
        }
        checks[0]++;
        if (!matchesKind(block, kind)) {
            return null;
        }
        if (type == ChestType.SINGLE) {
            if (isValidSingle(player, block, occupied)) {
                return new ChestPlacement(ChestType.SINGLE, block, null, preferredFacing(block));
            }
            return null;
        }
        return findDouble(player, block, occupied, kind);
    }

    private ChestPlacement findDouble(Player player, Block primary, Predicate<LocationKey> occupied,
                                      PlacementKind kind) {
        if (!isReplaceable(primary) || !isSafe(primary) || occupied.test(LocationKey.of(primary.getLocation()))
                || !plugin.protection().canCreateDeathChest(player, primary)) {
            return null;
        }
        for (BlockFace face : HORIZONTAL) {
            Block secondary = primary.getRelative(face);
            if (!matchesKind(secondary, kind) || !isReplaceable(secondary) || !isSafe(secondary)
                    || occupied.test(LocationKey.of(secondary.getLocation()))) {
                continue;
            }
            if (!plugin.protection().canCreateDeathChest(player, secondary)) {
                continue;
            }
            if (wouldConnectExternally(primary, secondary) || wouldConnectExternally(secondary, primary)) {
                continue;
            }
            return new ChestPlacement(ChestType.DOUBLE, primary, secondary, facingForPair(face));
        }
        return null;
    }

    private boolean matchesKind(Block block, PlacementKind kind) {
        return switch (kind) {
            case SURFACE -> block.getType() != Material.WATER && isExposedSurface(block);
            case WATER -> block.getType() == Material.WATER;
            case CAVE -> block.getType() != Material.WATER && !isExposedSurface(block);
        };
    }

    private boolean isExposedSurface(Block block) {
        return block.getY() == block.getWorld().getHighestBlockYAt(
                block.getX(), block.getZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
    }

    private void countCheck(int[] checks, int maxChecks) {
        if (checks[0] < maxChecks) {
            checks[0]++;
        }
    }

    private boolean isValidSingle(Player player, Block block, Predicate<LocationKey> occupied) {
        if (!isReplaceable(block) || !isSafe(block)) {
            return false;
        }
        if (occupied.test(LocationKey.of(block.getLocation()))) {
            return false;
        }
        if (plugin.settings().avoidExistingChests && hasAdjacentChest(block, null)) {
            return false;
        }
        return plugin.protection().canCreateDeathChest(player, block);
    }

    private boolean isReplaceable(Block block) {
        Material type = block.getType();
        if (type.isAir() || type == Material.SHORT_GRASS || type == Material.TALL_GRASS || type == Material.FERN
                || type == Material.LARGE_FERN || type == Material.DEAD_BUSH || type == Material.VINE
                || type == Material.GLOW_LICHEN || type == Material.MOSS_CARPET) {
            return true;
        }
        if (type == Material.SNOW && block.getBlockData() instanceof Snow snow && snow.getLayers() <= 1) {
            return true;
        }
        if (plugin.settings().allowWaterlogged && type == Material.WATER) {
            if (block.getBlockData() instanceof Levelled levelled) {
                return levelled.getLevel() == 0;
            }
            return true;
        }
        return Tag.REPLACEABLE.isTagged(type);
    }

    private boolean isSafe(Block block) {
        if (DANGEROUS.contains(block.getType()) || IMPORTANT.contains(block.getType())) {
            return false;
        }
        World world = block.getWorld();
        if (block.getY() < world.getMinHeight() || block.getY() >= world.getMaxHeight()) {
            return false;
        }
        Block below = block.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid() || DANGEROUS.contains(below.getType()) || Tag.LEAVES.isTagged(below.getType())) {
            return false;
        }
        BlockData data = block.getBlockData();
        return !(data instanceof org.bukkit.block.data.type.Bed);
    }

    private boolean hasAdjacentChest(Block block, Block allowed) {
        for (BlockFace face : HORIZONTAL) {
            Block relative = block.getRelative(face);
            if (allowed != null && relative.getX() == allowed.getX() && relative.getY() == allowed.getY() && relative.getZ() == allowed.getZ()) {
                continue;
            }
            Material type = relative.getType();
            if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
                return true;
            }
        }
        return false;
    }

    private boolean wouldConnectExternally(Block block, Block partner) {
        return plugin.settings().avoidExistingChests && hasAdjacentChest(block, partner);
    }

    private BlockFace preferredFacing(Block block) {
        return BlockFace.NORTH;
    }

    private BlockFace facingForPair(BlockFace partnerOffset) {
        if (partnerOffset == BlockFace.EAST || partnerOffset == BlockFace.WEST) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }
}

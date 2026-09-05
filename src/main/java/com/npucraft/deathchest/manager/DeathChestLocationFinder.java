package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.config.PluginSettings;
import com.npucraft.deathchest.model.ChestPlacement;
import com.npucraft.deathchest.model.ChestType;
import com.npucraft.deathchest.model.LocationKey;
import org.bukkit.Location;
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

        for (int dy = 0; dy <= vertical; dy++) {
            for (int sign = 0; sign < (dy == 0 ? 1 : 2); sign++) {
                int y = originY + (sign == 0 ? dy : -dy);
                if (y < minY || y > maxY) {
                    continue;
                }
                for (int r = 0; r <= radius; r++) {
                    ChestPlacement found = searchSquare(player, world, originX, y, originZ, r, type, occupied, checks, maxChecks);
                    if (found != null) {
                        return found;
                    }
                    if (checks[0] >= maxChecks) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private ChestPlacement searchSquare(Player player, World world, int originX, int y, int originZ, int r,
                                        ChestType type, Predicate<LocationKey> occupied, int[] checks, int maxChecks) {
        if (r == 0) {
            return tryBlock(player, world.getBlockAt(originX, y, originZ), type, occupied, checks, maxChecks);
        }
        for (int x = originX - r; x <= originX + r; x++) {
            ChestPlacement north = tryBlock(player, world.getBlockAt(x, y, originZ - r), type, occupied, checks, maxChecks);
            if (north != null) {
                return north;
            }
            ChestPlacement south = tryBlock(player, world.getBlockAt(x, y, originZ + r), type, occupied, checks, maxChecks);
            if (south != null) {
                return south;
            }
            if (checks[0] >= maxChecks) {
                return null;
            }
        }
        for (int z = originZ - r + 1; z <= originZ + r - 1; z++) {
            ChestPlacement west = tryBlock(player, world.getBlockAt(originX - r, y, z), type, occupied, checks, maxChecks);
            if (west != null) {
                return west;
            }
            ChestPlacement east = tryBlock(player, world.getBlockAt(originX + r, y, z), type, occupied, checks, maxChecks);
            if (east != null) {
                return east;
            }
            if (checks[0] >= maxChecks) {
                return null;
            }
        }
        return null;
    }

    private ChestPlacement tryBlock(Player player, Block block, ChestType type, Predicate<LocationKey> occupied,
                                    int[] checks, int maxChecks) {
        if (checks[0] >= maxChecks) {
            return null;
        }
        checks[0]++;
        if (type == ChestType.SINGLE) {
            if (isValidSingle(player, block, occupied)) {
                return new ChestPlacement(ChestType.SINGLE, block, null, preferredFacing(block));
            }
            return null;
        }
        return findDouble(player, block, occupied);
    }

    private ChestPlacement findDouble(Player player, Block primary, Predicate<LocationKey> occupied) {
        if (!isReplaceable(primary) || !isSafe(primary) || occupied.test(LocationKey.of(primary.getLocation()))
                || !plugin.protection().canCreateDeathChest(player, primary)) {
            return null;
        }
        for (BlockFace face : HORIZONTAL) {
            Block secondary = primary.getRelative(face);
            if (!isReplaceable(secondary) || !isSafe(secondary) || occupied.test(LocationKey.of(secondary.getLocation()))) {
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
        if (below.getType() == Material.LAVA || below.getType() == Material.MAGMA_BLOCK) {
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

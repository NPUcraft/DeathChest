package com.npucraft.deathchest.listener;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.DeathChestData;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public final class WorldListener implements Listener {
    private final DeathChestPlugin plugin;

    public WorldListener(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        long now = System.currentTimeMillis();
        for (BlockState state : event.getChunk().getTileEntities()) {
            if (!(state instanceof TileState tile)) {
                continue;
            }
            String id = plugin.keys().readChestId(tile);
            if (id == null) {
                continue;
            }
            plugin.chests().byBlock(state.getBlock()).ifPresent(chest -> {
                plugin.chests().syncPdc(state.getBlock(), chest);
                plugin.holograms().update(chest);
            });
        }
        for (DeathChestData chest : plugin.chests().all()) {
            if (!event.getWorld().getName().equals(chest.getWorld())
                    || event.getChunk().getX() != Math.floorDiv(chest.getX(), 16)
                    || event.getChunk().getZ() != Math.floorDiv(chest.getZ(), 16)) {
                continue;
            }
            try {
                plugin.holograms().update(chest);
                plugin.chests().cleanupIfDue(chest, now);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Death chest chunk load cleanup failed for " + chest.getId() + ": "
                        + exception.getMessage());
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.holograms().restoreInWorld(event.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        for (DeathChestData chest : plugin.chests().all()) {
            if (event.getWorld().getName().equals(chest.getWorld())) {
                plugin.holograms().remove(chest);
            }
        }
    }
}

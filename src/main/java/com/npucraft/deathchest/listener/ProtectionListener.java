package com.npucraft.deathchest.listener;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.manager.AuditLogger;
import com.npucraft.deathchest.manager.QuickRetrieveManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

public final class ProtectionListener implements Listener {
    private final DeathChestPlugin plugin;

    public ProtectionListener(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Optional<DeathChestData> optional = plugin.chests().byBlock(event.getClickedBlock());
        if (optional.isEmpty()) {
            return;
        }
        DeathChestData chest = optional.get();
        Player player = event.getPlayer();
        if (chest.isLocked()) {
            event.setCancelled(true);
            plugin.audit().player(player.getName(), "操作被拒绝（已锁定）",
                    "chest=" + chest.getId() + " " + AuditLogger.location(chest));
            plugin.messages().send(player, "retrieve-locked");
            return;
        }
        if (plugin.settings().quickRetrieveEnabled && player.isSneaking()) {
            event.setCancelled(true);
            if (!plugin.retrieve().canRetrieve(player, chest)) {
                plugin.audit().player(player.getName(), "快速取回被拒绝",
                        "chest=" + chest.getId() + " 主人=" + chest.getOwnerName() + " " + AuditLogger.location(chest));
                plugin.messages().send(player, "retrieve-not-owner", plugin.messages().map("owner", chest.getOwnerName()));
                return;
            }
            QuickRetrieveManager.RetrieveResult result = plugin.retrieve().retrieve(player, chest);
            sendRetrieveResult(player, result);
            return;
        }
        long now = System.currentTimeMillis();
        if (chest.isProtected(now) && !chest.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("deathchest.bypass")) {
            event.setCancelled(true);
            plugin.audit().player(player.getName(), "打开被拒绝（保护中）",
                    "chest=" + chest.getId() + " 主人=" + chest.getOwnerName() + " " + AuditLogger.location(chest));
            plugin.messages().send(player, "protected-deny", plugin.messages().map("owner", chest.getOwnerName()));
            plugin.messages().send(player, "protected-remaining", plugin.messages().map(
                    "time", plugin.placeholders().remaining(chest.getUnlockAt()),
                    "unlock", plugin.placeholders().absolute(chest.getUnlockAt())));
            return;
        }
        plugin.audit().player(player.getName(), "打开死亡箱",
                "chest=" + chest.getId() + " 主人=" + chest.getOwnerName() + " " + AuditLogger.location(chest));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<DeathChestData> optional = plugin.chests().byBlock(event.getBlock());
        if (optional.isEmpty()) {
            return;
        }
        DeathChestData chest = optional.get();
        Player player = event.getPlayer();
        if (chest.isLocked()) {
            event.setCancelled(true);
            plugin.audit().player(player.getName(), "破坏被拒绝（已锁定）",
                    "chest=" + chest.getId() + " " + AuditLogger.location(chest));
            plugin.messages().send(player, "retrieve-locked");
            return;
        }
        if (player.hasPermission("deathchest.break.bypass")) {
            plugin.audit().player(player.getName(), "破坏死亡箱（bypass）",
                    "chest=" + chest.getId() + " 主人=" + chest.getOwnerName() + " " + AuditLogger.location(chest));
            event.setCancelled(true);
            try {
                plugin.chests().removeAndStoreRemaining(chest, true);
                event.setDropItems(false);
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Failed to safely remove death chest " + chest.getId() + ": " + exception.getMessage());
                plugin.messages().send(player, "chest-remove-storage-failed");
            }
            return;
        }
        boolean owner = chest.getOwnerUuid().equals(player.getUniqueId());
        boolean allowed = owner ? plugin.settings().ownerCanBreak : plugin.settings().publicCanBreak;
        if (chest.isProtected(System.currentTimeMillis()) && !owner) {
            allowed = false;
        }
        if (!allowed) {
            event.setCancelled(true);
            plugin.audit().player(player.getName(), "破坏被拒绝",
                    "chest=" + chest.getId() + " 主人=" + chest.getOwnerName() + " " + AuditLogger.location(chest));
            plugin.messages().send(player, "protected-deny", plugin.messages().map("owner", chest.getOwnerName()));
            return;
        }
        plugin.audit().player(player.getName(), "破坏死亡箱",
                "chest=" + chest.getId() + " 主人=" + chest.getOwnerName() + " " + AuditLogger.location(chest));
        event.setCancelled(true);
        try {
            plugin.chests().removeAndStoreRemaining(chest, true);
            event.setDropItems(false);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Failed to safely remove death chest " + chest.getId() + ": " + exception.getMessage());
            plugin.messages().send(player, "chest-remove-storage-failed");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
            return;
        }
        Block placed = event.getBlockPlaced();
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            if (plugin.chests().byBlock(placed.getRelative(face)).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.settings().protectFromExplosion) {
            return;
        }
        filterExplosion(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.settings().protectFromExplosion) {
            return;
        }
        filterExplosion(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!plugin.settings().protectFromPiston) {
            return;
        }
        for (Block block : event.getBlocks()) {
            if (plugin.chests().byBlock(block).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!plugin.settings().protectFromPiston) {
            return;
        }
        for (Block block : event.getBlocks()) {
            if (plugin.chests().byBlock(block).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        Optional<DeathChestData> source = plugin.chests().byInventory(event.getSource());
        Optional<DeathChestData> destination = plugin.chests().byInventory(event.getDestination());
        long now = System.currentTimeMillis();
        if (source.isPresent()) {
            DeathChestData chest = source.get();
            if (chest.isLocked() || (plugin.settings().protectFromHopper && chest.isProtected(now))) {
                event.setCancelled(true);
                return;
            }
        }
        if (destination.isPresent()) {
            DeathChestData chest = destination.get();
            if (chest.isLocked() || (plugin.settings().protectFromHopper && chest.isProtected(now))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperMoved(InventoryMoveItemEvent event) {
        plugin.chests().byInventory(event.getSource()).ifPresent(chest -> plugin.chests().scheduleEmptyCheck(chest));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        plugin.chests().byInventory(event.getView().getTopInventory()).ifPresent(chest -> {
            if (chest.isLocked()) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        plugin.chests().byInventory(event.getView().getTopInventory()).ifPresent(chest -> {
            if (chest.isLocked()) {
                event.setCancelled(true);
            }
        });
    }

    private void filterExplosion(Iterator<Block> iterator) {
        long now = System.currentTimeMillis();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Optional<DeathChestData> chest = plugin.chests().byBlock(block);
            if (chest.isPresent() && chest.get().isProtected(now)) {
                iterator.remove();
            }
        }
    }

    private void sendRetrieveResult(Player player, QuickRetrieveManager.RetrieveResult result) {
        if (result.isMissing()) {
            plugin.messages().send(player, "invalid-id", Map.of("id", "?"));
            return;
        }
        if (result.isEmpty()) {
            plugin.messages().send(player, "retrieve-empty");
            return;
        }
        if (result.left() <= 0) {
            plugin.messages().send(player, "retrieve-success-all");
        } else {
            plugin.messages().send(player, "retrieve-success-partial", plugin.messages().map(
                    "taken", String.valueOf(result.taken()),
                    "left", String.valueOf(result.left())
            ));
        }
    }
}

package com.npucraft.deathchest.listener;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.command.ReadOnlyItemsGui;
import com.npucraft.deathchest.manager.RecoveryStorageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerListener implements Listener {
    private final DeathChestPlugin plugin;
    private final Map<UUID, Integer> recoverTasks = new ConcurrentHashMap<>();

    public PlayerListener(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.settings().recoveryEnabled) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        Integer previous = recoverTasks.remove(uuid);
        if (previous != null) {
            Bukkit.getScheduler().cancelTask(previous);
        }
        final int[] taskId = new int[1];
        taskId[0] = plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            recoverTasks.remove(uuid, taskId[0]);
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || !plugin.recovery().hasPending(uuid)) {
                return;
            }
            RecoveryStorageManager.RecoverResult result = plugin.recovery().recover(player);
            if (!plugin.settings().recoveryNotifyOnJoin || result.isEmpty()) {
                return;
            }
            if (!player.isOnline()) {
                return;
            }
            if (result.left() <= 0) {
                plugin.messages().send(player, "recover-success-all");
            } else {
                plugin.messages().send(player, "recover-success-partial", plugin.messages().map(
                        "taken", String.valueOf(result.taken()),
                        "left", String.valueOf(result.left())
                ));
            }
        }, 40L);
        recoverTasks.put(uuid, taskId[0]);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Integer previous = recoverTasks.remove(event.getPlayer().getUniqueId());
        if (previous != null) {
            Bukkit.getScheduler().cancelTask(previous);
        }
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ReadOnlyItemsGui gui)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean inTop = event.getRawSlot() >= 0 && event.getRawSlot() < topSize;
        boolean movesIntoTop = event.isShiftClick()
                || event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getClick() == ClickType.DOUBLE_CLICK;
        if (inTop || movesIntoTop) {
            event.setCancelled(true);
        }
        if (inTop && event.getWhoClicked() instanceof Player player && plugin.commandExecutor() != null) {
            plugin.commandExecutor().handleGuiClick(player, gui, event.getRawSlot());
        }
    }

    @EventHandler
    public void onGuiDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ReadOnlyItemsGui)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onGuiMove(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof ReadOnlyItemsGui || event.getDestination().getHolder() instanceof ReadOnlyItemsGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChestInventoryChange(InventoryClickEvent event) {
        plugin.chests().byInventory(event.getView().getTopInventory()).ifPresent(chest -> {
            if (!(event.getWhoClicked() instanceof Player)) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (com.npucraft.deathchest.util.ItemStacks.isInventoryEmpty(event.getView().getTopInventory())) {
                    plugin.chests().scheduleEmptyCheck(chest);
                } else {
                    plugin.chests().cancelEmptyTask(chest.getId());
                }
            });
        });
    }

    @EventHandler
    public void onChestClose(InventoryCloseEvent event) {
        plugin.chests().byInventory(event.getInventory()).ifPresent(chest -> {
            if (com.npucraft.deathchest.util.ItemStacks.isInventoryEmpty(event.getInventory())) {
                plugin.chests().scheduleEmptyCheck(chest);
            }
        });
    }
}

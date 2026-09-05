package com.npucraft.deathchest.listener;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.util.ItemStacks;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class TotemListener implements Listener {
    private final DeathChestPlugin plugin;

    public TotemListener(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!plugin.settings().inventoryTotem) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        if (player.getHealth() + player.getAbsorptionAmount() - event.getFinalDamage() > 0.0D) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        if (isTotem(inventory.getItemInMainHand()) || isTotem(inventory.getItemInOffHand())) {
            return;
        }
        int slot = findStorageTotem(inventory);
        if (slot < 0) {
            return;
        }
        ItemStack totem = inventory.getItem(slot);
        ItemStack offhand = inventory.getItemInOffHand();
        inventory.setItem(slot, ItemStacks.isEmpty(offhand) ? null : offhand);
        inventory.setItemInOffHand(totem);
    }

    private int findStorageTotem(PlayerInventory inventory) {
        ItemStack[] storage = inventory.getStorageContents();
        int held = inventory.getHeldItemSlot();
        for (int i = 0; i < storage.length; i++) {
            if (i == held) {
                continue;
            }
            if (isTotem(storage[i])) {
                return i;
            }
        }
        return -1;
    }

    private boolean isTotem(ItemStack item) {
        return !ItemStacks.isEmpty(item) && item.getType() == Material.TOTEM_OF_UNDYING;
    }
}

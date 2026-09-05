package com.npucraft.deathchest.util;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DeathItemRules {
    private DeathItemRules() {
    }

    public static void apply(PlayerDeathEvent event) {
        event.getDrops().removeIf(DeathItemRules::hasVanishingCurse);
        if (!event.getKeepInventory()) {
            ensureBindingCanDrop(event, event.getEntity().getInventory());
        }
    }

    public static boolean hasVanishingCurse(ItemStack item) {
        return hasEnchantment(item, "vanishing_curse");
    }

    public static boolean hasBindingCurse(ItemStack item) {
        return hasEnchantment(item, "binding_curse");
    }

    private static boolean hasEnchantment(ItemStack item, String key) {
        if (ItemStacks.isEmpty(item)) {
            return false;
        }
        NamespacedKey namespacedKey = NamespacedKey.minecraft(key);
        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            if (namespacedKey.equals(enchantment.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static void ensureBindingCanDrop(PlayerDeathEvent event, PlayerInventory inventory) {
        List<ItemStack> binding = new ArrayList<>();
        collectBinding(binding, inventory.getArmorContents());
        collectBinding(binding, inventory.getStorageContents());
        collectBinding(binding, new ItemStack[]{inventory.getItemInOffHand()});

        boolean[] matched = new boolean[event.getDrops().size()];
        for (ItemStack item : binding) {
            if (!markSimilarDrop(event.getDrops(), matched, item)) {
                event.getDrops().add(item.clone());
            }
        }
    }

    private static void collectBinding(List<ItemStack> output, ItemStack[] items) {
        if (items == null) {
            return;
        }
        for (ItemStack item : items) {
            if (ItemStacks.isEmpty(item) || hasVanishingCurse(item) || !hasBindingCurse(item)) {
                continue;
            }
            output.add(item);
        }
    }

    private static boolean markSimilarDrop(List<ItemStack> drops, boolean[] matched, ItemStack item) {
        for (int i = 0; i < matched.length; i++) {
            if (matched[i]) {
                continue;
            }
            ItemStack drop = drops.get(i);
            if (drop != null && drop.isSimilar(item) && drop.getAmount() == item.getAmount()) {
                matched[i] = true;
                return true;
            }
        }
        return false;
    }

    public static void removeVanishing(Iterable<ItemStack> items) {
        Iterator<ItemStack> iterator = items.iterator();
        while (iterator.hasNext()) {
            if (hasVanishingCurse(iterator.next())) {
                iterator.remove();
            }
        }
    }
}

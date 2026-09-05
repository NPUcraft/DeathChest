package com.npucraft.deathchest.util;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ItemMatcher {
    private ItemMatcher() {
    }

    public static boolean matches(Collection<ItemStack> expected, Collection<ItemStack> actual) {
        return canonicalAmounts(expected).equals(canonicalAmounts(actual));
    }

    public static Map<String, Integer> canonicalAmounts(Collection<ItemStack> items) {
        Map<String, Integer> amounts = new HashMap<>();
        if (items == null) {
            return amounts;
        }
        for (ItemStack item : items) {
            if (ItemStacks.isEmpty(item)) {
                continue;
            }
            ItemStack unit = item.clone();
            int amount = unit.getAmount();
            unit.setAmount(1);
            amounts.merge(key(unit), amount, Integer::sum);
        }
        return amounts;
    }

    public static String key(ItemStack unit) {
        try {
            byte[] serialized = ItemSerializer.serialize(List.of(unit));
            return Base64.getEncoder().encodeToString(serialized);
        } catch (Exception exception) {
            return Objects.toString(unit.getType(), "unknown") + ":" + String.valueOf(unit.getItemMeta());
        }
    }
}

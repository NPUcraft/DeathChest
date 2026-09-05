package com.npucraft.deathchest.util;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ItemSerializer {
    private ItemSerializer() {
    }

    public static byte[] serialize(List<ItemStack> items) {
        try {
            return ItemStack.serializeItemsAsBytes(ItemStacks.deepCopy(items));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize item snapshot", exception);
        }
    }

    public static List<ItemStack> deserialize(byte[] bytes) {
        List<ItemStack> items = new ArrayList<>();
        if (bytes == null || bytes.length == 0) {
            return items;
        }
        try {
            for (ItemStack item : ItemStack.deserializeItemsFromBytes(bytes)) {
                if (!ItemStacks.isEmpty(item)) {
                    items.add(item);
                }
            }
            return items;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize item snapshot", exception);
        }
    }
}

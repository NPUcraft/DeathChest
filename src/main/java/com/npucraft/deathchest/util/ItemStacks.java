package com.npucraft.deathchest.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ItemStacks {
    private ItemStacks() {
    }

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    public static ItemStack clone(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    public static List<ItemStack> deepCopy(Collection<ItemStack> items) {
        List<ItemStack> copy = new ArrayList<>();
        if (items == null) {
            return copy;
        }
        for (ItemStack item : items) {
            ItemStack cloned = clone(item);
            if (cloned != null) {
                copy.add(cloned);
            }
        }
        return copy;
    }

    public static List<ItemStack> fromArray(ItemStack[] contents) {
        List<ItemStack> items = new ArrayList<>();
        if (contents == null) {
            return items;
        }
        for (ItemStack item : contents) {
            ItemStack cloned = clone(item);
            if (cloned != null) {
                items.add(cloned);
            }
        }
        return items;
    }

    public static int stackCount(Collection<ItemStack> items) {
        int count = 0;
        if (items == null) {
            return 0;
        }
        for (ItemStack item : items) {
            if (!isEmpty(item)) {
                count++;
            }
        }
        return count;
    }

    public static int totalAmount(Collection<ItemStack> items) {
        int amount = 0;
        if (items == null) {
            return 0;
        }
        for (ItemStack item : items) {
            if (!isEmpty(item)) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    public static int occupiedSlots(PlayerInventory inventory) {
        int occupied = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (!isEmpty(item)) {
                occupied++;
            }
        }
        for (ItemStack item : inventory.getArmorContents()) {
            if (!isEmpty(item)) {
                occupied++;
            }
        }
        if (!isEmpty(inventory.getItemInOffHand())) {
            occupied++;
        }
        return occupied;
    }

    public static int emptyStorageSlots(PlayerInventory inventory) {
        int empty = 0;
        ItemStack[] storage = inventory.getStorageContents();
        for (ItemStack item : storage) {
            if (isEmpty(item)) {
                empty++;
            }
        }
        return empty;
    }

    public static boolean isInventoryEmpty(Inventory inventory) {
        if (inventory == null) {
            return true;
        }
        for (ItemStack item : inventory.getContents()) {
            if (!isEmpty(item)) {
                return false;
            }
        }
        return true;
    }

    public static ItemStack[] cloneArray(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = clone(source[i]);
        }
        return copy;
    }

    public static boolean similarEnough(ItemStack a, ItemStack b) {
        if (isEmpty(a) && isEmpty(b)) {
            return true;
        }
        if (isEmpty(a) || isEmpty(b)) {
            return false;
        }
        return a.isSimilar(b);
    }

    public static Material typeOrAir(ItemStack item) {
        return isEmpty(item) ? Material.AIR : item.getType();
    }
}

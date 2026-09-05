package com.npucraft.deathchest.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InventorySimulator {
    private InventorySimulator() {
    }

    public static boolean canFit(List<ItemStack> items, int size) {
        return leftover(items, size).isEmpty();
    }

    public static List<ItemStack> leftover(List<ItemStack> items, int size) {
        Inventory inventory = Bukkit.createInventory(null, size);
        ItemStack[] copies = ItemStacks.deepCopy(items).toArray(ItemStack[]::new);
        HashMap<Integer, ItemStack> leftover = inventory.addItem(copies);
        return new ArrayList<>(leftover.values());
    }

    public static FitResult splitToFit(List<ItemStack> items, int size) {
        Inventory inventory = Bukkit.createInventory(null, size);
        List<ItemStack> remaining = ItemStacks.deepCopy(items);
        List<ItemStack> fitted = new ArrayList<>();
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack item : remaining) {
            if (ItemStacks.isEmpty(item)) {
                continue;
            }
            Map<Integer, ItemStack> notAdded = inventory.addItem(item);
            if (notAdded.isEmpty()) {
                fitted.add(item.clone());
            } else {
                ItemStack leftoverStack = notAdded.values().iterator().next();
                int added = item.getAmount() - leftoverStack.getAmount();
                if (added > 0) {
                    ItemStack addedStack = item.clone();
                    addedStack.setAmount(added);
                    fitted.add(addedStack);
                }
                leftover.add(leftoverStack);
            }
        }
        return new FitResult(fromInventory(inventory), leftover);
    }

    public static List<ItemStack> fromInventory(Inventory inventory) {
        return ItemStacks.fromArray(inventory.getContents());
    }

    public record FitResult(List<ItemStack> fitted, List<ItemStack> leftover) {
        public boolean hasLeftover() {
            return leftover != null && !leftover.isEmpty();
        }
    }
}

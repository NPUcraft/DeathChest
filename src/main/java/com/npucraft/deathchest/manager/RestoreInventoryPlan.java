package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.util.EquipmentUtil;
import com.npucraft.deathchest.util.ItemStacks;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

final class RestoreInventoryPlan {
    private final ItemStack[] storage;
    private final ItemStack[] armor;
    private ItemStack offhand;
    private boolean fits = true;

    private RestoreInventoryPlan(ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {
        this.storage = storage;
        this.armor = armor;
        this.offhand = offhand;
    }

    static RestoreInventoryPlan incremental(PlayerInventory inventory, List<ItemStack> items) {
        return incremental(inventory.getStorageContents(), inventory.getArmorContents(),
                inventory.getItemInOffHand(), items);
    }

    static RestoreInventoryPlan incremental(ItemStack[] storage, ItemStack[] armor, ItemStack offhand,
                                            List<ItemStack> items) {
        RestoreInventoryPlan plan = new RestoreInventoryPlan(ItemStacks.cloneArray(storage),
                ItemStacks.cloneArray(armor), ItemStacks.clone(offhand));
        plan.addAll(items, false);
        return plan;
    }

    static RestoreInventoryPlan overwrite(PlayerInventory inventory, List<ItemStack> items) {
        return overwrite(inventory.getStorageContents().length, inventory.getArmorContents().length, items);
    }

    static RestoreInventoryPlan overwrite(int storageSize, int armorSize, List<ItemStack> items) {
        RestoreInventoryPlan plan = new RestoreInventoryPlan(
                new ItemStack[storageSize], new ItemStack[armorSize], null);
        plan.addAll(items, true);
        return plan;
    }

    boolean fits() {
        return fits;
    }

    void apply(PlayerInventory inventory, boolean overwrite) {
        if (!fits) {
            throw new IllegalStateException("Restore plan does not fit");
        }
        if (overwrite) {
            inventory.clear();
        }
        inventory.setStorageContents(ItemStacks.cloneArray(storage));
        if (overwrite) {
            inventory.setArmorContents(ItemStacks.cloneArray(armor));
            inventory.setItemInOffHand(ItemStacks.clone(offhand));
        }
    }

    private void addAll(List<ItemStack> items, boolean equip) {
        for (ItemStack source : items) {
            if (ItemStacks.isEmpty(source)) {
                continue;
            }
            ItemStack item = source.clone();
            if (equip && equip(item)) {
                continue;
            }
            if (!addToStorage(item)) {
                fits = false;
                return;
            }
        }
    }

    private boolean equip(ItemStack item) {
        if (item.getAmount() != 1) {
            return false;
        }
        EquipmentSlot slot = EquipmentUtil.equipmentSlot(item);
        if (slot == null) {
            return false;
        }
        if (slot == EquipmentSlot.OFF_HAND && ItemStacks.isEmpty(offhand)) {
            offhand = item;
            return true;
        }
        int index = switch (slot) {
            case FEET -> 0;
            case LEGS -> 1;
            case CHEST -> 2;
            case HEAD -> 3;
            default -> -1;
        };
        if (index >= 0 && index < armor.length && ItemStacks.isEmpty(armor[index])) {
            armor[index] = item;
            return true;
        }
        return false;
    }

    private boolean addToStorage(ItemStack item) {
        ItemStack remaining = item;
        for (ItemStack current : storage) {
            if (ItemStacks.isEmpty(current) || !current.isSimilar(remaining)) {
                continue;
            }
            int moved = Math.min(current.getMaxStackSize() - current.getAmount(), remaining.getAmount());
            if (moved <= 0) {
                continue;
            }
            current.setAmount(current.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() <= 0) {
                return true;
            }
        }
        for (int i = 0; i < storage.length; i++) {
            if (ItemStacks.isEmpty(storage[i])) {
                int moved = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
                ItemStack placed = remaining.clone();
                placed.setAmount(moved);
                storage[i] = placed;
                remaining.setAmount(remaining.getAmount() - moved);
                if (remaining.getAmount() <= 0) {
                    return true;
                }
            }
        }
        return false;
    }
}

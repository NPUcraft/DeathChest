package com.npucraft.deathchest.util;

import com.npucraft.deathchest.model.EquipmentMode;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class EquipmentUtil {
    private EquipmentUtil() {
    }

    public static EquipmentSlot equipmentSlot(ItemStack item) {
        if (ItemStacks.isEmpty(item)) {
            return null;
        }
        try {
            if (item.hasData(DataComponentTypes.EQUIPPABLE)) {
                Equippable equippable = item.getData(DataComponentTypes.EQUIPPABLE);
                if (equippable != null) {
                    return equippable.slot();
                }
            }
        } catch (Exception ignored) {
        }
        try {
            return item.getType().getEquipmentSlot();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean canAutoEquip(EquipmentSlot slot, boolean helmet, boolean chest, boolean legs, boolean boots, boolean offhand) {
        if (slot == null) {
            return false;
        }
        return switch (slot) {
            case HEAD -> helmet;
            case CHEST -> chest;
            case LEGS -> legs;
            case FEET -> boots;
            case OFF_HAND -> offhand;
            default -> false;
        };
    }

    public static boolean shouldReplace(EquipmentMode mode, ItemStack current, ItemStack candidate) {
        if (mode == EquipmentMode.ALWAYS_REPLACE) {
            return true;
        }
        return ItemStacks.isEmpty(current);
    }
}

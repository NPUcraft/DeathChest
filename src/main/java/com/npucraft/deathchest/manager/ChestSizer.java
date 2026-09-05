package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.model.ChestType;
import com.npucraft.deathchest.model.OverflowMode;
import com.npucraft.deathchest.model.SizingMode;
import com.npucraft.deathchest.util.InventorySimulator;
import com.npucraft.deathchest.util.ItemStacks;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ChestSizer {
    public List<SizedChest> plan(List<ItemStack> drops, SizingMode sizingMode, OverflowMode overflowMode) {
        List<ItemStack> remaining = ItemStacks.deepCopy(drops);
        List<SizedChest> planned = new ArrayList<>();
        if (remaining.isEmpty()) {
            return planned;
        }
        while (!remaining.isEmpty()) {
            ChestType type = chooseType(remaining, sizingMode, planned.isEmpty());
            InventorySimulator.FitResult fit = InventorySimulator.splitToFit(remaining, type.slots());
            if (fit.fitted().isEmpty()) {
                if (type == ChestType.SINGLE && sizingMode == SizingMode.AUTO) {
                    type = ChestType.DOUBLE;
                    fit = InventorySimulator.splitToFit(remaining, type.slots());
                }
                if (fit.fitted().isEmpty()) {
                    break;
                }
            }
            planned.add(new SizedChest(type, fit.fitted()));
            remaining = fit.leftover();
            if (sizingMode == SizingMode.SINGLE || sizingMode == SizingMode.DOUBLE) {
                if (overflowMode != OverflowMode.EXTRA_CHEST) {
                    break;
                }
            }
            if (overflowMode != OverflowMode.EXTRA_CHEST && planned.size() >= 1) {
                break;
            }
        }
        return planned;
    }

    public OverflowRemainder remainder(List<ItemStack> drops, List<SizedChest> planned) {
        List<ItemStack> remaining = ItemStacks.deepCopy(drops);
        for (SizedChest chest : planned) {
            InventorySimulator.FitResult fit = InventorySimulator.splitToFit(remaining, chest.type().slots());
            remaining = fit.leftover();
        }
        return new OverflowRemainder(remaining);
    }

    private ChestType chooseType(List<ItemStack> remaining, SizingMode sizingMode, boolean first) {
        if (sizingMode == SizingMode.DOUBLE) {
            return ChestType.DOUBLE;
        }
        if (sizingMode == SizingMode.SINGLE) {
            return ChestType.SINGLE;
        }
        if (InventorySimulator.canFit(remaining, 27)) {
            return ChestType.SINGLE;
        }
        return ChestType.DOUBLE;
    }

    public record SizedChest(ChestType type, List<ItemStack> items) {
    }

    public record OverflowRemainder(List<ItemStack> leftover) {
        public boolean isEmpty() {
            return leftover == null || leftover.isEmpty();
        }

        public boolean hasLeftover() {
            return !isEmpty();
        }
    }
}

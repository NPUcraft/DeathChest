package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.config.PluginSettings;
import com.npucraft.deathchest.model.InventoryPriceMode;
import com.npucraft.deathchest.model.RoundingMode;
import com.npucraft.deathchest.util.ItemStacks;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;

public final class DeathChestPriceCalculator {
    private final DeathChestPlugin plugin;

    public DeathChestPriceCalculator(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public double calculate(Player player, List<ItemStack> drops) {
        PluginSettings settings = plugin.settings();
        double price = settings.basePrice;
        if (settings.levelPriceEnabled) {
            price += player.getLevel() * settings.pricePerLevel;
        }
        if (settings.inventoryPriceEnabled && settings.inventoryPriceMode != InventoryPriceMode.DISABLED) {
            double units = switch (settings.inventoryPriceMode) {
                case EMPTY_SLOTS -> ItemStacks.emptyStorageSlots(player.getInventory());
                case ITEM_AMOUNT -> ItemStacks.totalAmount(drops);
                default -> ItemStacks.stackCount(drops);
            };
            price += units * settings.pricePerSlot;
        }
        price = round(price, settings.rounding);
        price = Math.max(settings.minPrice, Math.min(settings.maxPrice, price));
        return Math.max(0.0D, price);
    }

    static double round(double value, RoundingMode rounding) {
        return switch (rounding) {
            case FLOOR -> Math.floor(value);
            case CEIL -> Math.ceil(value);
            default -> BigDecimal.valueOf(value).setScale(0, java.math.RoundingMode.HALF_UP).doubleValue();
        };
    }
}

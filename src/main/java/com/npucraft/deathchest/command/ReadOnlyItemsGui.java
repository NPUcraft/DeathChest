package com.npucraft.deathchest.command;

import com.npucraft.deathchest.model.DeathRecord;
import com.npucraft.deathchest.util.ItemStacks;
import com.npucraft.deathchest.util.Texts;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ReadOnlyItemsGui implements InventoryHolder {
    private final Inventory inventory;
    private final String recordId;
    private final int page;
    private final int pages;

    public ReadOnlyItemsGui(DeathRecord record, int page, Component title, String prevLabel, String nextLabel,
                            String readonlyHint) {
        this.recordId = record.getRecordId();
        List<ItemStack> items = ItemStacks.deepCopy(record.getItems());
        this.pages = Math.max(1, (int) Math.ceil(items.size() / 45.0D));
        this.page = Math.max(1, Math.min(page, pages));
        this.inventory = Bukkit.createInventory(this, 54, title);
        int start = (this.page - 1) * 45;
        for (int i = 0; i < 45 && start + i < items.size(); i++) {
            inventory.setItem(i, items.get(start + i).clone());
        }
        if (this.page > 1) {
            inventory.setItem(45, nav(Material.ARROW, prevLabel));
        }
        inventory.setItem(49, nav(Material.BARRIER, readonlyHint));
        if (this.page < this.pages) {
            inventory.setItem(53, nav(Material.ARROW, nextLabel));
        }
    }

    private ItemStack nav(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Texts.mini(name == null ? "" : name));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public String recordId() {
        return recordId;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return pages;
    }
}

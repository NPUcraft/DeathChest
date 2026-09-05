package com.npucraft.deathchest.command;

import com.npucraft.deathchest.config.MessageManager;
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
    private final String ownerName;
    private final boolean adminControls;

    public ReadOnlyItemsGui(DeathRecord record, int page, Component title, MessageManager messages,
                            boolean adminControls) {
        this.recordId = record.getRecordId();
        this.ownerName = record.getPlayerName();
        this.adminControls = adminControls;
        List<ItemStack> items = ItemStacks.deepCopy(record.getItems());
        this.pages = Math.max(1, (int) Math.ceil(items.size() / 45.0D));
        this.page = Math.max(1, Math.min(page, pages));
        this.inventory = Bukkit.createInventory(this, 54, title);
        int start = (this.page - 1) * 45;
        for (int i = 0; i < 45 && start + i < items.size(); i++) {
            inventory.setItem(i, items.get(start + i).clone());
        }
        if (this.page > 1) {
            inventory.setItem(45, nav(Material.ARROW, messages.raw("gui-prev-page", "上一页")));
        }
        if (adminControls) {
            inventory.setItem(46, nav(Material.CHEST, messages.raw("gui-restore-item", "增量恢复物品")));
            inventory.setItem(47, nav(Material.EXPERIENCE_BOTTLE, messages.raw("gui-restore-exp", "恢复经验")));
            inventory.setItem(48, nav(Material.EMERALD, messages.raw("gui-restore-all", "增量恢复全部")));
            inventory.setItem(50, nav(Material.TNT, messages.raw("gui-force-item", "强制覆盖物品")));
            inventory.setItem(51, nav(Material.TNT, messages.raw("gui-force-exp", "强制恢复经验")));
            inventory.setItem(52, nav(Material.REDSTONE_BLOCK, messages.raw("gui-force-all", "强制覆盖全部")));
        }
        inventory.setItem(49, nav(Material.BARRIER, messages.raw("gui-readonly-hint", "只读预览，无法取出物品")));
        if (this.page < this.pages) {
            inventory.setItem(53, nav(Material.ARROW, messages.raw("gui-next-page", "下一页")));
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

    public String restoreCommand(int slot) {
        if (!adminControls) {
            return null;
        }
        String suffix = switch (slot) {
            case 46 -> " item";
            case 47 -> " exp";
            case 48 -> " all";
            case 50 -> " item --force";
            case 51 -> " exp --force";
            case 52 -> " all --force";
            default -> null;
        };
        return suffix == null ? null : "deathchest restore " + ownerName + " " + recordId + suffix;
    }
}

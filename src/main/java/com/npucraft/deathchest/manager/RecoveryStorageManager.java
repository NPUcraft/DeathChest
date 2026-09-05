package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.RecoveryEntry;
import com.npucraft.deathchest.util.Ids;
import com.npucraft.deathchest.util.ItemStacks;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public final class RecoveryStorageManager {
    private final DeathChestPlugin plugin;

    public RecoveryStorageManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean store(UUID player, String recordId, List<ItemStack> items) {
        if (!plugin.settings().recoveryEnabled || items == null || items.isEmpty()) {
            return false;
        }
        RecoveryEntry entry = new RecoveryEntry();
        entry.setId(Ids.recoveryId());
        entry.setPlayerUuid(player);
        entry.setRecordId(recordId);
        entry.setItems(ItemStacks.deepCopy(items));
        entry.setCreatedAt(System.currentTimeMillis());
        entry.setExpireAt(System.currentTimeMillis() + plugin.settings().recoveryExpireDays * 24L * 60L * 60L * 1000L);
        plugin.storage().saveRecovery(entry);
        plugin.audit().chest("写入恢复仓库", "玩家=" + player
                + " record=" + (recordId == null ? "-" : recordId)
                + " 物品栈=" + items.size()
                + " id=" + entry.getId());
        return true;
    }

    public List<ItemStack> takeItems(UUID player, String recordId) {
        List<ItemStack> taken = new ArrayList<>();
        if (player == null || recordId == null) {
            return taken;
        }
        for (RecoveryEntry entry : plugin.storage().loadRecovery(player)) {
            if (!recordId.equals(entry.getRecordId())) {
                continue;
            }
            taken.addAll(ItemStacks.deepCopy(entry.getItems()));
            plugin.storage().deleteRecovery(entry.getId());
        }
        return taken;
    }

    public boolean hasPending(UUID player) {
        return !activeEntries(player).isEmpty();
    }

    public RecoverResult recover(Player player) {
        if (player == null || !player.isOnline()) {
            return RecoverResult.empty();
        }
        List<RecoveryEntry> entries = activeEntries(player.getUniqueId());
        if (entries.isEmpty()) {
            return RecoverResult.empty();
        }
        int takenStacks = 0;
        int leftStacks = 0;
        PlayerInventory inventory = player.getInventory();
        for (RecoveryEntry entry : entries) {
            if (!player.isOnline()) {
                break;
            }
            List<ItemStack> remaining = new ArrayList<>();
            for (ItemStack item : ItemStacks.deepCopy(entry.getItems())) {
                if (ItemStacks.isEmpty(item)) {
                    continue;
                }
                if (!player.isOnline()) {
                    remaining.add(item);
                    continue;
                }
                HashMap<Integer, ItemStack> leftover = inventory.addItem(item);
                if (leftover.isEmpty()) {
                    takenStacks++;
                } else {
                    ItemStack left = leftover.values().iterator().next();
                    int added = item.getAmount() - left.getAmount();
                    if (added > 0) {
                        takenStacks++;
                    }
                    remaining.add(left);
                    leftStacks++;
                }
            }
            if (remaining.isEmpty()) {
                plugin.storage().deleteRecovery(entry.getId());
            } else {
                entry.setItems(remaining);
                plugin.storage().saveRecovery(entry);
            }
        }
        RecoverResult result = new RecoverResult(takenStacks, leftStacks);
        plugin.audit().player(player.getName(), "领取恢复仓库",
                "taken=" + takenStacks + " left=" + leftStacks);
        return result;
    }

    public void cleanupExpired() {
        plugin.storage().deleteExpiredRecovery(System.currentTimeMillis());
    }

    private List<RecoveryEntry> activeEntries(UUID player) {
        long now = System.currentTimeMillis();
        List<RecoveryEntry> active = new ArrayList<>();
        for (RecoveryEntry entry : plugin.storage().loadRecovery(player)) {
            if (entry.getExpireAt() > 0L && now >= entry.getExpireAt()) {
                plugin.storage().deleteRecovery(entry.getId());
                continue;
            }
            active.add(entry);
        }
        return active;
    }

    public record RecoverResult(int taken, int left) {
        public static RecoverResult empty() {
            return new RecoverResult(0, 0);
        }

        public boolean isEmpty() {
            return taken == 0 && left == 0;
        }
    }
}

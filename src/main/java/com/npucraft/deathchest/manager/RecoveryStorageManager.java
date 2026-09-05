package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.model.DeathRecord;
import com.npucraft.deathchest.model.RecoveryEntry;
import com.npucraft.deathchest.util.Ids;
import com.npucraft.deathchest.util.ItemMatcher;
import com.npucraft.deathchest.util.ItemStacks;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RecoveryStorageManager {
    private final DeathChestPlugin plugin;

    public RecoveryStorageManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean store(UUID player, String recordId, List<ItemStack> items) {
        return storeWithId(Ids.recoveryId(), player, recordId, items, false, false);
    }

    public boolean storeChestTransfer(DeathChestData chest, List<ItemStack> items) {
        if (chest == null) {
            return false;
        }
        return storeWithId(chestTransferId(chest.getId()), chest.getOwnerUuid(), chest.getRecordId(), items, true, false);
    }

    public boolean storeRestore(DeathRecord record, List<ItemStack> items) {
        if (record == null) {
            return false;
        }
        return storeWithId(restoreTransferId(record.getRecordId()), record.getPlayerUuid(), record.getRecordId(),
                items, false, true);
    }

    private boolean storeWithId(String id, UUID player, String recordId, List<ItemStack> items,
                                boolean allowEmpty, boolean neverExpire) {
        if (!plugin.settings().recoveryEnabled || items == null || (!allowEmpty && items.isEmpty())) {
            return false;
        }
        var existing = plugin.storage().loadRecovery(id);
        if (existing.isPresent()) {
            RecoveryEntry saved = existing.get();
            if (!Objects.equals(player, saved.getPlayerUuid()) || !Objects.equals(recordId, saved.getRecordId())) {
                throw new IllegalStateException("Recovery transfer ID collision: " + id);
            }
            if (!ItemMatcher.matches(saved.getItems(), items)) {
                throw new IllegalStateException("Recovery transfer content mismatch: " + id);
            }
            return true;
        }
        RecoveryEntry entry = new RecoveryEntry();
        entry.setId(id);
        entry.setPlayerUuid(player);
        entry.setRecordId(recordId);
        entry.setItems(ItemStacks.deepCopy(items));
        entry.setCreatedAt(System.currentTimeMillis());
        entry.setExpireAt(neverExpire ? 0L
                : System.currentTimeMillis() + plugin.settings().recoveryExpireDays * 24L * 60L * 60L * 1000L);
        plugin.storage().saveRecovery(entry);
        plugin.audit().chest("写入恢复仓库", "玩家=" + player
                + " record=" + (recordId == null ? "-" : recordId)
                + " 物品栈=" + items.size()
                + " id=" + entry.getId());
        return true;
    }

    public boolean hasChestTransfer(DeathChestData chest) {
        if (chest == null || chest.getId() == null) {
            return false;
        }
        return plugin.storage().loadRecovery(chestTransferId(chest.getId()))
                .filter(entry -> Objects.equals(chest.getOwnerUuid(), entry.getPlayerUuid()))
                .filter(entry -> Objects.equals(chest.getRecordId(), entry.getRecordId()))
                .isPresent();
    }

    public boolean hasRestoreTransfer(DeathRecord record) {
        return record != null && record.getRecordId() != null
                && plugin.storage().loadRecovery(restoreTransferId(record.getRecordId())).isPresent();
    }

    public boolean hasPendingForRecord(DeathRecord record) {
        return record != null && record.getRecordId() != null
                && plugin.storage().pendingRecoveryRecordIds().contains(record.getRecordId());
    }

    public void deleteRecordEntries(DeathRecord record) {
        if (record == null || record.getPlayerUuid() == null || record.getRecordId() == null) {
            return;
        }
        String restoreId = restoreTransferId(record.getRecordId());
        List<RecoveryEntry> entries = plugin.storage().loadRecovery(record.getPlayerUuid()).stream()
                .filter(entry -> record.getRecordId().equals(entry.getRecordId()))
                .toList();
        for (RecoveryEntry entry : entries) {
            if (!restoreId.equals(entry.getId())) {
                plugin.storage().deleteRecovery(entry.getId());
            }
        }
        if (entries.stream().anyMatch(entry -> restoreId.equals(entry.getId()))) {
            plugin.storage().deleteRecovery(restoreId);
        }
    }

    public static String chestTransferId(String chestId) {
        return "CX-" + chestId;
    }

    public static String restoreTransferId(String recordId) {
        return "RX-" + recordId;
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
            ItemStack[] inventoryBefore = ItemStacks.cloneArray(inventory.getStorageContents());
            List<ItemStack> remaining = new ArrayList<>();
            int entryTakenStacks = 0;
            int entryLeftStacks = 0;
            for (ItemStack item : ItemStacks.deepCopy(entry.getItems())) {
                if (ItemStacks.isEmpty(item)) {
                    continue;
                }
                if (!player.isOnline()) {
                    remaining.add(item);
                    entryLeftStacks++;
                    continue;
                }
                int originalAmount = item.getAmount();
                HashMap<Integer, ItemStack> leftover = inventory.addItem(item);
                if (leftover.isEmpty()) {
                    entryTakenStacks++;
                } else {
                    int leftoverAmount = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                    int added = originalAmount - leftoverAmount;
                    if (added > 0) {
                        entryTakenStacks++;
                    }
                    remaining.addAll(ItemStacks.deepCopy(leftover.values()));
                    entryLeftStacks += leftover.size();
                }
            }
            try {
                if (remaining.isEmpty()) {
                    plugin.storage().deleteRecovery(entry.getId());
                } else {
                    entry.setItems(remaining);
                    plugin.storage().saveRecovery(entry);
                }
            } catch (RuntimeException exception) {
                inventory.setStorageContents(inventoryBefore);
                plugin.getLogger().severe("领取恢复仓库时数据库写入失败，已回滚玩家背包：player="
                        + player.getUniqueId() + " recovery=" + entry.getId() + " error=" + exception.getMessage());
                break;
            }
            takenStacks += entryTakenStacks;
            leftStacks += entryLeftStacks;
        }
        try {
            leftStacks = activeEntries(player.getUniqueId()).stream()
                    .mapToInt(entry -> ItemStacks.stackCount(entry.getItems()))
                    .sum();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("无法重新统计恢复仓库剩余物品：player=" + player.getUniqueId()
                    + " error=" + exception.getMessage());
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
            if (entry.getId() != null && entry.getId().startsWith("RX-")) {
                continue;
            }
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

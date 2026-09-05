package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.AuditEventType;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.model.EquipmentMode;
import com.npucraft.deathchest.util.EquipmentUtil;
import com.npucraft.deathchest.util.ItemStacks;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

public final class QuickRetrieveManager {
    private final DeathChestPlugin plugin;

    public QuickRetrieveManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean canRetrieve(Player player, DeathChestData chest) {
        if (chest.isLocked()) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean owner = chest.getOwnerUuid().equals(player.getUniqueId());
        boolean bypass = player.hasPermission("deathchest.bypass")
                || (plugin.settings().retrieveAdminBypass && player.hasPermission("deathchest.retrieve.bypass"));
        if (chest.isProtected(now) && !owner && !bypass) {
            return false;
        }
        if (bypass && player.hasPermission("deathchest.retrieve.bypass") && plugin.settings().retrieveAdminBypass) {
            return true;
        }
        if (!plugin.settings().retrieveOwnerOnly) {
            return player.hasPermission("deathchest.retrieve");
        }
        return owner && player.hasPermission("deathchest.retrieve");
    }

    public RetrieveResult retrieve(Player player, DeathChestData chest) {
        if (!player.isOnline()) {
            return RetrieveResult.missing();
        }
        ItemStack[] originalChest = plugin.chests().contentsOf(chest);
        if (originalChest == null) {
            return RetrieveResult.missing();
        }
        originalChest = ItemStacks.cloneArray(originalChest);
        ItemStack[] chestContents = ItemStacks.cloneArray(originalChest);
        if (ItemStacks.fromArray(chestContents).isEmpty()) {
            return RetrieveResult.empty();
        }

        PlayerInventory playerInventory = player.getInventory();
        ItemStack[] storage = ItemStacks.cloneArray(playerInventory.getStorageContents());
        ItemStack[] armor = ItemStacks.cloneArray(playerInventory.getArmorContents());
        ItemStack[] offhand = new ItemStack[]{ItemStacks.clone(playerInventory.getItemInOffHand())};
        List<ItemStack> takenItems = new ArrayList<>();

        int taken = 0;
        int left = 0;
        for (int i = 0; i < chestContents.length; i++) {
            ItemStack item = chestContents[i];
            if (ItemStacks.isEmpty(item)) {
                continue;
            }
            if (tryEquip(item, armor, offhand, storage)) {
                takenItems.add(item.clone());
                chestContents[i] = null;
                taken++;
                continue;
            }
            ItemStack leftover = addToStorage(storage, item.clone());
            if (leftover == null) {
                takenItems.add(item.clone());
                chestContents[i] = null;
                taken++;
            } else if (leftover.getAmount() < item.getAmount()) {
                ItemStack moved = item.clone();
                moved.setAmount(item.getAmount() - leftover.getAmount());
                takenItems.add(moved);
                chestContents[i] = leftover;
                taken++;
                left++;
            } else {
                chestContents[i] = leftover;
                left++;
            }
        }

        plugin.chests().setLocked(chest, true);
        try {
            plugin.chests().setContents(chest, chestContents);
            if (!player.isOnline()) {
                if (!plugin.recovery().store(player.getUniqueId(), chest.getRecordId(), takenItems)) {
                    plugin.chests().setContents(chest, originalChest);
                    throw new IllegalStateException("Player disconnected and retrieved items could not be persisted");
                }
                if (ItemStacks.fromArray(chestContents).isEmpty()) {
                    plugin.chests().scheduleEmptyCheck(chest);
                }
                return new RetrieveResult(taken, left, false, false);
            }
            playerInventory.setStorageContents(storage);
            playerInventory.setArmorContents(armor);
            playerInventory.setItemInOffHand(offhand[0]);
        } catch (RuntimeException exception) {
            plugin.chests().setContents(chest, originalChest);
            throw exception;
        } finally {
            plugin.chests().setLocked(chest, false);
        }
        plugin.audit().log(AuditEventType.QUICK_RETRIEVE, player.getUniqueId(), player.getName(), chest.getOwnerUuid(),
                chest.getOwnerName(), chest.getId(), chest.getRecordId(), "taken=" + taken + " left=" + left, false);
        if (ItemStacks.fromArray(chestContents).isEmpty()) {
            plugin.chests().scheduleEmptyCheck(chest);
        }
        return new RetrieveResult(taken, left, false, false);
    }

    private boolean tryEquip(ItemStack item, ItemStack[] armor, ItemStack[] offhand, ItemStack[] storage) {
        if (!plugin.settings().equipmentEnabled) {
            return false;
        }
        EquipmentSlot slot = EquipmentUtil.equipmentSlot(item);
        if (!EquipmentUtil.canAutoEquip(slot, plugin.settings().autoEquipHelmet, plugin.settings().autoEquipChestplate,
                plugin.settings().autoEquipLeggings, plugin.settings().autoEquipBoots, plugin.settings().autoEquipOffhand)) {
            return false;
        }
        EquipmentMode mode = plugin.settings().equipmentMode;
        if (slot == EquipmentSlot.OFF_HAND) {
            if (EquipmentUtil.shouldReplace(mode, offhand[0], item) && stow(storage, offhand[0])) {
                offhand[0] = item.clone();
                return true;
            }
            return false;
        }
        int index = armorIndex(slot);
        if (index < 0) {
            return false;
        }
        if (EquipmentUtil.shouldReplace(mode, armor[index], item) && stow(storage, armor[index])) {
            armor[index] = item.clone();
            return true;
        }
        return false;
    }

    private boolean stow(ItemStack[] storage, ItemStack current) {
        if (ItemStacks.isEmpty(current)) {
            return true;
        }
        return addToStorage(storage, current.clone()) == null;
    }

    private int armorIndex(EquipmentSlot slot) {
        return switch (slot) {
            case FEET -> 0;
            case LEGS -> 1;
            case CHEST -> 2;
            case HEAD -> 3;
            default -> -1;
        };
    }

    private ItemStack addToStorage(ItemStack[] storage, ItemStack item) {
        ItemStack remaining = item.clone();
        if (plugin.settings().mergeExistingStacksFirst) {
            for (int i = 0; i < storage.length && remaining != null; i++) {
                ItemStack current = storage[i];
                if (ItemStacks.isEmpty(current) || !current.isSimilar(remaining)) {
                    continue;
                }
                int space = current.getMaxStackSize() - current.getAmount();
                if (space <= 0) {
                    continue;
                }
                int move = Math.min(space, remaining.getAmount());
                current.setAmount(current.getAmount() + move);
                remaining.setAmount(remaining.getAmount() - move);
                if (remaining.getAmount() <= 0) {
                    remaining = null;
                }
            }
        }
        if (remaining == null) {
            return null;
        }
        for (int i = 0; i < storage.length; i++) {
            if (ItemStacks.isEmpty(storage[i])) {
                storage[i] = remaining;
                return null;
            }
        }
        return remaining;
    }

    public record RetrieveResult(int taken, int left, boolean emptyChest, boolean missingChest) {
        public static RetrieveResult empty() {
            return new RetrieveResult(0, 0, true, false);
        }

        public static RetrieveResult missing() {
            return new RetrieveResult(0, 0, false, true);
        }

        public boolean isEmpty() {
            return emptyChest;
        }

        public boolean isMissing() {
            return missingChest;
        }
    }
}

package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.AuditEventType;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.model.DeathRecord;
import com.npucraft.deathchest.model.RecordStatus;
import com.npucraft.deathchest.model.RestorePart;
import com.npucraft.deathchest.util.ExperienceUtil;
import com.npucraft.deathchest.util.ItemMatcher;
import com.npucraft.deathchest.util.ItemStacks;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class RollbackManager {
    private final DeathChestPlugin plugin;

    public RollbackManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public int reconcileInterruptedRestores() {
        int reconciled = 0;
        for (DeathRecord record : plugin.storage().loadInterruptedRecords()) {
            try {
                boolean stagedItems = plugin.recovery().hasRestoreTransfer(record);
                for (DeathChestData chest : List.copyOf(plugin.chests().byRecord(record.getRecordId()))) {
                    if (stagedItems) {
                        plugin.chests().destroySilently(chest);
                    } else {
                        plugin.chests().setLocked(chest, false);
                    }
                }
                if (stagedItems) {
                    record.setItemsRestored(true);
                }
                record.setRollbackInProgress(false);
                updateRecordStatus(record, false);
                plugin.records().save(record);
                plugin.getLogger().warning("Interrupted restore was quarantined safely: " + record.getRecordId()
                        + ". Inspect the target inventory and use --force to resolve it.");
                reconciled++;
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not reconcile interrupted restore " + record.getRecordId()
                                + "; its chest remains locked and staged items remain quarantined.", exception);
            }
        }
        return reconciled;
    }

    public RestoreOutcome restore(CommandSender sender, Player target, DeathRecord record,
                                  RestorePart part, boolean force) {
        if (!plugin.settings().rollbackEnabled) {
            return RestoreOutcome.fail("restore-disabled");
        }
        if (force && !plugin.settings().allowForce) {
            return RestoreOutcome.fail("restore-force-disabled");
        }
        if (!target.isOnline()) {
            return RestoreOutcome.fail("restore-target-offline");
        }
        if (!target.getUniqueId().equals(record.getPlayerUuid())) {
            return RestoreOutcome.fail("restore-player-mismatch");
        }

        boolean restoreItems = plugin.settings().restoreItems
                && (part == RestorePart.ALL || part == RestorePart.ITEMS)
                && (force || !record.isItemsRestored());
        boolean restoreExp = plugin.settings().restoreExperience
                && (part == RestorePart.ALL || part == RestorePart.EXP)
                && (force || !record.isExperienceRestored());
        if (!restoreItems && !restoreExp) {
            return RestoreOutcome.fail("restore-already");
        }
        if (restoreItems && record.getItems().isEmpty()) {
            return RestoreOutcome.fail("restore-no-snapshot");
        }
        if (plugin.recovery().hasRestoreTransfer(record) && (!force || !restoreItems)) {
            return RestoreOutcome.fail("restore-pending-recovery");
        }
        if (restoreItems && !plugin.settings().recoveryEnabled) {
            return RestoreOutcome.fail("restore-recovery-required");
        }

        RestoreInventoryPlan inventoryPlan = null;
        ItemStack[] storageBefore = null;
        ItemStack[] armorBefore = null;
        ItemStack offhandBefore = null;
        if (restoreItems) {
            target.closeInventory();
            storageBefore = ItemStacks.cloneArray(target.getInventory().getStorageContents());
            armorBefore = ItemStacks.cloneArray(target.getInventory().getArmorContents());
            offhandBefore = ItemStacks.clone(target.getInventory().getItemInOffHand());
            inventoryPlan = force
                    ? RestoreInventoryPlan.overwrite(target.getInventory(), record.getItems())
                    : RestoreInventoryPlan.incremental(target.getInventory(), record.getItems());
            if (!inventoryPlan.fits()) {
                return RestoreOutcome.fail(force ? "restore-snapshot-too-large" : "restore-inventory-full");
            }
        }

        UUID actor = sender instanceof Player player ? player.getUniqueId() : null;
        List<DeathChestData> locked = new ArrayList<>();
        int experienceBefore = ExperienceUtil.totalExperience(target);
        boolean inventoryMutationStarted = false;
        boolean experienceMutationStarted = false;
        boolean deliveryCommitted = false;
        try {
            for (DeathChestData chest : plugin.chests().byRecord(record.getRecordId())) {
                plugin.chests().setLocked(chest, true);
                locked.add(chest);
            }
            if (restoreItems && !force) {
                if (!record.isDeathChestCreated() || locked.isEmpty()) {
                    return RestoreOutcome.fail("restore-normal-unavailable");
                }
                if (plugin.recovery().hasPendingForRecord(record)) {
                    return RestoreOutcome.fail("restore-pending-recovery");
                }
                List<ItemStack> current = new ArrayList<>();
                for (DeathChestData chest : locked) {
                    if (!plugin.chests().worldLoaded(chest) || !plugin.chests().chunkLoaded(chest)
                            || !plugin.chests().existsInWorld(chest)) {
                        return RestoreOutcome.fail("restore-normal-unavailable");
                    }
                    current.addAll(plugin.chests().currentItems(chest));
                }
                if (!ItemMatcher.matches(record.getItems(), current)) {
                    return RestoreOutcome.fail("restore-normal-mismatch");
                }
            }

            record.setRollbackInProgress(true);
            plugin.records().save(record);

            if (restoreItems) {
                if (!plugin.recovery().storeRestore(record, record.getItems())) {
                    throw new IllegalStateException("Could not stage restore snapshot " + record.getRecordId());
                }
                for (DeathChestData chest : List.copyOf(locked)) {
                    plugin.chests().destroySilently(chest);
                }
                locked.clear();
                inventoryMutationStarted = true;
                inventoryPlan.apply(target.getInventory(), force);
                target.updateInventory();
            }
            if (restoreExp) {
                experienceMutationStarted = true;
                ExperienceUtil.applyToPlayer(target, record.getTotalExperienceBefore());
            }

            if (restoreItems) {
                record.setItemsRestored(true);
            }
            if (restoreExp) {
                record.setExperienceRestored(true);
            }
            updateRecordStatus(record, force);
            plugin.records().save(record);
            if (restoreItems) {
                plugin.recovery().deleteRecordEntries(record);
            }
            deliveryCommitted = true;
            record.setRollbackInProgress(false);
            plugin.records().save(record);
            String auditParts = restoreItems && restoreExp ? "items,exp" : restoreItems ? "items" : "exp";
            String parts = restoreItems && restoreExp ? "物品和经验" : restoreItems ? "物品" : "经验";
            plugin.audit().log(force ? AuditEventType.ADMIN_FORCE_RESTORE : AuditEventType.ADMIN_RESTORE,
                    actor, sender.getName(), target.getUniqueId(), target.getName(), record.getDeathChestId(),
                    record.getRecordId(), "parts=" + auditParts + " overwrite=" + (force && restoreItems), force);
            if (force && restoreItems) {
                plugin.getLogger().log(Level.WARNING,
                        "Force restore overwrote inventory: admin={0} player={1} record={2}",
                        new Object[]{sender.getName(), target.getName(), record.getRecordId()});
            }
            String messageKey = force && restoreItems ? "restore-force-success" : "restore-success";
            return new RestoreOutcome(true, messageKey, parts);
        } catch (RuntimeException exception) {
            if (!deliveryCommitted) {
                if (inventoryMutationStarted) {
                    restoreInventory(target, storageBefore, armorBefore, offhandBefore);
                }
                if (experienceMutationStarted) {
                    ExperienceUtil.applyToPlayer(target, experienceBefore);
                }
            }
            plugin.getLogger().log(Level.SEVERE, "Restore failed for " + record.getRecordId(), exception);
            return RestoreOutcome.fail("restore-failed");
        } finally {
            boolean quarantined;
            try {
                quarantined = plugin.recovery().hasRestoreTransfer(record);
            } catch (RuntimeException exception) {
                quarantined = true;
                plugin.getLogger().log(Level.SEVERE,
                        "Could not verify restore staging; keeping associated chests locked: " + record.getRecordId(),
                        exception);
            }
            if (!quarantined) {
                for (DeathChestData chest : locked) {
                    try {
                        plugin.chests().setLocked(chest, false);
                    } catch (RuntimeException exception) {
                        plugin.getLogger().warning("Failed to unlock death chest after restore: " + chest.getId());
                    }
                }
            }
        }
    }

    private void restoreInventory(Player target, ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {
        try {
            target.getInventory().setStorageContents(ItemStacks.cloneArray(storage));
            target.getInventory().setArmorContents(ItemStacks.cloneArray(armor));
            target.getInventory().setItemInOffHand(ItemStacks.clone(offhand));
            target.updateInventory();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not roll back target inventory after failed restore: " + target.getUniqueId(), exception);
        }
    }

    private void updateRecordStatus(DeathRecord record, boolean force) {
        if (record.isItemsRestored() && record.isExperienceRestored()) {
            record.setStatus(force ? RecordStatus.ADMIN_RESTORED : RecordStatus.ROLLED_BACK);
        } else if (record.isItemsRestored() || record.isExperienceRestored()) {
            record.setStatus(RecordStatus.PARTIALLY_RESTORED);
        }
    }

    public record RestoreOutcome(boolean success, String messageKey, String parts) {
        public static RestoreOutcome fail(String key) {
            return new RestoreOutcome(false, key, "");
        }
    }
}

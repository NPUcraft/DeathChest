package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.AuditEventType;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.model.DeathRecord;
import com.npucraft.deathchest.model.RecordStatus;
import com.npucraft.deathchest.model.RestorePart;
import com.npucraft.deathchest.util.ExperienceUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
            for (DeathChestData chest : plugin.chests().byRecord(record.getRecordId())) {
                plugin.chests().setLocked(chest, false);
            }
            record.setRollbackInProgress(false);
            updateRecordStatus(record, false);
            plugin.records().save(record);
            plugin.getLogger().warning("Interrupted restore was closed conservatively: " + record.getRecordId()
                    + ". Use --force after checking the target inventory if another restore is required.");
            reconciled++;
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

        RestoreInventoryPlan inventoryPlan = null;
        if (restoreItems) {
            target.closeInventory();
            inventoryPlan = force
                    ? RestoreInventoryPlan.overwrite(target.getInventory(), record.getItems())
                    : RestoreInventoryPlan.incremental(target.getInventory(), record.getItems());
            if (!inventoryPlan.fits()) {
                return RestoreOutcome.fail(force ? "restore-snapshot-too-large" : "restore-inventory-full");
            }
        }

        UUID actor = sender instanceof Player player ? player.getUniqueId() : null;
        List<DeathChestData> locked = new ArrayList<>();
        try {
            for (DeathChestData chest : plugin.chests().byRecord(record.getRecordId())) {
                plugin.chests().setLocked(chest, true);
                locked.add(chest);
            }

            record.setRollbackInProgress(true);
            if (restoreItems) {
                record.setItemsRestored(true);
            }
            if (restoreExp) {
                record.setExperienceRestored(true);
            }
            plugin.records().save(record);

            if (restoreItems) {
                for (DeathChestData chest : List.copyOf(locked)) {
                    plugin.chests().destroySilently(chest);
                }
                locked.clear();
                inventoryPlan.apply(target.getInventory(), force);
                target.updateInventory();
            }
            if (restoreExp) {
                ExperienceUtil.applyToPlayer(target, record.getTotalExperienceBefore());
            }

            record.setRollbackInProgress(false);
            updateRecordStatus(record, force);
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
            plugin.getLogger().log(Level.SEVERE, "Restore failed for " + record.getRecordId(), exception);
            return RestoreOutcome.fail("restore-failed");
        } finally {
            for (DeathChestData chest : locked) {
                try {
                    plugin.chests().setLocked(chest, false);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Failed to unlock death chest after restore: " + chest.getId());
                }
            }
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

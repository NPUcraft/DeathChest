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
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RollbackManager {
    private final DeathChestPlugin plugin;

    public RollbackManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public int reconcileInterruptedRestores() {
        int reconciled = 0;
        for (DeathRecord record : plugin.storage().loadInterruptedRecords()) {
            reconcileInterrupted(record, false);
            reconciled++;
        }
        return reconciled;
    }

    public RestoreOutcome restore(CommandSender sender, DeathRecord record, RestorePart part, boolean force) {
        if (!plugin.settings().rollbackEnabled) {
            return RestoreOutcome.fail("restore-disabled");
        }
        if (force && !plugin.settings().allowForce) {
            return RestoreOutcome.fail("restore-force-disabled");
        }
        try {
            reconcileInterrupted(record, force);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Failed to reconcile interrupted restore " + record.getRecordId() + ": "
                    + exception.getMessage());
            return RestoreOutcome.fail("restore-failed");
        }

        boolean restoreItems = shouldRestoreItems(part) && !record.isItemsRestored();
        boolean restoreExp = shouldRestoreExp(part) && !record.isExperienceRestored();
        if (!restoreItems && !restoreExp) {
            return RestoreOutcome.fail("restore-already");
        }

        UUID actor = sender instanceof Player player ? player.getUniqueId() : null;
        String actorName = sender.getName();
        List<DeathChestData> locked = new ArrayList<>();
        boolean expSkippedOffline = false;
        boolean usedRecovery = false;
        boolean itemsRestoredNow = false;
        boolean expRestoredNow = false;
        List<String> parts = new ArrayList<>();

        try {
            boolean recoveryPending = hasPendingRecovery(record);
            List<DeathChestData> chests = plugin.chests().byRecord(record.getRecordId());
            RestoreItemPlan.Decision decision = RestoreItemPlan.decide(
                    restoreItems, force, record.isDeathChestCreated(), !chests.isEmpty(), recoveryPending,
                    record.getItems().isEmpty(), record.getFailureReason());
            if (decision.refuse()) {
                return RestoreOutcome.fail(decision.refuseKey());
            }

            if (restoreItems && (!plugin.settings().useRecoveryStorage || !plugin.settings().recoveryEnabled)) {
                plugin.getLogger().severe("Admin item restore requires rollback.use-recovery-storage=true and "
                        + "recovery-storage.enabled=true for crash safety.");
                return RestoreOutcome.fail("restore-recovery-required");
            }

            if (restoreItems && !chests.isEmpty()) {
                for (DeathChestData chest : chests) {
                    plugin.chests().setLocked(chest, true);
                    locked.add(chest);
                }
                if (!force && !contentsMatchAll(record, chests)) {
                    return RestoreOutcome.fail("restore-safe-mismatch");
                }
            }

            Player experienceTarget = restoreExp ? Bukkit.getPlayer(record.getPlayerUuid()) : null;
            if (restoreExp && experienceTarget == null && part == RestorePart.EXP) {
                return RestoreOutcome.fail("restore-player-offline-exp");
            }

            record.setRollbackInProgress(true);
            plugin.records().save(record);

            if (restoreItems) {
                if (decision.useRecovery()) {
                    usedRecovery = true;
                }
                if (!chests.isEmpty() && (decision.useChestContents() || decision.useSnapshot())) {
                    for (DeathChestData chest : List.copyOf(chests)) {
                        List<ItemStack> contents = plugin.chests().currentItems(chest);
                        if (!plugin.recovery().storeChestTransfer(chest, contents)) {
                            throw new IllegalStateException("Could not persist chest transfer " + chest.getId());
                        }
                        plugin.chests().destroySilently(chest);
                    }
                    locked.clear();
                    usedRecovery = true;
                    itemsRestoredNow = true;
                } else if (decision.useSnapshot()) {
                    if (!plugin.recovery().storeRestore(record, ItemStacks.deepCopy(record.getItems()))) {
                        throw new IllegalStateException("Could not persist restore snapshot " + record.getRecordId());
                    }
                    usedRecovery = true;
                    itemsRestoredNow = true;
                } else if (decision.useRecovery()) {
                    itemsRestoredNow = true;
                }
                if (itemsRestoredNow) {
                    record.setItemsRestored(true);
                    parts.add("items");
                }
            }

            if (restoreExp) {
                if (experienceTarget == null) {
                    expSkippedOffline = true;
                } else {
                    record.setExperienceRestored(true);
                    expRestoredNow = true;
                    parts.add("exp");
                }
            }

            if (!itemsRestoredNow && !expRestoredNow && !expSkippedOffline) {
                return RestoreOutcome.fail("restore-failed");
            }

            updateRecordStatus(record, force);
            record.setRollbackInProgress(false);
            plugin.records().save(record);

            if (experienceTarget != null && expRestoredNow) {
                ExperienceUtil.applyToPlayer(experienceTarget, record.getTotalExperienceBefore());
            }

            plugin.audit().log(force ? AuditEventType.ADMIN_FORCE_RESTORE : AuditEventType.ADMIN_RESTORE,
                    actor, actorName, record.getPlayerUuid(), record.getPlayerName(), record.getDeathChestId(),
                    record.getRecordId(), "parts=" + String.join(",", parts), force);

            if (usedRecovery) {
                Player online = Bukkit.getPlayer(record.getPlayerUuid());
                if (online != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.recovery().recover(online));
                }
            }
            String messageKey = usedRecovery && itemsRestoredNow ? "restore-partial" : "restore-success";
            return new RestoreOutcome(true, messageKey, String.join(", ", parts), usedRecovery,
                    expRestoredNow, itemsRestoredNow, expSkippedOffline);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Restore failed for " + record.getRecordId() + ": " + exception.getMessage());
            exception.printStackTrace();
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

    private void reconcileInterrupted(DeathRecord record, boolean force) {
        if (!record.isRollbackInProgress()) {
            return;
        }
        if (plugin.recovery().hasRestoreArtifacts(record)) {
            for (DeathChestData chest : List.copyOf(plugin.chests().byRecord(record.getRecordId()))) {
                List<ItemStack> contents = plugin.chests().currentItems(chest);
                if (!plugin.recovery().storeChestTransfer(chest, contents)) {
                    throw new IllegalStateException("Could not resume chest transfer " + chest.getId());
                }
                plugin.chests().destroySilently(chest);
            }
            record.setItemsRestored(true);
        }
        updateRecordStatus(record, force);
        record.setRollbackInProgress(false);
        plugin.records().save(record);
        plugin.getLogger().warning("Reconciled interrupted admin restore: " + record.getRecordId());
    }

    private boolean hasPendingRecovery(DeathRecord record) {
        return record.getRecordId() != null
                && plugin.storage().pendingRecoveryRecordIds().contains(record.getRecordId());
    }

    private boolean contentsMatchAll(DeathRecord record, List<DeathChestData> chests) {
        List<ItemStack> combined = new ArrayList<>();
        for (DeathChestData chest : chests) {
            combined.addAll(plugin.chests().currentItems(chest));
        }
        return ItemMatcher.matches(record.getItems(), combined);
    }

    private void updateRecordStatus(DeathRecord record, boolean force) {
        if (record.isItemsRestored() && record.isExperienceRestored()) {
            record.setStatus(force ? RecordStatus.ADMIN_RESTORED : RecordStatus.ROLLED_BACK);
        } else if (record.isItemsRestored() || record.isExperienceRestored()) {
            record.setStatus(RecordStatus.PARTIALLY_RESTORED);
        }
    }

    private boolean shouldRestoreItems(RestorePart part) {
        return plugin.settings().restoreItems && (part == RestorePart.ALL || part == RestorePart.ITEMS);
    }

    private boolean shouldRestoreExp(RestorePart part) {
        return plugin.settings().restoreExperience && (part == RestorePart.ALL || part == RestorePart.EXP);
    }

    public record RestoreOutcome(boolean success, String messageKey, String parts, boolean usedRecovery,
                                 boolean exp, boolean items, boolean expSkippedOffline) {
        public static RestoreOutcome fail(String key) {
            return new RestoreOutcome(false, key, "", false, false, false, false);
        }
    }
}

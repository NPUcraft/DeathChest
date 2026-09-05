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
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public final class RollbackManager {
    private final DeathChestPlugin plugin;

    public RollbackManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public RestoreOutcome restore(CommandSender sender, DeathRecord record, RestorePart part, boolean force) {
        if (!plugin.settings().rollbackEnabled) {
            return RestoreOutcome.fail("restore-disabled");
        }
        if (force && !plugin.settings().allowForce) {
            return RestoreOutcome.fail("restore-force-disabled");
        }
        if (alreadyRestored(record) && !force) {
            return RestoreOutcome.fail("restore-already");
        }
        UUID actor = sender instanceof Player player ? player.getUniqueId() : null;
        String actorName = sender.getName();
        record.setRollbackInProgress(true);
        plugin.records().save(record);
        List<DeathChestData> locked = new ArrayList<>();
        try {
            boolean recoveryPending = hasPendingRecovery(record);
            List<DeathChestData> chests = plugin.chests().byRecord(record.getRecordId());
            RestoreItemPlan.Decision decision = RestoreItemPlan.decide(
                    shouldRestoreItems(part),
                    force,
                    record.isDeathChestCreated(),
                    !chests.isEmpty(),
                    recoveryPending,
                    record.getItems().isEmpty(),
                    record.getFailureReason()
            );
            if (decision.refuse()) {
                return RestoreOutcome.fail(decision.refuseKey());
            }

            if (decision.useSnapshot() && !force && shouldRestoreItems(part)) {
                for (DeathChestData chest : chests) {
                    plugin.chests().setLocked(chest, true);
                    locked.add(chest);
                    List<ItemStack> current = plugin.chests().currentItems(chest);
                    if (!ItemMatcher.matches(record.getItems(), current) && chests.size() == 1) {
                        return RestoreOutcome.fail("restore-safe-mismatch");
                    }
                }
                if (!chests.isEmpty() && !contentsMatchAll(record, chests)) {
                    return RestoreOutcome.fail("restore-safe-mismatch");
                }
            }

            boolean itemsRestored = false;
            boolean expRestored = false;
            boolean usedRecovery = false;
            boolean expSkippedOffline = false;
            List<String> parts = new ArrayList<>();
            List<ItemStack> toGive = new ArrayList<>();

            if (shouldRestoreItems(part) && (decision.useRecovery() || decision.useChestContents() || decision.useSnapshot())) {
                if (decision.useRecovery()) {
                    Player online = Bukkit.getPlayer(record.getPlayerUuid());
                    if (online == null && !decision.useChestContents() && !decision.useSnapshot()) {
                        usedRecovery = true;
                        itemsRestored = true;
                    } else {
                        toGive.addAll(plugin.recovery().takeItems(record.getPlayerUuid(), record.getRecordId()));
                    }
                }
                if (decision.useChestContents()) {
                    for (DeathChestData chest : List.copyOf(chests)) {
                        toGive.addAll(plugin.chests().currentItems(chest));
                        plugin.chests().destroySilently(chest);
                    }
                    locked.clear();
                }
                if (decision.useSnapshot()) {
                    for (DeathChestData chest : List.copyOf(chests)) {
                        plugin.chests().destroySilently(chest);
                    }
                    locked.clear();
                    toGive.addAll(ItemStacks.deepCopy(record.getItems()));
                }
                if (!toGive.isEmpty()) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(record.getPlayerUuid());
                    Player online = offline.getPlayer();
                    if (online != null) {
                        List<ItemStack> leftover = giveItems(online, toGive);
                        itemsRestored = leftover.size() < toGive.size() || leftover.isEmpty();
                        if (!leftover.isEmpty()) {
                            usedRecovery = deliverOverflow(record, leftover);
                        }
                    } else {
                        usedRecovery = deliverOverflow(record, toGive);
                        itemsRestored = true;
                    }
                } else if (decision.useChestContents() || decision.useRecovery()) {
                    itemsRestored = true;
                }
                if (itemsRestored) {
                    parts.add("items");
                }
            }

            if (shouldRestoreExp(part)) {
                Player online = Bukkit.getPlayer(record.getPlayerUuid());
                if (online != null) {
                    ExperienceUtil.applyToPlayer(online, record.getTotalExperienceBefore());
                    expRestored = true;
                    parts.add("exp");
                } else if (part == RestorePart.EXP) {
                    return RestoreOutcome.fail("restore-player-offline-exp");
                } else {
                    expSkippedOffline = true;
                }
            }

            if (!itemsRestored && !expRestored && !expSkippedOffline) {
                return RestoreOutcome.fail("restore-failed");
            }

            if (itemsRestored) {
                if (usedRecovery) {
                    record.setStatus(RecordStatus.PARTIALLY_RESTORED);
                } else {
                    record.setStatus(force ? RecordStatus.ADMIN_RESTORED : RecordStatus.ROLLED_BACK);
                }
            }
            plugin.audit().log(force ? AuditEventType.ADMIN_FORCE_RESTORE : AuditEventType.ADMIN_RESTORE,
                    actor, actorName, record.getPlayerUuid(), record.getPlayerName(), record.getDeathChestId(),
                    record.getRecordId(), "parts=" + String.join(",", parts), force);
            String messageKey = usedRecovery && itemsRestored ? "restore-partial" : "restore-success";
            return new RestoreOutcome(true, messageKey, String.join(", ", parts), usedRecovery, expRestored,
                    itemsRestored, expSkippedOffline);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Restore failed for " + record.getRecordId() + ": " + exception.getMessage());
            exception.printStackTrace();
            return RestoreOutcome.fail("restore-failed");
        } finally {
            locked.forEach(chest -> plugin.chests().setLocked(chest, false));
            if (record.isRollbackInProgress()) {
                record.setRollbackInProgress(false);
                plugin.records().save(record);
            }
        }
    }

    private boolean hasPendingRecovery(DeathRecord record) {
        if (record.getRecordId() == null) {
            return false;
        }
        return plugin.storage().pendingRecoveryRecordIds().contains(record.getRecordId());
    }

    private boolean deliverOverflow(DeathRecord record, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        if (plugin.settings().useRecoveryStorage && plugin.settings().recoveryEnabled) {
            plugin.recovery().store(record.getPlayerUuid(), record.getRecordId(), items);
            return true;
        }
        Location location = dropLocation(record);
        if (location == null || location.getWorld() == null) {
            plugin.recovery().store(record.getPlayerUuid(), record.getRecordId(), items);
            return true;
        }
        World world = location.getWorld();
        for (ItemStack item : items) {
            if (!ItemStacks.isEmpty(item)) {
                world.dropItemNaturally(location, item.clone());
            }
        }
        return false;
    }

    private Location dropLocation(DeathRecord record) {
        Player online = Bukkit.getPlayer(record.getPlayerUuid());
        if (online != null) {
            return online.getLocation();
        }
        if (record.getWorld() == null) {
            return null;
        }
        World world = Bukkit.getWorld(record.getWorld());
        if (world == null) {
            return null;
        }
        return new Location(world, record.getX(), record.getY(), record.getZ());
    }

    private boolean contentsMatchAll(DeathRecord record, List<DeathChestData> chests) {
        List<ItemStack> combined = new ArrayList<>();
        for (DeathChestData chest : chests) {
            combined.addAll(plugin.chests().currentItems(chest));
        }
        return ItemMatcher.matches(record.getItems(), combined);
    }

    private List<ItemStack> giveItems(Player player, List<ItemStack> items) {
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack item : items) {
            if (ItemStacks.isEmpty(item)) {
                continue;
            }
            HashMap<Integer, ItemStack> notAdded = inventory.addItem(item.clone());
            leftover.addAll(notAdded.values());
        }
        return leftover;
    }

    private boolean alreadyRestored(DeathRecord record) {
        RecordStatus status = record.getStatus();
        return status == RecordStatus.ROLLED_BACK
                || status == RecordStatus.ADMIN_RESTORED
                || status == RecordStatus.PARTIALLY_RESTORED;
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

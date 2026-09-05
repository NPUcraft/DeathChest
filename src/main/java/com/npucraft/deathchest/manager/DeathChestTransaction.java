package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.config.PluginSettings;
import com.npucraft.deathchest.model.AuditEventType;
import com.npucraft.deathchest.model.ChestPlacement;
import com.npucraft.deathchest.model.ChestType;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.model.DeathRecord;
import com.npucraft.deathchest.model.InsufficientBalanceMode;
import com.npucraft.deathchest.model.LocationFailureMode;
import com.npucraft.deathchest.model.OverflowMode;
import com.npucraft.deathchest.model.RecordStatus;
import com.npucraft.deathchest.util.DeathItemRules;
import com.npucraft.deathchest.util.ExperienceUtil;
import com.npucraft.deathchest.util.ItemStacks;
import com.npucraft.deathchest.util.Texts;
import com.npucraft.deathchest.util.TimeFormats;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DeathChestTransaction {
    private final DeathChestPlugin plugin;
    private final DeathChestPriceCalculator priceCalculator;
    private final ChestSizer sizer;
    private final DeathChestLocationFinder locationFinder;

    public DeathChestTransaction(DeathChestPlugin plugin) {
        this.plugin = plugin;
        this.priceCalculator = new DeathChestPriceCalculator(plugin);
        this.sizer = new ChestSizer();
        this.locationFinder = new DeathChestLocationFinder(plugin);
    }

    public void handle(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        PluginSettings settings = plugin.settings();
        if (!settings.enabled || !player.hasPermission("deathchest.use")) {
            plugin.audit().player(player.getName(), "死亡未处理",
                    settings.enabled ? "缺少权限 deathchest.use" : "general.enabled=false");
            return;
        }

        if (!settings.defaultEnabled) {
            failSafe(plugin.records().createPrepared(player, event, ItemStacks.deepCopy(event.getDrops())),
                    RecordStatus.NORMAL_DROP, "PLAYER_DISABLED");
            plugin.messages().send(player, "death-disabled");
            return;
        }
        if (event.getKeepInventory()) {
            failSafe(plugin.records().createPrepared(player, event, ItemStacks.deepCopy(event.getDrops())),
                    RecordStatus.NORMAL_DROP, "KEEP_INVENTORY");
            plugin.messages().send(player, "death-keep-inventory");
            return;
        }

        DeathItemRules.apply(event);
        List<ItemStack> snapshot = ItemStacks.deepCopy(event.getDrops());
        DeathItemRules.removeVanishing(snapshot);
        DeathRecord record = plugin.records().createPrepared(player, event, snapshot);
        plugin.audit().log(AuditEventType.DEATH_PREPARED, player.getUniqueId(), player.getName(), player.getUniqueId(),
                player.getName(), null, record.getRecordId(), "drops=" + snapshot.size(), false);

        record.setDeathChestEnabled(settings.defaultEnabled);
        record.setEconomyProvider(plugin.economy().provider().id());
        record.setCurrencyId(plugin.economy().provider().getCurrencyId());

        if (snapshot.isEmpty()) {
            failSafe(record, RecordStatus.NORMAL_DROP, "NO_DROPS");
            plugin.messages().send(player, "death-no-drops");
            return;
        }

        if (plugin.economy().requiredButUnavailable()) {
            failSafe(record, RecordStatus.NORMAL_DROP, "ECONOMY_UNAVAILABLE");
            plugin.messages().send(player, "death-economy-unavailable");
            return;
        }
        var balanceLookup = plugin.economy().lookupBalance(player);
        if (plugin.economy().chargingEnabled() && balanceLookup.isEmpty()) {
            failSafe(record, RecordStatus.NORMAL_DROP, "ECONOMY_BALANCE_FAILED");
            plugin.messages().send(player, "death-economy-balance-failed");
            return;
        }
        double balance = balanceLookup.orElse(0.0D);
        record.setBalanceBefore(balance);
        record.setBalanceAfter(balance);

        ExperienceUtil.KeptExperience keptExperience = ExperienceUtil.calculate(player,
                settings.experienceEnabled ? settings.experienceMode : com.npucraft.deathchest.model.ExperienceMode.VANILLA,
                settings.experiencePercentage);
        if (!keptExperience.vanilla()) {
            record.setExperienceKept(keptExperience.kept());
            record.setExperienceLost(keptExperience.lost());
        }

        double price = plugin.economy().chargingEnabled() ? priceCalculator.calculate(player, snapshot) : 0.0D;
        record.setCalculatedPrice(price);
        boolean insufficient = plugin.economy().chargingEnabled() && price > 0.0D && balance + 0.000001D < price;
        record.setInsufficientBalance(insufficient);
        record.setInsufficientBalanceMode(settings.insufficientBalanceMode.name());

        boolean unpaidPublic = false;
        double charge = price;
        boolean immediatelyPublic = false;
        if (insufficient) {
            switch (settings.insufficientBalanceMode) {
                case NORMAL_DROP -> {
                    failSafe(record, RecordStatus.NORMAL_DROP, "INSUFFICIENT_BALANCE");
                    plugin.messages().send(player, "death-insufficient-normal-drop");
                    return;
                }
                case PUBLIC_CHEST -> {
                    charge = 0.0D;
                    unpaidPublic = true;
                    immediatelyPublic = true;
                }
                case TAKE_ALL -> charge = Math.max(0.0D, balance);
            }
        }

        List<ChestSizer.SizedChest> planned = sizer.plan(snapshot, settings.sizingMode, settings.overflowMode);
        ChestSizer.OverflowRemainder overflow = sizer.remainder(snapshot, planned);
        if (planned.isEmpty() || (overflow.hasLeftover() && settings.overflowMode == OverflowMode.NORMAL_DROP_ALL)) {
            failSafe(record, RecordStatus.NORMAL_DROP, "OVERFLOW_NORMAL_DROP");
            plugin.messages().send(player, "death-overflow-normal-drop");
            return;
        }

        Location origin = player.getLocation();
        List<PreparedChest> preparedChests = new ArrayList<>();
        Map<com.npucraft.deathchest.model.LocationKey, Boolean> reserved = new HashMap<>();
        boolean locationFailed = false;
        for (ChestSizer.SizedChest sized : planned) {
            ChestPlacement placement = locationFinder.find(player, origin, sized.type(), key ->
                    plugin.chests().isOccupied(key) || reserved.containsKey(key));
            if (placement == null) {
                locationFailed = true;
                break;
            }
            reserved.put(com.npucraft.deathchest.model.LocationKey.of(placement.getPrimary().getLocation()), true);
            if (placement.getSecondary() != null) {
                reserved.put(com.npucraft.deathchest.model.LocationKey.of(placement.getSecondary().getLocation()), true);
            }
            placement.getItems().addAll(ItemStacks.deepCopy(sized.items()));
            preparedChests.add(new PreparedChest(sized.type(), placement));
        }

        if (locationFailed) {
            if (settings.locationFailureMode == LocationFailureMode.VIRTUAL_STORAGE && settings.recoveryEnabled) {
                plugin.recovery().store(player.getUniqueId(), record.getRecordId(), snapshot);
                record.setStatus(RecordStatus.COMMITTED);
                record.setFailureReason("VIRTUAL_STORAGE");
                record.setChargedPrice(0.0D);
                plugin.records().save(record);
                plugin.audit().chest("改为恢复仓库", "玩家=" + player.getName()
                        + " record=" + record.getRecordId()
                        + " 物品栈=" + snapshot.size());
                applyKeptExperience(event, keptExperience, settings);
                event.getDrops().clear();
                plugin.messages().send(player, "death-virtual-storage");
                return;
            }
            failSafe(record, RecordStatus.NORMAL_DROP, "NO_SAFE_LOCATION");
            plugin.messages().send(player, "death-location-failed");
            return;
        }

        if (overflow.hasLeftover() && settings.overflowMode == OverflowMode.DROP_OVERFLOW) {
            // Leftover stays in event drops; stored items are removed later by replacing drops with leftover only.
        } else if (overflow.hasLeftover()) {
            failSafe(record, RecordStatus.NORMAL_DROP, "OVERFLOW_UNPLACED");
            plugin.messages().send(player, "death-overflow-normal-drop");
            return;
        }

        long now = System.currentTimeMillis();
        long unlockAt = immediatelyPublic ? now : now + settings.privateTimeSeconds * 1000L;
        long expireAt = settings.publicTimeSeconds > 0 ? unlockAt + settings.publicTimeSeconds * 1000L : 0L;
        List<DeathChestData> created = new ArrayList<>();
        try {
            for (int i = 0; i < preparedChests.size(); i++) {
                PreparedChest prepared = preparedChests.get(i);
                DeathChestData data = new DeathChestData();
                data.setId(plugin.nextChestId());
                data.setRecordId(record.getRecordId());
                data.setOwnerUuid(player.getUniqueId());
                data.setOwnerName(player.getName());
                data.setWorld(prepared.placement().getPrimary().getWorld().getName());
                data.setX(prepared.placement().getPrimary().getX());
                data.setY(prepared.placement().getPrimary().getY());
                data.setZ(prepared.placement().getPrimary().getZ());
                if (prepared.type() == ChestType.DOUBLE && prepared.placement().getSecondary() != null) {
                    data.setSecondX(prepared.placement().getSecondary().getX());
                    data.setSecondY(prepared.placement().getSecondary().getY());
                    data.setSecondZ(prepared.placement().getSecondary().getZ());
                }
                data.setChestType(prepared.type());
                data.setCreatedAt(now);
                data.setUnlockAt(unlockAt);
                data.setExpireAt(expireAt);
                data.setPrice(charge);
                data.setCurrency(plugin.economy().provider().getCurrencyName());
                data.setUnpaid(unpaidPublic);
                data.setLocked(false);
                data.setActive(true);
                plugin.chests().createPhysical(data, prepared.placement(), prepared.placement().getItems());
                created.add(data);
                try {
                    plugin.holograms().spawn(data);
                } catch (Exception exception) {
                    plugin.getLogger().warning("Hologram creation failed for " + data.getId() + ": " + exception.getMessage());
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().severe("Failed to create death chest for " + player.getName() + ": " + exception.getMessage());
            created.forEach(plugin.chests()::destroySilently);
            failSafe(record, RecordStatus.FAILED, "CHEST_CREATE_FAILED");
            plugin.messages().send(player, "death-failed");
            plugin.audit().log(AuditEventType.ERROR, player.getUniqueId(), player.getName(), player.getUniqueId(),
                    player.getName(), null, record.getRecordId(), exception.getMessage(), false);
            return;
        }

        record.setDeathChestCreated(true);
        record.setDeathChestId(created.getFirst().getId());
        record.setDeathChestWorld(created.getFirst().getWorld());
        record.setDeathChestX(created.getFirst().getX());
        record.setDeathChestY(created.getFirst().getY());
        record.setDeathChestZ(created.getFirst().getZ());
        record.setChestType(created.getFirst().getChestType());
        record.setProtectedChest(!immediatelyPublic);
        record.setUnlockAt(unlockAt);
        record.setExpireAt(expireAt);
        record.setStatus(RecordStatus.CHEST_CREATED);
        try {
            plugin.records().save(record);
        } catch (Exception exception) {
            created.forEach(plugin.chests()::destroySilently);
            failSafe(record, RecordStatus.FAILED, "RECORD_SAVE_FAILED");
            plugin.messages().send(player, "death-failed");
            return;
        }

        if (charge > 0.0D) {
            boolean withdrawn = plugin.economy().withdraw(player, charge);
            if (!withdrawn) {
                created.forEach(plugin.chests()::destroySilently);
                failSafe(record, RecordStatus.FAILED, "ECONOMY_WITHDRAW_FAILED");
                plugin.messages().send(player, "death-failed");
                return;
            }
            plugin.audit().log(AuditEventType.ECONOMY_WITHDRAW, player.getUniqueId(), player.getName(), player.getUniqueId(),
                    player.getName(), record.getDeathChestId(), record.getRecordId(), "amount=" + charge, false);
            record.setChargedPrice(charge);
            record.setBalanceAfter(Math.max(0.0D, balance - charge));
        } else {
            record.setChargedPrice(0.0D);
        }

        applyKeptExperience(event, keptExperience, settings);

        if (overflow.hasLeftover() && settings.overflowMode == OverflowMode.DROP_OVERFLOW) {
            event.getDrops().clear();
            event.getDrops().addAll(ItemStacks.deepCopy(overflow.leftover()));
        } else {
            event.getDrops().clear();
        }

        record.setStatus(RecordStatus.COMMITTED);
        try {
            plugin.records().save(record);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Death chest was created and charged, but marking the record COMMITTED failed for "
                    + record.getRecordId() + ": " + exception.getMessage());
        }
        plugin.audit().log(AuditEventType.DEATH_COMMITTED, player.getUniqueId(), player.getName(), player.getUniqueId(),
                player.getName(), record.getDeathChestId(), record.getRecordId(), "chests=" + created.size(), false);

        Map<String, String> placeholders = plugin.messages().map(
                "world", created.getFirst().getWorld(),
                "x", String.valueOf(created.getFirst().getX()),
                "y", String.valueOf(created.getFirst().getY()),
                "z", String.valueOf(created.getFirst().getZ()),
                "price", Texts.formatNumber(record.getChargedPrice()),
                "currency", plugin.economy().provider().getCurrencyName(),
                "protection", TimeFormats.duration(settings.privateTimeSeconds * 1000L, plugin.messages().durationMinutesSeconds(), plugin.messages().durationSeconds()),
                "public", TimeFormats.duration(settings.publicTimeSeconds * 1000L, plugin.messages().durationMinutesSeconds(), plugin.messages().durationSeconds()),
                "count", String.valueOf(Math.max(0, created.size() - 1))
        );
        plugin.messages().send(player, "death-created", placeholders);
        plugin.messages().send(player, "death-created-location", placeholders);
        if (record.getChargedPrice() > 0.0D) {
            plugin.messages().send(player, "death-created-price", placeholders);
        }
        if (unpaidPublic) {
            plugin.messages().send(player, "death-created-unpaid", placeholders);
        } else if (settings.privateTimeSeconds > 0) {
            plugin.messages().send(player, "death-created-protection", placeholders);
        }
        if (settings.publicTimeSeconds > 0) {
            plugin.messages().send(player, "death-created-drop-timer", placeholders);
        }
        if (created.size() > 1) {
            plugin.messages().send(player, "death-created-extra", placeholders);
        }
    }

    private void applyKeptExperience(PlayerDeathEvent event, ExperienceUtil.KeptExperience keptExperience,
                                     PluginSettings settings) {
        if (!keptExperience.vanilla() && settings.experienceEnabled) {
            ExperienceUtil.applyToDeathEvent(event, keptExperience.kept());
        }
    }

    private void failSafe(DeathRecord record, RecordStatus status, String reason) {
        record.setStatus(status);
        record.setFailureReason(reason);
        record.setDeathChestCreated(false);
        plugin.records().save(record);
        plugin.audit().chest("未创建", "玩家=" + record.getPlayerName()
                + " record=" + record.getRecordId()
                + " 状态=" + status.name()
                + " 原因=" + reason);
    }

    private record PreparedChest(ChestType type, ChestPlacement placement) {
    }
}

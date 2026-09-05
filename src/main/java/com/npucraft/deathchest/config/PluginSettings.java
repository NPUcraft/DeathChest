package com.npucraft.deathchest.config;

import com.npucraft.deathchest.model.EconomyProviderType;
import com.npucraft.deathchest.model.EquipmentMode;
import com.npucraft.deathchest.model.ExpireMode;
import com.npucraft.deathchest.model.ExperienceMode;
import com.npucraft.deathchest.model.InsufficientBalanceMode;
import com.npucraft.deathchest.model.InventoryPriceMode;
import com.npucraft.deathchest.model.LocationFailureMode;
import com.npucraft.deathchest.model.OverflowMode;
import com.npucraft.deathchest.model.RoundingMode;
import com.npucraft.deathchest.model.SizingMode;
import com.npucraft.deathchest.model.StorageType;
import com.npucraft.deathchest.model.TimerMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class PluginSettings {
    public final boolean enabled;
    public final boolean debug;

    public final boolean defaultEnabled;

    public final boolean inventoryTotem;

    public final boolean economyEnabled;
    public final EconomyProviderType economyProvider;
    public final InsufficientBalanceMode insufficientBalanceMode;
    public final String coinsEngineCurrency;

    public final double basePrice;
    public final boolean levelPriceEnabled;
    public final double pricePerLevel;
    public final boolean inventoryPriceEnabled;
    public final InventoryPriceMode inventoryPriceMode;
    public final double pricePerSlot;
    public final double minPrice;
    public final double maxPrice;
    public final RoundingMode rounding;

    public final boolean experienceEnabled;
    public final ExperienceMode experienceMode;
    public final int experiencePercentage;

    public final SizingMode sizingMode;
    public final OverflowMode overflowMode;
    public final Material chestBlockType;

    public final int safeLocationRadius;
    public final int verticalSearchRadius;
    public final int maxBlockChecks;
    public final LocationFailureMode locationFailureMode;
    public final boolean allowWaterlogged;
    public final boolean avoidExistingChests;

    public final boolean residenceEnabled;
    public final boolean avoidNoPermissionResidence;
    public final boolean checkBuildPermission;
    public final boolean checkPlacePermission;
    public final boolean checkContainerPermission;

    public final long privateTimeSeconds;
    public final long publicTimeSeconds;
    public final TimerMode timerMode;
    public final boolean ownerCanBreak;
    public final boolean publicCanBreak;
    public final boolean protectFromExplosion;
    public final boolean protectFromPiston;
    public final boolean protectFromHopper;

    public final boolean quickRetrieveEnabled;
    public final boolean retrieveOwnerOnly;
    public final boolean retrieveAdminBypass;
    public final boolean equipmentEnabled;
    public final EquipmentMode equipmentMode;
    public final boolean autoEquipHelmet;
    public final boolean autoEquipChestplate;
    public final boolean autoEquipLeggings;
    public final boolean autoEquipBoots;
    public final boolean autoEquipOffhand;
    public final boolean mergeExistingStacksFirst;

    public final boolean hologramEnabled;
    public final double hologramHeight;
    public final int hologramUpdateInterval;

    public final ExpireMode expireMode;
    public final boolean removeEmptyChest;
    public final int removeEmptyDelayTicks;

    public final boolean deathRecordsEnabled;
    public final boolean saveItemSnapshot;
    public final int maxRecordsPerPlayer;
    public final boolean protectActiveChestRecords;
    public final boolean protectPendingRecoveryRecords;
    public final boolean recordCleanupEnabled;
    public final int recordCleanupIntervalMinutes;

    public final boolean rollbackEnabled;
    public final boolean allowForce;
    public final boolean restoreItems;
    public final boolean restoreExperience;
    public final boolean useRecoveryStorage;

    public final boolean recoveryEnabled;
    public final int recoveryExpireDays;
    public final boolean recoveryNotifyOnJoin;

    public final boolean auditEnabled;
    public final boolean auditLogToConsole;
    public final int auditRetentionDays;

    public final StorageType storageType;
    public final String storageFile;
    public final String mysqlHost;
    public final int mysqlPort;
    public final String mysqlDatabase;
    public final String mysqlUsername;
    public final String mysqlPassword;
    public final String mysqlParameters;
    public final String mysqlJdbcUrl;
    public final boolean placeholderEnabled;

    public final String dateFormat;
    public final String timezone;

    public PluginSettings(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("general.enabled", true);
        this.debug = config.getBoolean("general.debug", false);
        this.inventoryTotem = config.getBoolean("totem.inventory-trigger", true);

        this.defaultEnabled = config.getBoolean("player-settings.default-enabled", true);

        this.economyEnabled = config.getBoolean("economy.enabled", true);
        this.economyProvider = enumValue(config.getString("economy.provider"), EconomyProviderType.VAULT, EconomyProviderType.class);
        this.insufficientBalanceMode = enumValue(config.getString("economy.insufficient-balance-mode"), InsufficientBalanceMode.NORMAL_DROP, InsufficientBalanceMode.class);
        this.coinsEngineCurrency = config.getString("coinsengine.currency", "coins");

        this.basePrice = config.getDouble("price.base", 300.0D);
        this.levelPriceEnabled = config.getBoolean("price.level.enabled", true);
        this.pricePerLevel = config.getDouble("price.level.price-per-level", 10.0D);
        this.inventoryPriceEnabled = config.getBoolean("price.inventory.enabled", true);
        this.inventoryPriceMode = enumValue(config.getString("price.inventory.mode"), InventoryPriceMode.OCCUPIED_SLOTS, InventoryPriceMode.class);
        this.pricePerSlot = config.getDouble("price.inventory.price-per-slot", 10.0D);
        this.minPrice = config.getDouble("price.minimum", 0.0D);
        this.maxPrice = config.getDouble("price.maximum", 3000.0D);
        this.rounding = enumValue(config.getString("price.rounding"), RoundingMode.ROUND, RoundingMode.class);

        this.experienceEnabled = config.getBoolean("experience.enabled", true);
        this.experienceMode = enumValue(config.getString("experience.mode"), ExperienceMode.PERCENTAGE, ExperienceMode.class);
        this.experiencePercentage = Math.max(0, Math.min(100, config.getInt("experience.percentage", 70)));

        this.sizingMode = enumValue(config.getString("chest.sizing-mode"), SizingMode.AUTO, SizingMode.class);
        this.overflowMode = enumValue(config.getString("chest.overflow-mode"), OverflowMode.EXTRA_CHEST, OverflowMode.class);
        Material chestType = Material.matchMaterial(config.getString("chest.block-type", "CHEST"));
        this.chestBlockType = chestType == null ? Material.CHEST : chestType;

        this.safeLocationRadius = Math.max(1, config.getInt("location.safe-location-radius", 32));
        this.verticalSearchRadius = Math.max(1, config.getInt("location.vertical-search-radius", 64));
        this.maxBlockChecks = Math.max(256, config.getInt("location.max-block-checks", 12000));
        this.locationFailureMode = enumValue(config.getString("location.failure-mode"), LocationFailureMode.VIRTUAL_STORAGE, LocationFailureMode.class);
        this.allowWaterlogged = config.getBoolean("location.allow-waterlogged", true);
        this.avoidExistingChests = config.getBoolean("location.avoid-existing-chests", true);

        this.residenceEnabled = config.getBoolean("integration.residence.enabled", true);
        this.avoidNoPermissionResidence = config.getBoolean("integration.residence.avoid-no-permission-residence", true);
        this.checkBuildPermission = config.getBoolean("integration.residence.check-build-permission", true);
        this.checkPlacePermission = config.getBoolean("integration.residence.check-place-permission", true);
        this.checkContainerPermission = config.getBoolean("integration.residence.check-container-permission", true);

        this.privateTimeSeconds = Math.max(0L, config.getLong("protection.private-time", 43200L));
        this.publicTimeSeconds = Math.max(0L, config.getLong("protection.public-time", 259200L));
        this.timerMode = enumValue(config.getString("protection.timer-mode"), TimerMode.REALTIME, TimerMode.class);
        this.ownerCanBreak = config.getBoolean("protection.owner-can-break", true);
        this.publicCanBreak = config.getBoolean("protection.public-can-break", true);
        this.protectFromExplosion = config.getBoolean("protection.protect-from-explosion", true);
        this.protectFromPiston = config.getBoolean("protection.protect-from-piston", true);
        this.protectFromHopper = config.getBoolean("protection.protect-from-hopper", true);

        this.quickRetrieveEnabled = config.getBoolean("quick-retrieve.enabled", true);
        this.retrieveOwnerOnly = config.getBoolean("quick-retrieve.owner-only", true);
        this.retrieveAdminBypass = config.getBoolean("quick-retrieve.allow-admin-bypass", true);
        this.equipmentEnabled = config.getBoolean("quick-retrieve.equipment.enabled", true);
        this.equipmentMode = enumValue(config.getString("quick-retrieve.equipment.mode"), EquipmentMode.EMPTY_SLOT_ONLY, EquipmentMode.class);
        this.autoEquipHelmet = config.getBoolean("quick-retrieve.equipment.auto-equip-helmet", true);
        this.autoEquipChestplate = config.getBoolean("quick-retrieve.equipment.auto-equip-chestplate", true);
        this.autoEquipLeggings = config.getBoolean("quick-retrieve.equipment.auto-equip-leggings", true);
        this.autoEquipBoots = config.getBoolean("quick-retrieve.equipment.auto-equip-boots", true);
        this.autoEquipOffhand = config.getBoolean("quick-retrieve.equipment.auto-equip-offhand", true);
        this.mergeExistingStacksFirst = config.getBoolean("quick-retrieve.inventory.merge-existing-stacks-first", true);

        this.hologramEnabled = config.getBoolean("hologram.enabled", true);
        this.hologramHeight = config.getDouble("hologram.height", 1.5D);
        this.hologramUpdateInterval = Math.max(1, config.getInt("hologram.update-interval", 20));

        this.expireMode = enumValue(config.getString("cleanup.expire-mode"), ExpireMode.DROP_ITEMS, ExpireMode.class);
        this.removeEmptyChest = config.getBoolean("cleanup.remove-empty-chest", true);
        this.removeEmptyDelayTicks = Math.max(0, config.getInt("cleanup.remove-delay", 20));

        this.deathRecordsEnabled = config.getBoolean("death-records.enabled", true);
        this.saveItemSnapshot = config.getBoolean("death-records.save-item-snapshot", true);
        this.maxRecordsPerPlayer = Math.max(0, config.getInt("death-records.max-records-per-player", 30));
        this.protectActiveChestRecords = config.getBoolean("death-records.protect-active-chest-records", true);
        this.protectPendingRecoveryRecords = config.getBoolean("death-records.protect-pending-recovery-records", true);
        this.recordCleanupEnabled = config.getBoolean("death-records.cleanup.enabled", true);
        this.recordCleanupIntervalMinutes = Math.max(1, config.getInt("death-records.cleanup.interval-minutes", 720));

        this.rollbackEnabled = config.getBoolean("rollback.enabled", true);
        this.allowForce = config.getBoolean("rollback.allow-force", true);
        this.restoreItems = config.getBoolean("rollback.restore-items", true);
        this.restoreExperience = config.getBoolean("rollback.restore-experience", true);
        this.useRecoveryStorage = config.getBoolean("rollback.use-recovery-storage", true);

        this.recoveryEnabled = config.getBoolean("recovery-storage.enabled", true);
        this.recoveryExpireDays = Math.max(1, config.getInt("recovery-storage.expire-days", 30));
        this.recoveryNotifyOnJoin = config.getBoolean("recovery-storage.notify-on-join", true);

        this.auditEnabled = config.getBoolean("audit.enabled", true);
        this.auditLogToConsole = config.getBoolean("audit.log-to-console", true);
        this.auditRetentionDays = Math.max(1, config.getInt("audit.retention-days", 365));

        this.storageType = enumValue(config.getString("storage.type"), StorageType.SQLITE, StorageType.class);
        this.storageFile = config.getString("storage.file", "deathchest.db");
        this.mysqlHost = config.getString("storage.mysql.host", "127.0.0.1");
        this.mysqlPort = config.getInt("storage.mysql.port", 3306);
        this.mysqlDatabase = config.getString("storage.mysql.database", "deathchest");
        this.mysqlUsername = config.getString("storage.mysql.username", "root");
        this.mysqlPassword = config.getString("storage.mysql.password", "");
        this.mysqlParameters = config.getString("storage.mysql.parameters",
                "createDatabaseIfNotExist=true&sslMode=DISABLE&allowPublicKeyRetrieval=true&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci");
        this.mysqlJdbcUrl = config.getString("storage.mysql.jdbc-url", "");
        this.placeholderEnabled = config.getBoolean("placeholder.enabled", true);

        this.dateFormat = config.getString("time.date-format", "yyyy-MM-dd HH:mm:ss");
        this.timezone = config.getString("time.timezone", "Asia/Shanghai");
    }

    private static <E extends Enum<E>> E enumValue(String raw, E fallback, Class<E> type) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

package com.npucraft.deathchest;

import com.npucraft.deathchest.manager.AuditLogger;
import com.npucraft.deathchest.manager.DeathChestManager;
import com.npucraft.deathchest.manager.CleanupManager;
import com.npucraft.deathchest.manager.TimerClock;
import com.npucraft.deathchest.command.DeathChestCommand;
import com.npucraft.deathchest.command.DeathChestTabCompleter;
import com.npucraft.deathchest.config.ConfigManager;
import com.npucraft.deathchest.config.PluginSettings;
import com.npucraft.deathchest.manager.DeathChestTransaction;
import com.npucraft.deathchest.hook.EconomyManager;
import com.npucraft.deathchest.manager.HologramManager;
import com.npucraft.deathchest.listener.DeathListener;
import com.npucraft.deathchest.listener.PlayerListener;
import com.npucraft.deathchest.listener.ProtectionListener;
import com.npucraft.deathchest.listener.TotemListener;
import com.npucraft.deathchest.listener.WorldListener;
import com.npucraft.deathchest.config.MessageManager;
import com.npucraft.deathchest.hook.DeathChestPlaceholderExpansion;
import com.npucraft.deathchest.hook.PlaceholderManager;
import com.npucraft.deathchest.hook.ProtectionManager;
import com.npucraft.deathchest.manager.DeathRecordManager;
import com.npucraft.deathchest.manager.DeathChestPriceCalculator;
import com.npucraft.deathchest.manager.PlayerSettingsManager;
import com.npucraft.deathchest.manager.RecoveryStorageManager;
import com.npucraft.deathchest.manager.QuickRetrieveManager;
import com.npucraft.deathchest.manager.RollbackManager;
import com.npucraft.deathchest.storage.PluginStorage;
import com.npucraft.deathchest.storage.StorageFactory;
import com.npucraft.deathchest.util.Ids;
import com.npucraft.deathchest.util.Keys;
import com.npucraft.deathchest.util.StartupBanner;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeathChestPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private MessageManager messages;
    private PlaceholderManager placeholders;
    private PluginStorage storage;
    private Keys keys;
    private EconomyManager economy;
    private ProtectionManager protection;
    private AuditLogger audit;
    private DeathChestManager chests;
    private DeathRecordManager records;
    private PlayerSettingsManager playerSettings;
    private DeathChestPriceCalculator priceCalculator;
    private RecoveryStorageManager recovery;
    private HologramManager holograms;
    private QuickRetrieveManager retrieve;
    private RollbackManager rollback;
    private CleanupManager cleanup;
    private TimerClock timerClock;
    private DeathChestTransaction deaths;
    private DeathChestCommand command;
    private DeathChestPlaceholderExpansion expansion;

    @Override
    public void onEnable() {
        StartupBanner.print(this);
        this.configManager = new ConfigManager(this);
        this.keys = new Keys(this);
        this.placeholders = new PlaceholderManager(this);
        this.messages = new MessageManager(this);
        this.storage = StorageFactory.create(this);
        this.storage.open();
        this.audit = new AuditLogger(this);
        this.playerSettings = new PlayerSettingsManager(this);
        this.priceCalculator = new DeathChestPriceCalculator(this);
        this.economy = new EconomyManager(this);
        this.protection = new ProtectionManager(this);
        this.chests = new DeathChestManager(this);
        this.records = new DeathRecordManager(this);
        this.recovery = new RecoveryStorageManager(this);
        this.holograms = new HologramManager(this);
        this.retrieve = new QuickRetrieveManager(this);
        this.rollback = new RollbackManager(this);
        this.deaths = new DeathChestTransaction(this);
        this.cleanup = new CleanupManager(this);
        this.timerClock = new TimerClock(this);
        this.command = new DeathChestCommand(this);

        this.chests.load();
        int reconciledTransfers = this.chests.reconcilePendingTransfers();
        if (reconciledTransfers > 0) {
            getLogger().warning("Reconciled " + reconciledTransfers + " interrupted death-chest transfers.");
        }
        int reconciledRestores = this.rollback.reconcileInterruptedRestores();
        if (reconciledRestores > 0) {
            getLogger().warning("Reconciled " + reconciledRestores + " interrupted admin restores.");
        }
        this.timerClock.applyOfflinePause();
        try {
            this.cleanup.runOnce();
        } catch (Exception exception) {
            getLogger().warning("Initial death chest cleanup failed: " + exception.getMessage());
        }
        Bukkit.getPluginManager().registerEvents(new DeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new TotemListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WorldListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        PluginCommand pluginCommand = getCommand("deathchest");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(new DeathChestTabCompleter(this));
        }

        this.holograms.start();
        this.cleanup.start();
        Bukkit.getWorlds().forEach(holograms::restoreInWorld);
        hookPlaceholderApi();
        getLogger().info("DeathChest enabled. Active chests: " + chests.all().size());
    }

    @Override
    public void onDisable() {
        if (cleanup != null) {
            cleanup.stop();
        }
        if (timerClock != null) {
            timerClock.heartbeat();
        }
        if (holograms != null) {
            holograms.stop();
            holograms.removeAll();
        }
        if (storage != null) {
            storage.close();
        }
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (Exception ignored) {
            }
        }
    }

    public void reloadPlugin() {
        configManager.reload();
        messages.reload();
        economy.hook();
        protection.hook();
        holograms.stop();
        holograms.start();
        cleanup.stop();
        cleanup.start();
        hookPlaceholderApi();
        Bukkit.getWorlds().forEach(holograms::restoreInWorld);
        getLogger().info("配置已重新加载");
    }

    private void hookPlaceholderApi() {
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (Exception exception) {
                getLogger().warning("Failed to unregister PlaceholderAPI expansion: " + exception.getMessage());
            } finally {
                expansion = null;
            }
        }
        if (!settings().placeholderEnabled) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            expansion = new DeathChestPlaceholderExpansion(this);
            expansion.register();
            getLogger().info("Registered PlaceholderAPI expansion: deathchest");
        } catch (Exception exception) {
            getLogger().warning("Failed to register PlaceholderAPI expansion: " + exception.getMessage());
        }
    }

    public String nextChestId(String playerId, long deathTime, int part) {
        String base = Ids.chestId(playerId, deathTime, settings().timezone, part);
        String id = base;
        int collision = 2;
        while (storage.loadChest(id).isPresent()
                || storage.loadRecovery(RecoveryStorageManager.chestTransferId(id)).isPresent()) {
            id = base + "-N" + collision++;
        }
        return id;
    }

    public String nextRecordId(String playerId, long deathTime) {
        String base = Ids.recordId(playerId, deathTime, settings().timezone);
        String id = base;
        int collision = 2;
        while (storage.loadRecord(id).isPresent()) {
            id = base + "-N" + collision++;
        }
        return id;
    }

    public void debug(String message) {
        if (settings().debug) {
            getLogger().info("[Debug] " + message);
        }
    }

    public PluginSettings settings() {
        return configManager.settings();
    }

    public MessageManager messages() {
        return messages;
    }

    public PlaceholderManager placeholders() {
        return placeholders;
    }

    public PluginStorage storage() {
        return storage;
    }

    public Keys keys() {
        return keys;
    }

    public EconomyManager economy() {
        return economy;
    }

    public ProtectionManager protection() {
        return protection;
    }

    public AuditLogger audit() {
        return audit;
    }

    public DeathChestManager chests() {
        return chests;
    }

    public DeathRecordManager records() {
        return records;
    }

    public PlayerSettingsManager playerSettings() {
        return playerSettings;
    }

    public DeathChestPriceCalculator priceCalculator() {
        return priceCalculator;
    }

    public double estimatedDeathPrice(Player player) {
        if (player == null || !settings().enabled || !playerSettings.isEnabled(player.getUniqueId())
                || !player.hasPermission("deathchest.use")
                || !economy.chargingEnabled()
                || Boolean.TRUE.equals(player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY))) {
            return 0.0D;
        }
        double calculated = priceCalculator.estimate(player);
        if (calculated <= 0.0D) {
            return 0.0D;
        }
        var balance = economy.lookupBalance(player);
        if (balance.isEmpty()) {
            return 0.0D;
        }
        double available = Math.max(0.0D, balance.getAsDouble());
        if (available + 0.000001D >= calculated) {
            return calculated;
        }
        return settings().insufficientBalanceMode == com.npucraft.deathchest.model.InsufficientBalanceMode.TAKE_ALL
                ? available : 0.0D;
    }

    public RecoveryStorageManager recovery() {
        return recovery;
    }

    public HologramManager holograms() {
        return holograms;
    }

    public QuickRetrieveManager retrieve() {
        return retrieve;
    }

    public RollbackManager rollback() {
        return rollback;
    }

    public DeathChestTransaction deaths() {
        return deaths;
    }

    public DeathChestCommand commandExecutor() {
        return command;
    }

    public TimerClock timerClock() {
        return timerClock;
    }
}

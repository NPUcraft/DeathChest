package com.npucraft.deathchest.hook;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.EconomyProviderType;
import org.bukkit.OfflinePlayer;

import java.util.OptionalDouble;

public final class EconomyManager {
    private final DeathChestPlugin plugin;
    private EconomyProvider provider = new NoEconomyProvider();

    public EconomyManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
        hook();
    }

    public void hook() {
        if (!plugin.settings().economyEnabled || plugin.settings().economyProvider == EconomyProviderType.NONE) {
            this.provider = new NoEconomyProvider();
            plugin.getLogger().info("Economy is disabled. Death chests will be created without charging.");
            return;
        }
        if (plugin.settings().economyProvider == EconomyProviderType.COINSENGINE) {
            CoinsEngineEconomyProvider coins = new CoinsEngineEconomyProvider(plugin, plugin.settings().coinsEngineCurrency);
            this.provider = coins.available() ? coins : new NoEconomyProvider();
            if (!coins.available()) {
                plugin.getLogger().severe("CoinsEngine economy is unavailable. Death chests will not be created until the currency and plugin are available.");
            }
            return;
        }
        VaultEconomyProvider vault = new VaultEconomyProvider(plugin);
        this.provider = vault.available() ? vault : new NoEconomyProvider();
        if (!vault.available()) {
            plugin.getLogger().severe("Vault economy is unavailable. Death chests will not be created until Vault and an economy plugin are available.");
        }
    }

    public EconomyProvider provider() {
        return provider;
    }

    public boolean chargingEnabled() {
        return plugin.settings().economyEnabled
                && plugin.settings().economyProvider != EconomyProviderType.NONE
                && provider.available()
                && !(provider instanceof NoEconomyProvider);
    }

    public boolean requiredButUnavailable() {
        return plugin.settings().economyEnabled
                && plugin.settings().economyProvider != EconomyProviderType.NONE
                && !chargingEnabled();
    }

    public OptionalDouble lookupBalance(OfflinePlayer player) {
        if (!chargingEnabled()) {
            return OptionalDouble.of(0.0D);
        }
        try {
            return OptionalDouble.of(provider.getBalance(player));
        } catch (Exception exception) {
            plugin.getLogger().warning("Economy getBalance failed: " + exception.getMessage());
            return OptionalDouble.empty();
        }
    }

    public double balance(OfflinePlayer player) {
        return lookupBalance(player).orElse(0.0D);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        try {
            return provider.withdraw(player, amount);
        } catch (Exception exception) {
            plugin.getLogger().warning("Economy withdraw failed: " + exception.getMessage());
            return false;
        }
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        try {
            return provider.deposit(player, amount);
        } catch (Exception exception) {
            plugin.getLogger().warning("Economy deposit failed: " + exception.getMessage());
            return false;
        }
    }
}

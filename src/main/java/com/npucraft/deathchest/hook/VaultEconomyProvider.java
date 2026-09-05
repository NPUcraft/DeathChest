package com.npucraft.deathchest.hook;

import com.npucraft.deathchest.DeathChestPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultEconomyProvider implements EconomyProvider {
    private final DeathChestPlugin plugin;
    private Economy economy;

    public VaultEconomyProvider(DeathChestPlugin plugin) {
        this.plugin = plugin;
        hook();
    }

    public void hook() {
        this.economy = null;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Economy provider is VAULT but Vault is not installed.");
            return;
        }
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            plugin.getLogger().warning("Vault is installed but no Economy provider was found (install EssentialsX Economy, CMI, etc.).");
            return;
        }
        this.economy = registration.getProvider();
        plugin.getLogger().info("Hooked Vault economy: " + economy.getName());
    }

    @Override
    public String id() {
        return "VAULT";
    }

    @Override
    public boolean available() {
        return economy != null && economy.isEnabled();
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (!available()) {
            return 0.0D;
        }
        return economy.getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        return available() && economy.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        if (!available()) {
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        if (!available()) {
            return false;
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    @Override
    public String getCurrencyName() {
        if (!available()) {
            return "Vault";
        }
        try {
            return economy.currencyNamePlural();
        } catch (Exception ignored) {
            return economy.getName();
        }
    }

    @Override
    public String getCurrencyId() {
        return available() ? economy.getName() : "vault";
    }
}

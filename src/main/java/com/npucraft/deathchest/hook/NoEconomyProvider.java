package com.npucraft.deathchest.hook;

import org.bukkit.OfflinePlayer;

public final class NoEconomyProvider implements EconomyProvider {
    @Override
    public String id() {
        return "NONE";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return 0.0D;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return amount <= 0.0D;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return amount <= 0.0D;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return true;
    }

    @Override
    public String getCurrencyName() {
        return "";
    }

    @Override
    public String getCurrencyId() {
        return "none";
    }
}

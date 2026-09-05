package com.npucraft.deathchest.hook;

import org.bukkit.OfflinePlayer;

public interface EconomyProvider {
    String id();

    boolean available();

    double getBalance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    String getCurrencyName();

    String getCurrencyId();
}

package com.npucraft.deathchest.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class StartupBanner {
    private static final String[] ART = {
            "  ____             _   _      ____ _               _   ",
            " |  _ \\  ___  __ _| |_| |__  / ___| |__   ___  ___| |_ ",
            " | | | |/ _ \\/ _` | __| '_ \\| |   | '_ \\ / _ \\/ __| __|",
            " | |_| |  __/ (_| | |_| | | | |___| | | |  __/\\__ \\ |_ ",
            " |____/ \\___|\\__,_|\\__|_| |_|\\____|_| |_|\\___||___/\\__|"
    };

    private StartupBanner() {
    }

    public static void print(Plugin plugin) {
        var console = Bukkit.getConsoleSender();
        console.sendMessage(Component.empty());
        for (String line : ART) {
            console.sendMessage(Component.text(line, NamedTextColor.AQUA));
        }
        console.sendMessage(Component.text("                   By NPUcraft", NamedTextColor.GOLD, TextDecoration.BOLD));
        console.sendMessage(Component.text(
                "                   v" + plugin.getPluginMeta().getVersion() + "  ·  npucraft.com",
                NamedTextColor.GRAY));
        console.sendMessage(Component.empty());
    }
}

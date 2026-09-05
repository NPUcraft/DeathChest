package com.npucraft.deathchest.config;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.util.Texts;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class MessageManager {
    private static final String DEFAULT_LANGUAGE = "zh";

    private final DeathChestPlugin plugin;
    private FileConfiguration yaml = new YamlConfiguration();
    private String prefix = "";

    public MessageManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String language = sanitize(plugin.getConfig().getString("language", DEFAULT_LANGUAGE));
        File file = languageFile(language);
        if (!file.exists()) {
            saveResourceQuietly("message_" + language + ".yml");
        }
        if (!file.exists() && !DEFAULT_LANGUAGE.equals(language)) {
            plugin.getLogger().warning("Language file message_" + language + ".yml not found, falling back to " + DEFAULT_LANGUAGE);
            language = DEFAULT_LANGUAGE;
            file = languageFile(language);
            if (!file.exists()) {
                saveResourceQuietly("message_" + language + ".yml");
            }
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        InputStream bundled = plugin.getResource("message_" + language + ".yml");
        if (bundled == null) {
            bundled = plugin.getResource("message_" + DEFAULT_LANGUAGE + ".yml");
        }
        if (bundled != null) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }
        this.yaml = loaded;
        this.prefix = raw("prefix", "<dark_gray>[<red>DeathChest<dark_gray>] ");
    }

    public String raw(String key, String fallback) {
        String value = yaml.getString(key);
        return value == null ? fallback : value;
    }

    public List<String> rawList(String key) {
        List<String> list = yaml.getStringList(key);
        return list == null ? List.of() : list;
    }

    public String durationDays() {
        return raw("time.duration-days", "{days}天{hours}小时{minutes}分钟{seconds}秒");
    }

    public String durationHours() {
        return raw("time.duration-hours", "{hours}小时{minutes}分钟{seconds}秒");
    }

    public String durationMinutesSeconds() {
        return raw("time.duration-minutes-seconds", "{minutes}分钟{seconds}秒");
    }

    public String durationSeconds() {
        return raw("time.duration-seconds", "{seconds}秒");
    }

    public List<String> hologramLines(String state) {
        return rawList("hologram." + state);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = raw(key, "");
        if (message.isBlank()) {
            return;
        }
        sender.sendMessage(component(sender instanceof Player player ? player : null, message, placeholders, true));
    }

    public Component component(Player player, String message, Map<String, String> placeholders, boolean withPrefix) {
        String parsed = plugin.placeholders().apply(player, null, null, (withPrefix ? prefix : "") + message, placeholders);
        return Texts.mini(parsed);
    }

    public Map<String, String> map(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return map;
    }

    public void sendHelp(CommandSender sender) {
        Player player = sender instanceof Player online ? online : null;
        sender.sendMessage(component(player, raw("help-header", ""), Map.of(), true));
        for (String line : rawList("help-player")) {
            sender.sendMessage(component(player, line, Map.of(), false));
        }
        if (sender.hasPermission("deathchest.admin")) {
            sender.sendMessage(component(player, raw("help-admin-header", ""), Map.of(), false));
            for (String line : rawList("help-admin")) {
                sender.sendMessage(component(player, line, Map.of(), false));
            }
        }
        sender.sendMessage(component(player, raw("help-footer", ""), Map.of(), false));
    }

    private File languageFile(String language) {
        return new File(plugin.getDataFolder(), "message_" + language + ".yml");
    }

    private void saveResourceQuietly(String path) {
        try {
            plugin.saveResource(path, false);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().log(Level.FINE, "Bundled language file missing: " + path);
        }
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String language = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!language.matches("[a-z0-9_-]{1,16}")) {
            return DEFAULT_LANGUAGE;
        }
        return language;
    }
}

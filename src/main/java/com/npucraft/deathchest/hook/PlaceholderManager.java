package com.npucraft.deathchest.hook;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.config.PluginSettings;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.model.DeathRecord;
import com.npucraft.deathchest.util.Texts;
import com.npucraft.deathchest.util.TimeFormats;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlaceholderManager {
    private final DeathChestPlugin plugin;

    public PlaceholderManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean papiAvailable() {
        return plugin.settings().placeholderEnabled
                && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null
                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public String apply(Player player, DeathChestData chest, DeathRecord record, String input, Map<String, String> extra) {
        if (input == null) {
            return "";
        }
        Map<String, String> values = builtIn(player, chest, record);
        if (player != null && (input.contains("%deathchest_estimated_price%")
                || input.contains("%deathchest_estimated_cost%"))) {
            String estimated = Texts.formatNumber(plugin.estimatedDeathPrice(player));
            values.put("deathchest_estimated_price", estimated);
            values.put("deathchest_estimated_cost", estimated);
        }
        if (player != null && input.contains("%deathchest_estimated_currency%")) {
            values.put("deathchest_estimated_currency", plugin.economy().provider().getCurrencyName());
        }
        if (extra != null) {
            values.putAll(extra);
        }
        String output = Texts.apply(input, values);
        if (player != null && papiAvailable()) {
            try {
                output = PlaceholderAPI.setPlaceholders(player, output);
            } catch (Exception exception) {
                plugin.debug("PlaceholderAPI failed: " + exception.getMessage());
            }
        }
        return output;
    }

    public List<String> applyLines(Player player, DeathChestData chest, List<String> lines) {
        List<String> output = new java.util.ArrayList<>();
        for (String line : lines) {
            output.add(apply(player, chest, null, line, Map.of()));
        }
        return output;
    }

    public String papiOrInternal(Player player, String params) {
        if (player == null) {
            return "";
        }
        List<DeathChestData> chests = plugin.chests().byOwner(player.getUniqueId());
        DeathChestData last = chests.isEmpty() ? null : chests.getFirst();
        return switch (params.toLowerCase()) {
            case "global_enabled" -> String.valueOf(plugin.settings().enabled);
            case "tab_footer" -> tabFooter(player, last);
            case "enabled" -> (plugin.settings().enabled && plugin.playerSettings().isEnabled(player.getUniqueId())) ? "true" : "false";
            case "estimated_price", "estimated_cost" -> Texts.formatNumber(plugin.estimatedDeathPrice(player));
            case "estimated_currency" -> plugin.economy().provider().getCurrencyName();
            case "count" -> String.valueOf(chests.size());
            case "last_id" -> last == null ? "" : last.getId();
            case "last_x" -> last == null ? "" : String.valueOf(last.getX());
            case "last_y" -> last == null ? "" : String.valueOf(last.getY());
            case "last_z" -> last == null ? "" : String.valueOf(last.getZ());
            case "last_world" -> last == null ? "" : last.getWorld();
            case "last_price" -> last == null ? "" : Texts.formatNumber(last.getPrice());
            case "last_protection_remaining" -> last == null ? "" : protectionRemaining(last);
            case "last_expire_remaining" -> last == null ? "" : remaining(last.getExpireAt());
            default -> "";
        };
    }

    public Map<String, String> builtIn(Player player, DeathChestData chest, DeathRecord record) {
        PluginSettings settings = plugin.settings();
        DateTimeFormatter formatter = TimeFormats.formatter(settings.dateFormat, settings.timezone);
        Map<String, String> values = new LinkedHashMap<>();
        DeathChestData sourceChest = chest;
        DeathRecord sourceRecord = record;
        if (sourceChest == null && player != null) {
            List<DeathChestData> chests = plugin.chests() == null ? List.of() : plugin.chests().byOwner(player.getUniqueId());
            if (!chests.isEmpty()) {
                sourceChest = chests.getFirst();
            }
        }
        if (sourceChest != null) {
            long now = System.currentTimeMillis();
            values.put("deathchest_id", sourceChest.getId());
            values.put("deathchest_owner", sourceChest.getOwnerName());
            values.put("deathchest_owner_uuid", sourceChest.getOwnerUuid().toString());
            values.put("deathchest_world", sourceChest.getWorld());
            values.put("deathchest_x", String.valueOf(sourceChest.getX()));
            values.put("deathchest_y", String.valueOf(sourceChest.getY()));
            values.put("deathchest_z", String.valueOf(sourceChest.getZ()));
            values.put("deathchest_price", Texts.formatNumber(sourceChest.getPrice()));
            values.put("deathchest_currency", sourceChest.getCurrency() == null ? "" : sourceChest.getCurrency());
            values.put("deathchest_created_time", TimeFormats.formatInstant(sourceChest.getCreatedAt(), formatter));
            values.put("deathchest_unlock_time", TimeFormats.formatInstant(sourceChest.getUnlockAt(), formatter));
            values.put("deathchest_expire_time", TimeFormats.formatInstant(sourceChest.getExpireAt(), formatter));
            values.put("deathchest_protection_remaining", protectionRemaining(sourceChest));
            values.put("deathchest_expire_remaining", remaining(sourceChest.getExpireAt()));
            values.put("deathchest_state", plugin.chests().stateLabel(sourceChest, now));
        }
        if (sourceRecord != null) {
            values.put("deathchest_id", sourceRecord.getDeathChestId() == null ? sourceRecord.getRecordId() : sourceRecord.getDeathChestId());
            values.put("deathchest_owner", sourceRecord.getPlayerName());
            values.put("deathchest_owner_uuid", sourceRecord.getPlayerUuid().toString());
            values.put("deathchest_world", sourceRecord.getWorld());
            values.put("deathchest_x", String.valueOf((int) Math.floor(sourceRecord.getX())));
            values.put("deathchest_y", String.valueOf((int) Math.floor(sourceRecord.getY())));
            values.put("deathchest_z", String.valueOf((int) Math.floor(sourceRecord.getZ())));
            values.put("deathchest_price", Texts.formatNumber(sourceRecord.getChargedPrice()));
            values.put("deathchest_currency", sourceRecord.getCurrencyId() == null ? "" : sourceRecord.getCurrencyId());
            values.put("deathchest_created_time", TimeFormats.formatInstant(sourceRecord.getDeathTime(), formatter));
            values.put("deathchest_unlock_time", TimeFormats.formatInstant(sourceRecord.getUnlockAt() == null ? 0L : sourceRecord.getUnlockAt(), formatter));
            values.put("deathchest_expire_time", TimeFormats.formatInstant(sourceRecord.getExpireAt() == null ? 0L : sourceRecord.getExpireAt(), formatter));
            values.put("deathchest_state", sourceRecord.getStatus().name());
            values.put("deathchest_item_count", String.valueOf(sourceRecord.getItems().size()));
            values.put("deathchest_slot_count", String.valueOf(sourceRecord.getItems().size()));
            values.put("deathchest_player_level", String.valueOf(sourceRecord.getPlayerLevelBefore()));
        }
        if (player != null) {
            values.put("player_name", player.getName());
            values.put("deathchest_player_level", String.valueOf(player.getLevel()));
            values.put("deathchest_enabled", String.valueOf(
                    settings.enabled && plugin.playerSettings().isEnabled(player.getUniqueId())));
        }
        return values;
    }

    private String tabFooter(Player player, DeathChestData last) {
        boolean globalEnabled = plugin.settings().enabled;
        boolean enabled = globalEnabled
                && plugin.playerSettings().isEnabled(player.getUniqueId());
        StringBuilder footer = new StringBuilder("<newline><gold>死亡箱：</gold>");
        if (enabled) {
            footer.append("<green>开</green>");
        } else {
            footer.append("<red>关</red>");
        }
        if (globalEnabled) {
            footer.append("<white> ｜ </white><gold>预计消耗：</gold><yellow>")
                    .append(Texts.formatNumber(plugin.estimatedDeathPrice(player))).append("🍉</yellow>");
        }
        if (last != null) {
            long now = System.currentTimeMillis();
            boolean protectedChest = last.isProtected(now);
            long target = protectedChest ? last.getUnlockAt() : last.getExpireAt();
            String label = protectedChest ? "保护剩余："
                    : plugin.settings().expireMode == com.npucraft.deathchest.model.ExpireMode.DROP_ITEMS
                    ? "掉落剩余：" : "清理剩余：";
            footer.append("<newline><gold>死亡点：</gold><aqua>")
                    .append(displayWorld(last.getWorld())).append("(")
                    .append(last.getX()).append(",").append(last.getY()).append(",").append(last.getZ())
                    .append(")</aqua><white> ｜ </white><gold>").append(label)
                    .append("</gold><yellow>")
                    .append(target <= 0L ? "永久" : TimeFormats.durationHms(TimeFormats.remaining(target, now)))
                    .append("</yellow>");
        }
        return footer.toString();
    }

    private String displayWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return worldName;
        }
        return switch (world.getEnvironment()) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> worldName;
        };
    }

    public String remaining(long target) {
        if (target <= 0L) {
            return "-";
        }
        long millis = TimeFormats.remaining(target, System.currentTimeMillis());
        if (millis <= 0L) {
            return "-";
        }
        return TimeFormats.duration(millis,
                plugin.messages().durationDays(),
                plugin.messages().durationHours(),
                plugin.messages().durationMinutesSeconds(),
                plugin.messages().durationSeconds());
    }

    public String absolute(long target) {
        return TimeFormats.formatInstant(target,
                TimeFormats.formatter(plugin.settings().dateFormat, plugin.settings().timezone));
    }

    public String protectionRemaining(DeathChestData chest) {
        if (chest == null || chest.isUnpaid() || !chest.isProtected(System.currentTimeMillis())) {
            return "-";
        }
        return remaining(chest.getUnlockAt());
    }
}

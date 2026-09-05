package com.npucraft.deathchest.command;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.model.DeathRecord;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class DeathChestTabCompleter implements TabCompleter {
    private final DeathChestPlugin plugin;

    public DeathChestTabCompleter(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.add("help");
            addIfPermitted(sender, suggestions, "on", "deathchest.toggle");
            addIfPermitted(sender, suggestions, "off", "deathchest.toggle");
            addIfPermitted(sender, suggestions, "status", "deathchest.status");
            addIfPermitted(sender, suggestions, "info", "deathchest.info");
            addIfPermitted(sender, suggestions, "unlock", "deathchest.unlock");
            addIfPermitted(sender, suggestions, "reload", "deathchest.reload");
            addIfPermitted(sender, suggestions, "tp", "deathchest.teleport");
            addIfPermitted(sender, suggestions, "restore", "deathchest.restore");
            addIfPermitted(sender, suggestions, "records", "deathchest.record");
            return partial(args[0], suggestions);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "info" -> infoTargets(sender, args[1]);
                case "unlock", "tp" -> chestIds(sender, args[1]);
                case "restore" -> players(args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "info" -> partial(args[2], List.of("all", "activate", "inactive"));
                case "restore" -> recordIds(args[1], args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4 && sub.equals("restore")) {
            return partial(args[3], List.of("all", "item", "exp", "--force"));
        }
        if (args.length == 5 && sub.equals("restore")) {
            return partial(args[4], List.of("--force"));
        }
        return List.of();
    }

    private void addIfPermitted(CommandSender sender, List<String> suggestions, String subcommand, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("deathchest.admin")) {
            suggestions.add(subcommand);
        }
    }

    private List<String> players(String token) {
        return partial(token, Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
    }

    private List<String> chestIds(CommandSender sender, String token) {
        List<String> ids = new ArrayList<>();
        if (sender instanceof Player player && !player.hasPermission("deathchest.admin")
                && !player.hasPermission("deathchest.unlock.others")) {
            plugin.chests().byOwner(player.getUniqueId()).forEach(chest -> ids.add(chest.getId()));
        } else {
            for (DeathChestData chest : plugin.chests().all()) {
                ids.add(chest.getId());
            }
        }
        return partial(token, ids);
    }

    private List<String> infoTargets(CommandSender sender, String token) {
        List<String> options = new ArrayList<>(List.of("all", "activate", "inactive"));
        if (sender.hasPermission("deathchest.info.others") || sender.hasPermission("deathchest.admin")) {
            options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return partial(token, options);
    }

    private List<String> recordIds(String playerName, String token) {
        Set<String> ids = new LinkedHashSet<>();
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) {
            int limit = plugin.settings().maxRecordsPerPlayer > 0 ? plugin.settings().maxRecordsPerPlayer : 1000;
            for (DeathRecord record : plugin.records().history(player.getUniqueId(), limit)) {
                ids.add(record.getRecordId());
                if (record.getDeathChestId() != null) {
                    ids.add(record.getDeathChestId());
                }
            }
        }
        return partial(token, new ArrayList<>(ids));
    }

    private List<String> partial(String token, List<String> options) {
        return StringUtil.copyPartialMatches(token == null ? "" : token, options, new ArrayList<>());
    }
}

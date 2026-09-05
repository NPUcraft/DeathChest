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
            addIfPermitted(sender, suggestions, "status", "deathchest.status");
            addIfPermitted(sender, suggestions, "list", "deathchest.list");
            addIfPermitted(sender, suggestions, "info", "deathchest.info");
            addIfPermitted(sender, suggestions, "unlock", "deathchest.unlock");
            addIfPermitted(sender, suggestions, "reload", "deathchest.reload");
            addIfPermitted(sender, suggestions, "history", "deathchest.history");
            addIfPermitted(sender, suggestions, "tp", "deathchest.teleport");
            addIfPermitted(sender, suggestions, "restore", "deathchest.restore");
            addIfPermitted(sender, suggestions, "records", "deathchest.record");
            return partial(args[0], suggestions);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "list", "history" -> players(args[1]);
                case "info", "unlock", "tp" -> chestIds(sender, args[1]);
                case "restore" -> recordIds(sender, args[1]);
                case "records" -> partial(args[1], List.of("stats"));
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "restore" -> partial(args[2], List.of("all", "items", "exp", "--force"));
                default -> List.of();
            };
        }
        if (args.length == 4 && sub.equals("restore")) {
            return partial(args[3], List.of("--force"));
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
                && !player.hasPermission("deathchest.list.others")) {
            plugin.chests().byOwner(player.getUniqueId()).forEach(chest -> ids.add(chest.getId()));
        } else {
            for (DeathChestData chest : plugin.chests().all()) {
                ids.add(chest.getId());
            }
        }
        return partial(token, ids);
    }

    private List<String> recordIds(CommandSender sender, String token) {
        Set<String> ids = new LinkedHashSet<>();
        if (plugin.chests() != null) {
            for (DeathChestData chest : plugin.chests().all()) {
                if (chest.getRecordId() != null) {
                    ids.add(chest.getRecordId());
                }
            }
        }
        if (sender instanceof Player player) {
            for (DeathRecord record : plugin.records().history(player.getUniqueId(), 20)) {
                ids.add(record.getRecordId());
            }
        }
        return partial(token, new ArrayList<>(ids));
    }

    private List<String> partial(String token, List<String> options) {
        return StringUtil.copyPartialMatches(token == null ? "" : token, options, new ArrayList<>());
    }
}

package com.npucraft.deathchest.command;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.model.DeathRecord;
import com.npucraft.deathchest.model.RestorePart;
import com.npucraft.deathchest.manager.RollbackManager;
import com.npucraft.deathchest.util.Texts;
import com.npucraft.deathchest.util.TimeFormats;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DeathChestCommand implements CommandExecutor {
    private final DeathChestPlugin plugin;

    public DeathChestCommand(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            plugin.audit().player(sender.getName(), "执行命令", "/" + label + " help");
            plugin.messages().sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        plugin.audit().player(sender.getName(), "执行命令", "/" + label + " " + String.join(" ", args));
        return switch (sub) {
            case "status" -> status(sender);
            case "list" -> list(sender, args);
            case "info" -> info(sender, args);
            case "unlock" -> unlock(sender, args);
            case "reload" -> reload(sender);
            case "history" -> history(sender, args);
            case "tp", "teleport" -> teleport(sender, args);
            case "restore" -> restore(sender, args);
            case "records" -> records(sender, args);
            default -> {
                plugin.messages().send(sender, "unknown-command");
                yield true;
            }
        };
    }

    private boolean status(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        if (!has(player, "deathchest.status")) {
            return true;
        }
        boolean enabled = plugin.settings().enabled && plugin.settings().defaultEnabled;
        List<DeathChestData> chests = plugin.chests().byOwner(player.getUniqueId());
        plugin.messages().send(player, "status-header");
        plugin.messages().send(player, "status-enabled", Map.of("enabled", enabled ? plugin.messages().raw("enabled-yes", "on") : plugin.messages().raw("enabled-no", "off")));
        plugin.messages().send(player, "status-active-count", Map.of("count", String.valueOf(chests.size())));
        if (chests.isEmpty()) {
            plugin.messages().send(player, "status-none");
        } else {
            DeathChestData last = chests.getFirst();
            plugin.messages().send(player, "status-last-id", Map.of("id", last.getId()));
            plugin.messages().send(player, "status-last-location", Map.of(
                    "world", last.getWorld(),
                    "x", String.valueOf(last.getX()),
                    "y", String.valueOf(last.getY()),
                    "z", String.valueOf(last.getZ())
            ));
        }
        return true;
    }

    private boolean list(CommandSender sender, String[] args) {
        UUID target;
        String name;
        if (args.length >= 2) {
            if (!sender.hasPermission("deathchest.list.others") && !sender.hasPermission("deathchest.admin")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            OfflinePlayer offline = findPlayer(args[1]);
            if (offline == null) {
                plugin.messages().send(sender, "player-not-found", Map.of("player", args[1]));
                return true;
            }
            target = offline.getUniqueId();
            name = offline.getName() == null ? args[1] : offline.getName();
        } else {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "player-only");
                return true;
            }
            if (!has(player, "deathchest.list")) {
                return true;
            }
            target = player.getUniqueId();
            name = player.getName();
        }
        List<DeathChestData> chests = plugin.chests().byOwner(target);
        plugin.messages().send(sender, "list-header", Map.of("player", name));
        if (chests.isEmpty()) {
            plugin.messages().send(sender, "list-empty");
            return true;
        }
        long now = System.currentTimeMillis();
        for (DeathChestData chest : chests) {
            plugin.messages().send(sender, "list-entry", Map.of(
                    "id", chest.getId(),
                    "world", chest.getWorld(),
                    "x", String.valueOf(chest.getX()),
                    "y", String.valueOf(chest.getY()),
                    "z", String.valueOf(chest.getZ()),
                    "state", plugin.chests().stateLabel(chest, now),
                    "items", String.valueOf(plugin.chests().currentItems(chest).size())
            ));
        }
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().sendHelp(sender);
            return true;
        }
        if (!sender.hasPermission("deathchest.info") && !sender.hasPermission("deathchest.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        Optional<DeathChestData> optional = plugin.chests().byId(args[1]);
        if (optional.isEmpty()) {
            plugin.messages().send(sender, "invalid-id", Map.of("id", args[1]));
            return true;
        }
        DeathChestData chest = optional.get();
        if (sender instanceof Player player
                && !player.getUniqueId().equals(chest.getOwnerUuid())
                && !player.hasPermission("deathchest.list.others")
                && !player.hasPermission("deathchest.admin")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        DateTimeFormatter formatter = TimeFormats.formatter(plugin.settings().dateFormat, plugin.settings().timezone);
        plugin.messages().send(sender, "info-header", Map.of("id", chest.getId()));
        plugin.messages().send(sender, "info-owner", Map.of("owner", chest.getOwnerName()));
        plugin.messages().send(sender, "info-location", Map.of(
                "world", chest.getWorld(),
                "x", String.valueOf(chest.getX()),
                "y", String.valueOf(chest.getY()),
                "z", String.valueOf(chest.getZ())
        ));
        plugin.messages().send(sender, "info-type", Map.of("type", chest.getChestType().name()));
        plugin.messages().send(sender, "info-price", Map.of("price", Texts.formatNumber(chest.getPrice()), "currency", String.valueOf(chest.getCurrency())));
        plugin.messages().send(sender, "info-state", Map.of("state", plugin.chests().stateLabel(chest, System.currentTimeMillis())));
        plugin.messages().send(sender, "info-created", Map.of("created", TimeFormats.formatInstant(chest.getCreatedAt(), formatter)));
        plugin.messages().send(sender, "info-unlock", Map.of("unlock", TimeFormats.formatInstant(chest.getUnlockAt(), formatter)));
        plugin.messages().send(sender, "info-expire", Map.of("expire", TimeFormats.formatInstant(chest.getExpireAt(), formatter)));
        if (!(sender instanceof Player player)) {
            return true;
        }
        Optional<DeathRecord> record = chest.getRecordId() == null ? Optional.empty() : plugin.records().get(chest.getRecordId());
        if (record.isEmpty()) {
            plugin.messages().send(player, "info-no-snapshot");
            return true;
        }
        openItems(player, record.get(), 1);
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!hasAny(sender, "deathchest.reload")) {
            return true;
        }
        plugin.reloadPlugin();
        plugin.messages().send(sender, "reload-success");
        return true;
    }

    private boolean history(CommandSender sender, String[] args) {
        if (!hasAny(sender, "deathchest.history")) {
            return true;
        }
        OfflinePlayer target;
        if (args.length >= 2) {
            if (!sender.hasPermission("deathchest.history.others") && !sender.hasPermission("deathchest.admin")
                    && !(sender instanceof Player player && player.getName().equalsIgnoreCase(args[1]))) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            target = findPlayer(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "player-not-found", Map.of("player", args[1]));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        List<DeathRecord> records = plugin.records().history(target.getUniqueId(), 15);
        plugin.messages().send(sender, "history-header", Map.of("player", target.getName() == null ? args[args.length - 1] : target.getName()));
        if (records.isEmpty()) {
            plugin.messages().send(sender, "history-empty");
            return true;
        }
        DateTimeFormatter formatter = TimeFormats.formatter(plugin.settings().dateFormat, plugin.settings().timezone);
        for (DeathRecord record : records) {
            plugin.messages().send(sender, "history-entry", Map.of(
                    "id", record.getRecordId(),
                    "time", TimeFormats.formatInstant(record.getDeathTime(), formatter),
                    "cause", String.valueOf(record.getDeathCause()),
                    "status", record.getStatus().name(),
                    "items", String.valueOf(record.getItems().size()),
                    "price", Texts.formatNumber(record.getChargedPrice())
            ));
        }
        return true;
    }

    public void openItems(Player player, DeathRecord record, int page) {
        Component title = plugin.messages().component(player, plugin.messages().raw("record-items-title", "Items"), plugin.messages().map(
                "id", record.getRecordId(),
                "page", String.valueOf(page),
                "pages", String.valueOf(Math.max(1, (int) Math.ceil(record.getItems().size() / 45.0D)))
        ), false);
        ReadOnlyItemsGui gui = new ReadOnlyItemsGui(record, page, title,
                plugin.messages().raw("gui-prev-page", "上一页"),
                plugin.messages().raw("gui-next-page", "下一页"),
                plugin.messages().raw("gui-readonly-hint", "只读预览，无法取出物品"));
        player.openInventory(gui.getInventory());
    }

    private boolean teleport(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        if (!hasAny(player, "deathchest.teleport")) {
            return true;
        }
        if (args.length < 2) {
            plugin.messages().sendHelp(player);
            return true;
        }
        Optional<DeathChestData> optional = plugin.chests().byId(args[1]);
        if (optional.isEmpty()) {
            plugin.messages().send(player, "invalid-id", Map.of("id", args[1]));
            return true;
        }
        DeathChestData chest = optional.get();
        World world = Bukkit.getWorld(chest.getWorld());
        if (world == null) {
            plugin.messages().send(player, "world-unloaded", Map.of("world", String.valueOf(chest.getWorld()), "id", chest.getId()));
            return true;
        }
        Location location = new Location(world, chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 0.5);
        player.teleportAsync(location);
        plugin.messages().send(player, "admin-teleported", Map.of("id", chest.getId()));
        return true;
    }

    private boolean unlock(CommandSender sender, String[] args) {
        if (!hasAny(sender, "deathchest.unlock")) {
            return true;
        }
        if (args.length < 2) {
            plugin.messages().sendHelp(sender);
            return true;
        }
        Optional<DeathChestData> optional = plugin.chests().byId(args[1]);
        if (optional.isEmpty()) {
            plugin.messages().send(sender, "invalid-id", Map.of("id", args[1]));
            return true;
        }
        DeathChestData chest = optional.get();
        boolean owner = sender instanceof Player player && player.getUniqueId().equals(chest.getOwnerUuid());
        if (!owner && !sender.hasPermission("deathchest.admin") && !sender.hasPermission("deathchest.unlock.others")) {
            plugin.messages().send(sender, "unlock-not-owner");
            return true;
        }
        if (chest.isLocked()) {
            plugin.messages().send(sender, "retrieve-locked");
            return true;
        }
        if (!chest.isProtected(System.currentTimeMillis())) {
            plugin.messages().send(sender, "unlock-already", Map.of("id", chest.getId()));
            return true;
        }
        plugin.chests().unlock(chest, sender);
        plugin.messages().send(sender, "unlock-success", Map.of("id", chest.getId()));
        return true;
    }

    private boolean restore(CommandSender sender, String[] args) {
        if (!hasAny(sender, "deathchest.restore")) {
            return true;
        }
        if (args.length < 2) {
            plugin.messages().sendHelp(sender);
            return true;
        }
        boolean force = false;
        RestorePart part = RestorePart.ALL;
        for (int i = 2; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            if (arg.equals("--force") || arg.equals("-force") || arg.equals("force")) {
                force = true;
            } else if (arg.equals("items")) {
                part = RestorePart.ITEMS;
            } else if (arg.equals("exp")) {
                part = RestorePart.EXP;
            } else if (arg.equals("all")) {
                part = RestorePart.ALL;
            }
        }
        if (force && !plugin.settings().allowForce) {
            plugin.messages().send(sender, "restore-force-disabled");
            return true;
        }
        if (force && !sender.hasPermission("deathchest.restore.force") && !sender.hasPermission("deathchest.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if ((part == RestorePart.EXP || part == RestorePart.ALL)
                && !sender.hasPermission("deathchest.restore.exp")
                && !sender.hasPermission("deathchest.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        Optional<DeathRecord> optional = plugin.records().get(args[1]);
        if (optional.isEmpty()) {
            plugin.messages().send(sender, "invalid-id", Map.of("id", args[1]));
            return true;
        }
        RollbackManager.RestoreOutcome outcome = plugin.rollback().restore(sender, optional.get(), part, force);
        plugin.messages().send(sender, outcome.messageKey(), Map.of("id", optional.get().getRecordId(), "parts", outcome.parts()));
        if (force && outcome.success()) {
            plugin.messages().send(sender, "restore-force-warn");
        }
        if (outcome.expSkippedOffline()) {
            plugin.messages().send(sender, "restore-player-offline-exp");
        }
        if (outcome.usedRecovery()) {
            Player online = Bukkit.getPlayer(optional.get().getPlayerUuid());
            if (online == null) {
                plugin.messages().send(sender, "restore-offline");
            }
        }
        return true;
    }

    private boolean records(CommandSender sender, String[] args) {
        if (!hasAny(sender, "deathchest.record")) {
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("stats")) {
            plugin.messages().sendHelp(sender);
            return true;
        }
        plugin.messages().send(sender, "stats-header");
        plugin.messages().send(sender, "stats-line", Map.of(
                "total", String.valueOf(plugin.storage().countRecords()),
                "chests", String.valueOf(plugin.chests().all().size()),
                "recovery", String.valueOf(plugin.storage().countRecovery())
        ));
        return true;
    }

    public void handleGuiClick(Player player, ReadOnlyItemsGui gui, int slot) {
        if (slot == 45 && gui.page() > 1) {
            plugin.records().get(gui.recordId()).ifPresent(record -> openItems(player, record, gui.page() - 1));
        } else if (slot == 53 && gui.page() < gui.pages()) {
            plugin.records().get(gui.recordId()).ifPresent(record -> openItems(player, record, gui.page() + 1));
        }
    }

    private Player asPlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.messages().send(sender, "player-only");
        return null;
    }

    private boolean hasAny(CommandSender sender, String... permissions) {
        if (sender.hasPermission("deathchest.admin")) {
            return true;
        }
        for (String permission : permissions) {
            if (sender.hasPermission(permission)) {
                return true;
            }
        }
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    private boolean has(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("deathchest.admin")) {
            return true;
        }
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) {
                return offline;
            }
        }
        return null;
    }
}

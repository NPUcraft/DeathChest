package com.npucraft.deathchest.command;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.model.DeathRecord;
import com.npucraft.deathchest.model.RestorePart;
import com.npucraft.deathchest.manager.RollbackManager;
import com.npucraft.deathchest.util.Texts;
import com.npucraft.deathchest.util.TimeFormats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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
            case "on" -> toggle(sender, true);
            case "off" -> toggle(sender, false);
            case "status" -> status(sender);
            case "info" -> info(sender, args);
            case "unlock" -> unlock(sender, args);
            case "reload" -> reload(sender);
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
        boolean enabled = plugin.settings().enabled && plugin.playerSettings().isEnabled(player.getUniqueId());
        List<DeathChestData> chests = plugin.chests().byOwner(player.getUniqueId());
        plugin.messages().send(player, "status-header");
        plugin.messages().send(player, "status-enabled", Map.of("enabled", enabled ? plugin.messages().raw("enabled-yes", "on") : plugin.messages().raw("enabled-no", "off")));
        plugin.messages().send(player, "status-estimated-price", Map.of(
                "price", Texts.formatNumber(plugin.estimatedDeathPrice(player)),
                "currency", plugin.economy().provider().getCurrencyName()
        ));
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

    private boolean toggle(CommandSender sender, boolean enabled) {
        Player player = asPlayer(sender);
        if (player == null || !has(player, "deathchest.toggle")) {
            return true;
        }
        if (!plugin.settings().allowToggle) {
            plugin.messages().send(player, "toggle-disabled");
            return true;
        }
        if (plugin.playerSettings().isEnabled(player.getUniqueId()) == enabled) {
            plugin.messages().send(player, enabled ? "toggle-already-on" : "toggle-already-off");
            return true;
        }
        try {
            plugin.playerSettings().setEnabled(player, enabled);
            plugin.messages().send(player, enabled ? "toggle-on" : "toggle-off");
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to save DeathChest setting for " + player.getName() + ": " + exception.getMessage());
            plugin.messages().send(player, "toggle-failed");
        }
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        Player viewer = asPlayer(sender);
        if (viewer == null || !has(viewer, "deathchest.info")) {
            return true;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("view")) {
            Optional<DeathRecord> selected = plugin.records().get(args[2]);
            if (selected.isEmpty()) {
                plugin.messages().send(viewer, "invalid-id", Map.of("id", args[2]));
                return true;
            }
            if (!canView(viewer, selected.get())) {
                plugin.messages().send(viewer, "no-permission");
                return true;
            }
            openItems(viewer, selected.get(), 1);
            return true;
        }

        OfflinePlayer target = viewer;
        String mode = null;
        if (args.length >= 2) {
            if (isInfoMode(args[1])) {
                mode = args[1].toLowerCase(Locale.ROOT);
            } else {
                if (!viewer.hasPermission("deathchest.info.others") && !viewer.hasPermission("deathchest.admin")) {
                    plugin.messages().send(viewer, "no-permission");
                    return true;
                }
                target = findPlayer(args[1]);
                if (target == null) {
                    plugin.messages().send(viewer, "player-not-found", Map.of("player", args[1]));
                    return true;
                }
                if (args.length >= 3) {
                    if (!isInfoMode(args[2])) {
                        plugin.messages().send(viewer, "info-invalid-filter");
                        return true;
                    }
                    mode = args[2].toLowerCase(Locale.ROOT);
                }
            }
        }

        int limit = plugin.settings().maxRecordsPerPlayer > 0 ? plugin.settings().maxRecordsPerPlayer : 1000;
        List<DeathRecord> records = plugin.records().history(target.getUniqueId(), limit).stream()
                .filter(DeathRecord::isDeathChestCreated)
                .toList();
        if (mode == null) {
            if (records.isEmpty()) {
                plugin.messages().send(viewer, "info-empty");
            } else {
                openItems(viewer, records.getFirst(), 1);
            }
            return true;
        }

        String selectedMode = mode;
        List<DeathRecord> filtered = records.stream().filter(record -> matchesInfoMode(record, selectedMode)).toList();
        String targetName = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        plugin.messages().send(viewer, "info-list-header", Map.of("player", targetName, "filter", mode));
        if (filtered.isEmpty()) {
            plugin.messages().send(viewer, "info-empty");
            return true;
        }
        DateTimeFormatter formatter = TimeFormats.formatter(plugin.settings().dateFormat, plugin.settings().timezone);
        for (DeathRecord record : filtered) {
            boolean active = !plugin.chests().byRecord(record.getRecordId()).isEmpty();
            Map<String, String> values = Map.of(
                    "id", record.getDeathChestId() == null ? record.getRecordId() : record.getDeathChestId(),
                    "time", TimeFormats.formatInstant(record.getDeathTime(), formatter),
                    "state", infoState(record, active),
                    "items", String.valueOf(record.getItems().size()),
                    "price", Texts.formatNumber(record.getChargedPrice())
            );
            Component line = plugin.messages().component(viewer,
                            plugin.messages().raw("info-list-entry", "%id%"), values, false)
                    .clickEvent(ClickEvent.runCommand("/deathchest info view " + record.getRecordId()))
                    .hoverEvent(HoverEvent.showText(plugin.messages().component(viewer,
                            plugin.messages().raw("info-list-hover", "点击预览"), Map.of(), false)));
            viewer.sendMessage(line);
        }
        return true;
    }

    private boolean isInfoMode(String value) {
        return value.equalsIgnoreCase("all") || value.equalsIgnoreCase("activate")
                || value.equalsIgnoreCase("inactive");
    }

    private boolean matchesInfoMode(DeathRecord record, String mode) {
        boolean active = !plugin.chests().byRecord(record.getRecordId()).isEmpty();
        return mode.equals("all") || (mode.equals("activate") && active)
                || (mode.equals("inactive") && !active && record.getStatus() != com.npucraft.deathchest.model.RecordStatus.EXPIRED);
    }

    private String infoState(DeathRecord record, boolean active) {
        if (active) {
            return plugin.messages().raw("info-state-active", "活动");
        }
        return switch (record.getStatus()) {
            case EXPIRED -> plugin.messages().raw("info-state-expired", "已过期");
            case ADMIN_RESTORED, ROLLED_BACK, PARTIALLY_RESTORED -> plugin.messages().raw("info-state-restored", "已恢复");
            default -> plugin.messages().raw("info-state-inactive", "已提取");
        };
    }

    private boolean canView(Player viewer, DeathRecord record) {
        return viewer.getUniqueId().equals(record.getPlayerUuid())
                || viewer.hasPermission("deathchest.info.others") || viewer.hasPermission("deathchest.admin");
    }

    private boolean reload(CommandSender sender) {
        if (!hasAny(sender, "deathchest.reload")) {
            return true;
        }
        plugin.reloadPlugin();
        plugin.messages().send(sender, "reload-success");
        return true;
    }

    public void openItems(Player player, DeathRecord record, int page) {
        Component title = plugin.messages().component(player, plugin.messages().raw("record-items-title", "Items"), plugin.messages().map(
                "id", record.getRecordId(),
                "page", String.valueOf(page),
                "pages", String.valueOf(Math.max(1, (int) Math.ceil(record.getItems().size() / 45.0D)))
        ), false);
        boolean adminControls = player.hasPermission("deathchest.restore")
                || player.hasPermission("deathchest.admin");
        ReadOnlyItemsGui gui = new ReadOnlyItemsGui(record, page, title, plugin.messages(), adminControls);
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
        if (args.length < 3) {
            plugin.messages().sendHelp(sender);
            return true;
        }
        OfflinePlayer selectedPlayer = findPlayer(args[1]);
        if (selectedPlayer == null) {
            plugin.messages().send(sender, "player-not-found", Map.of("player", args[1]));
            return true;
        }
        Player target = Bukkit.getPlayer(selectedPlayer.getUniqueId());
        if (target == null || !target.isOnline()) {
            plugin.messages().send(sender, "restore-target-offline", Map.of("player", args[1]));
            return true;
        }
        Optional<DeathRecord> optional = plugin.records().get(args[2]);
        if (optional.isEmpty()) {
            optional = plugin.chests().byId(args[2])
                    .flatMap(chest -> plugin.records().get(chest.getRecordId()));
        }
        if (optional.isEmpty()) {
            optional = plugin.storage().loadRecordByChestId(args[2]);
        }
        if (optional.isEmpty()) {
            plugin.messages().send(sender, "invalid-id", Map.of("id", args[2]));
            return true;
        }
        if (!optional.get().getPlayerUuid().equals(target.getUniqueId())) {
            plugin.messages().send(sender, "restore-player-mismatch", Map.of(
                    "player", target.getName(), "id", args[2]));
            return true;
        }
        boolean force = false;
        RestorePart part = RestorePart.ALL;
        boolean partSpecified = false;
        for (int i = 3; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            if (arg.equals("--force")) {
                if (force) {
                    plugin.messages().sendHelp(sender);
                    return true;
                }
                force = true;
            } else {
                if (partSpecified) {
                    plugin.messages().sendHelp(sender);
                    return true;
                }
                part = switch (arg) {
                    case "item" -> RestorePart.ITEMS;
                    case "exp" -> RestorePart.EXP;
                    case "all" -> RestorePart.ALL;
                    default -> null;
                };
                if (part == null) {
                    plugin.messages().sendHelp(sender);
                    return true;
                }
                partSpecified = true;
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
        RollbackManager.RestoreOutcome outcome = plugin.rollback().restore(sender, target, optional.get(), part, force);
        Map<String, String> messageValues = Map.of(
                "id", optional.get().getRecordId(),
                "parts", outcome.parts(),
                "player", target.getName()
        );
        plugin.messages().send(sender, outcome.messageKey(), messageValues);
        if (sender != target && (outcome.success()
                || outcome.messageKey().equals("restore-inventory-full")
                || outcome.messageKey().equals("restore-snapshot-too-large"))) {
            plugin.messages().send(target, outcome.messageKey(), messageValues);
        }
        return true;
    }

    private boolean records(CommandSender sender, String[] args) {
        if (!hasAny(sender, "deathchest.record")) {
            return true;
        }
        if (args.length != 1) {
            plugin.messages().send(sender, "unknown-command");
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
        } else {
            String restore = gui.restoreCommand(slot);
            if (restore != null) {
                player.closeInventory();
                player.performCommand(restore);
            }
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

package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.model.DeathChestData;
import com.npucraft.deathchest.util.Texts;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public final class HologramManager {
    private final DeathChestPlugin plugin;
    private int taskId = -1;

    public HologramManager(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.settings().hologramEnabled) {
            return;
        }
        int interval = plugin.settings().hologramUpdateInterval;
        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::updateAll, interval, interval);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public void spawn(DeathChestData chest) {
        if (!plugin.settings().hologramEnabled) {
            return;
        }
        World world = Bukkit.getWorld(chest.getWorld());
        if (world == null) {
            return;
        }
        remove(chest);
        Location location = chest.hologramLocation(world, plugin.settings().hologramHeight);
        if (!location.getChunk().isLoaded()) {
            return;
        }
        removeOrphans(chest, location);
        try {
            TextDisplay display = world.spawn(location, TextDisplay.class, spawned -> {
                spawned.setPersistent(true);
                spawned.setInvulnerable(true);
                spawned.setGravity(false);
                spawned.setSilent(true);
                spawned.setSeeThrough(false);
                spawned.setShadowed(true);
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
                spawned.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                spawned.setLineWidth(200);
                spawned.getPersistentDataContainer().set(plugin.keys().hologramMarker, PersistentDataType.STRING, chest.getId());
                spawned.text(text(chest));
            });
            chest.setHologramId(display.getUniqueId());
            plugin.storage().setChestHologram(chest.getId(), display.getUniqueId());
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to create hologram for " + chest.getId() + ": " + exception.getMessage());
        }
    }

    public void update(DeathChestData chest) {
        if (!plugin.settings().hologramEnabled || chest.getHologramId() == null) {
            if (plugin.settings().hologramEnabled) {
                spawn(chest);
            }
            return;
        }
        Entity entity = Bukkit.getEntity(chest.getHologramId());
        if (!(entity instanceof TextDisplay display) || display.isDead()) {
            spawn(chest);
            return;
        }
        display.text(text(chest));
    }

    public void remove(DeathChestData chest) {
        if (chest.getHologramId() == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(chest.getHologramId());
        if (entity != null) {
            entity.remove();
        }
        chest.setHologramId(null);
        try {
            plugin.storage().setChestHologram(chest.getId(), null);
        } catch (RuntimeException ignored) {
        }
    }

    private void removeOrphans(DeathChestData chest, Location location) {
        for (Entity entity : location.getWorld().getNearbyEntities(location, 2.5, 2.5, 2.5)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            String id = display.getPersistentDataContainer().get(plugin.keys().hologramMarker, PersistentDataType.STRING);
            if (chest.getId().equals(id)) {
                display.remove();
            }
        }
    }

    public void removeAll() {
        for (DeathChestData chest : plugin.chests().all()) {
            remove(chest);
        }
    }

    public void restoreInWorld(World world) {
        if (!plugin.settings().hologramEnabled || world == null) {
            return;
        }
        for (DeathChestData chest : plugin.chests().all()) {
            if (!world.getName().equals(chest.getWorld())) {
                continue;
            }
            Location location = chest.hologramLocation(world, plugin.settings().hologramHeight);
            if (location.getChunk().isLoaded()) {
                update(chest);
            }
        }
    }

    private void updateAll() {
        for (DeathChestData chest : plugin.chests().all()) {
            World world = Bukkit.getWorld(chest.getWorld());
            if (world == null) {
                continue;
            }
            Location location = chest.primaryLocation(world);
            if (!location.getChunk().isLoaded()) {
                continue;
            }
            update(chest);
        }
    }

    private Component text(DeathChestData chest) {
        long now = System.currentTimeMillis();
        List<String> lines;
        if (chest.isUnpaid()) {
            lines = plugin.messages().hologramLines("unpaid");
        } else if (chest.isProtected(now)) {
            lines = plugin.messages().hologramLines("protected");
        } else {
            lines = plugin.messages().hologramLines("public");
        }
        List<String> parsed = plugin.placeholders().applyLines(Bukkit.getPlayer(chest.getOwnerUuid()), chest, lines);
        Component component = Component.empty();
        for (int i = 0; i < parsed.size(); i++) {
            if (i > 0) {
                component = component.append(Component.newline());
            }
            component = component.append(Texts.mini(parsed.get(i)));
        }
        return component;
    }
}

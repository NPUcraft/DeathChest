package com.npucraft.deathchest.manager;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.config.MessageManager;
import com.npucraft.deathchest.config.PluginSettings;
import com.npucraft.deathchest.model.DeathRecord;
import com.npucraft.deathchest.model.RecordStatus;
import com.npucraft.deathchest.util.DeathItemRules;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KeptInventoryDeathTest {
    @Test
    void keptInventoryIsBackedUpWithoutMovingItemsOrCreatingChest() {
        assertKeptInventoryBackup(true);
    }

    @Test
    void playerDisablingChestStillGetsKeptInventoryBackup() {
        assertKeptInventoryBackup(false);
    }

    private void assertKeptInventoryBackup(boolean chestEnabled) {
        DeathChestPlugin plugin = mock(DeathChestPlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        PluginSettings settings = new PluginSettings(plugin);
        when(plugin.settings()).thenReturn(settings);
        DeathRecordManager records = mock(DeathRecordManager.class);
        PlayerSettingsManager preferences = mock(PlayerSettingsManager.class);
        when(plugin.records()).thenReturn(records);
        when(plugin.playerSettings()).thenReturn(preferences);
        when(preferences.isEnabled(any(UUID.class))).thenReturn(chestEnabled);
        when(plugin.audit()).thenReturn(mock(AuditLogger.class));
        when(plugin.messages()).thenReturn(mock(MessageManager.class));

        Player player = mock(Player.class);
        when(player.hasPermission("deathchest.use")).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack[] contents = new ItemStack[41];
        ItemStack storage = item();
        ItemStack armor = item();
        ItemStack offhand = item();
        contents[0] = storage;
        contents[38] = armor;
        contents[40] = offhand;
        when(inventory.getContents()).thenReturn(contents);

        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getEntity()).thenReturn(player);
        when(event.getKeepInventory()).thenReturn(true);
        List<ItemStack> drops = new ArrayList<>();
        when(event.getDrops()).thenReturn(drops);
        DeathRecord record = new DeathRecord();
        when(records.createPrepared(eq(player), eq(event), anyList())).thenAnswer(invocation -> {
            record.setItems(invocation.getArgument(2));
            return record;
        });

        new DeathChestTransaction(plugin).handle(event);

        assertEquals(3, record.getItems().size());
        assertNotSame(storage, record.getItems().get(0));
        assertEquals(RecordStatus.NORMAL_DROP, record.getStatus());
        assertEquals(chestEnabled ? "KEEP_INVENTORY" : "PLAYER_DISABLED", record.getFailureReason());
        assertFalse(record.isDeathChestCreated());
        verify(records).save(record);
        verify(inventory, never()).clear();
        verify(inventory, never()).setContents(any());
        verify(plugin, never()).chests();
        verify(plugin, never()).recovery();
        verify(plugin, never()).economy();
        assertTrue(drops.isEmpty());
        assertSame(storage, contents[0]);
        assertSame(armor, contents[38]);
        assertSame(offhand, contents[40]);
    }

    @Test
    void normalDropBackupDoesNotSubstitutePlayerInventory() {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        ItemStack drop = item();
        when(event.getDrops()).thenReturn(new ArrayList<>(List.of(drop)));
        List<ItemStack> snapshot = DeathItemRules.snapshotForSkippedDeath(event);
        assertEquals(1, snapshot.size());
        assertNotSame(drop, snapshot.get(0));
        assertEquals(List.of(drop), event.getDrops());
        verify(event, never()).getEntity();
    }

    private ItemStack item() {
        // Material.isAir() uses Paper's server registry; mock it in this server-free unit test.
        Material type = mock(Material.class);
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        when(item.getAmount()).thenReturn(1);
        ItemStack clone = mock(ItemStack.class);
        when(clone.getType()).thenReturn(type);
        when(clone.getAmount()).thenReturn(1);
        when(item.clone()).thenReturn(clone);
        // DeathRecord also deep-copies its items.
        when(clone.clone()).thenReturn(clone);
        return item;
    }
}

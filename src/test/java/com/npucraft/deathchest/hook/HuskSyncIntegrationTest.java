package com.npucraft.deathchest.hook;

import com.npucraft.deathchest.DeathChestPlugin;
import com.npucraft.deathchest.config.PluginSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HuskSyncIntegrationTest {
    @Test
    void legacyConfigEnablesGuardAndFailureCancelsUnsafeApplication() throws Exception {
        DeathChestPlugin plugin = plugin(new YamlConfiguration());
        assertTrue(plugin.settings().huskSyncPreventSyncDeath);
        HuskSyncHealthGuard guard = mock(HuskSyncHealthGuard.class);
        when(guard.protect(any())).thenThrow(new ReflectiveOperationException("incompatible snapshot"));
        Event event = event();

        new HuskSyncIntegration(plugin).handle(event, guard);

        verify((Cancellable) event).setCancelled(true);
    }

    @Test
    void disablingGuardDoesNotEditOrCancelSync() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("integrations.husksync.prevent-sync-death", false);
        HuskSyncHealthGuard guard = mock(HuskSyncHealthGuard.class);
        Event event = event();
        new HuskSyncIntegration(plugin(config)).handle(event, guard);
        verifyNoInteractions(guard);
        verify((Cancellable) event, never()).setCancelled(anyBoolean());
    }

    @Test
    void respectsCancellationByOtherPlugins() {
        HuskSyncHealthGuard guard = mock(HuskSyncHealthGuard.class);
        Event event = event();
        when(((Cancellable) event).isCancelled()).thenReturn(true);
        new HuskSyncIntegration(plugin(new YamlConfiguration())).handle(event, guard);
        verifyNoInteractions(guard);
    }

    private Event event() {
        return mock(Event.class, withSettings().extraInterfaces(Cancellable.class));
    }

    private DeathChestPlugin plugin(YamlConfiguration config) {
        DeathChestPlugin plugin = mock(DeathChestPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        PluginSettings settings = new PluginSettings(plugin);
        when(plugin.settings()).thenReturn(settings);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        return plugin;
    }
}

package org.fourz.RVNKLore.integration.dynmap;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DynmapIntegration lifecycle.
 */
@DisplayName("DynmapIntegration")
@ExtendWith(MockitoExtension.class)
class DynmapIntegrationTest {

    @Mock private RVNKLore plugin;
    @Mock private ConfigManager configManager;
    @Mock private org.bukkit.Server server;
    @Mock private org.bukkit.plugin.PluginManager pluginManager;
    @Mock private java.util.logging.Logger javaLogger;

    private DynmapIntegration integration;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getConfigManager()).thenReturn(configManager);
        lenient().when(plugin.getServer()).thenReturn(server);
        lenient().when(plugin.getLogger()).thenReturn(javaLogger);
        lenient().when(server.getPluginManager()).thenReturn(pluginManager);
        integration = new DynmapIntegration(plugin);
    }

    @Test
    @DisplayName("Not enabled when Dynmap plugin is absent")
    void notEnabledWhenDynmapAbsent() {
        when(configManager.isDynmapEnabled()).thenReturn(true);
        when(pluginManager.getPlugin("dynmap")).thenReturn(null);

        boolean result = integration.activate();

        assertFalse(result);
        assertFalse(integration.isEnabled());
        assertNull(integration.getMarkerManager());
    }

    @Test
    @DisplayName("Not enabled when disabled in config")
    void notEnabledWhenDisabledInConfig() {
        when(configManager.isDynmapEnabled()).thenReturn(false);

        boolean result = integration.activate();

        assertFalse(result);
        assertFalse(integration.isEnabled());
    }

    @Test
    @DisplayName("Cleanup resets all state")
    void cleanupResetsState() {
        integration.cleanup();

        assertFalse(integration.isEnabled());
        assertNull(integration.getMarkerManager());
    }
}

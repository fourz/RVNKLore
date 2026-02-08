package org.fourz.RVNKLore.integration.dynmap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.config.ConfigManager;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Manages Dynmap API lifecycle using the DynmapCommonAPIListener pattern.
 * Listens for Dynmap enable/disable events to safely initialize/cleanup markers.
 */
public class DynmapIntegration implements Listener {

    private final RVNKLore plugin;
    private final LogManager logger;
    private DynmapCommonAPI dynmapApi;
    private MarkerAPI markerApi;
    private MarkerSet markerSet;
    private LoreMarkerManager markerManager;
    private boolean enabled = false;

    public DynmapIntegration(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DynmapIntegration");
    }

    /**
     * Attempt to activate Dynmap integration.
     * Called during plugin enable or when Dynmap is detected.
     *
     * @return true if integration was activated successfully
     */
    public boolean activate() {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isDynmapEnabled()) {
            logger.info("Dynmap integration disabled in config");
            return false;
        }

        org.bukkit.plugin.Plugin dynmapPlugin = plugin.getServer().getPluginManager().getPlugin("dynmap");
        if (dynmapPlugin == null || !dynmapPlugin.isEnabled()) {
            logger.info("Dynmap not found - map marker support disabled");
            return false;
        }

        try {
            dynmapApi = (DynmapCommonAPI) dynmapPlugin;
            markerApi = dynmapApi.getMarkerAPI();
            if (markerApi == null) {
                logger.warning("Dynmap MarkerAPI not available");
                return false;
            }

            initMarkerSet();
            markerManager = new LoreMarkerManager(plugin, markerApi, markerSet);
            markerManager.populateAllMarkers();
            enabled = true;
            logger.info("Dynmap integration enabled - marker set '" + config.getDynmapMarkerSetId() + "' active");
            return true;
        } catch (Exception e) {
            logger.warning("Failed to initialize Dynmap integration: " + e.getMessage());
            cleanup();
            return false;
        }
    }

    private void initMarkerSet() {
        ConfigManager config = plugin.getConfigManager();
        String setId = config.getDynmapMarkerSetId();
        String setLabel = config.getDynmapMarkerSetLabel();

        markerSet = markerApi.getMarkerSet(setId);
        if (markerSet == null) {
            markerSet = markerApi.createMarkerSet(setId, setLabel, null, false);
        }

        if (markerSet != null) {
            markerSet.setMarkerSetLabel(setLabel);
            markerSet.setHideByDefault(config.isDynmapMarkerSetHidden());
            markerSet.setLayerPriority(config.getDynmapLayerPriority());
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if ("dynmap".equalsIgnoreCase(event.getPlugin().getName()) && !enabled) {
            logger.info("Dynmap loaded - attempting late integration");
            activate();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if ("dynmap".equalsIgnoreCase(event.getPlugin().getName()) && enabled) {
            logger.info("Dynmap unloaded - cleaning up integration");
            cleanup();
        }
    }

    public void cleanup() {
        if (markerManager != null) {
            markerManager.cleanup();
            markerManager = null;
        }
        markerSet = null;
        markerApi = null;
        dynmapApi = null;
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LoreMarkerManager getMarkerManager() {
        return markerManager;
    }
}

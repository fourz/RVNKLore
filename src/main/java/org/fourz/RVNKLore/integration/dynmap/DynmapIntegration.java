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
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.EnumMap;
import java.util.Map;

/**
 * Manages Dynmap API lifecycle using the DynmapCommonAPIListener pattern.
 * Listens for Dynmap enable/disable events to safely initialize/cleanup markers.
 */
public class DynmapIntegration implements Listener {

    private final RVNKLore plugin;
    private final LogManager logger;
    private DynmapCommonAPI dynmapApi;
    private MarkerAPI markerApi;
    private Map<LoreType, MarkerSet> loreMarkerSets;
    private MarkerSet collectionMarkerSet;
    private LoreMarkerManager markerManager;
    private CollectionMarkerManager collectionMarkerManager;
    private DynmapMarkerReader markerReader;
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

            initMarkerSets();
            markerManager = new LoreMarkerManager(plugin, markerApi, loreMarkerSets);
            markerManager.populateAllMarkers();

            // Initialize collection marker manager with its own dedicated set
            initCollectionMarkerSet();
            collectionMarkerManager = new CollectionMarkerManager(plugin, markerApi, collectionMarkerSet);
            collectionMarkerManager.populateAllCollectionMarkers();
            plugin.getServer().getPluginManager().registerEvents(collectionMarkerManager, plugin);

            markerReader = new DynmapMarkerReader(markerApi, logger);
            enabled = true;
            logger.debug("Dynmap integration activated - " + loreMarkerSets.size() + " per-type marker layers created");
            return true;
        } catch (Exception e) {
            logger.warning("Failed to initialize Dynmap integration: " + e.getMessage());
            cleanup();
            return false;
        }
    }

    private void initMarkerSets() {
        ConfigManager config = plugin.getConfigManager();
        loreMarkerSets = new EnumMap<>(LoreType.class);

        for (LoreType type : LoreType.values()) {
            if (!type.isLocationCapable()) {
                continue;
            }

            String setId = "rvnklore_" + type.name().toLowerCase();
            String setLabel = config.getDynmapLayerLabel(type);

            MarkerSet set = markerApi.getMarkerSet(setId);
            if (set == null) {
                set = markerApi.createMarkerSet(setId, setLabel, null, false);
            }

            if (set != null) {
                set.setMarkerSetLabel(setLabel);
                set.setHideByDefault(config.isDynmapLayerHidden(type));
                set.setLayerPriority(config.getDynmapLayerPriority(type));
                loreMarkerSets.put(type, set);
                logger.debug("Created marker layer: " + setLabel + " (" + setId + ")");
            }
        }

        logger.info("Initialized " + loreMarkerSets.size() + " per-type Dynmap layers");
    }

    private void initCollectionMarkerSet() {
        ConfigManager config = plugin.getConfigManager();
        String setId = config.getDynmapMarkerSetId() + "_collections";
        String setLabel = "Collections";

        collectionMarkerSet = markerApi.getMarkerSet(setId);
        if (collectionMarkerSet == null) {
            collectionMarkerSet = markerApi.createMarkerSet(setId, setLabel, null, false);
        }
        if (collectionMarkerSet != null) {
            collectionMarkerSet.setMarkerSetLabel(setLabel);
            collectionMarkerSet.setHideByDefault(true);
            collectionMarkerSet.setLayerPriority(20);
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
        if (collectionMarkerManager != null) {
            collectionMarkerManager.cleanup();
            collectionMarkerManager = null;
        }
        loreMarkerSets = null;
        collectionMarkerSet = null;
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

    public CollectionMarkerManager getCollectionMarkerManager() {
        return collectionMarkerManager;
    }

    public DynmapMarkerReader getMarkerReader() {
        return markerReader;
    }
}

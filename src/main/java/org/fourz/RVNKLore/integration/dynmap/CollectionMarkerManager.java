package org.fourz.RVNKLore.integration.dynmap;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.config.ConfigManager;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionTheme;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;
import org.fourz.RVNKLore.lore.item.collection.event.CollectionChangeEvent;
import org.fourz.RVNKLore.lore.item.collection.event.CollectionEventType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages Dynmap markers for collection item locations.
 * Shows players where collection items can be found on the map.
 * Updates markers when collections change (items added/removed).
 */
public class CollectionMarkerManager implements Listener {

    private static final String MARKER_ID_PREFIX = "rvnklore_collection_";
    private static final Map<String, String> THEME_ICON_MAP = new HashMap<>();

    static {
        // Map collection themes to Dynmap icons
        THEME_ICON_MAP.put("treasure", "chest");
        THEME_ICON_MAP.put("mob_heads", "skull");
        THEME_ICON_MAP.put("artifacts", "diamond");
        THEME_ICON_MAP.put("seasonal", "default");
        THEME_ICON_MAP.put("legendary", "star");
        THEME_ICON_MAP.put("quest_rewards", "book");
        THEME_ICON_MAP.put("default", "pin");
    }

    private final RVNKLore plugin;
    private final LogManager logger;
    private final MarkerAPI markerApi;
    private final MarkerSet markerSet;
    private final ConcurrentHashMap<String, Marker> markerCache = new ConcurrentHashMap<>();

    public CollectionMarkerManager(RVNKLore plugin, MarkerAPI markerApi, MarkerSet markerSet) {
        this.plugin = plugin;
        this.markerApi = markerApi;
        this.markerSet = markerSet;
        this.logger = LogManager.getInstance(plugin, "CollectionMarkerManager");
    }

    /**
     * Populate all markers for collection items from enabled collections.
     * Called during Dynmap integration activation.
     */
    public void populateAllCollectionMarkers() {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isCollectionMarkersEnabled()) {
            logger.debug("Collection markers disabled in config");
            return;
        }

        if (plugin.getLoreManager() == null) {
            logger.warning("LoreManager not available - skipping collection marker population");
            return;
        }

        ItemManager itemManager = plugin.getLoreManager().getItemManager();
        if (itemManager == null) {
            logger.warning("ItemManager not available - skipping collection marker population");
            return;
        }

        CollectionManager collectionManager = itemManager.getCollectionManager();
        if (collectionManager == null) {
            logger.warning("CollectionManager not available - skipping collection marker population");
            return;
        }

        int count = 0;
        Map<String, ItemCollection> allCollections = collectionManager.getAllCollectionsSync();
        for (ItemCollection collection : allCollections.values()) {
            count += createMarkersForCollection(collection);
        }

        if (count > 0) {
            logger.info("Populated " + count + " collection item markers from " + allCollections.size() + " collections");
        }
    }

    /**
     * Create markers for all items in a collection that have locations.
     *
     * @param collection The collection to create markers for
     * @return Number of markers created
     */
    private int createMarkersForCollection(ItemCollection collection) {
        if (collection == null) {
            return 0;
        }

        ConfigManager config = plugin.getConfigManager();
        int count = 0;

        // Get all collection items with their locations
        List<ItemStack> items = collection.getItems();
        if (items == null || items.isEmpty()) {
            return 0;
        }

        for (ItemStack item : items) {
            if (item == null) {
                continue;
            }

            // Get the lore item location from database
            // For now, we'll use a simplified approach that gets items by collection
            // In a full implementation, we'd query the database for exact locations
            Location itemLocation = getCollectionItemLocation(collection.getId(), item);
            if (itemLocation != null) {
                createOrUpdateMarker(collection.getId(), item, itemLocation, collection.getThemeId());
                count++;
            }
        }

        return count;
    }

    /**
     * Create or update a marker for a collection item at a specific location.
     *
     * @param collectionId The collection ID
     * @param item The item to create a marker for
     * @param location The location of the item
     * @param themeId The collection theme ID
     */
    public void createOrUpdateMarker(String collectionId, ItemStack item, Location location, String themeId) {
        if (location == null || location.getWorld() == null || item == null) {
            return;
        }

        ConfigManager config = plugin.getConfigManager();
        if (!config.isCollectionMarkersEnabled()) {
            return;
        }

        String markerId = MARKER_ID_PREFIX + collectionId + "_" + System.identityHashCode(item);
        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
            ? item.getItemMeta().getDisplayName()
            : item.getType().name();

        // Get icon for this collection theme
        String iconName = getCollectionMarkerIcon(themeId);
        MarkerIcon icon = markerApi.getMarkerIcon(iconName);
        if (icon == null) {
            icon = markerApi.getMarkerIcon("pin"); // fallback
        }

        // Delete existing marker if present
        Marker existing = markerSet.findMarker(markerId);
        if (existing != null) {
            existing.deleteMarker();
        }

        // Create new marker
        Marker marker = markerSet.createMarker(
            markerId,
            itemName,
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            icon,
            false // not persistent (we manage persistence ourselves)
        );

        if (marker != null) {
            // Set popup HTML with collection information
            if (config.isDynmapPopupEnabled()) {
                marker.setDescription(generateCollectionPopupHtml(collectionId, itemName, location));
            }
            markerCache.put(markerId, marker);
            logger.debug("Created collection marker for " + itemName + " in " + collectionId);
        } else {
            logger.warning("Failed to create collection marker for: " + itemName);
        }
    }

    /**
     * Delete a marker for a collection item.
     *
     * @param markerId The marker ID
     */
    public void deleteMarker(String markerId) {
        Marker cached = markerCache.remove(markerId);
        if (cached != null) {
            try {
                cached.deleteMarker();
                logger.debug("Deleted collection marker: " + markerId);
            } catch (Exception e) {
                logger.debug("Failed to delete collection marker: " + e.getMessage());
            }
        }
    }

    /**
     * Update all markers for a specific player's collection progress.
     * Called when a player collects or loses items.
     *
     * @param playerId The player's UUID
     * @param collectionId The collection ID that changed
     */
    public void updatePlayerMarkers(UUID playerId, String collectionId) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isCollectionMarkersEnabled()) {
            return;
        }

        // In the current implementation, markers are global (not per-player)
        // This method is provided for future enhancement if per-player markers are needed
        // For now, it's a no-op but reserved for expansion
        logger.debug("Collection markers updated for player " + playerId + " in collection " + collectionId);
    }

    /**
     * Handle collection change events to update markers in real-time.
     *
     * @param event The collection change event
     */
    @EventHandler
    public void onCollectionChange(CollectionChangeEvent event) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isCollectionMarkersEnabled()) {
            return;
        }

        CollectionEventType eventType = event.getEventType();
        if (eventType == CollectionEventType.CREATED) {
            // When a collection is created, populate its markers
            ItemCollection collection = event.getCollection();
            if (collection != null) {
                createMarkersForCollection(collection);
            }
        } else if (eventType == CollectionEventType.DELETED) {
            // When a collection is deleted, delete all its markers
            ItemCollection collection = event.getCollection();
            if (collection != null) {
                deleteMarkersForCollection(collection.getId());
            }
        }
    }

    /**
     * Delete all markers for a specific collection.
     *
     * @param collectionId The collection ID
     */
    private void deleteMarkersForCollection(String collectionId) {
        String prefix = MARKER_ID_PREFIX + collectionId + "_";
        markerCache.keySet().stream()
            .filter(id -> id.startsWith(prefix))
            .collect(Collectors.toList())
            .forEach(this::deleteMarker);
        logger.debug("Deleted all markers for collection: " + collectionId);
    }

    /**
     * Get the map icon name for a collection theme.
     *
     * @param themeId The collection theme ID
     * @return The icon name to use for this theme
     */
    private String getCollectionMarkerIcon(String themeId) {
        if (themeId == null || themeId.isEmpty()) {
            return "pin";
        }

        String themeName = themeId.toLowerCase();
        ConfigManager config = plugin.getConfigManager();
        String configIcon = config.getCollectionMarkerIcon(themeName);
        return configIcon != null ? configIcon : "pin";
    }

    /**
     * Generate HTML popup content for a collection item marker.
     *
     * @param collectionId The collection ID
     * @param itemName The item display name
     * @param location The item location
     * @return HTML string for the popup
     */
    private String generateCollectionPopupHtml(String collectionId, String itemName, Location location) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:sans-serif;max-width:300px;\">");

        // Collection item name
        html.append("<b style=\"font-size:14px;\">").append(escapeHtml(itemName)).append("</b>");

        // Collection name badge
        html.append(" <span style=\"background:#5865F2;color:#fff;padding:1px 6px;border-radius:3px;font-size:11px;\">")
            .append(escapeHtml(collectionId))
            .append("</span>");

        // Coordinates
        if (location != null) {
            html.append("<br><span style=\"color:#888;font-size:10px;\">")
                .append(String.format("%.0f, %.0f, %.0f", location.getX(), location.getY(), location.getZ()))
                .append(" @ ")
                .append(escapeHtml(location.getWorld().getName()))
                .append("</span>");
        }

        html.append("</div>");
        return html.toString();
    }

    /**
     * Get the location of a collection item from the database.
     * This is a placeholder that retrieves the location based on the item's lore metadata.
     *
     * @param collectionId The collection ID
     * @param item The item to find
     * @return The location of the item, or null if not found
     */
    private Location getCollectionItemLocation(String collectionId, ItemStack item) {
        // In a full implementation, this would query the database for the exact location
        // based on the collection_item → lore_item → lore_location chain.
        // For now, return null to indicate items don't have explicit locations yet.
        // This can be enhanced in future phases when item locations are persisted.
        return null;
    }

    /**
     * Clean up all collection markers.
     */
    public void cleanup() {
        int count = markerCache.size();
        for (Marker marker : markerCache.values()) {
            try {
                marker.deleteMarker();
            } catch (Exception e) {
                // Marker may already be invalid if Dynmap is shutting down
            }
        }
        markerCache.clear();
        if (count > 0) {
            logger.info("Cleaned up " + count + " collection markers");
        }
    }

    /**
     * Get the number of active collection markers.
     */
    public int getMarkerCount() {
        return markerCache.size();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}

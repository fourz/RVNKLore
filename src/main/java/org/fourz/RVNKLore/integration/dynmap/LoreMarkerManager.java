package org.fourz.RVNKLore.integration.dynmap;

import org.bukkit.Location;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.config.ConfigManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.text.SimpleDateFormat;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages Dynmap markers for lore entries.
 * Handles creating, updating, and deleting markers with HTML popups.
 */
public class LoreMarkerManager {

    private static final String MARKER_ID_PREFIX = "rvnklore_";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy");

    private final RVNKLore plugin;
    private final LogManager logger;
    private final MarkerAPI markerApi;
    private final Map<LoreType, MarkerSet> markerSets;
    private final ConcurrentHashMap<String, Marker> markerCache = new ConcurrentHashMap<>();

    public LoreMarkerManager(RVNKLore plugin, MarkerAPI markerApi, Map<LoreType, MarkerSet> markerSets) {
        this.plugin = plugin;
        this.markerApi = markerApi;
        this.markerSets = markerSets;
        this.logger = LogManager.getInstance(plugin, "LoreMarkerManager");
    }

    /**
     * Create or update a marker for a lore entry.
     *
     * @param entry The lore entry to create a marker for
     */
    public void createOrUpdateMarker(LoreEntry entry) {
        if (!shouldHaveMarker(entry)) {
            return;
        }

        Location loc = entry.getLocation();
        if (loc == null || loc.getWorld() == null) {
            logger.debug("Skipping marker for entry " + entry.getId() + " - no valid location");
            return;
        }

        MarkerSet targetSet = markerSets.get(entry.getType());
        if (targetSet == null) {
            logger.debug("No marker set for type " + entry.getType() + " - skipping");
            return;
        }

        String markerId = MARKER_ID_PREFIX + entry.getId();
        ConfigManager config = plugin.getConfigManager();

        // Get icon
        String iconName = config.getDynmapIcon(entry.getType());
        MarkerIcon icon = markerApi.getMarkerIcon(iconName);
        if (icon == null) {
            icon = markerApi.getMarkerIcon("sign"); // fallback
        }

        // Delete existing marker if present (search across all sets for type changes)
        deleteExistingMarker(markerId);

        // Create marker
        Marker marker = targetSet.createMarker(
            markerId,
            entry.getName(),
            loc.getWorld().getName(),
            loc.getX(),
            loc.getY(),
            loc.getZ(),
            icon,
            false // not persistent (we manage persistence ourselves)
        );

        if (marker != null) {
            // Set popup HTML
            if (config.isDynmapPopupEnabled()) {
                marker.setDescription(generatePopupHtml(entry));
            }
            markerCache.put(entry.getId(), marker);
            logger.debug("Created marker for lore entry: " + entry.getName());
        } else {
            logger.warning("Failed to create Dynmap marker for: " + entry.getName());
        }
    }

    /**
     * Delete a marker for a lore entry.
     *
     * @param entryId The UUID string of the lore entry
     */
    public void deleteMarker(String entryId) {
        Marker cached = markerCache.remove(entryId);
        if (cached != null) {
            cached.deleteMarker();
            logger.debug("Deleted marker for lore entry: " + entryId);
            return;
        }

        // Try to find directly across all marker sets
        String markerId = MARKER_ID_PREFIX + entryId;
        deleteExistingMarker(markerId);
    }

    /**
     * Delete a marker by ID, searching across all per-type marker sets.
     */
    private void deleteExistingMarker(String markerId) {
        for (MarkerSet set : markerSets.values()) {
            Marker marker = set.findMarker(markerId);
            if (marker != null) {
                marker.deleteMarker();
                logger.debug("Deleted marker (uncached): " + markerId);
                return;
            }
        }
    }

    /**
     * Populate all markers from existing approved location-type lore entries.
     */
    public void populateAllMarkers() {
        if (plugin.getLoreManager() == null) {
            logger.warning("LoreManager not available - skipping marker population");
            return;
        }

        List<LoreEntry> entries = plugin.getLoreManager().getAllLoreEntriesSync().stream()
            .filter(this::shouldHaveMarker)
            .collect(Collectors.toList());

        int count = 0;
        for (LoreEntry entry : entries) {
            createOrUpdateMarker(entry);
            count++;
        }

        logger.info("Populated " + count + " Dynmap markers from lore entries");
    }

    /**
     * Check whether a lore entry should have a Dynmap marker.
     */
    public boolean shouldHaveMarker(LoreEntry entry) {
        if (entry == null) {
            return false;
        }

        // Must be a location-capable type
        if (!entry.getType().isLocationCapable()) {
            return false;
        }

        // Must have a valid location
        if (entry.getLocation() == null || entry.getLocation().getWorld() == null) {
            return false;
        }

        // Check approval requirement
        if (plugin.getConfigManager().isDynmapOnlyApproved() && !entry.isApproved()) {
            return false;
        }

        return true;
    }

    /**
     * Generate HTML popup content for a marker.
     */
    String generatePopupHtml(LoreEntry entry) {
        ConfigManager config = plugin.getConfigManager();
        int maxLen = config.getDynmapMaxDescriptionLength();

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:sans-serif;max-width:300px;\">");

        // Name
        html.append("<b style=\"font-size:14px;\">").append(escapeHtml(entry.getName())).append("</b>");

        // Type badge
        html.append(" <span style=\"background:#5865F2;color:#fff;padding:1px 6px;border-radius:3px;font-size:11px;\">")
            .append(entry.getType().name())
            .append("</span>");

        // Description
        if (entry.getDescription() != null && !entry.getDescription().isEmpty()) {
            String desc = entry.getDescription();
            if (desc.length() > maxLen) {
                desc = desc.substring(0, maxLen) + "...";
            }
            html.append("<br><span style=\"color:#ccc;font-size:12px;\">")
                .append(escapeHtml(desc))
                .append("</span>");
        }

        // Creator + date
        html.append("<br><span style=\"color:#888;font-size:11px;\">");
        if (entry.getSubmittedBy() != null) {
            html.append("By ").append(escapeHtml(entry.getSubmittedBy()));
        }
        if (entry.getCreatedAt() != null) {
            html.append(" &middot; ").append(DATE_FORMAT.format(entry.getCreatedAt()));
        }
        html.append("</span>");

        // Coordinates
        Location loc = entry.getLocation();
        if (loc != null) {
            html.append("<br><span style=\"color:#888;font-size:10px;\">")
                .append(String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ()))
                .append("</span>");
        }

        html.append("</div>");
        return html.toString();
    }

    /**
     * Remove all managed markers from all per-type sets.
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
        logger.debug("Cleaned up " + count + " Dynmap markers across " + markerSets.size() + " layers");
    }

    /**
     * Get the number of active markers.
     */
    public int getMarkerCount() {
        return markerCache.size();
    }

    /**
     * Get the set of lore types that support map markers.
     */
    public static Set<LoreType> getLocationTypes() {
        EnumSet<LoreType> types = EnumSet.noneOf(LoreType.class);
        for (LoreType t : LoreType.values()) {
            if (t.isLocationCapable()) types.add(t);
        }
        return types;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}

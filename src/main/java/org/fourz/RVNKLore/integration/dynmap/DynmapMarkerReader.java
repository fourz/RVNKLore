package org.fourz.RVNKLore.integration.dynmap;

import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Reads existing markers from Dynmap marker sets.
 * This is the read-side complement to LoreMarkerManager (which writes lore entries as markers).
 * Provides data for diff/import operations comparing dynmap markers against the lore database.
 */
public class DynmapMarkerReader {

    private final MarkerAPI markerApi;
    private final LogManager logger;

    // Configurable mapping from dynmap marker set IDs to LoreTypes
    private final Map<String, LoreType> markerSetMapping = new LinkedHashMap<>();

    public DynmapMarkerReader(MarkerAPI markerApi, LogManager logger) {
        this.markerApi = markerApi;
        this.logger = logger;

        // Default mappings matching the live server's marker sets
        markerSetMapping.put("Cities", LoreType.CITY);
        markerSetMapping.put("Landmarks", LoreType.LANDMARK);
        markerSetMapping.put("Facilities", LoreType.LANDMARK);
    }

    /**
     * Configure the marker set to LoreType mapping.
     *
     * @param mapping map of marker set ID to LoreType
     */
    public void setMarkerSetMapping(Map<String, LoreType> mapping) {
        markerSetMapping.clear();
        markerSetMapping.putAll(mapping);
    }

    /**
     * Get the configured marker set to LoreType mapping.
     */
    public Map<String, LoreType> getMarkerSetMapping() {
        return Collections.unmodifiableMap(markerSetMapping);
    }

    /**
     * Get all available marker set IDs from dynmap.
     *
     * @return list of marker set IDs
     */
    public List<String> getMarkerSetIds() {
        Set<MarkerSet> sets = markerApi.getMarkerSets();
        List<String> ids = new ArrayList<>();
        for (MarkerSet set : sets) {
            ids.add(set.getMarkerSetID());
        }
        return ids;
    }

    /**
     * Get all markers from a specific marker set.
     *
     * @param markerSetId the dynmap marker set ID (e.g., "Cities", "Landmarks")
     * @return list of marker DTOs, empty if set not found
     */
    public List<DynmapMarkerDTO> getMarkersFromSet(String markerSetId) {
        MarkerSet set = markerApi.getMarkerSet(markerSetId);
        if (set == null) {
            logger.debug("Marker set not found: " + markerSetId);
            return Collections.emptyList();
        }

        return readMarkersFromSet(set);
    }

    /**
     * Get all markers from all mapped marker sets (Cities, Landmarks, Facilities).
     *
     * @return list of all marker DTOs from mapped sets
     */
    public List<DynmapMarkerDTO> getAllMappedMarkers() {
        List<DynmapMarkerDTO> allMarkers = new ArrayList<>();
        for (String setId : markerSetMapping.keySet()) {
            allMarkers.addAll(getMarkersFromSet(setId));
        }
        return allMarkers;
    }

    /**
     * Get the LoreType mapped to a marker set ID.
     *
     * @param markerSetId the dynmap marker set ID
     * @return the mapped LoreType, or null if not mapped
     */
    public LoreType getLoreTypeForMarkerSet(String markerSetId) {
        return markerSetMapping.get(markerSetId);
    }

    /**
     * Find a specific marker by ID within a marker set.
     *
     * @param markerSetId the marker set ID
     * @param markerId the marker ID within the set
     * @return the marker DTO, or empty if not found
     */
    public Optional<DynmapMarkerDTO> getMarkerById(String markerSetId, String markerId) {
        MarkerSet set = markerApi.getMarkerSet(markerSetId);
        if (set == null) return Optional.empty();

        Marker marker = set.findMarker(markerId);
        if (marker == null) return Optional.empty();

        return Optional.of(toDTO(set, marker));
    }

    private List<DynmapMarkerDTO> readMarkersFromSet(MarkerSet set) {
        Collection<Marker> markers = set.getMarkers();
        List<DynmapMarkerDTO> result = new ArrayList<>(markers.size());
        for (Marker marker : markers) {
            result.add(toDTO(set, marker));
        }
        logger.debug("Read " + result.size() + " markers from set: " + set.getMarkerSetID());
        return result;
    }

    private DynmapMarkerDTO toDTO(MarkerSet set, Marker marker) {
        return new DynmapMarkerDTO(
            set.getMarkerSetID(),
            set.getMarkerSetLabel(),
            marker.getMarkerID(),
            marker.getLabel(),
            marker.getWorld(),
            marker.getX(),
            marker.getY(),
            marker.getZ(),
            marker.getMarkerIcon() != null ? marker.getMarkerIcon().getMarkerIconID() : "default"
        );
    }
}

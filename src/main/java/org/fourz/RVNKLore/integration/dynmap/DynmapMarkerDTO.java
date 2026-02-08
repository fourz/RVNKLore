package org.fourz.RVNKLore.integration.dynmap;

/**
 * Data transfer object representing a marker from a Dynmap marker set.
 * Used for reading existing dynmap markers (Cities, Landmarks, etc.)
 * to compare against the lore database.
 */
public record DynmapMarkerDTO(
    String markerSetId,
    String markerSetLabel,
    String markerId,
    String label,
    String world,
    double x,
    double y,
    double z,
    String icon
) {}

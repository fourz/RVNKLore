package org.fourz.RVNKLore.integration.dynmap;

import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Compares live dynmap markers against the lore database to find discrepancies.
 * Used by /lore dynmap diff and /lore dynmap import commands.
 */
public class DynmapLoreDiffService {

    private final DynmapMarkerReader markerReader;
    private final LoreManager loreManager;

    public DynmapLoreDiffService(DynmapMarkerReader markerReader, LoreManager loreManager) {
        this.markerReader = markerReader;
        this.loreManager = loreManager;
    }

    /**
     * Performs a diff between dynmap markers and lore entries for a specific marker set.
     *
     * @param markerSetId the dynmap marker set ID (e.g., "Cities"), or null for all mapped sets
     * @return the diff result
     */
    public DiffResult diff(String markerSetId) {
        List<DynmapMarkerDTO> markers;
        if (markerSetId != null) {
            markers = markerReader.getMarkersFromSet(markerSetId);
        } else {
            markers = markerReader.getAllMappedMarkers();
        }

        // Build a lookup of lore entries by normalized name + type
        Map<String, LoreEntry> loreByKey = new HashMap<>();
        for (LoreEntry entry : loreManager.getAllLoreEntriesSync()) {
            String key = normalizeKey(entry.getName(), entry.getType());
            loreByKey.put(key, entry);
        }

        List<DynmapMarkerDTO> missingFromLore = new ArrayList<>();
        List<MatchedEntry> matched = new ArrayList<>();
        Set<String> matchedLoreKeys = new HashSet<>();

        for (DynmapMarkerDTO marker : markers) {
            LoreType expectedType = markerReader.getLoreTypeForMarkerSet(marker.markerSetId());
            if (expectedType == null) continue;

            String key = normalizeKey(marker.label(), expectedType);
            LoreEntry loreEntry = loreByKey.get(key);

            if (loreEntry != null) {
                matched.add(new MatchedEntry(marker, loreEntry));
                matchedLoreKeys.add(key);
            } else {
                missingFromLore.add(marker);
            }
        }

        // Find lore entries that exist in DB but not in dynmap (only for relevant types)
        Set<LoreType> relevantTypes = new HashSet<>(markerReader.getMarkerSetMapping().values());
        List<LoreEntry> missingFromDynmap = new ArrayList<>();
        for (LoreEntry entry : loreManager.getAllLoreEntriesSync()) {
            if (!relevantTypes.contains(entry.getType())) continue;
            if (entry.getLocation() == null) continue;

            String key = normalizeKey(entry.getName(), entry.getType());
            if (!matchedLoreKeys.contains(key)) {
                missingFromDynmap.add(entry);
            }
        }

        return new DiffResult(
            markerSetId != null ? markerSetId : "All",
            markers.size(),
            matched,
            missingFromLore,
            missingFromDynmap
        );
    }

    /**
     * Creates a normalized lookup key from name + type for matching.
     */
    private String normalizeKey(String name, LoreType type) {
        return type.name() + ":" + name.trim().toLowerCase();
    }

    /**
     * Result of a dynmap vs lore diff operation.
     */
    public static class DiffResult {
        private final String scope;
        private final int totalMarkers;
        private final List<MatchedEntry> matched;
        private final List<DynmapMarkerDTO> missingFromLore;
        private final List<LoreEntry> missingFromDynmap;

        public DiffResult(String scope, int totalMarkers, List<MatchedEntry> matched,
                          List<DynmapMarkerDTO> missingFromLore, List<LoreEntry> missingFromDynmap) {
            this.scope = scope;
            this.totalMarkers = totalMarkers;
            this.matched = matched;
            this.missingFromLore = missingFromLore;
            this.missingFromDynmap = missingFromDynmap;
        }

        public String getScope() { return scope; }
        public int getTotalMarkers() { return totalMarkers; }
        public List<MatchedEntry> getMatched() { return matched; }
        public List<DynmapMarkerDTO> getMissingFromLore() { return missingFromLore; }
        public List<LoreEntry> getMissingFromDynmap() { return missingFromDynmap; }
    }

    /**
     * A dynmap marker matched to a lore entry.
     */
    public static class MatchedEntry {
        private final DynmapMarkerDTO marker;
        private final LoreEntry loreEntry;

        public MatchedEntry(DynmapMarkerDTO marker, LoreEntry loreEntry) {
            this.marker = marker;
            this.loreEntry = loreEntry;
        }

        public DynmapMarkerDTO getMarker() { return marker; }
        public LoreEntry getLoreEntry() { return loreEntry; }
    }
}

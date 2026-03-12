package org.fourz.RVNKLore.data.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for lore_discovery enriched discovery data.
 * Supplements the existing player_discoveries table with trigger type,
 * location, and first-discoverer tracking.
 */
public interface IDiscoveryRepository {

    /**
     * Record a discovery with full context.
     */
    CompletableFuture<Boolean> recordDiscovery(UUID playerUuid, String entryId,
                                                String triggerType, String world,
                                                Double x, Double y, Double z,
                                                boolean isFirstDiscovery);

    /**
     * Check if a player has discovered an entry.
     */
    CompletableFuture<Boolean> hasDiscovered(UUID playerUuid, String entryId);

    /**
     * Get all entry IDs discovered by a player.
     */
    CompletableFuture<List<String>> getDiscoveredEntryIds(UUID playerUuid);

    /**
     * Get the first discoverer UUID for an entry.
     */
    CompletableFuture<UUID> getFirstDiscoverer(String entryId);

    /**
     * Load all first discoverers (entry_id -> player_uuid) for startup cache.
     */
    CompletableFuture<Map<String, UUID>> loadAllFirstDiscoverers();

    /**
     * Count total discoveries for a player.
     */
    CompletableFuture<Integer> countPlayerDiscoveries(UUID playerUuid);

    /**
     * Count how many first discoveries a player has.
     */
    CompletableFuture<Integer> countFirstDiscoveries(UUID playerUuid);
}

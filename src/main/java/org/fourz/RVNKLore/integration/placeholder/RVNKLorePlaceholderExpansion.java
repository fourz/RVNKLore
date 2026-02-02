package org.fourz.RVNKLore.integration.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.service.ICollectionService;
import org.fourz.RVNKLore.service.ILoreService;
import org.fourz.RVNKLore.service.IPlayerService;
import org.fourz.rvnkcore.util.log.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * PlaceholderAPI expansion for RVNKLore statistics.
 *
 * <p>Provides placeholders for lore discovery tracking, collection progress,
 * and player statistics.</p>
 *
 * <p>Available placeholders:
 * <ul>
 *   <li>%rvnklore_total_discovered% - Total lore entries discovered</li>
 *   <li>%rvnklore_total_entries% - Total available entries</li>
 *   <li>%rvnklore_discovery_percentage% - Discovery completion percentage</li>
 *   <li>%rvnklore_items_discovered% - Items discovered by type</li>
 *   <li>%rvnklore_locations_discovered% - Locations discovered</li>
 *   <li>%rvnklore_characters_discovered% - Characters discovered</li>
 *   <li>%rvnklore_collection_&lt;name&gt;_progress% - Specific collection progress</li>
 *   <li>%rvnklore_rarest_item% - Player's rarest discovered item (placeholder)</li>
 *   <li>%rvnklore_latest_discovery% - Most recent discovery name (placeholder)</li>
 * </ul>
 * </p>
 *
 * @since feat-06 PlaceholderAPI Integration
 */
public class RVNKLorePlaceholderExpansion extends PlaceholderExpansion {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final IPlayerService playerService;
    private final ILoreService loreService;
    private final ICollectionService collectionService;

    // Cache with 5-second TTL
    private static final long CACHE_TTL_MS = 5000;
    private final Map<String, CachedValue<?>> cache = new ConcurrentHashMap<>();

    /**
     * Creates a new PlaceholderAPI expansion for RVNKLore.
     *
     * @param plugin The RVNKLore plugin instance
     */
    public RVNKLorePlaceholderExpansion(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "PlaceholderExpansion");
        this.playerService = plugin.getPlayerManager();
        this.loreService = plugin.getLoreManager();
        this.collectionService = plugin.getLoreManager().getItemManager().getCollectionManager();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rvnklore";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Keep expansion loaded across /papi reload
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return null;
        }

        UUID playerId = player.getUniqueId();
        String cacheKey = playerId + ":" + params;

        // Check cache first
        CachedValue<?> cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return String.valueOf(cached.getValue());
        }

        // Process placeholder
        String result = processPlaceholder(playerId, params);

        // Cache the result
        if (result != null) {
            cache.put(cacheKey, new CachedValue<>(result, CACHE_TTL_MS));
        }

        return result;
    }

    /**
     * Process a placeholder request.
     *
     * @param playerId The player UUID
     * @param params The placeholder parameters
     * @return The placeholder value, or null if unavailable
     */
    private String processPlaceholder(UUID playerId, String params) {
        try {
            switch (params.toLowerCase()) {
                case "total_discovered":
                    return getTotalDiscovered(playerId);

                case "total_entries":
                    return getTotalEntries();

                case "discovery_percentage":
                    return getDiscoveryPercentage(playerId);

                case "items_discovered":
                    return getItemsDiscovered(playerId);

                case "locations_discovered":
                    return getLocationsDiscovered(playerId);

                case "characters_discovered":
                    return getCharactersDiscovered(playerId);

                case "rarest_item":
                    return getRarestItem(playerId);

                case "latest_discovery":
                    return getLatestDiscovery(playerId);

                default:
                    // Check for collection progress: collection_<name>_progress
                    if (params.toLowerCase().startsWith("collection_") && params.toLowerCase().endsWith("_progress")) {
                        String collectionName = params.substring(11, params.length() - 9);
                        return getCollectionProgress(playerId, collectionName);
                    }
                    return null;
            }
        } catch (Exception e) {
            logger.error("Error processing placeholder: " + params, e);
            return "Error";
        }
    }

    /**
     * Get total lore entries discovered by player.
     */
    private String getTotalDiscovered(UUID playerId) {
        try {
            CompletableFuture<Integer> future = playerService.getPlayerLoreEntryIds(playerId)
                .thenApply(entries -> entries.size());

            return String.valueOf(future.get(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            logger.debug("Failed to get total discovered for player: " + playerId);
            return "0";
        }
    }

    /**
     * Get total available lore entries.
     */
    private String getTotalEntries() {
        try {
            CompletableFuture<Integer> future = loreService.getApprovedLoreEntries()
                .thenApply(entries -> entries.size());

            return String.valueOf(future.get(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            logger.debug("Failed to get total entries");
            return "0";
        }
    }

    /**
     * Get discovery completion percentage.
     */
    private String getDiscoveryPercentage(UUID playerId) {
        try {
            CompletableFuture<Integer> totalDiscovered = playerService.getPlayerLoreEntryIds(playerId)
                .thenApply(entries -> entries.size());

            CompletableFuture<Integer> totalEntries = loreService.getApprovedLoreEntries()
                .thenApply(entries -> entries.size());

            int discovered = totalDiscovered.get(1, TimeUnit.SECONDS);
            int total = totalEntries.get(1, TimeUnit.SECONDS);

            if (total == 0) {
                return "0%";
            }

            double percentage = (discovered * 100.0) / total;
            return String.format("%.1f%%", percentage);
        } catch (Exception e) {
            logger.debug("Failed to get discovery percentage for player: " + playerId);
            return "0%";
        }
    }

    /**
     * Get number of items discovered by player.
     */
    private String getItemsDiscovered(UUID playerId) {
        try {
            CompletableFuture<Long> future = playerService.getPlayerLoreEntriesByType(playerId, LoreType.ITEM.name())
                .thenApply(entries -> (long) entries.size());

            return String.valueOf(future.get(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            logger.debug("Failed to get items discovered for player: " + playerId);
            return "0";
        }
    }

    /**
     * Get number of locations (landmarks + cities) discovered by player.
     */
    private String getLocationsDiscovered(UUID playerId) {
        try {
            CompletableFuture<Integer> landmarks = playerService.getPlayerLoreEntriesByType(playerId, LoreType.LANDMARK.name())
                .thenApply(entries -> entries.size());

            CompletableFuture<Integer> cities = playerService.getPlayerLoreEntriesByType(playerId, LoreType.CITY.name())
                .thenApply(entries -> entries.size());

            int landmarkCount = landmarks.get(1, TimeUnit.SECONDS);
            int cityCount = cities.get(1, TimeUnit.SECONDS);

            return String.valueOf(landmarkCount + cityCount);
        } catch (Exception e) {
            logger.debug("Failed to get locations discovered for player: " + playerId);
            return "0";
        }
    }

    /**
     * Get number of characters (players + factions) discovered by player.
     */
    private String getCharactersDiscovered(UUID playerId) {
        try {
            CompletableFuture<Integer> players = playerService.getPlayerLoreEntriesByType(playerId, LoreType.PLAYER.name())
                .thenApply(entries -> entries.size());

            CompletableFuture<Integer> factions = playerService.getPlayerLoreEntriesByType(playerId, LoreType.FACTION.name())
                .thenApply(entries -> entries.size());

            int playerCount = players.get(1, TimeUnit.SECONDS);
            int factionCount = factions.get(1, TimeUnit.SECONDS);

            return String.valueOf(playerCount + factionCount);
        } catch (Exception e) {
            logger.debug("Failed to get characters discovered for player: " + playerId);
            return "0";
        }
    }

    /**
     * Get collection progress for a specific collection.
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @return Progress percentage string
     */
    private String getCollectionProgress(UUID playerId, String collectionId) {
        try {
            CompletableFuture<Double> future = collectionService.getPlayerProgress(playerId, collectionId);
            double progress = future.get(1, TimeUnit.SECONDS);

            return String.format("%.1f%%", progress * 100.0);
        } catch (Exception e) {
            logger.debug("Failed to get collection progress for player: " + playerId + ", collection: " + collectionId);
            return "0%";
        }
    }

    /**
     * Get player's rarest discovered item (placeholder implementation).
     *
     * <p>This is a placeholder that returns "None" as rarity tracking
     * requires additional database schema enhancements.</p>
     */
    private String getRarestItem(UUID playerId) {
        // TODO: Implement rarity tracking in database
        return "None";
    }

    /**
     * Get player's latest discovery (placeholder implementation).
     *
     * <p>This is a placeholder that returns "None" as discovery history
     * tracking requires additional database schema enhancements.</p>
     */
    private String getLatestDiscovery(UUID playerId) {
        // TODO: Implement discovery timestamp tracking in database
        return "None";
    }

    /**
     * Cached value holder with TTL.
     */
    private static class CachedValue<T> {
        private final T value;
        private final long expiryTime;

        public CachedValue(T value, long ttlMs) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + ttlMs;
        }

        public T getValue() {
            return value;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    /**
     * Clear the placeholder cache.
     */
    public void clearCache() {
        cache.clear();
        logger.debug("Placeholder cache cleared");
    }

    /**
     * Get cache statistics.
     *
     * @return Map of cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("size", cache.size());
        stats.put("ttl_ms", CACHE_TTL_MS);

        long expiredCount = cache.values().stream()
            .filter(CachedValue::isExpired)
            .count();
        stats.put("expired", expiredCount);

        return stats;
    }
}

package org.fourz.RVNKLore.lore;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service class for finding and searching lore entries using the new database architecture.
 * Uses async operations and DTOs for efficient data transfer. Includes retry logic for
 * critical operations and proper error handling.
 */
public class LoreFinder {    private final DatabaseManager databaseManager;
    private final LogManager logger;
    private final Map<String, CacheEntry<?>> findCache;
    private static final long CACHE_TTL_MS = 300000; // 5 minutes
      public LoreFinder(RVNKLore plugin) {
        this.databaseManager = plugin.getDatabaseManager();
        this.logger = LogManager.getInstance(plugin, "LoreFinder");
        this.findCache = new ConcurrentHashMap<>();
        setupCacheCleanupTask(plugin);
    }

    private static class CacheEntry<T> {
        final T value;
        final long timestamp;

        CacheEntry(T value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }    private void setupCacheCleanupTask(RVNKLore plugin) {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            findCache.entrySet().removeIf(entry -> ((CacheEntry<?>)entry.getValue()).isExpired());
        }, CACHE_TTL_MS / 1000 * 20, CACHE_TTL_MS / 1000 * 20); // Convert to ticks
    }

    private String getCacheKey(String prefix, Object... params) {
        return prefix + ":" + String.join(":", Arrays.stream(params)
            .map(Object::toString)
            .collect(Collectors.toList()));
    }    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> withCache(String cacheKey, Supplier<CompletableFuture<T>> operation) {
        CacheEntry<T> cached = (CacheEntry<T>) findCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.value);
        }

        return operation.get().thenApply(result -> {
            findCache.put(cacheKey, new CacheEntry<T>(result));
            return result;
        });
    }
    
    /**
     * Find lore entries near a location asynchronously.
     * 
     * @param location The location to search near
     * @param radius The search radius
     * @return CompletableFuture with a list of nearby lore entries
     */
    public CompletableFuture<List<LoreEntry>> findNearbyLoreAsync(Location location, double radius) {
        if (location == null) {
            logger.warning("&e⚠ Cannot find nearby lore - location is null");
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        String cacheKey = getCacheKey("nearby", location.getWorld().getName(), 
            (int)location.getX(), (int)location.getY(), (int)location.getZ(), (int)radius);

        return withCache(cacheKey, () ->
            databaseManager.findNearbyLoreEntries(location, radius)
                .thenApply(entries -> entries.stream()
                    .map(LoreEntry::fromDTO)
                    .filter(LoreEntry::isApproved)
                    .collect(Collectors.toList()))
                .exceptionally(e -> {
                    logger.error("&c✖ Error finding nearby lore", e);
                    return new ArrayList<>();
                }));
    }
    
    /**
     * Find all lore entries in a specific world asynchronously.
     * 
     * @param worldName The name of the world
     * @return CompletableFuture with a list of lore entries in the world
     */
    public CompletableFuture<List<LoreEntry>> findLoreInWorldAsync(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) {
            logger.warning("&e⚠ Cannot find lore - world name is null or empty");
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        String cacheKey = getCacheKey("world", worldName);

        return withCache(cacheKey, () ->
            databaseManager.findLoreEntriesInWorld(worldName)
                .thenApply(entries -> entries.stream()
                    .map(LoreEntry::fromDTO)
                    .filter(LoreEntry::isApproved)
                    .collect(Collectors.toList()))
                .exceptionally(e -> {
                    logger.error("&c✖ Error finding lore in world: " + worldName, e);
                    return new ArrayList<>();
                }));
    }
    
    /**
     * Find lore by player who submitted it asynchronously.
     * 
     * @param playerName The name of the player
     * @return CompletableFuture with a list of lore entries submitted by the player
     */
    public CompletableFuture<List<LoreEntry>> findLoreBySubmitterAsync(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            logger.warning("&e⚠ Cannot find lore - player name is null or empty");
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        String cacheKey = getCacheKey("submitter", playerName.toLowerCase());

        return withCache(cacheKey, () ->
            databaseManager.findLoreEntriesBySubmitter(playerName)
                .thenApply(entries -> entries.stream()
                    .map(LoreEntry::fromDTO)
                    .collect(Collectors.toList()))
                .exceptionally(e -> {
                    logger.error("&c✖ Error finding lore by submitter: " + playerName, e);
                    return new ArrayList<>();
                }));
    }
    
    /**
     * Get a lore entry by UUID asynchronously.
     * 
     * @param id The UUID of the lore entry
     * @return CompletableFuture with the lore entry or null if not found
     */
    public CompletableFuture<LoreEntry> getLoreEntryAsync(UUID id) {
        if (id == null) {
            logger.warning("&e⚠ Cannot get lore entry - id is null");
            return CompletableFuture.completedFuture(null);
        }

        String cacheKey = getCacheKey("entry", id.toString());

        return withCache(cacheKey, () ->
            databaseManager.getLoreEntryById(id)
                .thenApply(dto -> dto != null ? LoreEntry.fromDTO(dto) : null)
                .exceptionally(e -> {
                    logger.error("&c✖ Error getting lore entry: " + id, e);
                    return null;
                }));
    }
    
    /**
     * Find pending lore entries that need approval asynchronously.
     * 
     * @return CompletableFuture with a list of unapproved lore entries
     */
    public CompletableFuture<List<LoreEntry>> findPendingLoreEntriesAsync() {
        return withCache("pending", () ->
            databaseManager.findPendingLoreEntries()
                .thenApply(entries -> entries.stream()
                    .map(LoreEntry::fromDTO)
                    .collect(Collectors.toList()))
                .exceptionally(e -> {
                    logger.error("&c✖ Error finding pending lore entries", e);
                    return new ArrayList<>();
                }));
    }
    
    /**
     * Find recent lore entries asynchronously.
     * 
     * @param count Number of recent entries to return
     * @return CompletableFuture with a list of most recent lore entries
     */
    public CompletableFuture<List<LoreEntry>> findRecentLoreEntriesAsync(int count) {
        if (count <= 0) {
            logger.warning("&e⚠ Cannot find recent lore - count must be positive");
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        String cacheKey = getCacheKey("recent", count);

        return withCache(cacheKey, () ->
            databaseManager.findRecentLoreEntries(count)
                .thenApply(entries -> entries.stream()
                    .map(LoreEntry::fromDTO)
                    .filter(LoreEntry::isApproved)
                    .collect(Collectors.toList()))
                .exceptionally(e -> {
                    logger.error("&c✖ Error finding recent lore entries", e);
                    return new ArrayList<>();
                }));
    }
    
    /**
     * Search for lore entries containing a text fragment asynchronously.
     * 
     * @param searchText The text to search for
     * @return CompletableFuture with a list of matching lore entries
     */
    public CompletableFuture<List<LoreEntry>> searchLoreAsync(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            logger.warning("&e⚠ Cannot search lore - search text is null or empty");
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        String cacheKey = getCacheKey("search", searchText.toLowerCase());

        return withCache(cacheKey, () ->            databaseManager.searchLoreSubmissions(searchText)
                .thenApply(entries -> entries.stream()
                    .map(LoreEntry::fromDTO)
                    .filter(LoreEntry::isApproved)
                    .collect(Collectors.toList()))
                .exceptionally(e -> {
                    logger.error("&c✖ Error searching lore", e);
                    return new ArrayList<>();
                }));
    }
    
    /**
     * Find lore that might be relevant to a player's current activity asynchronously.
     * 
     * @param player The player
     * @return CompletableFuture with a list of contextually relevant lore entries
     */
    public CompletableFuture<List<LoreEntry>> findRelevantLoreAsync(Player player) {
        if (player == null) {
            logger.warning("&e⚠ Cannot find relevant lore - player is null");
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        String cacheKey = getCacheKey("relevant", player.getUniqueId().toString());

        return withCache(cacheKey, () -> {
            // Combine all relevant async operations
            List<CompletableFuture<List<LoreEntry>>> futures = new ArrayList<>();
            futures.add(findNearbyLoreAsync(player.getLocation(), 100.0));
            futures.add(findLoreBySubmitterAsync(player.getName()));
            futures.add(findPlayerSpecificLoreAsync(player.getUniqueId()));

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .distinct()
                    .collect(Collectors.toList()))
                .exceptionally(e -> {
                    logger.error("&c✖ Error finding relevant lore for player: " + player.getName(), e);
                    return new ArrayList<>();
                });
        });
    }

    /**
     * Helper method to find lore specific to a player UUID asynchronously.
     */
    private CompletableFuture<List<LoreEntry>> findPlayerSpecificLoreAsync(UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        String cacheKey = getCacheKey("player-specific", playerUuid.toString());

        return withCache(cacheKey, () ->
            databaseManager.findPlayerSpecificLoreEntries(playerUuid)
                .thenApply(entries -> entries.stream()
                    .map(LoreEntry::fromDTO)
                    .filter(LoreEntry::isApproved)
                    .collect(Collectors.toList()))
                .exceptionally(e -> {
                    logger.error("&c✖ Error finding player-specific lore", e);
                    return new ArrayList<>();
                }));
    }
}

package org.fourz.RVNKLore.lore;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.repository.LoreEntryRepository;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service class for finding and managing lore entries using the new async database architecture.
 * Implements caching and retry logic for improved performance and reliability.
 *
 * This class follows the repository pattern and uses DTOs for data transfer, ensuring
 * all database operations are non-blocking.
 *
 * Key Features:
 * - Async database operations
 * - Two-level caching with TTL
 * - Automated cache cleanup
 * - Retry logic for critical operations
 * - Standardized error handling and logging
 */
public class LoreFinder {
    private final RVNKLore plugin;
    private final LoreEntryRepository loreEntryRepository;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private final Map<String, CacheEntry<?>> findCache;
    private static final long CACHE_TTL_MS = 300000; // 5 minutes
    private static final int MAX_RETRIES = 3;
    
    /**
     * Constructs a new LoreFinder with necessary dependencies and initializes the cache.
     *
     * @param plugin The RVNKLore plugin instance
     * @throws IllegalArgumentException if plugin is null
     */
    public LoreFinder(RVNKLore plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("&c✖ Plugin instance cannot be null");
        }
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.loreEntryRepository = databaseManager.getLoreEntryRepository();
        this.logger = LogManager.getInstance(plugin, "LoreFinder");
        this.findCache = new ConcurrentHashMap<>();
        setupCacheCleanupTask(plugin);
    }

    /**
     * Cache entry wrapper with timestamp for TTL tracking.
     *
     * @param <T> The type of value being cached
     */
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
    }
    
    /**
     * Sets up periodic cache cleanup to remove expired entries.
     */
    private void setupCacheCleanupTask(RVNKLore plugin) {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            int beforeSize = findCache.size();
            findCache.entrySet().removeIf(entry -> ((CacheEntry<?>)entry.getValue()).isExpired());
            int removedCount = beforeSize - findCache.size();
            if (removedCount > 0) {
                logger.debug("&7   Cache cleanup completed. Removed " + removedCount + " expired entries.");
            }
        }, CACHE_TTL_MS / 1000 * 20, CACHE_TTL_MS / 1000 * 20); // Convert to ticks
    }

    private String getCacheKey(String prefix, Object... params) {
        return prefix + ":" + String.join(":", Arrays.stream(params)
            .map(Object::toString)
            .collect(Collectors.toList()));
    }
    
    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> withCache(String cacheKey, Supplier<CompletableFuture<T>> operation) {
        CacheEntry<T> cached = (CacheEntry<T>) findCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            logger.debug("&7   Cache hit for key: " + cacheKey);
            return CompletableFuture.completedFuture(cached.value);
        }

        logger.debug("&7   Cache miss for key: " + cacheKey);
        return operation.get()
            .thenApply(result -> {
                if (result != null) {
                    findCache.put(cacheKey, new CacheEntry<>(result));
                }
                return result;
            })
            .exceptionally(e -> {
                logger.error("&c✖ Cache operation failed for key: " + cacheKey, e);
                findCache.remove(cacheKey); // Remove failed cache entry
                return null;
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
            loreEntryRepository.findNearbyLoreEntries(location, radius)
                .thenApply(entries -> entries.stream()
                    .map(dto -> {
                        LoreEntry entry = LoreEntry.fromDTO(dto);
                        if (entry == null) {
                            logger.warning("&e⚠ Failed to convert DTO to LoreEntry");
                            return null;
                        }
                        return entry;
                    })
                    .filter(Objects::nonNull)
                    .filter(LoreEntry::isApproved)
                    .collect(Collectors.toList()))
                .exceptionally(e -> {
                    logger.error("&c✖ Error finding nearby lore: " + e.getMessage(), e);
                    return new ArrayList<>();
                }));
    }
    
    /**
     * Find all lore entries in a specific world asynchronously.
     */
    public CompletableFuture<List<LoreEntry>> findLoreInWorldAsync(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) {
            logger.warning("&e⚠ Cannot find lore - world name is null or empty");
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        String cacheKey = getCacheKey("world", worldName);
        return withCache(cacheKey, () ->
            loreEntryRepository.findLoreEntriesInWorld(worldName)
                .thenApply(this::convertAndFilterDTOs)
                .exceptionally(e -> {
                    logger.error("&c✖ Error finding lore in world: " + worldName, e);
                    return new ArrayList<>();
                }));
    }
    
    /**
     * Find lore by player who submitted it asynchronously.
     */
    public CompletableFuture<List<LoreEntry>> findLoreBySubmitterAsync(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            logger.warning("&e⚠ Cannot find lore - player name is null or empty");
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        String cacheKey = getCacheKey("submitter", playerName.toLowerCase());
        return withCache(cacheKey, () ->
            loreEntryRepository.findLoreEntriesBySubmitter(playerName)
                .thenApply(this::convertAndFilterDTOs)
                .exceptionally(e -> {
                    logger.error("&c✖ Error finding lore by submitter: " + playerName, e);
                    return new ArrayList<>();
                }));
    }
    
    /**
     * Get a lore entry by UUID asynchronously.
     */    public CompletableFuture<LoreEntry> getLoreEntryAsync(UUID id) {
        if (id == null) {
            logger.warning("&e⚠ Cannot get lore entry - id is null");
            return CompletableFuture.completedFuture(null);
        }
        
        String cacheKey = getCacheKey("entry", id.toString());
        return withCache(cacheKey, () -> {
            try {
                // First try by UUID string
                return loreEntryRepository.getLoreEntriesByUuid(id.toString())
                    .thenCompose(results -> {
                        if (results != null && !results.isEmpty()) {
                            try {
                                return CompletableFuture.completedFuture(LoreEntry.fromDTO(results.get(0)));
                            } catch (Exception e) {
                                logger.error("&c✖ Error converting DTO to LoreEntry", e);
                                return CompletableFuture.completedFuture(null);
                            }
                        }
                        // If not found by UUID, try legacy ID lookup
                        return loreEntryRepository.getLoreEntryById(0)
                            .thenApply(dto -> {
                                if (dto == null) return null;
                                try {
                                    return LoreEntry.fromDTO(dto);
                                } catch (Exception e) {
                                    logger.error("&c✖ Error converting DTO to LoreEntry", e);
                                    return null;
                                }
                            });
                    })
                    .exceptionally(e -> {
                        logger.error("&c✖ Error getting lore entry: " + id, e);
                        return null;
                    });
            } catch (Exception e) {
                logger.error("&c✖ Error initiating lore entry lookup: " + id, e);
                return CompletableFuture.completedFuture(null);
            }
        });
    }
    
    /**
     * Find pending lore entries that need approval asynchronously.
     */
    public CompletableFuture<List<LoreEntry>> findPendingLoreEntriesAsync() {
        return withCache("pending", () ->
            loreEntryRepository.findPendingLoreEntries()
                .thenApply(this::convertAndFilterDTOs)
                .exceptionally(e -> {
                    logger.error("&c✖ Error finding pending lore entries", e);
                    return new ArrayList<>();
                }));
    }
    
    /**
     * Find recent lore entries asynchronously.
     */
    public CompletableFuture<List<LoreEntry>> findRecentLoreEntriesAsync(int count) {
        if (count <= 0) {
            logger.warning("&e⚠ Cannot find recent lore - count must be positive");
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        String cacheKey = getCacheKey("recent", count);
        return withCache(cacheKey, () ->
            loreEntryRepository.findRecentLoreEntries(count)
                .thenApply(this::convertAndFilterDTOs)
                .exceptionally(e -> {
                    logger.error("&c✖ Error finding recent lore entries", e);
                    return new ArrayList<>();
                }));
    }
    
    /**
     * Search for lore entries containing a text fragment asynchronously.
     */
    public CompletableFuture<List<LoreEntry>> searchLoreAsync(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            logger.warning("&e⚠ Cannot search lore - search text is null or empty");
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        String cacheKey = getCacheKey("search", searchText.toLowerCase());
        return withCache(cacheKey, () ->
            loreEntryRepository.searchLoreEntries(searchText)
                .thenApply(this::convertAndFilterDTOs)
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
            List<CompletableFuture<List<LoreEntry>>> futures = new ArrayList<>();
            futures.add(findNearbyLoreAsync(player.getLocation(), 100.0));
            futures.add(findLoreBySubmitterAsync(player.getName()));
            futures.add(findLoreByPlayerAsync(player.getUniqueId()));

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
    private CompletableFuture<List<LoreEntry>> findLoreByPlayerAsync(UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        String cacheKey = getCacheKey("player-specific", playerUuid.toString());
        return withCache(cacheKey, () ->
            loreEntryRepository.findLoreEntriesByPlayerUuid(playerUuid.toString())
                .thenApply(this::convertAndFilterDTOs)
                .exceptionally(e -> {
                    logger.error("&c✖ Error finding player-specific lore", e);
                    return new ArrayList<>();
                }));
    }
    
    /**
     * Helper method to convert DTOs to LoreEntries and filter out invalid/unapproved entries
     */
    private List<LoreEntry> convertAndFilterDTOs(List<LoreEntryDTO> dtos) {
        return dtos.stream()
            .map(dto -> {
                try {
                    return LoreEntry.fromDTO(dto);
                } catch (Exception e) {
                    logger.debug("Failed to convert DTO to LoreEntry: " + e.getMessage());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .filter(LoreEntry::isApproved)
            .collect(Collectors.toList());
    }
    
    /**
     * Helper method to implement retry logic for database operations
     */
    private <T> CompletableFuture<T> withRetry(Supplier<CompletableFuture<T>> operation, String operationName) {
        return CompletableFuture.supplyAsync(() -> {
            Exception lastException = null;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    return operation.get().get();
                } catch (Exception e) {
                    lastException = e;
                    logger.warning("&e⚠ Attempt " + attempt + " failed for " + operationName + ": " + e.getMessage());
                    if (attempt < MAX_RETRIES) {
                        try {
                            Thread.sleep(1000L * attempt); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new CompletionException(ie);
                        }
                    }
                }
            }
            throw new CompletionException(lastException);
        });
    }
}

package org.fourz.RVNKLore.lore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.data.dto.LoreSubmissionDTO;
import org.fourz.RVNKLore.exception.LoreException;

import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages lore entries and interactions with the database.
 * Handles asynchronous operations and caching for lore entries.
 * Refactored to use DatabaseManager as the single entry point for all DB operations.
 */
public class LoreManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private final Map<String, LoreEntry> cache = new HashMap<>();
    private final Map<String, Long> cacheTimestamps = new HashMap<>();
    private static LoreManager instance;
    private boolean initializing = false;
    private static final int CACHE_DURATION_MINUTES = 30;

    private LoreManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreManager");
        this.databaseManager = plugin.getDatabaseManager();
        setupCacheCleanupTask();
    }

    public static LoreManager getInstance(RVNKLore plugin) {
        if (instance == null) {
            instance = new LoreManager(plugin);
        }
        return instance;
    }

    private void setupCacheCleanupTask() {
        long cleanupInterval = CACHE_DURATION_MINUTES * 60L * 20L; // Convert to ticks
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            List<String> expiredKeys = new ArrayList<>();

            cacheTimestamps.forEach((key, timestamp) -> {
                if (currentTime - timestamp > CACHE_DURATION_MINUTES * 60 * 1000) {
                    expiredKeys.add(key);
                }
            });

            expiredKeys.forEach(key -> {
                cache.remove(key);
                cacheTimestamps.remove(key);
            });

            if (!expiredKeys.isEmpty()) {
                logger.info("Cleared " + expiredKeys.size() + " expired entries from cache");
            }
        }, cleanupInterval, cleanupInterval);
    }

    /**
     * Asynchronously loads all lore entries from the database and updates the cache.
     */
    public CompletableFuture<Void> loadAllLoreEntries() {
        return databaseManager.getAllLoreEntries()
            .thenAccept(dtoList -> {
                cache.clear();
                cacheTimestamps.clear();
                for (LoreEntryDTO dto : dtoList) {
                    LoreEntry entry = LoreEntry.fromDTO(dto);
                    String id = String.valueOf(dto.getId());
                    cache.put(id, entry);
                    cacheTimestamps.put(id, System.currentTimeMillis());
                }
                logger.info("Loaded " + cache.size() + " lore entries.");
            })
            .exceptionally(ex -> {
                logger.error("Failed to load lore entries", ex);
                return null;
            });
    }

    /**
     * Asynchronously adds a new lore entry and updates the cache if successful.
     */
    public CompletableFuture<Boolean> addLoreEntry(LoreEntry entry) {
        LoreEntryDTO dto = LoreEntryDTO.fromLoreEntry(entry);
        return databaseManager.saveLoreEntry(dto)
            .thenApply(id -> {
                if (id != null && id > 0) {
                    entry.setNumericId(id);
                    cache.put(String.valueOf(id), entry);
                    cacheTimestamps.put(String.valueOf(id), System.currentTimeMillis());
                    logger.info("Successfully added lore entry: " + id);
                    return true;
                }
                logger.warning("Failed to add lore entry: null or invalid ID returned");
                return false;
            })
            .exceptionally(ex -> {
                logger.error("Failed to add lore entry", ex);
                return false;
            });
    }

    /**
     * Asynchronously retrieves a lore entry by ID, checking cache first.
     */
    public CompletableFuture<LoreEntry> getLoreEntry(String id) {
        // Check cache first
        LoreEntry cachedEntry = getFromCache(id);
        if (cachedEntry != null) {
            return CompletableFuture.completedFuture(cachedEntry);
        }

        return databaseManager.getLoreEntry(Integer.parseInt(id))
            .thenApply(dto -> {
                if (dto != null) {
                    LoreEntry entry = LoreEntry.fromDTO(dto);
                    cache.put(id, entry);
                    cacheTimestamps.put(id, System.currentTimeMillis());
                    return entry;
                }
                logger.warning("Lore entry not found: " + id);
                return null;
            })
            .exceptionally(ex -> {
                logger.error("Failed to get lore entry: " + id, ex);
                return null;
            });
    }

    /**
     * Asynchronously finds lore entries near a location.
     */
    public CompletableFuture<List<LoreEntry>> findNearbyLore(Location location, double radius) {
        return databaseManager.findNearbyLoreEntries(location, radius)
            .thenApply(dtoList -> {
                List<LoreEntry> entries = new ArrayList<>();
                for (LoreEntryDTO dto : dtoList) {
                    LoreEntry entry = LoreEntry.fromDTO(dto);
                    String id = String.valueOf(dto.getId());
                    cache.put(id, entry);
                    cacheTimestamps.put(id, System.currentTimeMillis());
                    entries.add(entry);
                }
                logger.info("Found " + entries.size() + " nearby lore entries");
                return entries;
            })
            .exceptionally(ex -> {
                logger.error("Failed to find nearby lore entries", ex);
                return new ArrayList<>();
            });
    }

    /**
     * Submits new lore for approval.
     */
    public CompletableFuture<Boolean> submitLore(LoreSubmission submission) {
        LoreSubmissionDTO dto = new LoreSubmissionDTO();
        dto.setContent(submission.getContent());
        dto.setSubmitterUuid(submission.getSubmitterUuid());
        dto.setApprovalStatus("PENDING");
        dto.setSubmissionDate(new Timestamp(System.currentTimeMillis()));

        return databaseManager.saveLoreSubmission(dto)
            .thenApply(id -> {
                if (id != null && id > 0) {
                    logger.info("Successfully submitted lore for approval: " + id);
                    return true;
                }
                logger.warning("Failed to submit lore: null or invalid ID returned");
                return false;
            })
            .exceptionally(ex -> {
                logger.error("Failed to submit lore", ex);
                return false;
            });
    }

    /**
     * Asynchronously approves a submitted lore entry.
     * @param submissionId The ID of the submission to approve
     * @param approverUuid The UUID of the staff member approving the submission
     * @return A future that completes with true if approval was successful
     */
    public CompletableFuture<Boolean> approveLoreSubmission(int submissionId, String approverUuid) {
        return databaseManager.approveLoreSubmission(submissionId, approverUuid)
            .thenApply(success -> {
                if (success) {
                    logger.info("Successfully approved lore submission: " + submissionId);
                } else {
                    logger.warning("Failed to approve lore submission: " + submissionId);
                }
                return success;
            })
            .exceptionally(ex -> {
                logger.error("Error approving lore submission: " + submissionId, ex);
                return false;
            });
    }

    /**
     * Initializes this manager, loading all entries if needed.
     */
    public CompletableFuture<Void> initialize() {
        if (initializing) {
            return CompletableFuture.completedFuture(null);
        }
        initializing = true;
        
        return loadAllLoreEntries()
            .whenComplete((v, ex) -> {
                initializing = false;
                if (ex != null) {
                    logger.error("Failed to initialize LoreManager", ex);
                } else {
                    logger.info("LoreManager initialized successfully");
                }
            });
    }

    /**
     * Retrieves a cached lore entry if available and not expired.
     * @param id The ID of the entry to retrieve from cache
     * @return The cached entry or null if not found or expired
     */
    private LoreEntry getFromCache(String id) {
        if (!cache.containsKey(id) || !cacheTimestamps.containsKey(id)) {
            return null;
        }

        long timestamp = cacheTimestamps.get(id);
        if (System.currentTimeMillis() - timestamp > CACHE_DURATION_MINUTES * 60 * 1000) {
            cache.remove(id);
            cacheTimestamps.remove(id);
            return null;
        }

        return cache.get(id);
    }

    /**
     * Clean up lore manager resources and caches.
     */
    public void cleanup() {
        cache.clear();
        cacheTimestamps.clear();
        instance = null;
        logger.info("LoreManager resources cleaned up.");
    }
}

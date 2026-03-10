package org.fourz.RVNKLore.discovery;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.repository.DiscoveryRepository;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.lore.player.PlayerManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Core manager for the lore discovery system.
 * Coordinates triggers, notifications, and collection tracking.
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * DiscoveryManager discoveryManager = plugin.getDiscoveryManager();
 *
 * // Trigger a discovery
 * discoveryManager.triggerDiscovery(player, loreEntry, DiscoveryTriggerType.LOCATION_ENTER, location);
 *
 * // Check if player has discovered entry
 * boolean discovered = discoveryManager.hasPlayerDiscovered(player, entry);
 * }</pre>
 */
public class DiscoveryManager {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final LoreManager loreManager;
    private final PlayerManager playerManager;
    private final DiscoveryNotificationManager notificationManager;
    private final DiscoveryRepository discoveryRepository;

    // Cache for first-time discoveries (entry ID -> first discoverer UUID)
    private final Map<String, UUID> firstDiscoverers = new ConcurrentHashMap<>();

    // Discovery cooldowns to prevent spam (player UUID -> entry ID -> timestamp)
    private final Map<UUID, Map<String, Long>> discoveryCooldowns = new ConcurrentHashMap<>();
    private static final long DISCOVERY_COOLDOWN_MS = 60000; // 1 minute cooldown
    private static final long PENDING_WRITE_TIMEOUT_MS = 5000; // 5 second drain timeout

    // Track in-flight async writes to prevent data loss on reload/shutdown
    private final Set<CompletableFuture<?>> pendingWrites = ConcurrentHashMap.newKeySet();

    private boolean initialized = false;

    public DiscoveryManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DiscoveryManager");
        this.loreManager = plugin.getLoreManager();
        this.playerManager = plugin.getPlayerManager();
        this.notificationManager = new DiscoveryNotificationManager(plugin);
        this.discoveryRepository = plugin.getDatabaseManager().getDiscoveryRepository();
    }

    /**
     * Initializes the discovery manager.
     */
    public void initialize() {
        if (initialized) {
            logger.warning("DiscoveryManager already initialized");
            return;
        }

        logger.debug("Initializing DiscoveryManager...");

        // Load first discoverers from database if available
        loadFirstDiscoverers();

        // Register event listener
        DiscoveryListener listener = new DiscoveryListener(plugin, this);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        // Register cartography table discovery listener
        CartographyDiscoveryListener cartographyListener = new CartographyDiscoveryListener(plugin, this);
        Bukkit.getPluginManager().registerEvents(cartographyListener, plugin);

        // Register quest discovery listener if RVNKQuests is available
        if (Bukkit.getPluginManager().getPlugin("RVNKQuests") != null) {
            try {
                QuestDiscoveryListener questListener = new QuestDiscoveryListener(plugin, this);
                Bukkit.getPluginManager().registerEvents(questListener, plugin);
                logger.info("RVNKQuests integration enabled - QUEST_COMPLETE discoveries active");
            } catch (NoClassDefFoundError e) {
                logger.warning("RVNKQuests found but event class not available - quest discoveries disabled");
            }
        }

        initialized = true;
        logger.info("DiscoveryManager initialized");
    }

    /**
     * Loads first discoverers from the database.
     */
    private void loadFirstDiscoverers() {
        if (discoveryRepository == null) {
            logger.warning("DiscoveryRepository not available, first discoverers will be tracked from this session only");
            return;
        }
        try {
            Map<String, UUID> loaded = discoveryRepository.loadAllFirstDiscoverers().join();
            firstDiscoverers.putAll(loaded);
            logger.info("Loaded " + loaded.size() + " first discoverers from database");
        } catch (Exception e) {
            logger.warning("Failed to load first discoverers: " + e.getMessage());
        }
    }

    /**
     * Triggers a lore discovery for a player.
     *
     * @param player The player discovering the lore
     * @param entry The lore entry being discovered
     * @param triggerType The type of trigger
     * @param location The location where discovery occurred (can be null)
     * @return CompletableFuture resolving to true if discovery was recorded
     */
    public CompletableFuture<Boolean> triggerDiscovery(Player player, LoreEntry entry,
                                                        DiscoveryTriggerType triggerType,
                                                        Location location) {
        if (player == null || entry == null) {
            return CompletableFuture.completedFuture(false);
        }

        UUID playerUuid = player.getUniqueId();
        String entryId = entry.getId();

        // Check cooldown
        if (isOnCooldown(playerUuid, entryId)) {
            logger.debug("Discovery on cooldown for " + player.getName() + " - " + entry.getName());
            return CompletableFuture.completedFuture(false);
        }

        // Check if player has already discovered this entry
        return hasPlayerDiscoveredAsync(playerUuid, entryId).thenCompose(alreadyDiscovered -> {
            boolean isFirstForPlayer = !alreadyDiscovered;
            boolean isFirstDiscovery = !firstDiscoverers.containsKey(entryId);

            // Create and fire the event
            LoreDiscoveryEvent event = new LoreDiscoveryEvent(
                player, entry, triggerType, location, isFirstDiscovery, isFirstForPlayer
            );

            // Fire event on main thread
            return runOnMainThread(() -> {
                Bukkit.getPluginManager().callEvent(event);
                return event;
            }).thenCompose(evt -> {
                if (evt.isCancelled()) {
                    logger.debug("Discovery cancelled for " + player.getName() + " - " + entry.getName());
                    return CompletableFuture.completedFuture(false);
                }

                // Record discovery with full context
                return recordDiscovery(playerUuid, entryId, isFirstDiscovery, triggerType, location).thenApply(recorded -> {
                    if (recorded) {
                        // Set cooldown
                        setCooldown(playerUuid, entryId);

                        // Send notification (on main thread)
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            notificationManager.sendDiscoveryNotification(evt);
                        });

                        logger.info("Player " + player.getName() + " discovered: " + entry.getName() +
                            (isFirstDiscovery ? " (FIRST DISCOVERY)" : ""));
                    }
                    return recorded;
                });
            });
        }).exceptionally(ex -> {
            logger.error("Error triggering discovery for " + player.getName(), ex);
            return false;
        });
    }

    /**
     * Records a discovery in the player's collection and the enriched lore_discovery table.
     */
    private CompletableFuture<Boolean> recordDiscovery(UUID playerUuid, String entryId,
                                                        boolean isFirstDiscovery,
                                                        DiscoveryTriggerType triggerType,
                                                        Location location) {
        // Track first discoverer in cache
        if (isFirstDiscovery) {
            firstDiscoverers.put(entryId, playerUuid);
        }

        // Record in PlayerManager (legacy player_discoveries table)
        CompletableFuture<Boolean> legacyRecord = trackWrite(playerManager.recordLoreDiscovery(playerUuid, entryId));

        // Also persist to enriched lore_discovery table with full context
        if (discoveryRepository != null) {
            String world = location != null && location.getWorld() != null ? location.getWorld().getName() : null;
            Double x = location != null ? location.getX() : null;
            Double y = location != null ? location.getY() : null;
            Double z = location != null ? location.getZ() : null;

            trackWrite(discoveryRepository.recordDiscovery(playerUuid, entryId,
                    triggerType != null ? triggerType.name() : "UNKNOWN",
                    world, x, y, z, isFirstDiscovery)
                .exceptionally(ex -> {
                    logger.warning("Failed to persist enriched discovery: " + ex.getMessage());
                    return false;
                }));
        }

        return legacyRecord;
    }

    /**
     * Checks if a player has already discovered an entry.
     */
    public CompletableFuture<Boolean> hasPlayerDiscoveredAsync(UUID playerUuid, String entryId) {
        if (discoveryRepository != null) {
            return discoveryRepository.hasDiscovered(playerUuid, entryId);
        }
        // Fallback to legacy player_discoveries table
        return playerManager.hasDiscoveredEntry(playerUuid, entryId);
    }

    /**
     * Synchronous check if player has discovered entry (uses cache).
     */
    public boolean hasPlayerDiscovered(Player player, LoreEntry entry) {
        if (player == null || entry == null) return false;

        try {
            return hasPlayerDiscoveredAsync(player.getUniqueId(), entry.getId())
                .get(java.util.concurrent.TimeUnit.SECONDS.toMillis(5), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.debug("Timeout checking discovery status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the first discoverer of an entry.
     */
    public Optional<UUID> getFirstDiscoverer(String entryId) {
        return Optional.ofNullable(firstDiscoverers.get(entryId));
    }

    /**
     * Gets all entries discovered by a player.
     */
    public CompletableFuture<List<LoreEntry>> getPlayerDiscoveries(UUID playerUuid) {
        CompletableFuture<List<String>> idsFuture;
        if (discoveryRepository != null) {
            idsFuture = discoveryRepository.getDiscoveredEntryIds(playerUuid);
        } else {
            idsFuture = playerManager.getPlayerLoreEntryIds(playerUuid);
        }
        return idsFuture.thenApply(ids -> {
            List<LoreEntry> entries = new ArrayList<>();
            for (String id : ids) {
                loreManager.getLoreById(id).ifPresent(entries::add);
            }
            return entries;
        });
    }

    /**
     * Gets discovery statistics for a player.
     */
    public CompletableFuture<DiscoveryStats> getPlayerStats(UUID playerUuid) {
        if (discoveryRepository != null) {
            CompletableFuture<Integer> countFuture = discoveryRepository.countPlayerDiscoveries(playerUuid);
            CompletableFuture<Integer> firstFuture = discoveryRepository.countFirstDiscoveries(playerUuid);
            return countFuture.thenCombine(firstFuture, (discovered, firstCount) -> {
                int totalEntries = loreManager.getAllLoreEntriesSync().size();
                return new DiscoveryStats(playerUuid, discovered, totalEntries, firstCount);
            });
        }
        return getPlayerDiscoveries(playerUuid).thenApply(discoveries -> {
            int totalEntries = loreManager.getAllLoreEntriesSync().size();
            int discovered = discoveries.size();
            long firstDiscoveries = discoveries.stream()
                .filter(entry -> playerUuid.equals(firstDiscoverers.get(entry.getId())))
                .count();
            return new DiscoveryStats(playerUuid, discovered, totalEntries, (int) firstDiscoveries);
        });
    }

    // Pending write tracking

    private <T> CompletableFuture<T> trackWrite(CompletableFuture<T> future) {
        pendingWrites.add(future);
        future.whenComplete((result, ex) -> pendingWrites.remove(future));
        return future;
    }

    /**
     * Blocks until all in-flight discovery writes complete (or timeout).
     * Call before reload or shutdown to prevent data loss.
     */
    public void awaitPendingWrites() {
        if (pendingWrites.isEmpty()) {
            return;
        }
        logger.info("Awaiting " + pendingWrites.size() + " pending discovery writes...");
        try {
            CompletableFuture.allOf(pendingWrites.toArray(new CompletableFuture[0]))
                .get(PENDING_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            logger.info("All pending discovery writes completed");
        } catch (Exception e) {
            logger.warning("Timed out waiting for pending writes: " + e.getMessage());
        }
    }

    // Cooldown management

    private boolean isOnCooldown(UUID playerUuid, String entryId) {
        Map<String, Long> playerCooldowns = discoveryCooldowns.get(playerUuid);
        if (playerCooldowns == null) return false;

        Long cooldownEnd = playerCooldowns.get(entryId);
        if (cooldownEnd == null) return false;

        return System.currentTimeMillis() < cooldownEnd;
    }

    private void setCooldown(UUID playerUuid, String entryId) {
        discoveryCooldowns.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
            .put(entryId, System.currentTimeMillis() + DISCOVERY_COOLDOWN_MS);
    }

    /**
     * Clears discovery cooldowns for a player.
     */
    public void clearCooldowns(UUID playerUuid) {
        discoveryCooldowns.remove(playerUuid);
    }

    // Utility method to run code on main thread

    private <T> CompletableFuture<T> runOnMainThread(java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();

        if (Bukkit.isPrimaryThread()) {
            try {
                future.complete(supplier.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    future.complete(supplier.get());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        }

        return future;
    }

    /**
     * Shuts down the discovery manager.
     */
    public void shutdown() {
        awaitPendingWrites();
        discoveryCooldowns.clear();
        initialized = false;
        logger.debug("DiscoveryManager shut down");
    }

    public boolean isInitialized() {
        return initialized;
    }

    public DiscoveryNotificationManager getNotificationManager() {
        return notificationManager;
    }

    /**
     * Discovery statistics holder.
     */
    public static class DiscoveryStats {
        private final UUID playerId;
        private final int discovered;
        private final int total;
        private final int firstDiscoveries;

        public DiscoveryStats(UUID playerId, int discovered, int total, int firstDiscoveries) {
            this.playerId = playerId;
            this.discovered = discovered;
            this.total = total;
            this.firstDiscoveries = firstDiscoveries;
        }

        public UUID getPlayerId() { return playerId; }
        public int getDiscovered() { return discovered; }
        public int getTotal() { return total; }
        public int getFirstDiscoveries() { return firstDiscoveries; }
        public double getCompletionPercentage() {
            return total > 0 ? (discovered * 100.0) / total : 0.0;
        }
    }
}

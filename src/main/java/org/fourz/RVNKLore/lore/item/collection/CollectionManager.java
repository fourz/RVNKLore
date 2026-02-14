package org.fourz.RVNKLore.lore.item.collection;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.cosmetic.HeadCollection;
import org.fourz.RVNKLore.data.ItemRepository;
import org.fourz.RVNKLore.data.model.CollectionReward;
import org.fourz.RVNKLore.data.repository.CollectionRewardRepository;
import org.fourz.RVNKLore.lore.item.collection.reward.RewardHandlerRegistry;
import org.fourz.RVNKLore.lore.item.collection.event.CollectionChangeEvent;
import org.fourz.RVNKLore.lore.item.collection.event.CollectionEventType;
import org.fourz.RVNKLore.service.ICollectionService;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages item collections and thematic groupings within the lore system.
 * Handles organization, tracking, and distribution of items across different collections.
 * This manager coordinates with the CosmeticItem for head collections while
 * also providing a unified interface for managing different types of items.
 * 
 * Implements ICollectionService for cross-plugin access via RVNKCore ServiceRegistry.
 */
public class CollectionManager implements ICollectionService {
    
    /** Tracks whether the service is operating in degraded mode due to errors */
    private volatile boolean fallbackMode = false;
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, ItemCollection> collections = new ConcurrentHashMap<>();
    private final Map<String, CollectionTheme> themes = new ConcurrentHashMap<>();
    private final RewardHandlerRegistry rewardHandlers;

    public CollectionManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CollectionManager");
        this.rewardHandlers = new RewardHandlerRegistry(plugin);
        initializeCollections();
        logger.info("CollectionManager initialized with reward handlers");
    }

    private void initializeCollections() {
        createDefaultThemes();

        // Load collections from database if available
        try {
            loadCollectionsFromDatabase();
        } catch (Exception e) {
            logger.warning("Failed to load collections from database, using defaults");
            // Fall through to create defaults
        }

        // Create defaults if no collections loaded
        if (collections.isEmpty()) {
            createCollection("starter_items", "Starter Collection", "Basic items for new players");
            createCollection("rare_finds", "Rare Discoveries", "Uncommon items found throughout the world");
            createCollection("legendary_artifacts", "Legendary Artifacts", "Powerful items of great significance");
            logger.info("Default collections initialized");
        }

        // Load items for all collections from database
        for (ItemCollection collection : new ArrayList<>(collections.values())) {
            try {
                loadItemsForCollectionSync(collection);
            } catch (Exception e) {
                logger.warning("Failed to load items for collection " + collection.getId() + ": " + e.getMessage());
            }
        }
    }

    private void createDefaultThemes() {
        for (CollectionTheme theme : CollectionTheme.values()) {
            themes.put(theme.name().toLowerCase(), theme);
            logger.debug("Registered collection theme: " + theme.getDisplayName());
        }
    }

    /**
     * Creates a new collection with validation (sync internal method)
     * 
     * @param id Collection identifier
     * @param name Display name
     * @param description Collection description
     * @return The created collection, or null if validation failed
     */
    public ItemCollection createCollectionSync(String id, String name, String description) {
        // Validate the collection before creation
        if (!validateNewCollection(id, name, description)) {
            logger.warning("Failed to create collection due to validation errors: " + id);
            return null;
        }
        
        ItemCollection collection = new ItemCollection(id, name, description);
        collections.put(id, collection);
        logger.info("Created collection: " + name + " (" + id + ")");
        return collection;
    }
    
    /**
     * Creates a new collection with validation (async interface method).
     * Implements ICollectionService.createCollection().
     */
    @Override
    public CompletableFuture<Optional<ItemCollection>> createCollection(String id, String name, String description) {
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(createCollectionSync(id, name, description)));
    }
    
    /**
     * Validate a new collection before creation
     * 
     * @param id Collection identifier
     * @param name Display name
     * @param description Collection description
     * @return True if valid, false otherwise
     */
    private boolean validateNewCollection(String id, String name, String description) {
        if (id == null || id.trim().isEmpty()) {
            logger.warning("Collection validation failed: missing or empty ID");
            return false;
        }
        
        if (name == null || name.trim().isEmpty()) {
            logger.warning("Collection validation failed: missing or empty name");
            return false;
        }
        
        // Check for duplicate IDs
        if (collections.containsKey(id)) {
            logger.warning("Collection validation failed: duplicate ID - " + id);
            return false;
        }
        
        // Validate ID format (lowercase alphanumeric + underscore)
        if (!id.matches("^[a-z0-9_]+$")) {
            logger.warning("Collection validation failed: invalid ID format - " + id);
            return false;
        }
        
        logger.debug("Collection validation passed: " + id);
        return true;
    }
    
    /**
     * Validate an existing collection
     * 
     * @param collection The collection to validate
     * @return True if valid, false otherwise
     */
    private boolean validateCollection(ItemCollection collection) {
        if (collection == null) {
            logger.warning("Collection validation failed: null collection");
            return false;
        }
        
        if (collection.getId() == null || collection.getId().trim().isEmpty()) {
            logger.warning("Collection validation failed: missing or empty ID");
            return false;
        }
        
        if (collection.getName() == null || collection.getName().trim().isEmpty()) {
            logger.warning("Collection validation failed: missing or empty name");
            return false;
        }
        
        logger.debug("Collection validation passed: " + collection.getId());
        return true;
    }
    
    /**
     * Gets a collection by its ID (sync internal method).
     */
    public ItemCollection getCollectionSync(String id) {
        if (id == null || id.trim().isEmpty()) {
            logger.warning("Cannot retrieve collection: null or empty ID provided");
            return null;
        }
        
        ItemCollection collection = collections.get(id);
        if (collection == null) {
            logger.debug("Collection not found: " + id);
        }
        
        return collection;
    }
    
    /**
     * Gets a collection by its ID (async interface method).
     * Implements ICollectionService.getCollection().
     */
    @Override
    public CompletableFuture<Optional<ItemCollection>> getCollection(String id) {
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(getCollectionSync(id)));
    }

    /**
     * Gets all collections (sync internal method).
     */
    public Map<String, ItemCollection> getAllCollectionsSync() {
        return new HashMap<>(collections);
    }
    
    /**
     * Gets all collections (async interface method).
     * Implements ICollectionService.getAllCollections().
     */
    @Override
    public CompletableFuture<Map<String, ItemCollection>> getAllCollections() {
        return CompletableFuture.supplyAsync(this::getAllCollectionsSync);
    }

    public ItemStack createCollectionItem(ItemProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("ItemProperties cannot be null");
        }
        Material material = properties.getMaterial();
        if (material == null) {
            material = Material.PAPER;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (properties.getDisplayName() != null) {
                meta.setDisplayName(properties.getDisplayName());
            }
            List<String> lore = properties.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            String collectionId = properties.getCollectionId();
            if (collectionId != null) {
                ItemCollection collection = getCollectionSync(collectionId);
                if (collection != null) {
                    lore.add("Â§7Collection: Â§a" + collection.getName());
                }
            }
            if (properties.getRarityLevel() != null) {
                lore.add("Â§7Rarity: Â§e" + properties.getRarityLevel());
            }
            meta.setLore(lore);            
            meta.setCustomModelData(properties.getCustomModelData());            
            item.setItemMeta(meta);
        }
        logger.debug("Created collection item: " + properties.getDisplayName());
        return item;
    }

    /**
     * Adds an item to a collection (sync internal method).
     */
    public boolean addItemToCollectionSync(String collectionId, ItemStack item) {
        ItemCollection collection = getCollectionSync(collectionId);
        if (collection == null) {
            logger.warning("Cannot add item to non-existent collection: " + collectionId);
            return false;
        }
        collection.addItem(item);
        logger.debug("Added item to collection: " + collectionId);
        return true;
    }
    
    /**
     * Adds an item to a collection (async interface method).
     * Implements ICollectionService.addItemToCollection().
     */
    @Override
    public CompletableFuture<Boolean> addItemToCollection(String collectionId, ItemStack item) {
        return CompletableFuture.supplyAsync(() -> addItemToCollectionSync(collectionId, item));
    }

    /**
     * Removes an item from a collection (sync internal method).
     */
    public boolean removeItemFromCollectionSync(String collectionId, ItemStack item) {
        ItemCollection collection = getCollectionSync(collectionId);
        if (collection == null) {
            return false;
        }
        boolean removed = collection.removeItem(item);
        if (removed) {
            logger.debug("Removed item from collection: " + collectionId);
        }
        return removed;
    }
    
    /**
     * Removes an item from a collection (async interface method).
     * Implements ICollectionService.removeItemFromCollection().
     */
    @Override
    public CompletableFuture<Boolean> removeItemFromCollection(String collectionId, ItemStack item) {
        return CompletableFuture.supplyAsync(() -> removeItemFromCollectionSync(collectionId, item));
    }

    /**
     * Gets items in a collection (sync internal method).
     */
    public List<ItemStack> getCollectionItemsSync(String collectionId) {
        ItemCollection collection = getCollectionSync(collectionId);
        return collection != null ? collection.getItems() : new ArrayList<>();
    }
    
    /**
     * Gets items in a collection (async interface method).
     * Implements ICollectionService.getCollectionItems().
     */
    @Override
    public CompletableFuture<List<ItemStack>> getCollectionItems(String collectionId) {
        return CompletableFuture.supplyAsync(() -> getCollectionItemsSync(collectionId));
    }

    public Integer getItemCount(String collectionId) {
        ItemCollection collection = getCollectionSync(collectionId);
        return collection != null ? collection.getItemCount() : 0;
    }

    public void registerTheme(CollectionTheme theme) {
        if (theme == null) return;
        themes.put(theme.name().toLowerCase(), theme);
        logger.info("Registered collection theme: " + theme.getDisplayName());
    }

    public CollectionTheme getTheme(String id) {
        return themes.get(id);
    }

    public Map<String, CollectionTheme> getAllThemes() {
        return new HashMap<>(themes);
    }

    public void shutdown() {
        collections.clear();
        themes.clear();
        logger.info("CollectionManager shutdown");
    }

    /**
     * Initialize collections from database on startup
     */
    private void loadCollectionsFromDatabase() {
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - using default collections only");
            return;
        }
        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        List<ItemCollection> loadedCollections = repository.loadAllCollections().join();
        for (ItemCollection collection : loadedCollections) {
            collections.put(collection.getId(), collection);
            logger.info("Loaded collection from database: " + collection.getName());
        }
        logger.info("Loaded " + loadedCollections.size() + " collections from database");
    }

    /**
     * Save a collection to the database with enhanced error handling (sync internal method)
     *
     * @param collection The collection to persist
     * @return True if successfully saved
     */
    public boolean saveCollectionSync(ItemCollection collection) {
        if (!validateCollection(collection)) {
            logger.warning("Cannot save invalid collection");
            return false;
        }
        
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - collection will not be persisted");
            fallbackMode = true;
            return false;
        }
        
        try {
            ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
            boolean saved = repository.saveCollection(collection).join();
            
            if (saved) {
                logger.info("Successfully saved collection: " + collection.getId());
                // Update the in-memory collection
                collections.put(collection.getId(), collection);
            } else {
                logger.warning("Failed to save collection: " + collection.getId());
            }
            
            return saved;
        } catch (Exception e) {
            logger.error("Error saving collection: " + collection.getId(), e);
            fallbackMode = true;
            return false;
        }
    }
    
    /**
     * Save a collection to the database (async interface method).
     * Implements ICollectionService.saveCollection().
     */
    @Override
    public CompletableFuture<Boolean> saveCollection(ItemCollection collection) {
        return CompletableFuture.supplyAsync(() -> saveCollectionSync(collection));
    }

    /**
     * Load items for a collection from the database (sync internal method).
     * Populates the collection with items persisted to the collection_item junction table.
     *
     * @param collection The collection to load items into
     * @return True if items were loaded successfully
     */
    public boolean loadItemsForCollectionSync(ItemCollection collection) {
        if (collection == null) {
            logger.warning("Cannot load items for null collection");
            return false;
        }

        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.debug("Database not available - skipping item loading for collection: " + collection.getId());
            return false;
        }

        try {
            // First, get item IDs from the junction table
            ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());

            // Get the numeric collection ID from database
            int collectionDbId = getCollectionDatabaseId(collection.getId());
            if (collectionDbId <= 0) {
                logger.warning("Collection not found in database: " + collection.getId());
                return false;
            }

            // Query items in this collection (ordered by sequence_number)
            List<ItemProperties> itemProps = repository.getItemsByCollection(collectionDbId).join();

            if (itemProps.isEmpty()) {
                logger.debug("No items found for collection: " + collection.getId());
                return true;
            }

            // Convert ItemProperties to ItemStack and add to collection
            for (ItemProperties props : itemProps) {
                if (props != null && props.getMaterial() != null) {
                    ItemStack item = new ItemStack(props.getMaterial());
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && props.getDisplayName() != null) {
                        meta.setDisplayName(props.getDisplayName());
                    }
                    if (meta != null && props.getCustomModelData() > 0) {
                        meta.setCustomModelData(props.getCustomModelData());
                    }
                    if (meta != null) {
                        item.setItemMeta(meta);
                    }
                    collection.addItem(item);
                }
            }

            logger.info("Loaded " + itemProps.size() + " items for collection: " + collection.getId());
            return true;
        } catch (Exception e) {
            logger.error("Failed to load items for collection: " + collection.getId(), e);
            return false;
        }
    }

    /**
     * Load items for a collection from the database (async interface method).
     *
     * @param collection The collection to load items into
     * @return CompletableFuture that completes with true if successful
     */
    public CompletableFuture<Boolean> loadItemsForCollection(ItemCollection collection) {
        return CompletableFuture.supplyAsync(() -> loadItemsForCollectionSync(collection));
    }

    /**
     * Get the numeric database ID for a collection by its string ID.
     * Helper method for looking up database collection IDs.
     *
     * @param collectionId The string collection ID
     * @return The numeric database ID, or -1 if not found
     */
    private int getCollectionDatabaseId(String collectionId) {
        try {
            ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
            Map<Integer, String> allCollections = repository.getAllCollections().join();
            for (Map.Entry<Integer, String> entry : allCollections.entrySet()) {
                if (entry.getValue().equals(collectionId)) {
                    return entry.getKey();
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to lookup collection database ID: " + collectionId);
        }
        return -1;
    }

    /**
     * Get a player's progress for a specific collection (sync internal method)
     * 
     * @param playerId The player's UUID
     * @param collectionId The collection identifier
     * @return Progress value between 0.0 and 1.0
     */
    public double getPlayerProgressSync(UUID playerId, String collectionId) {
        if (playerId == null || collectionId == null) {
            return 0.0;
        }
        
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - cannot retrieve player progress");
            fallbackMode = true;
            return 0.0;
        }
        
        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        return repository.getPlayerCollectionProgress(playerId.toString(), collectionId).join();
    }
    
    /**
     * Get a player's progress for a specific collection (async interface method).
     * Implements ICollectionService.getPlayerProgress().
     */
    @Override
    public CompletableFuture<Double> getPlayerProgress(UUID playerId, String collectionId) {
        return CompletableFuture.supplyAsync(() -> getPlayerProgressSync(playerId, collectionId));
    }

    /**
     * Update a player's progress for a collection (sync internal method)
     * 
     * @param playerId The player's UUID
     * @param collectionId The collection identifier
     * @param progress Progress value between 0.0 and 1.0
     * @return True if successfully updated
     */
    public boolean updatePlayerProgressSync(UUID playerId, String collectionId, double progress) {
        if (playerId == null || collectionId == null || progress < 0.0 || progress > 1.0) {
            logger.warning("Invalid parameters for progress update");
            return false;
        }
        
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - cannot update player progress");
            return false;
        }
        
        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        boolean updated = repository.updatePlayerCollectionProgress(playerId.toString(), collectionId, progress).join();
        
        if (updated) {
            logger.info("Updated progress for player " + playerId + " in collection " + collectionId + ": " + String.format("%.1f%%", progress * 100));
            
            // Check for completion and trigger rewards
            if (progress >= 1.0) {
                handleCollectionCompletion(playerId, collectionId);
            }
        }
        
        return updated;
    }
    
    /**
     * Update a player's progress for a collection (async interface method).
     * Implements ICollectionService.updatePlayerProgress().
     */
    @Override
    public CompletableFuture<Boolean> updatePlayerProgress(UUID playerId, String collectionId, double progress) {
        return CompletableFuture.supplyAsync(() -> updatePlayerProgressSync(playerId, collectionId, progress));
    }
    
    /**
     * Handle collection completion events and rewards
     * 
     * @param playerId The player who completed the collection
     * @param collectionId The completed collection
     */
    private void handleCollectionCompletion(UUID playerId, String collectionId) {
        ItemCollection collection = getCollectionSync(collectionId);
        if (collection == null) {
            logger.warning("Cannot handle completion for unknown collection: " + collectionId);
            return;
        }

        logger.info("Player " + playerId + " completed collection: " + collection.getName());

        // Emit a collection completion event for external systems (to be integrated)
        // TODO: Fire CollectionChangeEvent with ChangeType.COMPLETED for event-driven handling

        // Mark completion timestamp in the database
        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        repository.markCollectionCompleted(playerId.toString(), collectionId, System.currentTimeMillis());
    }

    /**
     * Grant collection rewards to a player (sync internal method)
     * Validates rewards, executes handlers, and marks rewards as claimed in the database.
     *
     * @param playerId The player's UUID
     * @param collectionId The collection identifier
     * @return True if rewards were successfully granted
     */
    public boolean grantCollectionRewardSync(UUID playerId, String collectionId) {
        if (getPlayerProgressSync(playerId, collectionId) < 1.0) {
            logger.warning("Cannot grant rewards - collection not completed by player " + playerId);
            return false;
        }

        ItemCollection collection = getCollectionSync(collectionId);
        if (collection == null) {
            logger.warning("Cannot grant rewards for unknown collection: " + collectionId);
            return false;
        }

        // Get the player (must be online to receive rewards)
        org.bukkit.entity.Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            logger.warning("Cannot grant rewards - player is not online: " + playerId);
            return false;
        }

        CollectionRewardRepository rewardRepo = getRewardRepository();
        if (rewardRepo == null) {
            logger.warning("Reward repository not available - cannot grant rewards");
            return false;
        }

        List<CollectionReward> rewards = rewardRepo.findByCollection(collectionId).join();
        int granted = 0;
        int failed = 0;

        for (CollectionReward reward : rewards) {
            // Check if already claimed
            if (rewardRepo.hasPlayerClaimed(reward.getId(), playerId).join()) {
                logger.debug("Reward " + reward.getId() + " already claimed by player " + playerId);
                continue;
            }

            // Validate reward data
            if (!validateReward(reward)) {
                logger.warning("Invalid reward data for reward " + reward.getId() + ": " + reward.getRewardData());
                failed++;
                continue;
            }

            // Get handler for this reward type
            var handler = rewardHandlers.getHandler(reward.getRewardType());
            if (handler == null) {
                logger.warning("No handler found for reward type: " + reward.getRewardType());
                failed++;
                continue;
            }

            // Execute the reward
            try {
                if (handler.executeReward(player, reward)) {
                    // Mark as claimed in database
                    rewardRepo.claimReward(reward.getId(), playerId).join();
                    granted++;
                    logger.info("Granted reward " + reward.getId() + " (" + reward.getRewardType() + ") to player " + player.getName());
                } else {
                    logger.warning("Handler failed to execute reward " + reward.getId() + " for player " + playerId);
                    failed++;
                }
            } catch (Exception e) {
                logger.error("Exception while granting reward " + reward.getId() + ": " + e.getMessage());
                failed++;
            }
        }

        logger.info("Collection reward distribution complete for " + player.getName() + ": " + granted + " granted, " + failed + " failed");

        // Fire event if rewards were granted
        if (granted > 0) {
            fireRewardGranted(collection, playerId);
        }

        return granted > 0;
    }
    
    /**
     * Grant collection rewards to a player (async interface method).
     * Implements ICollectionService.grantCollectionReward().
     */
    @Override
    public CompletableFuture<Boolean> grantCollectionReward(UUID playerId, String collectionId) {
        return CompletableFuture.supplyAsync(() -> grantCollectionRewardSync(playerId, collectionId));
    }

    /**
     * Emit collection change event for external plugins to listen to.
     * Maps internal ChangeType to public CollectionEventType and fires event.
     *
     * @param collection The collection that changed
     * @param changeType The type of change
     */
    @Deprecated
    @SuppressWarnings("unused")
    private void fireCollectionChangeEvent(ItemCollection collection, ChangeType changeType) {
        // Legacy method - use the overloaded versions instead
        CollectionEventType eventType = switch (changeType) {
            case COMPLETED -> CollectionEventType.COMPLETED;
            case CREATED -> CollectionEventType.CREATED;
            case DELETED -> CollectionEventType.DELETED;
            case UPDATED -> CollectionEventType.PROGRESS_UPDATE;
        };
        logger.debug("Collection change event: " + eventType + " for " + collection.getId());
    }

    /**
     * Fire a collection event for player progress changes.
     *
     * @param collection The collection
     * @param playerUuid The player affected
     * @param eventType The type of event
     * @param previousProgress Previous progress (0-1.0)
     * @param currentProgress Current progress (0-1.0)
     */
    private void fireCollectionEvent(ItemCollection collection, UUID playerUuid, CollectionEventType eventType,
                                     double previousProgress, double currentProgress) {
        try {
            CollectionChangeEvent event = new CollectionChangeEvent(collection, playerUuid, eventType,
                    previousProgress, currentProgress);
            Bukkit.getPluginManager().callEvent(event);
            logger.debug("Fired " + eventType + " event for collection " + collection.getId() +
                    " player " + playerUuid);
        } catch (Exception e) {
            logger.error("Failed to fire collection event: " + e.getMessage());
        }
    }

    /**
     * Fire a collection completion event.
     *
     * @param collection The collection
     * @param playerUuid The player who completed it
     */
    public void fireCollectionCompleted(ItemCollection collection, UUID playerUuid) {
        fireCollectionEvent(collection, playerUuid, CollectionEventType.COMPLETED, 0.99, 1.0);
    }

    /**
     * Fire a reward granted event.
     *
     * @param collection The collection
     * @param playerUuid The player receiving rewards
     */
    public void fireRewardGranted(ItemCollection collection, UUID playerUuid) {
        fireCollectionEvent(collection, playerUuid, CollectionEventType.REWARD_GRANTED, 1.0, 1.0);
    }

    /**
     * Fire a progress update event.
     *
     * @param collection The collection
     * @param playerUuid The player
     * @param previousProgress Previous progress
     * @param currentProgress Current progress
     */
    public void fireProgressUpdate(ItemCollection collection, UUID playerUuid, double previousProgress, double currentProgress) {
        fireCollectionEvent(collection, playerUuid, CollectionEventType.PROGRESS_UPDATE, previousProgress, currentProgress);
    }

    private CollectionRewardRepository getRewardRepository() {
        if (plugin.getDatabaseManager() == null) return null;
        return plugin.getDatabaseManager().getCollectionRewardRepository();
    }

    /**
     * Validate a reward's data format before execution.
     *
     * @param reward The reward to validate
     * @return true if reward data is valid for its type, false otherwise
     */
    private boolean validateReward(CollectionReward reward) {
        var handler = rewardHandlers.getHandler(reward.getRewardType());
        if (handler == null) {
            logger.warning("No handler exists for reward type: " + reward.getRewardType());
            return false;
        }
        return handler.validateRewardData(reward.getRewardData());
    }

    /**
     * Types of collection changes for event system
     */
    public enum ChangeType {
        CREATED, UPDATED, DELETED, COMPLETED
    }

    /**
     * Get all collections, optionally filtered by theme (sync internal method).
     * 
     * @param themeId The theme ID to filter by, or null for all
     * @return Map of collection IDs to ItemCollection
     */
    public Map<String, ItemCollection> getCollectionsByThemeSync(String themeId) {
        if (themeId == null) {
            return getAllCollectionsSync();
        }
        Map<String, ItemCollection> filtered = new HashMap<>();
        for (Map.Entry<String, ItemCollection> entry : collections.entrySet()) {
            if (themeId.equalsIgnoreCase(entry.getValue().getThemeId())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }
    
    /**
     * Get all collections, optionally filtered by theme (async interface method).
     * Implements ICollectionService.getCollectionsByTheme().
     */
    @Override
    public CompletableFuture<Map<String, ItemCollection>> getCollectionsByTheme(String themeId) {
        return CompletableFuture.supplyAsync(() -> getCollectionsByThemeSync(themeId));
    }
    
    /**
     * Check if the service is in fallback mode due to errors.
     * Implements ICollectionService.isInFallbackMode().
     */
    @Override
    public boolean isInFallbackMode() {
        return fallbackMode;
    }

    /**
     * Reload all collections from the database.
     * This will replace the in-memory map with the latest from storage.
     */
    public void reloadCollectionsFromDatabase() {
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - cannot reload collections");
            return;
        }
        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        List<ItemCollection> loadedCollections = repository.loadAllCollections().join();
        collections.clear();
        for (ItemCollection collection : loadedCollections) {
            collections.put(collection.getId(), collection);
        }
        logger.info("Reloaded " + loadedCollections.size() + " collections from database");
    }

    /**
     * Track individual item discovery for a player (sync internal method).
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @param itemId The item ID being discovered
     * @return True if successfully recorded
     */
    public boolean trackItemDiscoverySync(UUID playerId, String collectionId, int itemId) {
        if (playerId == null || collectionId == null || itemId <= 0) {
            logger.warning("Invalid parameters for item discovery tracking");
            return false;
        }

        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - cannot track item discovery");
            return false;
        }

        // Get collection database ID
        int collectionDbId = getCollectionDatabaseId(collectionId);
        if (collectionDbId <= 0) {
            logger.warning("Collection not found in database: " + collectionId);
            return false;
        }

        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        boolean tracked = repository.trackItemDiscovery(playerId.toString(), collectionDbId, itemId).join();

        if (tracked) {
            logger.debug("Tracked item discovery for player " + playerId + " in collection " + collectionId);
        }

        return tracked;
    }

    /**
     * Track individual item discovery for a player (async interface method).
     * Implements ICollectionService.trackItemDiscovery().
     */
    @Override
    public CompletableFuture<Boolean> trackItemDiscovery(UUID playerId, String collectionId, int itemId) {
        return CompletableFuture.supplyAsync(() -> trackItemDiscoverySync(playerId, collectionId, itemId));
    }

    /**
     * Get all items discovered by a player in a collection (sync internal method).
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @return List of discovered items as ItemStack
     */
    public List<ItemStack> getPlayerCollectionItemsSync(UUID playerId, String collectionId) {
        if (playerId == null || collectionId == null) {
            return new ArrayList<>();
        }

        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - cannot retrieve collection items");
            return new ArrayList<>();
        }

        // Get collection database ID
        int collectionDbId = getCollectionDatabaseId(collectionId);
        if (collectionDbId <= 0) {
            logger.warning("Collection not found in database: " + collectionId);
            return new ArrayList<>();
        }

        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        List<ItemProperties> items = repository.getCollectedItems(playerId.toString(), collectionDbId).join();

        List<ItemStack> result = new ArrayList<>();
        for (ItemProperties props : items) {
            ItemStack stack = new ItemStack(Material.PAPER);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(props.getDisplayName());
                stack.setItemMeta(meta);
            }
            result.add(stack);
        }

        return result;
    }

    /**
     * Get all items discovered by a player in a collection (async interface method).
     * Implements ICollectionService.getPlayerCollectionItems().
     */
    @Override
    public CompletableFuture<List<ItemStack>> getPlayerCollectionItems(UUID playerId, String collectionId) {
        return CompletableFuture.supplyAsync(() -> getPlayerCollectionItemsSync(playerId, collectionId));
    }

    /**
     * Get count of items collected by a player in a collection (sync internal method).
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @return Count of discovered items
     */
    public int getCollectedItemCountSync(UUID playerId, String collectionId) {
        if (playerId == null || collectionId == null) {
            return 0;
        }

        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - cannot retrieve item count");
            return 0;
        }

        // Get collection database ID
        int collectionDbId = getCollectionDatabaseId(collectionId);
        if (collectionDbId <= 0) {
            logger.warning("Collection not found in database: " + collectionId);
            return 0;
        }

        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        return repository.getCollectedItemCount(playerId.toString(), collectionDbId).join();
    }

    /**
     * Get count of items collected by a player in a collection (async interface method).
     * Implements ICollectionService.getCollectedItemCount().
     */
    @Override
    public CompletableFuture<Integer> getCollectedItemCount(UUID playerId, String collectionId) {
        return CompletableFuture.supplyAsync(() -> getCollectedItemCountSync(playerId, collectionId));
    }

    /**
     * Get items NOT yet discovered by a player in a collection (sync internal method).
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @return List of missing items as ItemStack
     */
    public List<ItemStack> getMissingItemsSync(UUID playerId, String collectionId) {
        if (playerId == null || collectionId == null) {
            return new ArrayList<>();
        }

        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - cannot retrieve missing items");
            return new ArrayList<>();
        }

        // Get collection database ID
        int collectionDbId = getCollectionDatabaseId(collectionId);
        if (collectionDbId <= 0) {
            logger.warning("Collection not found in database: " + collectionId);
            return new ArrayList<>();
        }

        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        List<ItemProperties> items = repository.getMissingItems(playerId.toString(), collectionDbId).join();

        List<ItemStack> result = new ArrayList<>();
        for (ItemProperties props : items) {
            ItemStack stack = new ItemStack(Material.PAPER);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(props.getDisplayName());
                stack.setItemMeta(meta);
            }
            result.add(stack);
        }

        return result;
    }

    /**
     * Get items NOT yet discovered by a player in a collection (async interface method).
     * Implements ICollectionService.getMissingItems().
     */
    @Override
    public CompletableFuture<List<ItemStack>> getMissingItems(UUID playerId, String collectionId) {
        return CompletableFuture.supplyAsync(() -> getMissingItemsSync(playerId, collectionId));
    }

    /**
     * Calculate progress based on individual items collected vs total (sync internal method).
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @return Progress value between 0.0 and 1.0
     */
    public double calculateItemBasedProgressSync(UUID playerId, String collectionId) {
        if (playerId == null || collectionId == null) {
            return 0.0;
        }

        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - cannot calculate item-based progress");
            return 0.0;
        }

        // Get collection database ID
        int collectionDbId = getCollectionDatabaseId(collectionId);
        if (collectionDbId <= 0) {
            logger.warning("Collection not found in database: " + collectionId);
            return 0.0;
        }

        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        return repository.calculateItemBasedProgress(playerId.toString(), collectionDbId).join();
    }

    /**
     * Calculate progress based on individual items collected vs total (async interface method).
     * Implements ICollectionService.calculateItemBasedProgress().
     */
    @Override
    public CompletableFuture<Double> calculateItemBasedProgress(UUID playerId, String collectionId) {
        return CompletableFuture.supplyAsync(() -> calculateItemBasedProgressSync(playerId, collectionId));
    }
}





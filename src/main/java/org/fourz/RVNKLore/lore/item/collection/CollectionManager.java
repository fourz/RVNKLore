package org.fourz.RVNKLore.lore.item.collection;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.data.CollectionRepository;
import org.fourz.RVNKLore.data.dto.ItemCollectionDTO;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages item collections and thematic groupings within the lore system.
 * Handles organization, tracking, and distribution of items across different collections.
 * This manager coordinates with the CosmeticItem for head collections while
 * also providing a unified interface for managing different types of items.
 */
public class CollectionManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, ItemCollection> collections = new ConcurrentHashMap<>();
    private final Map<String, CollectionTheme> themes = new ConcurrentHashMap<>();
    private final CollectionRepository collectionRepository;

    public CollectionManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CollectionManager");
        
        // Initialize repository with the DatabaseManager
        this.collectionRepository = new CollectionRepository(plugin, plugin.getDatabaseManager());
        
        initializeCollections();
        logger.info("CollectionManager initialized");
    }

    private void initializeCollections() {
        createDefaultThemes();
        createCollection("starter_items", "Starter Collection", "Basic items for new players");
        createCollection("rare_finds", "Rare Discoveries", "Uncommon items found throughout the world");
        createCollection("legendary_artifacts", "Legendary Artifacts", "Powerful items of great significance");
        logger.info("Default collections initialized");
    }

    private void createDefaultThemes() {
        for (CollectionTheme theme : CollectionTheme.values()) {
            themes.put(theme.name().toLowerCase(), theme);
            logger.debug("Registered collection theme: " + theme.getDisplayName());
        }
    }

    /**
     * Creates a new collection with validation
     * 
     * @param id Collection identifier
     * @param name Display name
     * @param description Collection description
     * @return The created collection, or null if validation failed
     */
    public ItemCollection createCollection(String id, String name, String description) {
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
    
    public ItemCollection getCollection(String id) {
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

    public Map<String, ItemCollection> getAllCollections() {
        return new HashMap<>(collections);
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
                ItemCollection collection = getCollection(collectionId);
                if (collection != null) {
                    lore.add("§7Collection: §a" + collection.getName());
                }
            }
            if (properties.getRarityLevel() != null) {
                lore.add("§7Rarity: §e" + properties.getRarityLevel());
            }
            meta.setLore(lore);            
            meta.setCustomModelData(properties.getCustomModelData());            
            item.setItemMeta(meta);
        }
        logger.debug("Created collection item: " + properties.getDisplayName());
        return item;
    }

    public boolean addItemToCollection(String collectionId, ItemStack item) {
        ItemCollection collection = getCollection(collectionId);
        if (collection == null) {
            logger.warning("Cannot add item to non-existent collection: " + collectionId);
            return false;
        }
        collection.addItem(item);
        logger.debug("Added item to collection: " + collectionId);
        return true;
    }

    public boolean removeItemFromCollection(String collectionId, ItemStack item) {
        ItemCollection collection = getCollection(collectionId);
        if (collection == null) {
            return false;
        }
        boolean removed = collection.removeItem(item);
        if (removed) {
            logger.debug("Removed item from collection: " + collectionId);
        }
        return removed;
    }

    public List<ItemStack> getCollectionItems(String collectionId) {
        ItemCollection collection = getCollection(collectionId);
        return collection != null ? collection.getItems() : new ArrayList<>();
    }

    public Integer getItemCount(String collectionId) {
        ItemCollection collection = getCollection(collectionId);
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
     * Initialize collections from database on startup (async)
     */
    private void loadCollectionsFromDatabase() {
        logger.info("Loading collections from database asynchronously...");
        collectionRepository.getAllCollections()
            .thenAccept(dtos -> {
                for (ItemCollectionDTO dto : dtos) {
                    ItemCollection collection = dto.toCollection();
                    collections.put(collection.getId(), collection);
                    logger.info("Loaded collection from database: " + collection.getName());
                }
                logger.info("Loaded " + dtos.size() + " collections from database");
            })
            .exceptionally(e -> {
                logger.error("Error loading collections from database", e);
                return null;
            });
    }

    /**
     * Save a collection to the database with enhanced error handling (async)
     *
     * @param collection The collection to persist
     * @return CompletableFuture<Boolean> that resolves to true if successfully saved
     */
    public CompletableFuture<Boolean> saveCollection(ItemCollection collection) {
        if (!validateCollection(collection)) {
            logger.warning("Cannot save invalid collection");
            return CompletableFuture.completedFuture(false);
        }
        
        return collectionRepository.saveCollection(collection)
            .thenApply(saved -> {
                if (saved) {
                    logger.info("Successfully saved collection: " + collection.getId());
                    // Update the in-memory collection
                    collections.put(collection.getId(), collection);
                } else {
                    logger.warning("Failed to save collection: " + collection.getId());
                }
                return saved;
            })
            .exceptionally(e -> {
                logger.error("Error saving collection: " + collection.getId(), e);
                return false;
            });
    }

    /**
     * Get a player's progress for a specific collection (async)
     * 
     * @param playerId The player's UUID
     * @param collectionId The collection identifier
     * @return CompletableFuture<Double> with progress value between 0.0 and 1.0
     */
    public CompletableFuture<Double> getPlayerProgress(UUID playerId, String collectionId) {
        if (playerId == null || collectionId == null) {
            return CompletableFuture.completedFuture(0.0);
        }
        
        return collectionRepository.getPlayerCollectionProgress(playerId.toString(), collectionId)
            .exceptionally(e -> {
                logger.error("Error getting player progress", e);
                return 0.0;
            });
    }

    /**
     * Update a player's progress for a collection (async)
     * 
     * @param playerId The player's UUID
     * @param collectionId The collection identifier
     * @param progress Progress value between 0.0 and 1.0
     * @return CompletableFuture<Boolean> that resolves to true if successfully updated
     */
    public CompletableFuture<Boolean> updatePlayerProgress(UUID playerId, String collectionId, double progress) {
        if (playerId == null || collectionId == null || progress < 0.0 || progress > 1.0) {
            logger.warning("Invalid parameters for progress update");
            return CompletableFuture.completedFuture(false);
        }
        
        return collectionRepository.updatePlayerCollectionProgress(playerId.toString(), collectionId, progress)
            .thenCompose(updated -> {
                if (updated) {
                    logger.info("Updated progress for player " + playerId + " in collection " + collectionId + ": " + String.format("%.1f%%", progress * 100));
                    
                    // Check for completion and trigger rewards
                    if (progress >= 1.0) {
                        return handleCollectionCompletionAsync(playerId, collectionId);
                    }
                }
                return CompletableFuture.completedFuture(updated);
            })
            .exceptionally(e -> {
                logger.error("Error updating player progress", e);
                return false;
            });
    }
    
    /**
     * Handle collection completion events and rewards (async)
     * 
     * @param playerId The player who completed the collection
     * @param collectionId The completed collection
     * @return CompletableFuture<Boolean> that resolves to true if handling was successful
     */
    private CompletableFuture<Boolean> handleCollectionCompletionAsync(UUID playerId, String collectionId) {
        ItemCollection collection = getCollection(collectionId);
        if (collection == null) {
            logger.warning("Cannot handle completion for unknown collection: " + collectionId);
            return CompletableFuture.completedFuture(false);
        }

        logger.info("Player " + playerId + " completed collection: " + collection.getName());

        // Emit a collection completion event for external systems (to be integrated)
        // TODO: Fire CollectionChangeEvent with ChangeType.COMPLETED for event-driven handling

        // Mark completion timestamp in the database
        return collectionRepository.markCollectionCompleted(playerId.toString(), collectionId, System.currentTimeMillis())
            .exceptionally(e -> {
                logger.error("Error marking collection as completed", e);
                return false;
            });
    }

    /**
     * Get all collections, optionally filtered by theme.
     * 
     * @param themeId The theme ID to filter by, or null for all
     * @return Map of collection IDs to ItemCollection
     */
    public Map<String, ItemCollection> getCollectionsByTheme(String themeId) {
        if (themeId == null) {
            return getAllCollections();
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
     * Reload all collections from the database (async).
     * This will replace the in-memory map with the latest from storage.
     * 
     * @return CompletableFuture<Integer> that resolves to the number of loaded collections
     */
    public CompletableFuture<Integer> reloadCollectionsFromDatabase() {
        logger.info("Reloading collections from database asynchronously...");
        collections.clear();
        
        return collectionRepository.getAllCollections()
            .thenApply(loadedCollections -> {
                for (ItemCollectionDTO dto : loadedCollections) {
                    ItemCollection collection = dto.toCollection();
                    collections.put(collection.getId(), collection);
                }
                logger.info("Reloaded " + loadedCollections.size() + " collections from database");
                return loadedCollections.size();
            })
            .exceptionally(e -> {
                logger.error("Error reloading collections from database", e);
                return 0;
            });
    }

    /**
     * Grant collection rewards to a player (async)
     *
     * @param playerId The player's UUID
     * @param collectionId The collection identifier
     * @return CompletableFuture<Boolean> that resolves to true if rewards were successfully granted
     */
    public CompletableFuture<Boolean> grantCollectionReward(UUID playerId, String collectionId) {
        return getPlayerProgress(playerId, collectionId).thenApply(progress -> {
            if (progress < 1.0) {
                logger.warning("Cannot grant rewards - collection not completed by player " + playerId);
                return false;
            }
            ItemCollection collection = getCollection(collectionId);
            if (collection == null) {
                logger.warning("Cannot grant rewards for unknown collection: " + collectionId);
                return false;
            }
            // TODO: Integrate with the reward system to actually distribute rewards.
            // This may involve firing an event or directly awarding items.
            logger.info("Granted collection rewards to player " + playerId + " for collection: " + collectionId);
            return true;
        });
    }
}

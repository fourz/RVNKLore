package org.fourz.RVNKLore.lore.item.collection;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.cosmetic.HeadCollection;
import org.fourz.RVNKLore.lore.item.cosmetic.CollectionTheme;
import org.fourz.RVNKLore.data.ItemRepository;
import java.util.*;
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

    public CollectionManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CollectionManager");
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
            logger.info("Registered collection theme: " + theme.getDisplayName());
        }
    }

    public ItemCollection createCollection(String id, String name, String description) {
        ItemCollection collection = new ItemCollection(id, name, description);
        collections.put(id, collection);
        logger.info("Created collection: " + name + " (" + id + ")");
        return collection;
    }

    public ItemCollection getCollection(String id) {
        return collections.get(id);
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
            if (properties.getCustomModelData() != null) {
                meta.setCustomModelData(properties.getCustomModelData());
            }
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
     * Initialize collections from database on startup
     */
    private void loadCollectionsFromDatabase() {
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - using default collections only");
            return;
        }
        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        List<ItemCollection> loadedCollections = repository.loadAllCollections();
        for (ItemCollection collection : loadedCollections) {
            collections.put(collection.getId(), collection);
            logger.info("Loaded collection from database: " + collection.getName());
        }
        logger.info("Loaded " + loadedCollections.size() + " collections from database");
    }

    /**
     * Save a collection to the database
     *
     * @param collection The collection to persist
     * @return True if successfully saved
     */
    public boolean saveCollection(ItemCollection collection) {
        if (collection == null) {
            logger.warning("Cannot save null collection");
            return false;
        }
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - collection will not be persisted");
            return false;
        }
        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        boolean saved = repository.saveCollection(collection);
        if (saved) {
            logger.info("Successfully saved collection: " + collection.getId());
        } else {
            logger.warning("Failed to save collection: " + collection.getId());
        }
        return saved;
    }

    /**
     * Get a player's progress for a specific collection
     * 
     * @param playerId The player's UUID
     * @param collectionId The collection identifier
     * @return Progress value between 0.0 and 1.0
     */
    public double getPlayerProgress(UUID playerId, String collectionId) {
        if (playerId == null || collectionId == null) {
            return 0.0;
        }
        
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - cannot retrieve player progress");
            return 0.0;
        }
        
        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        return repository.getPlayerCollectionProgress(playerId.toString(), collectionId);
    }

    /**
     * Update a player's progress for a collection
     * 
     * @param playerId The player's UUID
     * @param collectionId The collection identifier
     * @param progress Progress value between 0.0 and 1.0
     * @return True if successfully updated
     */
    public boolean updatePlayerProgress(UUID playerId, String collectionId, double progress) {
        if (playerId == null || collectionId == null || progress < 0.0 || progress > 1.0) {
            logger.warning("Invalid parameters for progress update");
            return false;
        }
        
        if (plugin.getDatabaseManager() == null || !plugin.getDatabaseManager().isConnected()) {
            logger.warning("Database not available - cannot update player progress");
            return false;
        }
        
        ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
        boolean updated = repository.updatePlayerCollectionProgress(playerId.toString(), collectionId, progress);
        
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
     * Handle collection completion events and rewards
     * 
     * @param playerId The player who completed the collection
     * @param collectionId The completed collection
     */
    private void handleCollectionCompletion(UUID playerId, String collectionId) {
        ItemCollection collection = getCollection(collectionId);
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
     * Grant collection rewards to a player
     * 
     * @param playerId The player's UUID
     * @param collectionId The collection identifier
     * @return True if rewards were successfully granted
     */
    public boolean grantCollectionReward(UUID playerId, String collectionId) {
        if (getPlayerProgress(playerId, collectionId) < 1.0) {
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
    }
}

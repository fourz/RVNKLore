package org.fourz.RVNKLore.lore.item.collection;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.cosmetic.HeadCollection;
import org.fourz.RVNKLore.lore.item.cosmetic.CollectionTheme;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages item collections and thematic groupings within the lore system.
 * Handles organization, tracking, and distribution of items across different collections.
 * 
 * This manager coordinates with the CosmeticManager for head collections while
 * providing a broader framework for other item types and collection categories.
 */
public class CollectionManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    
    // Collection storage
    private final Map<String, ItemCollection> collections = new ConcurrentHashMap<>();
    private final Map<String, CollectionTheme> themes = new ConcurrentHashMap<>();
    
    public CollectionManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CollectionManager");
        
        initializeCollections();
        logger.info("CollectionManager initialized");
    }
    
    /**
     * Initialize default collections and themes.
     */
    private void initializeCollections() {
        // Create default themes
        createDefaultThemes();
        
        // Initialize basic collections
        createCollection("starter_items", "Starter Collection", "Basic items for new players");
        createCollection("rare_finds", "Rare Discoveries", "Uncommon items found throughout the world");
        createCollection("legendary_artifacts", "Legendary Artifacts", "Powerful items of great significance");
        
        logger.info("Default collections initialized");
    }
    
    /**
     * Create default collection themes.
     */
    private void createDefaultThemes() {
        // Register enum-based themes
        for (CollectionTheme theme : CollectionTheme.values()) {
            themes.put(theme.name().toLowerCase(), theme);
            logger.info("Registered collection theme: " + theme.getDisplayName());
        }
    }
    
    /**
     * Create a new item collection.
     * 
     * @param id The collection identifier
     * @param name The display name
     * @param description The collection description
     * @return The created ItemCollection
     */
    public ItemCollection createCollection(String id, String name, String description) {
        ItemCollection collection = new ItemCollection(id, name, description);
        collections.put(id, collection);
        logger.info("Created collection: " + name + " (" + id + ")");
        return collection;
    }
    
    /**
     * Get a collection by its identifier.
     * 
     * @param id The collection identifier
     * @return The ItemCollection, or null if not found
     */
    public ItemCollection getCollection(String id) {
        return collections.get(id);
    }
    
    /**
     * Get all registered collections.
     * 
     * @return Map of collection IDs to ItemCollection objects
     */
    public Map<String, ItemCollection> getAllCollections() {
        return new HashMap<>(collections);
    }
    
    /**
     * Create a collection item using ItemProperties configuration.
     * 
     * @param properties The item properties including collection metadata
     * @return The collection ItemStack
     */
    public ItemStack createCollectionItem(ItemProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("ItemProperties cannot be null");
        }
        
        Material material = properties.getMaterial();
        if (material == null) {
            material = Material.PAPER; // Default for collection items
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // Set display name
            if (properties.getDisplayName() != null) {
                meta.setDisplayName(properties.getDisplayName());
            }
            
            // Set lore
            List<String> lore = properties.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            
            // Add collection-specific lore
            String collectionId = properties.getCollectionId();
            if (collectionId != null) {
                ItemCollection collection = getCollection(collectionId);
                if (collection != null) {
                    lore.add("§7Collection: §a" + collection.getName());
                }
            }
            
            // Add rarity information
            if (properties.getRarityLevel() != null) {
                lore.add("§7Rarity: §e" + properties.getRarityLevel());
            }
            
            meta.setLore(lore);
            
            // Set custom model data
            if (properties.getCustomModelData() != null) {
                meta.setCustomModelData(properties.getCustomModelData());
            }
            
            item.setItemMeta(meta);
        }
        
        logger.debug("Created collection item: " + properties.getDisplayName());
        return item;
    }
    
    /**
     * Add an item to a collection.
     * 
     * @param collectionId The collection identifier
     * @param item The item to add
     * @return True if successfully added
     */
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
    
    /**
     * Remove an item from a collection.
     * 
     * @param collectionId The collection identifier
     * @param item The item to remove
     * @return True if successfully removed
     */
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
    
    /**
     * Get all items in a collection.
     * 
     * @param collectionId The collection identifier
     * @return List of items in the collection
     */
    public List<ItemStack> getCollectionItems(String collectionId) {
        ItemCollection collection = getCollection(collectionId);
        return collection != null ? collection.getItems() : new ArrayList<>();
    }
    
    /**
     * Register a collection theme (enum only).
     *
     * @param theme The collection theme to register
     */
    public void registerTheme(CollectionTheme theme) {
        if (theme == null) return;
        themes.put(theme.name().toLowerCase(), theme);
        logger.info("Registered collection theme: " + theme.getDisplayName());
    }
    
    /**
     * Get a theme by its identifier.
     * 
     * @param id The theme identifier
     * @return The CollectionTheme, or null if not found
     */
    public CollectionTheme getTheme(String id) {
        return themes.get(id);
    }
    
    /**
     * Get all registered themes.
     * 
     * @return Map of theme IDs to CollectionTheme objects
     */
    public Map<String, CollectionTheme> getAllThemes() {
        return new HashMap<>(themes);
    }
    
    /**
     * Shutdown the collection manager and clean up resources.
     */
    public void shutdown() {
        collections.clear();
        themes.clear();
        logger.info("CollectionManager shutdown");
    }
}

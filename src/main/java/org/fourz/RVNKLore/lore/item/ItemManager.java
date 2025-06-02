package org.fourz.RVNKLore.lore.item;

import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseConnection;
import org.fourz.RVNKLore.data.ItemRepository;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.enchant.EnchantManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.cosmetic.CosmeticItem;
import org.fourz.RVNKLore.lore.item.model.ModelDataManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base manager class for all item-related functionality in the lore system.
 * Acts as a central orchestrator for enchantments, cosmetics, collections, and model data.
 */
public class ItemManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    
    // Sub-managers for different item domains
    private EnchantManager enchantManager;
    private CosmeticItem cosmeticItem;
    private CollectionManager collectionManager;
    private ModelDataManager modelDataManager;
    private ItemRepository itemRepository;
    
    // Caches for better performance
    private final Map<String, ItemProperties> itemCache = new ConcurrentHashMap<>();
    private final Map<Integer, List<ItemProperties>> collectionCache = new ConcurrentHashMap<>();
    private boolean cacheInitialized = false;
    
    public ItemManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ItemManager");
        logger.info("Initializing ItemManager...");

        // Initialize database repository
        if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
            DatabaseConnection dbConnection = plugin.getDatabaseManager().getDatabaseConnection();
            if (dbConnection != null) {
                this.itemRepository = new ItemRepository(plugin, dbConnection);
                logger.info("ItemRepository initialized");
            } else {
                logger.warning("Database connection is null, ItemRepository will not be available");
            }
        } else {
            logger.warning("Database not available - some item features may be limited");
        }
        
        // Initialize cosmetic manager
        this.cosmeticItem = new CosmeticItem(plugin);
        logger.info("CosmeticItem initialized");
        
        // Initialize enchant manager
        this.enchantManager = new EnchantManager(plugin);
        logger.info("EnchantManager initialized");
        
        // Initialize model data manager
        this.modelDataManager = new ModelDataManager(plugin);
        logger.info("ModelDataManager initialized");
        
        // Initialize collection manager after other managers
        this.collectionManager = new CollectionManager(plugin);
        logger.info("CollectionManager initialized");
        
        // Initial cache load in async task to avoid blocking startup
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::initializeCache);
    }
    
    /**
     * Get the enchantment manager for enchanted item generation and management.
     * 
     * @return The EnchantManager instance
     */
    public EnchantManager getEnchantManager() {
        return enchantManager;
    }
    
    /**
     * Get the cosmetic manager for head collections and variants.
     * 
     * @return The CosmeticManager instance
     */
    public CosmeticItem getCosmeticItem() {
        return cosmeticItem;
    }
    
    /**
     * Get the collection manager for item collections.
     * 
     * @return The CollectionManager instance
     */
    public CollectionManager getCollectionManager() {
        return collectionManager;
    }
    
    /**
     * Get the model data manager for custom model data allocation and tracking.
     * 
     * @return The ModelDataManager instance
     */
    public ModelDataManager getModelDataManager() {
        return modelDataManager;
    }
    
    /**
     * Create a generic lore item with basic properties.
     */
    public ItemStack createLoreItem(ItemType type, String name, ItemProperties properties) {
        switch (type) {
            case ENCHANTED:
                return enchantManager.createEnchantedItem(properties);
            case COSMETIC:
                return cosmeticItem.createCosmeticItem(properties);
            case COLLECTION:
                return collectionManager.createCollectionItem(properties);
            case MODEL_DATA:
                if (modelDataManager != null) {
                    ItemStack item = new ItemStack(properties.getMaterial());
                    // Map ItemType to ModelDataCategory if possible, else use ModelDataCategory.COSMETIC as default
                    org.fourz.RVNKLore.lore.item.model.ModelDataCategory category = org.fourz.RVNKLore.lore.item.model.ModelDataCategory.COSMETIC;
                    try {
                        category = org.fourz.RVNKLore.lore.item.model.ModelDataCategory.valueOf(type.name());
                    } catch (IllegalArgumentException ignored) {}
                    return modelDataManager.applyModelData(item, name, category);
                }
                // Fallback to standard item creation
                ItemStack fallback = new ItemStack(properties.getMaterial());
                org.bukkit.inventory.meta.ItemMeta fallbackMeta = fallback.getItemMeta();
                if (fallbackMeta != null) {
                    if (properties.getDisplayName() != null) {
                        fallbackMeta.setDisplayName(properties.getDisplayName());
                    }
                    if (properties.getLore() != null && !properties.getLore().isEmpty()) {
                        fallbackMeta.setLore(properties.getLore());
                    }
                    if (properties.getCustomModelData() != null) {
                        fallbackMeta.setCustomModelData(properties.getCustomModelData());
                    }
                    fallback.setItemMeta(fallbackMeta);
                }
                return fallback;
            default:
                // For standard items, create a basic item with the properties
                ItemStack item = new ItemStack(properties.getMaterial());
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    if (properties.getDisplayName() != null) {
                        meta.setDisplayName(properties.getDisplayName());
                    }
                    if (properties.getLore() != null && !properties.getLore().isEmpty()) {
                        meta.setLore(properties.getLore());
                    }
                    if (properties.getCustomModelData() != null) {
                        meta.setCustomModelData(properties.getCustomModelData());
                    }
                    item.setItemMeta(meta);
                }
                return item;
        }
    }

    /**
     * Returns a list of all registered item names for tab completion and lookup.
     */
    public List<String> getAllItemNames() {
        List<String> names = new ArrayList<>();
        
        // Add items from database if available
        if (itemRepository != null && cacheInitialized) {
            // Use the cached items
            names.addAll(itemCache.keySet());
        } else {
            // Fallback to memory-based items
            if (cosmeticItem != null) {
                for (var collection : cosmeticItem.getAllCollections()) {
                    collection.getAllHeads().forEach(head -> names.add(head.getId()));
                }
            }
            if (collectionManager != null) {
                names.addAll(collectionManager.getAllCollections().keySet());
            }
        }
        
        return names;
    }

    /**
     * Create a lore item by name.
     */
    public ItemStack createLoreItem(String itemName) {
        // First try to get from database cache
        if (itemRepository != null && cacheInitialized && itemCache.containsKey(itemName)) {
            ItemProperties props = itemCache.get(itemName);
            return createLoreItem(props.getItemType(), itemName, props);
        }
        
        // If not in cache, try to load directly from database
        if (itemRepository != null) {
            ItemProperties props = itemRepository.getItemByName(itemName);
            if (props != null) {
                // Add to cache for future lookups
                itemCache.put(itemName, props);
                return createLoreItem(props.getItemType(), itemName, props);
            }
        }
        
        // Fallback to memory-based items
        if (cosmeticItem != null) {
            var variant = cosmeticItem.getHeadVariant(itemName);
            if (variant != null) {
                return cosmeticItem.createHeadItem(variant);
            }
        }
        
        if (collectionManager != null) {
            var collection = collectionManager.getCollection(itemName);
            if (collection != null) {
                var props = new ItemProperties(org.bukkit.Material.PAPER, collection.getName());
                props.setCollectionId(collection.getId());
                return collectionManager.createCollectionItem(props);
            }
        }
        
        return null;
    }
    
    /**
     * Initialize or refresh the item cache from database
     */
    private void initializeCache() {
        if (itemRepository == null) {
            logger.warning("Cannot initialize cache: ItemRepository is null");
            return;
        }
        
        try {
            logger.info("Initializing item cache from database...");
            
            // Clear existing caches
            itemCache.clear();
            collectionCache.clear();
            
            // Load all items
            List<ItemProperties> allItems = itemRepository.getAllItems();
            for (ItemProperties item : allItems) {
                itemCache.put(item.getDisplayName(), item);
            }
            
            // Load all collections
            Map<Integer, String> collections = itemRepository.getAllCollections();
            for (Integer collectionId : collections.keySet()) {
                collectionCache.put(collectionId, itemRepository.getItemsByCollection(collectionId));
            }
            
            cacheInitialized = true;
            logger.info("Item cache initialized with " + itemCache.size() + " items and " + 
                        collectionCache.size() + " collections");
        } catch (Exception e) {
            logger.error("Error initializing item cache", e);
        }
    }
    
    /**
     * Refresh the item cache, reloading all data from the database.
     */
    public void refreshCache() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::initializeCache);
    }
    
    /**
     * Shutdown all sub-managers and clean up resources.
     */
    public void shutdown() {
        logger.info("Shutting down ItemManager...");
        
        if (modelDataManager != null) {
            modelDataManager.shutdown();
        }
        
        if (collectionManager != null) {
            collectionManager.shutdown();
        }
        
        if (cosmeticItem != null) {
            cosmeticItem.shutdown();
        }
        
        if (enchantManager != null) {
            enchantManager.shutdown();
        }
        
        logger.info("ItemManager shutdown complete");
    }
    
    /**
     * Clean up resources and shutdown (alias for shutdown).
     */
    public void cleanup() {
        shutdown();
    }

    /**
     * Get the plugin instance.
     * 
     * @return The RVNKLore plugin instance
     */
    protected RVNKLore getPlugin() {
        return plugin;
    }
    
    /**
     * Get the logger instance.
     * 
     * @return The LogManager instance
     */
    protected LogManager getLogger() {
        return logger;
    }
}

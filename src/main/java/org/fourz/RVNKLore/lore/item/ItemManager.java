package org.fourz.RVNKLore.lore.item;

import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.enchant.EnchantManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.cosmetic.CosmeticItem;
import org.fourz.RVNKLore.lore.item.model.ModelDataManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Base manager class for all item-related functionality in the lore system.
 * Acts as a central orchestrator for enchantments, cosmetics, collections, and model data.
 * 
 * This class follows the manager pattern, providing a unified interface for accessing
 * specialized item management subsystems while maintaining clear separation of concerns.
 */
public class ItemManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    
    // Sub-managers for different item domains
    private EnchantManager enchantManager;
    private CosmeticItem cosmeticItem;
    private CollectionManager collectionManager;
    private ModelDataManager modelDataManager;
    
    public ItemManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ItemManager");
        
        logger.info("Initializing ItemManager...");
        // Remove direct initialization of collectionManager here to avoid double init
        // this.collectionManager = new CollectionManager(plugin);
        // logger.info("CollectionManager initialized");
        
        // Initialize cosmetic manager (move from plugin to here)
        this.cosmeticItem = new CosmeticItem(plugin);
        logger.info("CosmeticItem initialized");
        
        // Initialize enchant manager
        this.enchantManager = new EnchantManager(plugin);
        logger.info("EnchantManager initialized");
        
        // Initialize model data manager
        this.modelDataManager = new ModelDataManager(plugin);
        logger.info("ModelDataManager initialized");
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
     * This method provides a unified interface for item creation across all domains.
     * 
     * @param type The type of item to create
     * @param name The display name for the item
     * @param properties Additional properties for the item
     * @return The created ItemStack
     */
    public ItemStack createLoreItem(ItemType type, String name, ItemProperties properties) {
        switch (type) {
            case ENCHANTED:
                return enchantManager.createEnchantedItem(properties);
            case COSMETIC:
                return cosmeticItem.createCosmeticItem(properties);
            case COLLECTION:
                return collectionManager.createCollectionItem(properties);
            default:
                throw new IllegalArgumentException("Unsupported item type: " + type);
        }
    }

    /**
     * Returns a list of all registered item names for tab completion and lookup.
     * Includes cosmetic, collection, and enchanted items.
     *
     * @return List of all item names
     */
    public List<String> getAllItemNames() {
        List<String> names = new ArrayList<>();
        // Cosmetic items
        if (cosmeticItem != null) {
            for (var collection : cosmeticItem.getAllCollections()) {
                collection.getAllHeads().forEach(head -> names.add(head.getId()));
            }
        }
        // Collection items
        if (collectionManager != null) {
            names.addAll(collectionManager.getAllCollections().keySet());
        }
        // TODO: Add enchanted and other item types as needed
        return names;
    }

    /**
     * Create a lore item by name, searching all registered item types.
     * Returns null if not found.
     *
     * @param itemName The unique item name or ID
     * @return An ItemStack for the item, or null if not found
     */
    public ItemStack createLoreItem(String itemName) {
        // Cosmetic heads
        if (cosmeticItem != null) {
            var variant = cosmeticItem.getHeadVariant(itemName);
            if (variant != null) {
                return cosmeticItem.createHeadItem(variant);
            }
        }
        // Collection items
        if (collectionManager != null) {
            var collection = collectionManager.getCollection(itemName);
            if (collection != null) {
                // Create a representative item for the collection
                var props = new ItemProperties(org.bukkit.Material.PAPER, collection.getName());
                props.setCollectionId(collection.getId());
                return collectionManager.createCollectionItem(props);
            }
        }
        // TODO: Add enchanted and other item types as needed
        return null;
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

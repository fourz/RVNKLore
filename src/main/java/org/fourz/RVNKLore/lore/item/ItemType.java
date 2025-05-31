package org.fourz.RVNKLore.lore.item;

/**
 * Enumeration of item types managed by the ItemManager.
 * Used to route item creation requests to appropriate sub-managers.
 */
public enum ItemType {
    /**
     * Enchanted items with special properties and enchantments.
     */
    ENCHANTED,
    
    /**
     * Cosmetic items including player heads, mob heads, and custom textures.
     */
    COSMETIC,
    
    /**
     * Collection items that are part of thematic groups.
     */
    COLLECTION,
    
    /**
     * Items with custom model data for resource pack integration.
     */
    MODEL_DATA,
    
    /**
     * Standard lore items without special properties.
     */
    STANDARD
}

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
    STANDARD,

    /**
     * Legendary items with unique properties and high rarity.
     */
    LEGENDARY,

    /**
     * Artifact items with historical significance or special lore.
     */
    ARTIFACT,

    /**
     * Seasonal items available during specific events or time periods.
     */
    SEASONAL,

    /**
     * Uncommon items with slightly elevated rarity.
     */
    UNCOMMON,

    /**
     * Unique one-of-a-kind items.
     */
    UNIQUE,

    /**
     * Rare items with low drop rates or limited availability.
     */
    RARE,

    /**
     * Event-specific items earned during server events.
     */
    EVENT,

    /**
     * Items awarded as quest completion rewards.
     */
    QUEST_REWARD
}

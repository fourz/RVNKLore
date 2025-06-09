package org.fourz.RVNKLore.lore.item.custommodeldata;

/**
 * Enumeration of model data categories for organized ID allocation.
 * Each category has a dedicated range to prevent conflicts.
 */
public enum CustomModelDataCategory {
    /**
     * Reserved for system use - IDs 1-100
     */
    SYSTEM,
    
    /**
     * Weapons (swords, bows, etc.) - IDs 101-200
     */
    WEAPONS,
    
    /**
     * Armor pieces - IDs 201-300
     */
    ARMOR,
    
    /**
     * Tools (pickaxes, shovels, etc.) - IDs 301-400
     */
    TOOLS,
    
    /**
     * Cosmetic items (heads, decorations) - IDs 401-500
     */
    COSMETIC,
    
    /**
     * Decorative blocks - IDs 501-600
     */
    DECORATIVE,
    
    /**
     * Consumable items (food, potions) - IDs 601-700
     */
    CONSUMABLES,
    
    /**
     * Seasonal items - IDs 701-800
     */
    SEASONAL,
    
    /**
     * Event-specific items - IDs 801-900
     */
    EVENT,
    
    /**
     * Special/Legendary items - IDs 901-1000
     */
    LEGENDARY
}

package org.fourz.RVNKLore.lore;

/**
 * Enum of all supported lore entry types
 */
public enum LoreType {
    /**
     * Generic/unspecified lore type
     */
    GENERIC("Generic/unspecified lore type"),
    
    /**
     * Player character lore
     */
    PLAYER("Player related events and milestones"),
    
    /**
     * City/settlement lore
     */
    CITY("City/settlement lore"),
    
    /**
     * Notable landmark
     */
    LANDMARK("Notable landmark"),
    
    /**
     * Faction or group
     */
    FACTION("Faction or group"),
    
    /**
     * Path or road
     */
    PATH("Path or road"),
    
    /**
     * Crafted or special item
     */
    ITEM("Crafted or special item"),
    
    /**
     * Event that occurred
     */
    EVENT("Event that occurred"),
    
    /**
     * Quest or mission
     */
    QUEST("Quest or mission"),
    
    /**
     * Enchantment on an item
     */
    ENCHANTMENT("Enchantment on an item"),
    
    /**
     * Decorative head item (includes player heads, mob heads, custom heads, and hats)
     */
    HEAD("Decorative head or hat item");

    private final String description;

    LoreType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }
}

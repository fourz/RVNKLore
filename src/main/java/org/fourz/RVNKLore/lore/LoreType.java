package org.fourz.RVNKLore.lore;

/**
 * Enum of all supported lore entry types
 */
public enum LoreType {
    /**
     * Generic/unspecified lore type
     */
    GENERIC("Generic/unspecified lore type", LoreCategory.NARRATIVE, false),

    /**
     * Player character lore
     */
    PLAYER("Player related events and milestones", LoreCategory.CHARACTER, false),

    /**
     * City/settlement lore
     */
    CITY("City/settlement lore", LoreCategory.LOCATION, true),

    /**
     * Notable landmark
     */
    LANDMARK("Notable landmark", LoreCategory.LOCATION, true),

    /**
     * Faction or group
     */
    FACTION("Faction or group", LoreCategory.LOCATION, true),

    /**
     * Path or road
     */
    PATH("Path or road", LoreCategory.LOCATION, true),

    /**
     * Crafted or special item
     */
    ITEM("Crafted or special item", LoreCategory.ITEM, false),

    /**
     * Event that occurred
     */
    EVENT("Event that occurred", LoreCategory.LOCATION, true),

    /**
     * Quest or mission
     */
    QUEST("Quest or mission", LoreCategory.NARRATIVE, false),

    /**
     * Enchantment on an item
     */
    ENCHANTMENT("Enchantment on an item", LoreCategory.ITEM, false),

    /**
     * Monument or memorial marker
     */
    MONUMENT("Monument or memorial marker", LoreCategory.LOCATION, true),

    /**
     * Decorative head item (includes player heads, mob heads, custom heads, and hats)
     */
    HEAD("Decorative head or hat item", LoreCategory.ITEM, false),

    /**
     * Tavern, inn, or social gathering place
     */
    TAVERN("Tavern, inn, or social gathering place", LoreCategory.LOCATION, true),

    /**
     * Guild hall or professional organization
     */
    GUILD("Guild hall or professional organization", LoreCategory.LOCATION, true),

    /**
     * Shrine, altar, or place of worship
     */
    SHRINE("Shrine, altar, or place of worship", LoreCategory.LOCATION, true);

    private final String description;
    private final LoreCategory category;
    private final boolean locationCapable;

    LoreType(String description, LoreCategory category, boolean locationCapable) {
        this.description = description;
        this.category = category;
        this.locationCapable = locationCapable;
    }

    public String getDescription() {
        return this.description;
    }

    /**
     * Returns the broad category this lore type belongs to.
     */
    public LoreCategory getCategory() {
        return this.category;
    }

    /**
     * Returns true if this type represents a location-anchored entry
     * (eligible for dynmap markers and requires coordinates).
     */
    public boolean isLocationCapable() {
        return this.locationCapable;
    }
}

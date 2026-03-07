package org.fourz.RVNKLore.achievement;

/**
 * Types of achievements that can be earned.
 */
public enum AchievementType {
    /**
     * Complete a specific collection
     */
    COLLECTION_COMPLETE("Complete Collection", "Complete all items in a specific collection"),

    /**
     * Discover N items of a specific rarity
     */
    RARITY_COUNT("Rarity Milestone", "Discover a number of items of a specific rarity"),

    /**
     * Discover all items in a category (ITEM, LOCATION, CHARACTER)
     */
    CATEGORY_COMPLETE("Category Master", "Discover all items in a lore category"),

    /**
     * First player to complete a collection
     */
    FIRST_COMPLETION("First to Discover", "Be the first player to complete a collection"),

    /**
     * Complete within a timeframe (speed achievement)
     */
    SPEED_COMPLETION("Speed Run", "Complete a collection within a time limit"),

    /**
     * Discover N total lore entries
     */
    DISCOVERY_COUNT("Lore Seeker", "Discover a total number of lore entries"),

    /**
     * Discover entries in multiple categories
     */
    MULTI_CATEGORY("Explorer", "Discover entries across multiple categories"),

    /**
     * Custom achievement defined by admins
     */
    CUSTOM("Custom", "Custom achievement with specific criteria");

    private final String displayName;
    private final String description;

    AchievementType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}

package org.fourz.RVNKLore.lore.item.cosmetic;

/**
 * Enumeration of thematic categories for organizing head collections.
 * Each theme provides semantic grouping for related cosmetic items.
 */
public enum CollectionTheme {
    MEDIEVAL("Medieval", "Items from medieval times and fantasy settings"),
    MODERN("Modern", "Contemporary and urban-themed items"),
    FANTASY("Fantasy", "Magical and mystical themed items"),
    HORROR("Horror", "Spooky and frightening themed items"),
    ANIME("Anime", "Japanese animation inspired items"),
    HISTORICAL("Historical", "Items based on real historical periods"),
    SEASONAL("Seasonal", "Items tied to specific seasons or holidays"),
    MYTHOLOGY("Mythology", "Items inspired by various mythologies"),
    ANIMALS("Animals", "Animal-themed heads and variants"),
    GAMING("Gaming", "Video game character inspired items"),
    CUSTOM("Custom", "Community created and unique items"),
    EVENT("Event", "Special server event exclusive items"),
    LEGENDARY("Legendary", "Items of great power and significance");
    
    private final String displayName;
    private final String description;
    
    CollectionTheme(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * Get the human-readable display name for this theme.
     *
     * @return Display name of the theme
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get the description of what this theme encompasses.
     *
     * @return Description of the theme
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this theme is event-related.
     *
     * @return True if this is an event or seasonal theme
     */
    public boolean isEventRelated() {
        return this == EVENT || this == SEASONAL;
    }
    
    /**
     * Get theme by display name (case-insensitive).
     *
     * @param displayName The display name to search for
     * @return The theme or CUSTOM if not found
     */
    public static CollectionTheme fromDisplayName(String displayName) {
        if (displayName == null) return CUSTOM;
        
        for (CollectionTheme theme : values()) {
            if (theme.displayName.equalsIgnoreCase(displayName)) {
                return theme;
            }
        }
        return CUSTOM;
    }
}

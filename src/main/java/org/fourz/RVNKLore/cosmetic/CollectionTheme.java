package org.fourz.RVNKLore.cosmetic;

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
    PROFESSIONS("Professions", "Job and occupation themed items"),
    GAMING("Gaming", "Video game character inspired items"),
    CELEBRITIES("Celebrities", "Famous person themed items"),
    CUSTOM("Custom", "Community created and unique items"),
    EVENT("Event", "Special server event exclusive items"),
    RARE("Rare", "Extremely limited or difficult to obtain items"),
    COMMON("Common", "Easily accessible starter items");
    
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
     * Get a theme by its display name (case-insensitive).
     *
     * @param displayName The display name to search for
     * @return The matching theme or null if not found
     */
    public static CollectionTheme fromDisplayName(String displayName) {
        for (CollectionTheme theme : values()) {
            if (theme.displayName.equalsIgnoreCase(displayName)) {
                return theme;
            }
        }
        return null;
    }
}

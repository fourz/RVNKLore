package org.fourz.RVNKLore.cosmetic;

import org.bukkit.ChatColor;

/**
 * Enumeration of rarity classifications for head variants.
 * Each rarity has associated display properties and acquisition difficulty.
 */
public enum HeadRarity {
    COMMON(ChatColor.WHITE, "Common", 1.0f, "Easily obtainable through normal gameplay"),
    UNCOMMON(ChatColor.GREEN, "Uncommon", 0.7f, "Moderately rare, requires some effort to obtain"),
    RARE(ChatColor.BLUE, "Rare", 0.4f, "Difficult to obtain, requires significant effort"),
    EPIC(ChatColor.DARK_PURPLE, "Epic", 0.2f, "Very rare, requires special conditions or events"),
    LEGENDARY(ChatColor.GOLD, "Legendary", 0.05f, "Extremely rare, major achievement required"),
    MYTHIC(ChatColor.DARK_RED, "Mythic", 0.01f, "Nearly impossible to obtain, ultimate rarity"),
    EVENT(ChatColor.YELLOW, "Event", 0.0f, "Special event exclusive, time-limited availability"),
    ADMIN(ChatColor.DARK_GRAY, "Admin", 0.0f, "Staff exclusive, not obtainable by players");
    
    private final ChatColor color;
    private final String displayName;
    private final float dropChance;
    private final String description;
    
    HeadRarity(ChatColor color, String displayName, float dropChance, String description) {
        this.color = color;
        this.displayName = displayName;
        this.dropChance = dropChance;
        this.description = description;
    }
    
    /**
     * Get the color associated with this rarity level.
     *
     * @return ChatColor for displaying this rarity
     */
    public ChatColor getColor() {
        return color;
    }
    
    /**
     * Get the human-readable display name for this rarity.
     *
     * @return Display name of the rarity
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get the colored display name for this rarity.
     *
     * @return Color-formatted display name
     */
    public String getColoredDisplayName() {
        return color + displayName;
    }
    
    /**
     * Get the base drop chance for items of this rarity.
     * Note: This is a suggestion value, actual drop rates may vary by implementation.
     *
     * @return Drop chance as a float between 0.0 and 1.0
     */
    public float getDropChance() {
        return dropChance;
    }
    
    /**
     * Get the description of this rarity level.
     *
     * @return Description explaining the rarity
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this rarity requires special permissions to obtain.
     *
     * @return True if special permissions are typically required
     */
    public boolean requiresPermission() {
        return this == LEGENDARY || this == MYTHIC || this == ADMIN;
    }
    
    /**
     * Check if this rarity is obtainable through normal gameplay.
     *
     * @return True if obtainable through normal means
     */
    public boolean isObtainable() {
        return this != ADMIN && dropChance > 0.0f;
    }
    
    /**
     * Get a rarity by its display name (case-insensitive).
     *
     * @param displayName The display name to search for
     * @return The matching rarity or null if not found
     */
    public static HeadRarity fromDisplayName(String displayName) {
        for (HeadRarity rarity : values()) {
            if (rarity.displayName.equalsIgnoreCase(displayName)) {
                return rarity;
            }
        }
        return null;
    }
}

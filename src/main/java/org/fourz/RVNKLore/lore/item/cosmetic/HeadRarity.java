package org.fourz.RVNKLore.lore.item.cosmetic;

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
     * @return Colored display name with appropriate ChatColor
     */
    public String getColoredDisplayName() {
        return color + displayName;
    }
    
    /**
     * Get the drop chance for this rarity level.
     *
     * @return Drop chance as a float between 0.0 and 1.0
     */
    public float getDropChance() {
        return dropChance;
    }
    
    /**
     * Get the description of this rarity level.
     *
     * @return Description explaining acquisition difficulty
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this rarity is obtainable by players.
     *
     * @return True if players can obtain this rarity
     */
    public boolean isObtainable() {
        return this != ADMIN;
    }
    
    /**
     * Check if this rarity is event-exclusive.
     *
     * @return True if this rarity is only available during events
     */
    public boolean isEventExclusive() {
        return this == EVENT;
    }
    
    /**
     * Get rarity by name (case-insensitive).
     *
     * @param name The rarity name
     * @return The rarity or COMMON if not found
     */
    public static HeadRarity fromString(String name) {
        if (name == null) return COMMON;
        
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }
    
    /**
     * Get all obtainable rarities (excluding admin-only).
     *
     * @return Array of obtainable rarities
     */
    public static HeadRarity[] getObtainableRarities() {
        return java.util.Arrays.stream(values())
                .filter(HeadRarity::isObtainable)
                .toArray(HeadRarity[]::new);
    }
}

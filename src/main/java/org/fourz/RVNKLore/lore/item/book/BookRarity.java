package org.fourz.RVNKLore.lore.item.book;

import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;

/**
 * Represents the rarity of a lore book.
 * Different rarities have different visual appearances.
 */
public enum BookRarity {
    /**
     * Common books - standard appearance
     */
    COMMON("Common", ChatColor.WHITE, 0, false),

    /**
     * Uncommon books - slightly enhanced
     */
    UNCOMMON("Uncommon", ChatColor.GREEN, 1, false),

    /**
     * Rare books - noticeable enhancement
     */
    RARE("Rare", ChatColor.BLUE, 2, false),

    /**
     * Epic books - enchantment glint
     */
    EPIC("Epic", ChatColor.DARK_PURPLE, 3, true),

    /**
     * Legendary books - full enchantment glint
     */
    LEGENDARY("Legendary", ChatColor.GOLD, 4, true),

    /**
     * Unique books - one of a kind
     */
    UNIQUE("Unique", ChatColor.LIGHT_PURPLE, 5, true);

    private final String displayName;
    private final ChatColor color;
    private final int customModelDataOffset;
    private final boolean hasEnchantmentGlint;

    BookRarity(String displayName, ChatColor color, int customModelDataOffset, boolean hasEnchantmentGlint) {
        this.displayName = displayName;
        this.color = color;
        this.customModelDataOffset = customModelDataOffset;
        this.hasEnchantmentGlint = hasEnchantmentGlint;
    }

    /**
     * Get the display name for this rarity.
     * @return The formatted display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the chat color for this rarity.
     * @return The ChatColor
     */
    public ChatColor getColor() {
        return color;
    }

    /**
     * Get the custom model data offset for this rarity.
     * Base model data + offset = final custom model data
     * @return The offset value
     */
    public int getCustomModelDataOffset() {
        return customModelDataOffset;
    }

    /**
     * Check if this rarity should have an enchantment glint.
     * @return true if the book should glow
     */
    public boolean hasEnchantmentGlint() {
        return hasEnchantmentGlint;
    }

    /**
     * Get the formatted name with color.
     * @return Colored display name
     */
    public String getColoredName() {
        return color + displayName;
    }

    /**
     * Get a rarity from a string name (case-insensitive).
     * @param name The rarity name
     * @return The BookRarity, or COMMON if not found
     */
    public static BookRarity fromString(String name) {
        if (name == null) return COMMON;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }

    /**
     * Determine rarity based on lore type and attributes.
     * @param isFirstDiscovery Whether this is a first discovery
     * @param hasLocation Whether the lore has a location
     * @param descriptionLength The length of the description
     * @return The appropriate rarity
     */
    public static BookRarity determineRarity(boolean isFirstDiscovery, boolean hasLocation, int descriptionLength) {
        int score = 0;

        if (isFirstDiscovery) score += 3;
        if (hasLocation) score += 1;
        if (descriptionLength > 500) score += 2;
        else if (descriptionLength > 200) score += 1;

        if (score >= 5) return LEGENDARY;
        if (score >= 4) return EPIC;
        if (score >= 3) return RARE;
        if (score >= 2) return UNCOMMON;
        return COMMON;
    }
}

package org.fourz.RVNKLore.lore;

/**
 * Broad category groupings for LoreType values.
 * Used for shared behavior (dynmap eligibility, location requirements, discovery sounds).
 */
public enum LoreCategory {
    /** Location-anchored entries that can appear on dynmap and require coordinates */
    LOCATION,
    /** Player-related entries (player histories, faction records) */
    CHARACTER,
    /** Item, enchantment, and cosmetic entries */
    ITEM,
    /** Narrative/quest entries with no fixed location requirement */
    NARRATIVE
}

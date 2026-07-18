package org.fourz.RVNKLore.lore.item;

/**
 * Enumeration of item types managed by the ItemManager.
 * Used to route item creation requests to appropriate sub-managers.
 *
 * <p><b>Routing contract (#1498):</b> only <b>four</b> values have distinct
 * factory behavior in {@code ItemManager.createLoreItemInternal}:
 * {@link #ENCHANTED}, {@link #COSMETIC}, {@link #COLLECTION}, and
 * {@link #MODEL_DATA} route to dedicated sub-managers. <b>Every other value</b>
 * ({@link #STANDARD}, {@link #LEGENDARY}, {@link #ARTIFACT}, {@link #SEASONAL},
 * {@link #UNCOMMON}, {@link #UNIQUE}, {@link #RARE}, {@link #EVENT},
 * {@link #QUEST_REWARD}) falls through to the shared STANDARD branch
 * (displayName + lore + custom-model-data + PDC stamps; WRITTEN_BOOK pages are
 * a material-based special case within it). These remaining values are therefore
 * semantic <i>labels</i>, not behavioral variants.</p>
 *
 * <p><b>Why the enum is not pruned:</b> the value name is persisted verbatim in
 * the {@code lore_item.item_type} column. Removing a value would orphan existing
 * rows (and break {@code getItemsByType}). Add new behavior by adding a
 * {@code switch} case in {@code createLoreItemInternal}, not by removing labels.</p>
 */
public enum ItemType {
    /**
     * Enchanted items with special properties and enchantments.
     */
    ENCHANTED,
    
    /**
     * Cosmetic items including player heads, mob heads, and custom textures.
     */
    COSMETIC,
    
    /**
     * Collection items that are part of thematic groups.
     */
    COLLECTION,
    
    /**
     * Items with custom model data for resource pack integration.
     */
    MODEL_DATA,
    
    /**
     * Standard lore items without special properties.
     */
    STANDARD,

    /**
     * Legendary items with unique properties and high rarity.
     */
    LEGENDARY,

    /**
     * Artifact items with historical significance or special lore.
     */
    ARTIFACT,

    /**
     * Seasonal items available during specific events or time periods.
     */
    SEASONAL,

    /**
     * Uncommon items with slightly elevated rarity.
     */
    UNCOMMON,

    /**
     * Unique one-of-a-kind items.
     */
    UNIQUE,

    /**
     * Rare items with low drop rates or limited availability.
     */
    RARE,

    /**
     * Event-specific items earned during server events.
     */
    EVENT,

    /**
     * Items awarded as quest completion rewards.
     */
    QUEST_REWARD
}

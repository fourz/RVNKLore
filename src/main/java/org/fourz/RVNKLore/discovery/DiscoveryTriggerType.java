package org.fourz.RVNKLore.discovery;

/**
 * Types of triggers that can cause lore discovery.
 */
public enum DiscoveryTriggerType {
    /**
     * Player enters a region (WorldGuard integration).
     */
    REGION_ENTER,

    /**
     * Player breaks a specific block type.
     */
    BLOCK_BREAK,

    /**
     * Player interacts with a specific block (e.g., signs, lecterns).
     */
    BLOCK_INTERACT,

    /**
     * Player kills a mob with a lore drop.
     */
    MOB_KILL,

    /**
     * Player finds lore item in a chest.
     */
    CHEST_LOOT,

    /**
     * Player completes a quest (RVNKQuests integration).
     */
    QUEST_COMPLETE,

    /**
     * Player talks to an NPC (Citizens integration).
     */
    NPC_INTERACT,

    /**
     * Player uses a specific item.
     */
    ITEM_USE,

    /**
     * Player enters a specific location (coordinate-based).
     */
    LOCATION_ENTER,

    /**
     * Manual discovery via command or API.
     */
    COMMAND,

    /**
     * Discovery triggered by another plugin.
     */
    EXTERNAL,

    /**
     * First time player joins server.
     */
    FIRST_JOIN
}

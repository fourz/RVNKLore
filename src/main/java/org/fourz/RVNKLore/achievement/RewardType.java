package org.fourz.RVNKLore.achievement;

/**
 * Types of rewards that can be granted for achievements.
 */
public enum RewardType {
    /**
     * Custom item with NBT data
     */
    ITEM("Item Reward", "Receive a custom item"),

    /**
     * Permission grant via LuckPerms
     */
    PERMISSION("Permission Grant", "Gain a new permission"),

    /**
     * Economy currency via Vault
     */
    ECONOMY("Currency Reward", "Receive in-game currency"),

    /**
     * Title or prefix via LuckPerms
     */
    TITLE("Title Reward", "Earn a displayable title"),

    /**
     * Cosmetic item (head, etc.)
     */
    COSMETIC("Cosmetic Reward", "Receive a cosmetic item"),

    /**
     * Experience points
     */
    EXPERIENCE("XP Reward", "Gain experience points"),

    /**
     * Unlock access to another collection
     */
    COLLECTION_UNLOCK("Collection Unlock", "Gain access to a new collection"),

    /**
     * Execute a command as console
     */
    COMMAND("Command Reward", "Execute a custom command"),

    /**
     * Custom reward handler
     */
    CUSTOM("Custom Reward", "Custom reward implementation");

    private final String displayName;
    private final String description;

    RewardType(String displayName, String description) {
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

package org.fourz.RVNKLore.achievement;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a reward that can be granted for completing an achievement.
 */
public class AchievementReward {
    private final RewardType type;
    private final String value;
    private final Map<String, String> metadata;

    /**
     * Create a new reward.
     *
     * @param type The type of reward
     * @param value The reward value (item name, permission, amount, etc.)
     */
    public AchievementReward(RewardType type, String value) {
        this.type = type;
        this.value = value;
        this.metadata = new HashMap<>();
    }

    /**
     * Create a reward with metadata.
     */
    public AchievementReward(RewardType type, String value, Map<String, String> metadata) {
        this.type = type;
        this.value = value;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public RewardType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public Map<String, String> getMetadata() {
        return new HashMap<>(metadata);
    }

    public void addMetadata(String key, String val) {
        metadata.put(key, val);
    }

    public String getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Create an item reward.
     */
    public static AchievementReward item(String itemName) {
        return new AchievementReward(RewardType.ITEM, itemName);
    }

    /**
     * Create an item reward with amount.
     */
    public static AchievementReward item(String itemName, int amount) {
        AchievementReward reward = new AchievementReward(RewardType.ITEM, itemName);
        reward.addMetadata("amount", String.valueOf(amount));
        return reward;
    }

    /**
     * Create a permission reward.
     */
    public static AchievementReward permission(String permission) {
        return new AchievementReward(RewardType.PERMISSION, permission);
    }

    /**
     * Create an economy reward.
     */
    public static AchievementReward economy(double amount) {
        return new AchievementReward(RewardType.ECONOMY, String.valueOf(amount));
    }

    /**
     * Create a title reward.
     */
    public static AchievementReward title(String prefix, String suffix) {
        AchievementReward reward = new AchievementReward(RewardType.TITLE, prefix);
        if (suffix != null) {
            reward.addMetadata("suffix", suffix);
        }
        return reward;
    }

    /**
     * Create an experience reward.
     */
    public static AchievementReward experience(int xp) {
        return new AchievementReward(RewardType.EXPERIENCE, String.valueOf(xp));
    }

    /**
     * Create a command reward.
     */
    public static AchievementReward command(String command) {
        return new AchievementReward(RewardType.COMMAND, command);
    }

    /**
     * Create a collection unlock reward.
     */
    public static AchievementReward collectionUnlock(String collectionId) {
        return new AchievementReward(RewardType.COLLECTION_UNLOCK, collectionId);
    }

    @Override
    public String toString() {
        return "AchievementReward{type=" + type + ", value='" + value + "'}";
    }
}

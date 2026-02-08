package org.fourz.RVNKLore.data.model;

/**
 * Represents a reward definition for completing a collection.
 * Stored in the collection_reward table.
 */
public class CollectionReward {

    private int id;
    private String collectionId;
    private RewardType rewardType;
    private String rewardData;

    public CollectionReward(int id, String collectionId, RewardType rewardType, String rewardData) {
        this.id = id;
        this.collectionId = collectionId;
        this.rewardType = rewardType;
        this.rewardData = rewardData;
    }

    public CollectionReward(String collectionId, RewardType rewardType, String rewardData) {
        this(-1, collectionId, rewardType, rewardData);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCollectionId() { return collectionId; }

    public RewardType getRewardType() { return rewardType; }

    public String getRewardData() { return rewardData; }

    /**
     * Reward types for collection completion.
     */
    public enum RewardType {
        ITEM,
        EXPERIENCE,
        TITLE,
        COMMAND;

        public static RewardType fromString(String value) {
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ITEM;
            }
        }
    }

    @Override
    public String toString() {
        return "CollectionReward{id=" + id + ", collectionId='" + collectionId +
                "', type=" + rewardType + ", data='" + rewardData + "'}";
    }
}

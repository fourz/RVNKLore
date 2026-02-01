package org.fourz.RVNKLore.achievement;

import java.util.*;

/**
 * Represents an achievement that players can earn.
 */
public class Achievement {
    private final String id;
    private String name;
    private String description;
    private AchievementType type;
    private final List<AchievementReward> rewards;
    private final Map<String, String> criteria;
    private boolean hidden;
    private int points;
    private String iconMaterial;
    private long createdAt;

    /**
     * Create a new achievement.
     *
     * @param id Unique achievement identifier
     * @param name Display name
     * @param description Achievement description
     * @param type The type of achievement
     */
    public Achievement(String id, String name, String description, AchievementType type) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.rewards = new ArrayList<>();
        this.criteria = new HashMap<>();
        this.hidden = false;
        this.points = 10;
        this.iconMaterial = "BOOK";
        this.createdAt = System.currentTimeMillis();
    }

    // Getters and setters
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AchievementType getType() { return type; }
    public void setType(AchievementType type) { this.type = type; }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public String getIconMaterial() { return iconMaterial; }
    public void setIconMaterial(String iconMaterial) { this.iconMaterial = iconMaterial; }
    public long getCreatedAt() { return createdAt; }

    // Rewards management
    public List<AchievementReward> getRewards() { return new ArrayList<>(rewards); }
    public void addReward(AchievementReward reward) { rewards.add(reward); }
    public void clearRewards() { rewards.clear(); }

    // Criteria management
    public Map<String, String> getCriteria() { return new HashMap<>(criteria); }
    public void setCriterion(String key, String value) { criteria.put(key, value); }
    public String getCriterion(String key) { return criteria.get(key); }
    public boolean hasCriterion(String key) { return criteria.containsKey(key); }

    /**
     * Get the target collection ID (for COLLECTION_COMPLETE type).
     */
    public String getTargetCollectionId() {
        return criteria.get("collection_id");
    }

    /**
     * Get the target count (for count-based achievements).
     */
    public int getTargetCount() {
        String count = criteria.get("count");
        return count != null ? Integer.parseInt(count) : 1;
    }

    /**
     * Get the target rarity (for RARITY_COUNT type).
     */
    public String getTargetRarity() {
        return criteria.get("rarity");
    }

    /**
     * Get the target category (for CATEGORY_COMPLETE type).
     */
    public String getTargetCategory() {
        return criteria.get("category");
    }

    /**
     * Get the time limit in seconds (for SPEED_COMPLETION type).
     */
    public long getTimeLimit() {
        String limit = criteria.get("time_limit");
        return limit != null ? Long.parseLong(limit) : 0;
    }

    // Builder pattern for easy achievement creation
    public static class Builder {
        private final Achievement achievement;

        public Builder(String id, String name, AchievementType type) {
            this.achievement = new Achievement(id, name, "", type);
        }

        public Builder description(String description) {
            achievement.setDescription(description);
            return this;
        }

        public Builder hidden(boolean hidden) {
            achievement.setHidden(hidden);
            return this;
        }

        public Builder points(int points) {
            achievement.setPoints(points);
            return this;
        }

        public Builder icon(String material) {
            achievement.setIconMaterial(material);
            return this;
        }

        public Builder criterion(String key, String value) {
            achievement.setCriterion(key, value);
            return this;
        }

        public Builder collectionId(String collectionId) {
            achievement.setCriterion("collection_id", collectionId);
            return this;
        }

        public Builder targetCount(int count) {
            achievement.setCriterion("count", String.valueOf(count));
            return this;
        }

        public Builder targetRarity(String rarity) {
            achievement.setCriterion("rarity", rarity);
            return this;
        }

        public Builder targetCategory(String category) {
            achievement.setCriterion("category", category);
            return this;
        }

        public Builder timeLimit(long seconds) {
            achievement.setCriterion("time_limit", String.valueOf(seconds));
            return this;
        }

        public Builder reward(AchievementReward reward) {
            achievement.addReward(reward);
            return this;
        }

        public Achievement build() {
            return achievement;
        }
    }

    @Override
    public String toString() {
        return "Achievement{id='" + id + "', name='" + name + "', type=" + type + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Achievement that = (Achievement) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

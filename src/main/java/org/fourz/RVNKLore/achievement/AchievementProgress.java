package org.fourz.RVNKLore.achievement;

import java.util.UUID;

/**
 * Tracks a player's progress toward completing an achievement.
 */
public class AchievementProgress {
    private final UUID playerId;
    private final String achievementId;
    private int currentProgress;
    private int targetProgress;
    private boolean completed;
    private boolean rewardsClaimed;
    private long startedAt;
    private long completedAt;

    /**
     * Create new progress tracking for an achievement.
     *
     * @param playerId The player's UUID
     * @param achievementId The achievement ID
     * @param targetProgress The target value to complete
     */
    public AchievementProgress(UUID playerId, String achievementId, int targetProgress) {
        this.playerId = playerId;
        this.achievementId = achievementId;
        this.currentProgress = 0;
        this.targetProgress = targetProgress;
        this.completed = false;
        this.rewardsClaimed = false;
        this.startedAt = System.currentTimeMillis();
        this.completedAt = 0;
    }

    /**
     * Full constructor for loading from database.
     */
    public AchievementProgress(UUID playerId, String achievementId, int currentProgress,
                               int targetProgress, boolean completed, boolean rewardsClaimed,
                               long startedAt, long completedAt) {
        this.playerId = playerId;
        this.achievementId = achievementId;
        this.currentProgress = currentProgress;
        this.targetProgress = targetProgress;
        this.completed = completed;
        this.rewardsClaimed = rewardsClaimed;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public String getAchievementId() { return achievementId; }
    public int getCurrentProgress() { return currentProgress; }
    public int getTargetProgress() { return targetProgress; }
    public boolean isCompleted() { return completed; }
    public boolean isRewardsClaimed() { return rewardsClaimed; }
    public long getStartedAt() { return startedAt; }
    public long getCompletedAt() { return completedAt; }

    /**
     * Get progress as a percentage (0.0 to 1.0).
     */
    public double getProgressPercentage() {
        if (targetProgress <= 0) return completed ? 1.0 : 0.0;
        return Math.min(1.0, (double) currentProgress / targetProgress);
    }

    /**
     * Get progress as a display percentage (0 to 100).
     */
    public int getProgressDisplayPercent() {
        return (int) (getProgressPercentage() * 100);
    }

    /**
     * Increment progress by 1.
     *
     * @return true if this increment completed the achievement
     */
    public boolean increment() {
        return increment(1);
    }

    /**
     * Increment progress by a specific amount.
     *
     * @param amount The amount to add
     * @return true if this increment completed the achievement
     */
    public boolean increment(int amount) {
        if (completed) return false;

        currentProgress += amount;
        if (currentProgress >= targetProgress) {
            currentProgress = targetProgress;
            completed = true;
            completedAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    /**
     * Set progress to a specific value.
     *
     * @param progress The new progress value
     * @return true if this update completed the achievement
     */
    public boolean setProgress(int progress) {
        if (completed) return false;

        currentProgress = Math.max(0, Math.min(progress, targetProgress));
        if (currentProgress >= targetProgress) {
            completed = true;
            completedAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    /**
     * Mark rewards as claimed.
     */
    public void markRewardsClaimed() {
        this.rewardsClaimed = true;
    }

    /**
     * Get the time taken to complete (in milliseconds).
     * Returns 0 if not completed.
     */
    public long getCompletionTime() {
        if (!completed || completedAt == 0) return 0;
        return completedAt - startedAt;
    }

    /**
     * Check if completed within a time limit.
     *
     * @param limitMillis The time limit in milliseconds
     * @return true if completed within the limit
     */
    public boolean completedWithinTime(long limitMillis) {
        if (!completed) return false;
        return getCompletionTime() <= limitMillis;
    }

    @Override
    public String toString() {
        return "AchievementProgress{player=" + playerId +
               ", achievement='" + achievementId + "'" +
               ", progress=" + currentProgress + "/" + targetProgress +
               ", completed=" + completed + "}";
    }
}

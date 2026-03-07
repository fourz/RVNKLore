package org.fourz.RVNKLore.achievement;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a player unlocks an achievement.
 */
public class AchievementUnlockEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Achievement achievement;
    private final AchievementProgress progress;
    private boolean cancelled;
    private boolean suppressNotification;
    private boolean suppressRewards;

    /**
     * Create a new achievement unlock event.
     *
     * @param player The player who unlocked the achievement
     * @param achievement The achievement that was unlocked
     * @param progress The player's progress data
     */
    public AchievementUnlockEvent(Player player, Achievement achievement, AchievementProgress progress) {
        this.player = player;
        this.achievement = achievement;
        this.progress = progress;
        this.cancelled = false;
        this.suppressNotification = false;
        this.suppressRewards = false;
    }

    /**
     * Get the player who unlocked the achievement.
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Get the unlocked achievement.
     */
    public Achievement getAchievement() {
        return achievement;
    }

    /**
     * Get the player's progress data.
     */
    public AchievementProgress getProgress() {
        return progress;
    }

    /**
     * Check if notifications should be suppressed.
     */
    public boolean isSuppressNotification() {
        return suppressNotification;
    }

    /**
     * Set whether to suppress the unlock notification.
     */
    public void setSuppressNotification(boolean suppress) {
        this.suppressNotification = suppress;
    }

    /**
     * Check if rewards should be suppressed.
     */
    public boolean isSuppressRewards() {
        return suppressRewards;
    }

    /**
     * Set whether to suppress reward distribution.
     */
    public void setSuppressRewards(boolean suppress) {
        this.suppressRewards = suppress;
    }

    /**
     * Get the completion time in milliseconds.
     */
    public long getCompletionTime() {
        return progress.getCompletionTime();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

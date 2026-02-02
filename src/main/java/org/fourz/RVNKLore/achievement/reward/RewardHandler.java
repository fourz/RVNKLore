package org.fourz.RVNKLore.achievement.reward;

import org.bukkit.entity.Player;
import org.fourz.RVNKLore.achievement.AchievementReward;

/**
 * Interface for handling reward distribution.
 */
public interface RewardHandler {

    /**
     * Grant a reward to a player.
     *
     * @param player The player to reward
     * @param reward The reward to grant
     * @return true if the reward was successfully granted
     */
    boolean grantReward(Player player, AchievementReward reward);

    /**
     * Check if this handler can process the given reward.
     *
     * @param reward The reward to check
     * @return true if this handler can process the reward
     */
    boolean canHandle(AchievementReward reward);

    /**
     * Get a description of what this handler does.
     *
     * @return Handler description
     */
    String getDescription();
}

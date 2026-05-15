package org.fourz.RVNKLore.lore.item.collection.reward;

import org.bukkit.entity.Player;
import org.fourz.RVNKLore.data.model.CollectionReward;

/**
 * Interface for handling different reward types when players complete collections.
 * Implementations handle the actual reward distribution (items, permissions, commands, currency).
 *
 * <p>Candidate for unification with {@code achievement.reward.RewardHandler}: the two interfaces
 * serve the same logical purpose (dispatch a reward to a player) but use incompatible dispatch
 * mechanisms. {@code RewardHandler} routes via a boolean predicate
 * ({@code canHandle(AchievementReward)}) and accepts an {@code AchievementReward} DTO, while this
 * interface routes via a type-enum registry ({@code getHandledType()}) and accepts a
 * {@code CollectionReward} DTO. A common super-interface would require either a shared reward
 * base type or a raw/wildcard bound, both of which add complexity without eliminating duplication.
 * Blocker: no shared reward model across the achievement and collection subsystems (#957).
 */
public interface CollectionRewardHandler {

    /**
     * Execute the reward for a player.
     *
     * @param player The player receiving the reward (must be online)
     * @param reward The reward definition containing type and data
     * @return true if reward was successfully granted, false otherwise
     */
    boolean executeReward(Player player, CollectionReward reward);

    /**
     * Get the reward type this handler manages.
     *
     * @return The RewardType this handler handles
     */
    CollectionReward.RewardType getHandledType();

    /**
     * Validate that the reward data is in correct format for this handler.
     *
     * @param rewardData The data string from the reward definition
     * @return true if data is valid, false otherwise
     */
    boolean validateRewardData(String rewardData);
}

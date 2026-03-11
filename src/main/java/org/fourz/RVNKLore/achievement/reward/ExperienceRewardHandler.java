package org.fourz.RVNKLore.achievement.reward;

import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.achievement.AchievementReward;
import org.fourz.RVNKLore.achievement.RewardType;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Handles experience point rewards.
 */
public class ExperienceRewardHandler implements RewardHandler {

    private final RVNKLore plugin;
    private final LogManager logger;

    public ExperienceRewardHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ExperienceRewardHandler");
    }

    @Override
    public boolean grantReward(Player player, AchievementReward reward) {
        if (!canHandle(reward)) return false;

        try {
            int xp = Integer.parseInt(reward.getValue());
            player.giveExp(xp);
            logger.debug("Granted " + xp + " XP to " + player.getName());
            return true;
        } catch (NumberFormatException e) {
            logger.warning("Invalid XP value: " + reward.getValue());
            return false;
        }
    }

    @Override
    public boolean canHandle(AchievementReward reward) {
        return reward != null && reward.getType() == RewardType.EXPERIENCE;
    }

    @Override
    public String getDescription() {
        return "Grants experience points to the player";
    }
}

package org.fourz.RVNKLore.lore.item.collection.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.model.CollectionReward;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Handles ACHIEVEMENT reward type — grants a named achievement to the player on collection completion.
 *
 * Reward data format: JSON
 * {
 *   "achievementId": "first_collection"
 * }
 */
public class AchievementRewardHandler implements CollectionRewardHandler {

    private final RVNKLore plugin;
    private final LogManager logger;

    public AchievementRewardHandler(RVNKLore plugin, LogManager logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public boolean executeReward(Player player, CollectionReward reward) {
        try {
            JsonObject data = JsonParser.parseString(reward.getRewardData()).getAsJsonObject();
            if (!data.has("achievementId")) {
                logger.warning("ACHIEVEMENT reward missing 'achievementId' field");
                return false;
            }

            String achievementId = data.get("achievementId").getAsString();
            boolean granted = plugin.getAchievementManager().grantAchievement(player, achievementId);

            if (granted) {
                logger.debug("Granted achievement '" + achievementId + "' to " + player.getName());
            } else {
                logger.debug("Achievement '" + achievementId + "' not granted to " + player.getName() + " (already has it or not found)");
            }
            return granted;

        } catch (Exception e) {
            logger.error("ACHIEVEMENT reward: unexpected error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public CollectionReward.RewardType getHandledType() {
        return CollectionReward.RewardType.ACHIEVEMENT;
    }

    @Override
    public boolean validateRewardData(String rewardData) {
        try {
            JsonObject data = JsonParser.parseString(rewardData).getAsJsonObject();
            if (!data.has("achievementId")) {
                logger.warning("ACHIEVEMENT reward missing 'achievementId'");
                return false;
            }
            String id = data.get("achievementId").getAsString();
            return !id.trim().isEmpty();
        } catch (Exception e) {
            logger.warning("Invalid ACHIEVEMENT reward data: " + e.getMessage());
            return false;
        }
    }
}

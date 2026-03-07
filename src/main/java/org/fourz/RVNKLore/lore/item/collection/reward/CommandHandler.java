package org.fourz.RVNKLore.lore.item.collection.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.data.model.CollectionReward;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Handles COMMAND type rewards - executes console commands with player placeholders.
 *
 * Reward data format: JSON
 * {
 *   "command": "say {player} completed a collection!"
 * }
 *
 * Supported placeholders:
 * - {player} - Player's display name
 * - {uuid} - Player's UUID
 * - {player_lower} - Lowercase player name
 */
public class CommandHandler implements CollectionRewardHandler {

    private final LogManager logger;

    public CommandHandler(LogManager logger) {
        this.logger = logger;
    }

    @Override
    public boolean executeReward(Player player, CollectionReward reward) {
        try {
            JsonObject data = JsonParser.parseString(reward.getRewardData()).getAsJsonObject();

            if (!data.has("command")) {
                logger.warning("Command reward missing 'command' field");
                return false;
            }

            String command = data.get("command").getAsString();

            // Replace placeholders
            command = command.replace("{player}", player.getName());
            command = command.replace("{player_lower}", player.getName().toLowerCase());
            command = command.replace("{uuid}", player.getUniqueId().toString());

            // Execute command
            boolean result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            if (result) {
                logger.info("Executed reward command for " + player.getName() + ": " + command);
            } else {
                logger.warning("Failed to execute reward command: " + command);
            }

            return result;

        } catch (Exception e) {
            logger.error("Failed to execute command reward: " + e.getMessage());
            return false;
        }
    }

    @Override
    public CollectionReward.RewardType getHandledType() {
        return CollectionReward.RewardType.COMMAND;
    }

    @Override
    public boolean validateRewardData(String rewardData) {
        try {
            JsonObject data = JsonParser.parseString(rewardData).getAsJsonObject();
            if (!data.has("command")) {
                logger.warning("Command reward missing 'command' field");
                return false;
            }
            String command = data.get("command").getAsString();
            return !command.trim().isEmpty();
        } catch (Exception e) {
            logger.warning("Invalid JSON in command reward data: " + e.getMessage());
            return false;
        }
    }
}

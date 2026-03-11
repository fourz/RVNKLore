package org.fourz.RVNKLore.achievement.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.achievement.AchievementReward;
import org.fourz.RVNKLore.achievement.RewardType;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Handles command-based rewards (executed as console).
 */
public class CommandRewardHandler implements RewardHandler {

    private final RVNKLore plugin;
    private final LogManager logger;

    public CommandRewardHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CommandRewardHandler");
    }

    @Override
    public boolean grantReward(Player player, AchievementReward reward) {
        if (!canHandle(reward)) return false;

        String command = reward.getValue();
        if (command == null || command.isEmpty()) {
            logger.warning("Empty command for reward");
            return false;
        }

        // Replace placeholders
        command = command.replace("{player}", player.getName())
                        .replace("{uuid}", player.getUniqueId().toString())
                        .replace("{world}", player.getWorld().getName());

        // Execute on main thread
        final String finalCommand = command;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
        });

        logger.debug("Executed reward command for " + player.getName() + ": " + command);
        return true;
    }

    @Override
    public boolean canHandle(AchievementReward reward) {
        return reward != null && reward.getType() == RewardType.COMMAND;
    }

    @Override
    public String getDescription() {
        return "Executes a command as console with player placeholders";
    }
}

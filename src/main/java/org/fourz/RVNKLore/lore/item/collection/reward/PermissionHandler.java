package org.fourz.RVNKLore.lore.item.collection.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.fourz.RVNKLore.data.model.CollectionReward;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Handles PERMISSION type rewards - grants permission nodes to players.
 *
 * Reward data format: JSON
 * {
 *   "permission": "rvnk.vip.claim",
 *   "duration_days": 0  // 0 = permanent
 * }
 *
 * Note: Duration requires LuckPerms. Without it, permissions are temporary (until server restart).
 */
public class PermissionHandler implements CollectionRewardHandler {

    private final LogManager logger;
    private final PluginManager pluginManager;

    public PermissionHandler(LogManager logger, PluginManager pluginManager) {
        this.logger = logger;
        this.pluginManager = pluginManager;
    }

    @Override
    public boolean executeReward(Player player, CollectionReward reward) {
        try {
            JsonObject data = JsonParser.parseString(reward.getRewardData()).getAsJsonObject();

            if (!data.has("permission")) {
                logger.warning("Permission reward missing 'permission' field");
                return false;
            }

            String permission = data.get("permission").getAsString();
            int durationDays = data.has("duration_days") ? data.get("duration_days").getAsInt() : 0;

            // Try LuckPerms first if available
            if (pluginManager.getPlugin("LuckPerms") != null) {
                return grantViaLuckPerms(player, permission, durationDays);
            }

            // Fallback: Use Bukkit's attachment system (temporary only)
            logger.warning("LuckPerms not found - granting permission temporarily (until restart)");
            player.addAttachment(pluginManager.getPlugin("RVNKLore"), permission, true);
            logger.debug("Granted permission " + permission + " to " + player.getName() + " (temporary)");
            return true;

        } catch (Exception e) {
            logger.error("Failed to execute permission reward: " + e.getMessage());
            return false;
        }
    }

    /**
     * Grant permission using LuckPerms API (permanent storage).
     */
    private boolean grantViaLuckPerms(Player player, String permission, int durationDays) {
        try {
            // Get LuckPerms API
            Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
            Object luckPermsApi = luckPermsClass.getMethod("get").invoke(null);

            // Get user manager
            Object userManager = luckPermsClass.getMethod("getUserManager").invoke(luckPermsApi);

            // Load user
            Object userCompletableFuture = userManager.getClass().getMethod("loadUser", java.util.UUID.class).invoke(userManager, player.getUniqueId());
            Object user = userCompletableFuture.getClass().getMethod("join").invoke(userCompletableFuture);

            // Create node
            Object nodeFactory = Class.forName("net.luckperms.api.node.NodeFactory").getMethod("builder", String.class).invoke(null, permission);

            if (durationDays > 0) {
                long expiryTime = System.currentTimeMillis() + (durationDays * 24 * 60 * 60 * 1000L);
                nodeFactory = nodeFactory.getClass().getMethod("expireAt", long.class).invoke(nodeFactory, expiryTime);
            }

            Object node = nodeFactory.getClass().getMethod("build").invoke(nodeFactory);

            // Add node to user
            user.getClass().getMethod("data").invoke(user).getClass().getMethod("add", Class.forName("net.luckperms.api.node.Node")).invoke(user.getClass().getMethod("data").invoke(user), node);

            // Save user
            userManager.getClass().getMethod("saveUser", Class.forName("net.luckperms.api.user.User")).invoke(userManager, user);

            String duration = durationDays > 0 ? " (" + durationDays + " days)" : " (permanent)";
            logger.debug("Granted permission " + permission + " to " + player.getName() + duration);
            return true;

        } catch (Exception e) {
            logger.error("Failed to grant permission via LuckPerms: " + e.getMessage());
            return false;
        }
    }

    @Override
    public CollectionReward.RewardType getHandledType() {
        return CollectionReward.RewardType.PERMISSION;
    }

    @Override
    public boolean validateRewardData(String rewardData) {
        try {
            JsonObject data = JsonParser.parseString(rewardData).getAsJsonObject();
            if (!data.has("permission")) {
                logger.warning("Permission reward missing 'permission' field");
                return false;
            }
            String permission = data.get("permission").getAsString();
            return !permission.trim().isEmpty();
        } catch (Exception e) {
            logger.warning("Invalid JSON in permission reward data: " + e.getMessage());
            return false;
        }
    }
}

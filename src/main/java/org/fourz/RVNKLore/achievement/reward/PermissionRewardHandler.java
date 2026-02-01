package org.fourz.RVNKLore.achievement.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.achievement.AchievementReward;
import org.fourz.RVNKLore.achievement.RewardType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.lang.reflect.Method;

/**
 * Handles permission-based rewards via LuckPerms.
 */
public class PermissionRewardHandler implements RewardHandler {

    private final RVNKLore plugin;
    private final LogManager logger;
    private boolean luckPermsAvailable;
    private Object luckPermsApi;

    public PermissionRewardHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "PermissionRewardHandler");
        initializeLuckPerms();
    }

    private void initializeLuckPerms() {
        try {
            Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (luckPerms != null && luckPerms.isEnabled()) {
                // Get LuckPerms API via reflection
                Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                Method getMethod = providerClass.getMethod("get");
                luckPermsApi = getMethod.invoke(null);
                luckPermsAvailable = true;
                logger.info("LuckPerms integration enabled");
            } else {
                luckPermsAvailable = false;
                logger.info("LuckPerms not found - permission rewards will use fallback");
            }
        } catch (Exception e) {
            luckPermsAvailable = false;
            logger.warning("Could not initialize LuckPerms: " + e.getMessage());
        }
    }

    @Override
    public boolean grantReward(Player player, AchievementReward reward) {
        if (!canHandle(reward)) return false;

        String permission = reward.getValue();
        if (permission == null || permission.isEmpty()) {
            logger.warning("Empty permission for reward");
            return false;
        }

        if (luckPermsAvailable) {
            return grantViaLuckPerms(player, permission);
        } else {
            return grantViaCommand(player, permission);
        }
    }

    private boolean grantViaLuckPerms(Player player, String permission) {
        try {
            // Use reflection to add permission via LuckPerms API
            Class<?> userManagerClass = luckPermsApi.getClass().getMethod("getUserManager").getReturnType();
            Object userManager = luckPermsApi.getClass().getMethod("getUserManager").invoke(luckPermsApi);

            // Get user
            Method getUser = userManager.getClass().getMethod("getUser", java.util.UUID.class);
            Object user = getUser.invoke(userManager, player.getUniqueId());

            if (user == null) {
                // Load user if not cached
                Method loadUser = userManager.getClass().getMethod("loadUser", java.util.UUID.class);
                Object futureUser = loadUser.invoke(userManager, player.getUniqueId());
                user = futureUser.getClass().getMethod("join").invoke(futureUser);
            }

            if (user != null) {
                // Build permission node
                Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
                Class<?> nodeBuilderClass = Class.forName("net.luckperms.api.node.Node$Builder");
                Method builderMethod = nodeClass.getMethod("builder", String.class);
                Object builder = builderMethod.invoke(null, permission);
                Method buildMethod = builder.getClass().getMethod("build");
                Object node = buildMethod.invoke(builder);

                // Add node to user
                Method dataMethod = user.getClass().getMethod("data");
                Object data = dataMethod.invoke(user);
                Method addMethod = data.getClass().getMethod("add", nodeClass);
                addMethod.invoke(data, node);

                // Save user
                Method saveUser = userManager.getClass().getMethod("saveUser", user.getClass().getInterfaces()[0]);
                saveUser.invoke(userManager, user);

                logger.info("Granted permission via LuckPerms to " + player.getName() + ": " + permission);
                return true;
            }
        } catch (Exception e) {
            logger.error("Error granting permission via LuckPerms", e);
        }
        return false;
    }

    private boolean grantViaCommand(Player player, String permission) {
        // Fallback: use console command
        String command = "lp user " + player.getName() + " permission set " + permission + " true";
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        });
        logger.info("Granted permission via command to " + player.getName() + ": " + permission);
        return true;
    }

    @Override
    public boolean canHandle(AchievementReward reward) {
        return reward != null && (reward.getType() == RewardType.PERMISSION || reward.getType() == RewardType.TITLE);
    }

    @Override
    public String getDescription() {
        return "Grants permissions via LuckPerms (or fallback command)";
    }
}

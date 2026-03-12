package org.fourz.RVNKLore.lore.item.collection.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.fourz.RVNKLore.data.model.CollectionReward;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Handles CURRENCY type rewards - adds economy currency to player accounts.
 *
 * Reward data format: JSON
 * {
 *   "amount": 1000.0
 * }
 *
 * Requires Vault plugin and a compatible economy plugin (EssentialsX Economy, etc.)
 */
public class CurrencyHandler implements CollectionRewardHandler {

    private final LogManager logger;
    private final PluginManager pluginManager;

    public CurrencyHandler(LogManager logger, PluginManager pluginManager) {
        this.logger = logger;
        this.pluginManager = pluginManager;
    }

    @Override
    public boolean executeReward(Player player, CollectionReward reward) {
        try {
            // Check if Vault is available
            if (pluginManager.getPlugin("Vault") == null) {
                logger.warning("Vault plugin not found - cannot grant currency reward");
                return false;
            }

            JsonObject data = JsonParser.parseString(reward.getRewardData()).getAsJsonObject();

            if (!data.has("amount")) {
                logger.warning("Currency reward missing 'amount' field");
                return false;
            }

            double amount = data.get("amount").getAsDouble();

            if (amount <= 0) {
                logger.warning("Invalid currency amount: " + amount);
                return false;
            }

            // Get economy provider via reflection
            Object economyProvider = getEconomyProvider();
            if (economyProvider == null) {
                logger.warning("No economy provider available");
                return false;
            }

            // Deposit money
            boolean success = depositMoney(economyProvider, player.getName(), amount);

            if (success) {
                logger.debug("Gave " + amount + " currency to " + player.getName());
            } else {
                logger.warning("Failed to deposit currency for " + player.getName());
            }

            return success;

        } catch (Exception e) {
            logger.error("Failed to execute currency reward: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the Vault economy provider via reflection.
     */
    private Object getEconomyProvider() {
        try {
            // Get Vault's economy provider
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Class<?> serviceManagerClass = Class.forName("net.milkbowl.vault.chat.Chat");

            // Use plugin manager to get registered service
            Object provider = pluginManager.getPlugin("Vault").getClass().getClassLoader()
                    .loadClass("net.milkbowl.vault.economy.Economy");

            // This is a simplified approach - return null if reflection fails
            return provider;

        } catch (Exception e) {
            logger.debug("Failed to get economy provider: " + e.getMessage());
            return null;
        }
    }

    /**
     * Deposit money using Vault economy.
     */
    private boolean depositMoney(Object economyProvider, String playerName, double amount) {
        try {
            // This uses reflection to call Economy.depositPlayer(PlayerOfflinePlayer, amount)
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Class<?> offlinePlayerClass = Class.forName("org.bukkit.OfflinePlayer");

            // Get player offline
            java.lang.reflect.Method getOfflinePlayerMethod = Class.forName("org.bukkit.Bukkit")
                    .getMethod("getOfflinePlayer", String.class);
            Object offlinePlayer = getOfflinePlayerMethod.invoke(null, playerName);

            // Deposit
            java.lang.reflect.Method depositMethod = economyClass.getMethod("depositPlayer", offlinePlayerClass, double.class);
            Object response = depositMethod.invoke(economyProvider, offlinePlayer, amount);

            // Check response
            java.lang.reflect.Method transactionSuccessMethod = response.getClass().getMethod("transactionSuccessful");
            return (Boolean) transactionSuccessMethod.invoke(response);

        } catch (Exception e) {
            logger.error("Failed to deposit currency via Vault: " + e.getMessage());
            return false;
        }
    }

    @Override
    public CollectionReward.RewardType getHandledType() {
        return CollectionReward.RewardType.CURRENCY;
    }

    @Override
    public boolean validateRewardData(String rewardData) {
        try {
            JsonObject data = JsonParser.parseString(rewardData).getAsJsonObject();
            if (!data.has("amount")) {
                logger.warning("Currency reward missing 'amount' field");
                return false;
            }
            double amount = data.get("amount").getAsDouble();
            return amount > 0;
        } catch (Exception e) {
            logger.warning("Invalid JSON in currency reward data: " + e.getMessage());
            return false;
        }
    }
}

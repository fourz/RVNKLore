package org.fourz.RVNKLore.lore.item.collection.reward;

import org.bukkit.plugin.PluginManager;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.model.CollectionReward;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for managing all collection reward handlers.
 * Handles initialization and lookup of reward handlers by type.
 */
public class RewardHandlerRegistry {

    private final Map<CollectionReward.RewardType, CollectionRewardHandler> handlers = new HashMap<>();
    private final LogManager logger;

    public RewardHandlerRegistry(RVNKLore plugin) {
        this.logger = LogManager.getInstance(plugin, "RewardHandlerRegistry");
        initializeHandlers(plugin);
    }

    /**
     * Initialize all reward handlers.
     */
    private void initializeHandlers(RVNKLore plugin) {
        PluginManager pluginManager = plugin.getServer().getPluginManager();

        // Register item handler
        handlers.put(CollectionReward.RewardType.ITEM, new ItemRewardHandler(logger));
        logger.debug("Registered ItemRewardHandler");

        // Register permission handler
        handlers.put(CollectionReward.RewardType.PERMISSION, new PermissionHandler(logger, pluginManager));
        logger.debug("Registered PermissionHandler");

        // Register command handler
        handlers.put(CollectionReward.RewardType.COMMAND, new CommandHandler(logger));
        logger.debug("Registered CommandHandler");

        // Register currency handler
        handlers.put(CollectionReward.RewardType.CURRENCY, new CurrencyHandler(logger, pluginManager));
        logger.debug("Registered CurrencyHandler");

        logger.debug("Reward handler registry initialized with " + handlers.size() + " handlers");
    }

    /**
     * Get a handler for a specific reward type.
     *
     * @param type The reward type
     * @return The handler for this type, or null if not found
     */
    public CollectionRewardHandler getHandler(CollectionReward.RewardType type) {
        return handlers.get(type);
    }

    /**
     * Check if a handler exists for the given reward type.
     *
     * @param type The reward type
     * @return true if a handler exists, false otherwise
     */
    public boolean hasHandler(CollectionReward.RewardType type) {
        return handlers.containsKey(type);
    }

    /**
     * Get the number of registered handlers.
     *
     * @return Number of handlers
     */
    public int getHandlerCount() {
        return handlers.size();
    }
}

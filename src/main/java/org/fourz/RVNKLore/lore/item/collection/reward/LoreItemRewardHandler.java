package org.fourz.RVNKLore.lore.item.collection.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.model.CollectionReward;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.UUID;

/**
 * Handles LORE_ITEM reward type — materializes a lore item from a lore entry and gives it to the player.
 *
 * Reward data format: JSON
 * {
 *   "entryId": "uuid-of-lore-entry"
 * }
 */
public class LoreItemRewardHandler implements CollectionRewardHandler {

    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreItemRewardHandler(RVNKLore plugin, LogManager logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public boolean executeReward(Player player, CollectionReward reward) {
        try {
            JsonObject data = JsonParser.parseString(reward.getRewardData()).getAsJsonObject();
            if (!data.has("entryId")) {
                logger.warning("LORE_ITEM reward missing 'entryId' field");
                return false;
            }

            UUID entryId = UUID.fromString(data.get("entryId").getAsString());
            LoreEntry entry = plugin.getLoreManager().getLoreEntrySync(entryId);
            if (entry == null) {
                logger.warning("LORE_ITEM reward: lore entry not found: " + entryId);
                return false;
            }

            ItemStack item = plugin.getLoreManager().getItemManager().createLoreItemSync(entry.getName());
            if (item == null) {
                logger.warning("LORE_ITEM reward: createLoreItemSync returned null for entry: " + entry.getName());
                return false;
            }

            player.getInventory().addItem(item);
            logger.debug("Gave lore item '" + entry.getName() + "' to " + player.getName());
            return true;

        } catch (IllegalArgumentException e) {
            logger.error("LORE_ITEM reward: invalid entryId UUID: " + e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("LORE_ITEM reward: unexpected error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public CollectionReward.RewardType getHandledType() {
        return CollectionReward.RewardType.LORE_ITEM;
    }

    @Override
    public boolean validateRewardData(String rewardData) {
        try {
            JsonObject data = JsonParser.parseString(rewardData).getAsJsonObject();
            if (!data.has("entryId")) {
                logger.warning("LORE_ITEM reward missing 'entryId'");
                return false;
            }
            UUID.fromString(data.get("entryId").getAsString());
            return true;
        } catch (Exception e) {
            logger.warning("Invalid LORE_ITEM reward data: " + e.getMessage());
            return false;
        }
    }
}

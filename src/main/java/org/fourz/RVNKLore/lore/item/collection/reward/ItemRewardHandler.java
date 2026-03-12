package org.fourz.RVNKLore.lore.item.collection.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.data.model.CollectionReward;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Handles ITEM type rewards - gives items to player inventory.
 *
 * Reward data format: JSON
 * {
 *   "material": "DIAMOND",
 *   "quantity": 5,
 *   "name": "Reward Item"
 * }
 */
public class ItemRewardHandler implements CollectionRewardHandler {

    private final LogManager logger;

    public ItemRewardHandler(LogManager logger) {
        this.logger = logger;
    }

    @Override
    public boolean executeReward(Player player, CollectionReward reward) {
        try {
            JsonObject data = JsonParser.parseString(reward.getRewardData()).getAsJsonObject();

            String materialName = data.has("material") ? data.get("material").getAsString() : "DIAMOND";
            int quantity = data.has("quantity") ? data.get("quantity").getAsInt() : 1;
            String itemName = data.has("name") ? data.get("name").getAsString() : null;

            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                logger.warning("Invalid material in item reward: " + materialName);
                return false;
            }

            ItemStack item = new ItemStack(material, quantity);
            if (itemName != null && !itemName.isEmpty()) {
                item.getItemMeta().setDisplayName(itemName);
            }

            player.getInventory().addItem(item);
            logger.debug("Gave " + quantity + "x " + materialName + " to " + player.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to execute item reward: " + e.getMessage());
            return false;
        }
    }

    @Override
    public CollectionReward.RewardType getHandledType() {
        return CollectionReward.RewardType.ITEM;
    }

    @Override
    public boolean validateRewardData(String rewardData) {
        try {
            JsonObject data = JsonParser.parseString(rewardData).getAsJsonObject();
            if (!data.has("material")) {
                logger.warning("Item reward missing required 'material' field");
                return false;
            }
            String materialName = data.get("material").getAsString();
            return Material.matchMaterial(materialName) != null;
        } catch (Exception e) {
            logger.warning("Invalid JSON in item reward data: " + e.getMessage());
            return false;
        }
    }
}

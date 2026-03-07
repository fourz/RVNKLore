package org.fourz.RVNKLore.achievement.reward;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.achievement.AchievementReward;
import org.fourz.RVNKLore.achievement.RewardType;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.HashMap;

/**
 * Handles item-based rewards.
 */
public class ItemRewardHandler implements RewardHandler {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final ItemManager itemManager;

    public ItemRewardHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ItemRewardHandler");
        this.itemManager = plugin.getLoreManager().getItemManager();
    }

    @Override
    public boolean grantReward(Player player, AchievementReward reward) {
        if (!canHandle(reward)) return false;

        String itemName = reward.getValue();
        int amount = 1;

        // Check for amount in metadata
        String amountStr = reward.getMetadata("amount");
        if (amountStr != null) {
            try {
                amount = Integer.parseInt(amountStr);
            } catch (NumberFormatException e) {
                logger.warning("Invalid amount for item reward: " + amountStr);
            }
        }

        // Try to create the item
        ItemStack item = itemManager.createLoreItemSync(itemName);
        if (item == null) {
            logger.warning("Could not create item for reward: " + itemName);
            return false;
        }

        item.setAmount(amount);

        // Add to player inventory
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            // Drop at player's feet if inventory is full
            for (ItemStack dropped : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), dropped);
            }
            player.sendMessage("§eYour inventory was full. Some items were dropped at your feet.");
        }

        logger.info("Granted item reward to " + player.getName() + ": " + itemName + " x" + amount);
        return true;
    }

    @Override
    public boolean canHandle(AchievementReward reward) {
        return reward != null && (reward.getType() == RewardType.ITEM || reward.getType() == RewardType.COSMETIC);
    }

    @Override
    public String getDescription() {
        return "Grants custom lore items from the item system";
    }
}

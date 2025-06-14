package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /lore item give <item_name> <player> command.
 * Looks up items by name from ItemManager and gives them to the specified player.
 * Supports all item types registered in the system.
 */
public class LoreItemGiveSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final ItemManager itemManager;

    public LoreItemGiveSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemGiveSubCommand");
        this.itemManager = plugin.getLoreManager().getItemManager();
    }

    /**
     * Handles the /lore item give <item_name> <player> command.
     * Looks up items by name from ItemManager and gives them to the specified player.
     * Supports all item types registered in the system.
     */
    public LoreItemGiveSubCommand(RVNKLore plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemGiveSubCommand");
        this.itemManager = itemManager;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.item.give");
    }    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item give <item_name> <player>");
            sender.sendMessage(ChatColor.GRAY + "   Give any registered lore item to a player");
            return true;
        }
        String itemName = args[0];
        String playerName = args[1];
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "✖ Player '" + playerName + "' not found or not online.");
            return true;
        }
        if (itemManager == null) {
            sender.sendMessage(ChatColor.RED + "✖ Item system is not available. Please try again later.");
            logger.error("ItemManager is null when trying to give item: " + itemName, null);
            return true;
        }
        // Use ItemManager for item lookup and giving
        org.bukkit.inventory.ItemStack item = itemManager.createLoreItem(itemName);
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "✖ Item not found: " + itemName);
            return true;
        }
        target.getInventory().addItem(item);
        sender.sendMessage(ChatColor.GREEN + "✓ Gave " + itemName + " to " + playerName);
        return true;
    }

    @Override
    public String getDescription() {
        return "Give any lore item by name to a player.";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(itemManager.getAllItemNames());
        } else if (args.length == 2) {
            // Suggest online player names for the second argument
            org.bukkit.Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }
        return completions;
    }
}

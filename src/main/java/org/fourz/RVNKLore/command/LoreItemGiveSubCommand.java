package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.item.ItemManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /lore item give <item_name> <recipient> command.
 * Looks up items by name from ItemManager and gives them to the specified player.
 * Supports all item types registered in the system.
 */
public class LoreItemGiveSubCommand implements SubCommand {
    private final ItemManager itemManager;

    public LoreItemGiveSubCommand(RVNKLore plugin, ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.item.give");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("&c▶ Usage: /lore itemgive <item_name> <player>");
            return true;
        }
        String itemName = args[0];
        String playerName = args[1];
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("&c✖ Player not found or not online.");
            return true;
        }
        ItemStack item = itemManager.createLoreItem(itemName);
        if (item == null) {
            sender.sendMessage("&c✖ Item not found: " + itemName);
            return true;
        }
        target.getInventory().addItem(item);
        sender.sendMessage("&a✓ Gave " + itemName + " to " + playerName);
        return true;
    }

    @Override
    public String getDescription() {
        return "Gives any lore item by name to a player.";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(itemManager.getAllItemNames());
        } else if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
        }
        return completions;
    }
}

package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.subcommand.SubCommand;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.ItemPropertiesDTO;
import org.fourz.RVNKLore.debug.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /lore item give <item_name> <player> command.
 * Looks up items by name from database and gives them to the specified player.
 * Uses async database methods for retrieving data.
 */
public class LoreItemGiveSubCommand implements SubCommand {
    private final LogManager logger;
    private final DatabaseManager databaseManager;

    public LoreItemGiveSubCommand(RVNKLore plugin) {
        this.logger = LogManager.getInstance(plugin, "LoreItemGiveSubCommand");
        this.databaseManager = plugin.getDatabaseManager();
    }

    /**
     * Legacy constructor kept for backward compatibility.
     */
    public LoreItemGiveSubCommand(RVNKLore plugin, org.fourz.RVNKLore.lore.item.ItemManager itemManager) {
        this.logger = LogManager.getInstance(plugin, "LoreItemGiveSubCommand");
        this.databaseManager = plugin.getDatabaseManager();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.item.give");
    }    

    @Override
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
        
        if (databaseManager == null) {
            sender.sendMessage(ChatColor.RED + "✖ Database system is not available. Please try again later.");
            logger.error("DatabaseManager is null when trying to give item: " + itemName, null);
            return true;
        }
        
        // Look up the item by name from the database
        databaseManager.getItemRepository().getAllItems().thenAccept(items -> {
            ItemPropertiesDTO matchedItem = null;
            for (ItemPropertiesDTO item : items) {
                if (item.getDisplayName().equalsIgnoreCase(itemName)) {
                    matchedItem = item;
                    break;
                }
            }
            
            if (matchedItem != null) {
                // Convert DTO to domain object and create an ItemStack
                org.fourz.RVNKLore.lore.item.ItemProperties properties = matchedItem.toItemProperties();
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(properties.getMaterial());
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(properties.getDisplayName());
                    meta.setLore(properties.getLore());
                    if (properties.getCustomModelData() > 0) {
                        meta.setCustomModelData(properties.getCustomModelData());
                    }
                    item.setItemMeta(meta);
                }
                
                // Give the item to the player
                target.getInventory().addItem(item);
                sender.sendMessage(ChatColor.GREEN + "✓ Gave " + itemName + " to " + playerName);
            } else {
                sender.sendMessage(ChatColor.RED + "✖ Item not found: " + itemName);
            }
        }).exceptionally(e -> {
            logger.error("Error retrieving item: " + itemName, e);
            sender.sendMessage(ChatColor.RED + "✖ An error occurred while retrieving the item.");
            return null;
        });
        
        return true;
    }

    @Override
    public String getDescription() {
        return "Give any lore item by name to a player.";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!hasPermission(sender)) {
            return completions;
        }
        
        if (args.length == 1 && databaseManager != null) {
            databaseManager.getItemRepository().getAllItems().thenAccept(items -> {
                for (ItemPropertiesDTO item : items) {
                    completions.add(item.getDisplayName());
                }
            }).exceptionally(e -> {
                logger.error("Error getting item names for tab completion", e);
                return null;
            });
            
            // Since we're in a synchronous context, we need to block until we get the results
            try {
                Thread.sleep(100); // Give the database a moment to respond
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
        } else if (args.length == 2) {
            // Suggest online player names for the second argument
            org.bukkit.Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }
        
        return completions;
    }
}

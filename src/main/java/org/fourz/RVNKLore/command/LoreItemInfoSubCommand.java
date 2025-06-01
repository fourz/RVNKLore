package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /lore item info <item_name> command.
 * Displays detailed information about a registered lore item including
 * material, display name, lore, and metadata.
 */
public class LoreItemInfoSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final ItemManager itemManager;

    public LoreItemInfoSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemInfoSubCommand");
        this.itemManager = plugin.getItemManager();
    }

    public LoreItemInfoSubCommand(RVNKLore plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemInfoSubCommand");
        this.itemManager = itemManager;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.item.give") || sender.hasPermission("rvnklore.command.collection");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showAvailableItems(sender);
            return true;
        }
        if (args.length > 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item info <item_name>");
            sender.sendMessage(ChatColor.GRAY + "   Display information about a registered item");
            return true;
        }
        String itemName = args[0];
        if (itemManager == null) {
            sender.sendMessage(ChatColor.RED + "✖ Item system is not available. Please try again later.");
            logger.error("ItemManager is null when trying to get item info: " + itemName, null);
            return true;
        }
        ItemStack item = itemManager.createLoreItem(itemName);
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "✖ Item not found: " + itemName);
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        sender.sendMessage(ChatColor.GOLD + "===== Item Info: " + itemName + " =====");
        sender.sendMessage(ChatColor.YELLOW + "Material: " + item.getType());
        if (meta != null) {
            if (meta.hasDisplayName()) {
                sender.sendMessage(ChatColor.YELLOW + "Display Name: " + meta.getDisplayName());
            }
            if (meta.hasLore()) {
                sender.sendMessage(ChatColor.YELLOW + "Lore:");
                for (String line : meta.getLore()) {
                    sender.sendMessage(ChatColor.GRAY + "  " + line);
                }
            }
            // Show custom model data if present
            if (meta.hasCustomModelData()) {
                sender.sendMessage(ChatColor.YELLOW + "Custom Model Data: " + meta.getCustomModelData());
            }
        }
        return true;
    }

    private void showAvailableItems(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Available Items:");
        for (String name : itemManager.getAllItemNames()) {
            sender.sendMessage(ChatColor.YELLOW + " - " + name);
        }
    }

    @Override
    public String getDescription() {
        return "Get information about a lore item by name.";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(itemManager.getAllItemNames());
        }
        return completions;
    }
}

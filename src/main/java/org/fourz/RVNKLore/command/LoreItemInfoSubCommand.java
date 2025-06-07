package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
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
            if (itemManager == null) {
                sender.sendMessage(ChatColor.RED + "✖ Item system is not available. Please try again later.");
                logger.error("ItemManager is null when trying to list items", null);
                return true;
            }
            // Delegate to ItemManager for item list, but output here
            List<String> allItems = itemManager.getAllItemNames();
            sender.sendMessage(ChatColor.GOLD + "Available Items:");
            if (allItems.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "   No items available.");
            } else {
                for (String name : allItems) {
                    sender.sendMessage(ChatColor.YELLOW + " - " + name);
                }
            }
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
        // Delegate to ItemManager for info, but output here
        org.bukkit.inventory.ItemStack item = itemManager.createLoreItem(itemName);
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "✖ Item not found: " + itemName);
            return true;
        }
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
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
            if (meta.hasCustomModelData()) {
                sender.sendMessage(ChatColor.YELLOW + "Custom Model Data: " + meta.getCustomModelData());
            }
            if (meta.hasEnchants()) {
                sender.sendMessage(ChatColor.YELLOW + "Enchantments:");
                for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                    String enchantName = entry.getKey().toString().replace("Enchantment[", "").replace("]", "");
                    sender.sendMessage(ChatColor.GRAY + "  " + formatEnchantmentName(enchantName) + " " + entry.getValue());
                }
            }
        }
        return true;
    }

    private String formatEnchantmentName(String enchantName) {
        String[] parts = enchantName.split("_");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 0) {
                formatted.append(part.substring(0, 1).toUpperCase())
                         .append(part.substring(1).toLowerCase())
                         .append(" ");
            }
        }
        return formatted.toString().trim();
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

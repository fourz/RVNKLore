package org.fourz.RVNKLore.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.command.output.DisplayFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /lore item info <item_name> command.
 * Displays detailed information about a registered lore item using DisplayFactory.
 * Also supports lookup by UUID or short UUID.
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
                sender.sendMessage(org.bukkit.ChatColor.RED + "✖ Item system is not available. Please try again later.");
                logger.error("ItemManager is null when trying to list items", null);
                return true;
            }
            List<String> allItems = itemManager.getAllItemNames();
            DisplayFactory.displayPaginatedList(sender, "Available Items", allItems, 1, 50, s -> org.bukkit.ChatColor.YELLOW + " - " + s);
            return true;
        }
        if (args.length > 1) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "▶ Usage: /lore item info <item_name>");
            sender.sendMessage(org.bukkit.ChatColor.GRAY + "   Display information about a registered item");
            return true;
        }
        String itemNameOrId = args[0];
        if (itemManager == null) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "✖ Item system is not available. Please try again later.");
            logger.error("ItemManager is null when trying to get item info: " + itemNameOrId, null);
            return true;
        }

        // Try to match by UUID or short UUID for LoreEntry
       LoreEntry matched = null;
        for (LoreEntry entry : plugin.getLoreManager().getAllLoreEntries()) {
            String uuid = entry.getId().toString();
            if (uuid.equalsIgnoreCase(itemNameOrId) || uuid.substring(0, 8).equalsIgnoreCase(itemNameOrId)) {
                matched = entry;
                break;
            }
        }
        if (matched != null) {
            return DisplayFactory.displayLoreEntry(sender, matched);
        }

        // Fallback: try to display as item by name
        org.bukkit.inventory.ItemStack item = itemManager.createLoreItem(itemNameOrId);
        return DisplayFactory.displayItem(sender, item, itemNameOrId);
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

package org.fourz.RVNKLore.command;

import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.output.DisplayFactory;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;

import java.util.Comparator;
import java.util.List;

/**
 * Handles the /lore item list command.
 * Outputs a paginated, sorted list of items using DisplayFactory.
 * Ensures fresh database queries for up-to-date information.
 */
public class LoreItemListSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final ItemManager itemManager;
    private final LogManager logger;
    private static final int ITEMS_PER_PAGE = 10;

    public LoreItemListSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
        this.logger = LogManager.getInstance(plugin, "LoreItemListCommand");
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.command.collection") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (itemManager == null) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "✖ Item system is not available. Please try again later.");
            logger.error("ItemManager is null when listing items", null);
            return true;
        }

        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) {
                    page = 1;
                    sender.sendMessage(org.bukkit.ChatColor.YELLOW + "⚠ Page number must be positive. Showing first page.");
                }
            } catch (NumberFormatException ignored) {
                sender.sendMessage(org.bukkit.ChatColor.RED + "✖ Invalid page number. Showing first page.");
            }
        }

        itemManager.refreshCache();
        List<ItemProperties> items = itemManager.getAllItemsWithProperties();
        items.sort(Comparator.comparing(ItemProperties::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        // Use DisplayFactory for paginated output
        return org.fourz.RVNKLore.command.output.DisplayFactory.displayPaginatedList(
            sender,
            "Lore Items",
            items,
            page,
            ITEMS_PER_PAGE,
            item -> {
                String dateStr = (item.getCreatedAt() != null)
                    ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(item.getCreatedAt()))
                    : "Unknown";
                return org.bukkit.ChatColor.WHITE + item.getDisplayName() +
                       org.bukkit.ChatColor.GRAY + " (" + item.getItemType() + ") - " +
                       org.bukkit.ChatColor.YELLOW + dateStr;
            }
        );
    }

    @Override
    public String getDescription() {
        return "List all lore items, sorted from newest to oldest.";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("1", "2", "3");
        }
        return List.of();
    }
}

package org.fourz.RVNKLore.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.output.DisplayFactory;
import org.fourz.RVNKLore.command.subcommand.SubCommand;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.ItemPropertiesDTO;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Handles the /lore item list command.
 * Outputs a paginated, sorted list of items using DisplayFactory.
 * Uses async database methods for retrieving items.
 */
public class LoreItemListSubCommand implements SubCommand {
    private final DatabaseManager databaseManager;
    private final LogManager logger;
    private static final int ITEMS_PER_PAGE = 10;

    public LoreItemListSubCommand(RVNKLore plugin) {
        this.databaseManager = plugin.getDatabaseManager();
        this.logger = LogManager.getInstance(plugin, "LoreItemListCommand");
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.command.collection") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (databaseManager == null) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "✖ Database system is not available. Please try again later.");
            logger.error("DatabaseManager is null when listing items", null);
            return true;
        }

        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                // Page validation and warning now handled by DisplayFactory
            }
        }

        final int finalPage = page;
        
        // Use async database method to get all items
        databaseManager.getAllItems().thenAccept(itemDTOs -> {
            // Convert DTOs to domain objects for display
            List<ItemProperties> items = new ArrayList<>();
            for (ItemPropertiesDTO dto : itemDTOs) {
                items.add(dto.toItemProperties());
            }
            
            // Sort by creation date (newest first)
            items.sort(Comparator.comparing(ItemProperties::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

            // Display the paginated list
            DisplayFactory.displayPaginatedList(
                sender,
                "Lore Items",
                items,
                finalPage,
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
        }).exceptionally(e -> {
            logger.error("Error retrieving items from database", e);
            sender.sendMessage(org.bukkit.ChatColor.RED + "✖ An error occurred while retrieving items.");
            return null;
        });
        
        return true;
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

package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.output.DisplayFactory;
import org.fourz.RVNKLore.lore.item.book.LoreBookManager;
import org.fourz.RVNKLore.lore.item.book.LoreBookManager.BookListEntry;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand for listing available lore books.
 * Usage: /lore book list [page] [type]
 */
public class LoreBookListSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final LoreBookManager bookManager;
    private static final int ITEMS_PER_PAGE = 10;

    public LoreBookListSubCommand(RVNKLore plugin, LoreBookManager bookManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreBookListSubCommand");
        this.bookManager = bookManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        int page = 1;
        String typeFilter = null;

        // Parse arguments
        for (String arg : args) {
            try {
                page = Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                // Not a number, might be a type filter
                typeFilter = arg.toUpperCase();
            }
        }

        sender.sendMessage(ChatColor.YELLOW + "⚙ Loading available lore books...");

        final int finalPage = page;
        final String finalTypeFilter = typeFilter;

        bookManager.getObtainableBooks().thenAccept(books -> {
            // Apply type filter if specified
            List<BookListEntry> filtered = books;
            if (finalTypeFilter != null) {
                filtered = books.stream()
                    .filter(b -> b.type().name().equalsIgnoreCase(finalTypeFilter))
                    .toList();
            }

            if (filtered.isEmpty()) {
                if (finalTypeFilter != null) {
                    sender.sendMessage(ChatColor.YELLOW + "No lore books found for type: " + finalTypeFilter);
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "No lore books available.");
                }
                return;
            }

            // Format entries for display
            List<String> formattedEntries = new ArrayList<>();
            for (BookListEntry entry : filtered) {
                String line = entry.rarity().getColor() + entry.name() +
                    ChatColor.GRAY + " [" + formatTypeName(entry.type().name()) + "] " +
                    ChatColor.DARK_GRAY + "(" + entry.getShortId() + ")";
                formattedEntries.add(line);
            }

            // Display paginated list
            String title = "Available Lore Books";
            if (finalTypeFilter != null) {
                title += " (" + formatTypeName(finalTypeFilter) + ")";
            }

            DisplayFactory.displayPaginatedList(
                sender,
                title,
                formattedEntries,
                finalPage,
                ITEMS_PER_PAGE,
                entry -> ChatColor.GRAY + "• " + entry
            );

            // Show usage hint
            sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/lore book give <player> <id>" +
                ChatColor.GRAY + " to give a book.");
        }).exceptionally(ex -> {
            logger.error("Error listing lore books", (Exception) ex);
            sender.sendMessage(ChatColor.RED + "✖ An error occurred while listing books.");
            return null;
        });

        return true;
    }

    /**
     * Format a type name for display.
     */
    private String formatTypeName(String typeName) {
        if (typeName == null) return "Unknown";
        String name = typeName.toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Page numbers or type filters
            String partial = args[0].toLowerCase();

            // Add page numbers
            for (int i = 1; i <= 5; i++) {
                String pageNum = String.valueOf(i);
                if (pageNum.startsWith(partial)) {
                    completions.add(pageNum);
                }
            }

            // Add type filters
            for (var type : org.fourz.RVNKLore.lore.LoreType.values()) {
                String typeName = type.name().toLowerCase();
                if (typeName.startsWith(partial)) {
                    completions.add(typeName);
                }
            }
        } else if (args.length == 2) {
            // Second arg can be page number or type filter
            String partial = args[1].toLowerCase();

            // If first arg was a type, suggest page numbers
            try {
                Integer.parseInt(args[0]);
                // First arg was a number, suggest types
                for (var type : org.fourz.RVNKLore.lore.LoreType.values()) {
                    String typeName = type.name().toLowerCase();
                    if (typeName.startsWith(partial)) {
                        completions.add(typeName);
                    }
                }
            } catch (NumberFormatException e) {
                // First arg was a type, suggest page numbers
                for (int i = 1; i <= 5; i++) {
                    String pageNum = String.valueOf(i);
                    if (pageNum.startsWith(partial)) {
                        completions.add(pageNum);
                    }
                }
            }
        }

        return completions;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.book.list") ||
               sender.hasPermission("rvnklore.book") ||
               sender.hasPermission("rvnklore.use");
    }

    @Override
    public String getDescription() {
        return "List available lore books";
    }
}

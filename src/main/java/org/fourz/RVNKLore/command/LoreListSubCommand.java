package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.subcommand.SubCommand;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Subcommand for listing lore entries
 * Usage: /lore list [type] [page]
 */
public class LoreListSubCommand implements SubCommand {
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private static final int ITEMS_PER_PAGE = 10;

    public LoreListSubCommand(RVNKLore plugin) {
        this.logger = LogManager.getInstance(plugin, "LoreListSubCommand");
        this.databaseManager = plugin.getDatabaseManager();
    }    @Override
    public boolean execute(CommandSender sender, String[] args) {
        LoreType type = null;
        int page = 1;

        // Parse arguments
        if (args.length > 0) {
            try {
                type = LoreType.valueOf(args[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                try {
                    // If first arg isn't a valid type, try to parse as a page number
                    page = Integer.parseInt(args[0]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "▶ Invalid lore type or page number: " + args[0]);
                    return true;
                }
            }
        }

        // If both type and page are specified
        if (args.length > 1 && type != null) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "▶ Invalid page number: " + args[1]);
                return true;
            }
        }

        // Ensure page is at least 1
        page = Math.max(1, page);
        
        // Notify user that we're fetching entries
        sender.sendMessage(ChatColor.GOLD + "⚙ Fetching lore entries...");
        
        final int finalPage = page;
        final LoreType finalType = type;
        
        // Get lore entries asynchronously
        CompletableFuture<List<LoreEntryDTO>> entriesFuture;
        
        if (sender.hasPermission("rvnklore.admin")) {
            // Admins can see all entries including unapproved ones
            if (finalType != null) {
                entriesFuture = databaseManager.getLoreEntryRepository().getLoreEntriesByType(finalType.name());
            } else {
                entriesFuture = databaseManager.getAllLoreEntries();
            }
        } else {
            // Regular users only see approved entries
            if (finalType != null) {
                entriesFuture = databaseManager.getLoreEntryRepository().getLoreEntriesByTypeAndApproved(finalType.name(), true);
            } else {
                entriesFuture = databaseManager.getLoreEntryRepository().getLoreEntriesByApproved(true);
            }
        }
        
        entriesFuture.thenAccept(entries -> {
            // Calculate pagination
            int totalPages = (int) Math.ceil(entries.size() / (double) ITEMS_PER_PAGE);
            int startIndex = (finalPage - 1) * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, entries.size());

            // Display header
            sender.sendMessage(ChatColor.GOLD + "=== Lore Entries" + 
                    (finalType != null ? " (" + finalType + ")" : "") + 
                    " - Page " + finalPage + "/" + Math.max(1, totalPages) + " ===");

            // Display entries
            if (entries.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ No lore entries found.");
            } else {
                for (int i = startIndex; i < endIndex; i++) {
                    LoreEntryDTO entry = entries.get(i);
                    String approvalStatus = entry.isApproved() ? 
                            ChatColor.GREEN + "[✓]" : 
                            ChatColor.RED + "[✗]";
                      // Get shortened UUID (first 8 characters)
                    UUID uuid = entry.getUuid();
                    String uuidStr = uuid != null ? uuid.toString() : "unknown";
                    // Only take substring if long enough
                    String shortId = uuidStr.length() >= 8 ? uuidStr.substring(0, 8) : uuidStr;
                              if (sender.hasPermission("rvnklore.admin")) {
                        sender.sendMessage(approvalStatus + " " + 
                                ChatColor.YELLOW + entry.getName() + 
                                ChatColor.GRAY + " (" + entry.getEntryType() + ") - " + 
                                ChatColor.WHITE + shortId);
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + entry.getName() + 
                                ChatColor.GRAY + " (" + entry.getEntryType() + ") - " + 
                                ChatColor.WHITE + shortId);
                    }
                }
            }

            // Pagination navigation help
            if (totalPages > 1) {
                sender.sendMessage(ChatColor.GRAY + "   Use /lore list" + 
                        (finalType != null ? " " + finalType : "") + 
                        " <page> to navigate pages");            }
        }).exceptionally(e -> {
            logger.error("Error fetching lore entries", e);
            sender.sendMessage(ChatColor.RED + "✖ An error occurred while fetching lore entries. Please check the console for details.");
            return null;
        });
        
        return true;
    }

    @Override
    public String getDescription() {
        return "Lists available lore entries";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.command.list") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toUpperCase();
            List<String> completions = Arrays.stream(LoreType.values())
                    .map(LoreType::name)
                    .filter(type -> type.startsWith(partial))
                    .collect(Collectors.toList());
            
            // Add page number suggestion
            if ("1".startsWith(partial)) {
                completions.add("1");
            }
            
            return completions;
        }
        return new ArrayList<>();
    }
}

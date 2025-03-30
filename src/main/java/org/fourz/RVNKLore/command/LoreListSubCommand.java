package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.util.Debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Subcommand for listing lore entries
 * Usage: /lore list [type] [page]
 */
public class LoreListSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final Debug debug;
    private static final int ITEMS_PER_PAGE = 10;

    public LoreListSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "LoreListCommand", Level.FINE);
    }

    @Override
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
                    sender.sendMessage(ChatColor.RED + "Invalid lore type or page number: " + args[0]);
                    return true;
                }
            }
        }

        // If both type and page are specified
        if (args.length > 1 && type != null) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid page number: " + args[1]);
                return true;
            }
        }

        // Ensure page is at least 1
        page = Math.max(1, page);

        // Get lore entries
        List<LoreEntry> entries;
        if (type != null) {
            entries = plugin.getLoreManager().getLoreEntriesByType(type);
        } else {
            entries = plugin.getLoreManager().getApprovedLoreEntries();
        }

        // If sender is an admin, include unapproved entries
        if (sender.hasPermission("rvnklore.admin")) {
            entries = type != null ? 
                    plugin.getLoreManager().getLoreEntriesByType(type) : 
                    new ArrayList<>(plugin.getDatabaseManager().getAllLoreEntries());
        }

        // Calculate pagination
        int totalPages = (int) Math.ceil(entries.size() / (double) ITEMS_PER_PAGE);
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, entries.size());

        // Display header
        sender.sendMessage(ChatColor.GOLD + "=== Lore Entries" + 
                (type != null ? " (" + type + ")" : "") + 
                " - Page " + page + "/" + Math.max(1, totalPages) + " ===");

        // Display entries
        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No lore entries found.");
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                LoreEntry entry = entries.get(i);
                String approvalStatus = entry.isApproved() ? 
                        ChatColor.GREEN + "[✓]" : 
                        ChatColor.RED + "[✗]";
                        
                if (sender.hasPermission("rvnklore.admin")) {
                    sender.sendMessage(approvalStatus + " " + 
                            ChatColor.YELLOW + entry.getName() + 
                            ChatColor.GRAY + " (" + entry.getType() + ") - " + 
                            ChatColor.WHITE + entry.getId());
                } else {
                    sender.sendMessage(ChatColor.YELLOW + entry.getName() + 
                            ChatColor.GRAY + " (" + entry.getType() + ") - " + 
                            ChatColor.WHITE + entry.getId());
                }
            }
        }

        // Pagination navigation help
        if (totalPages > 1) {
            sender.sendMessage(ChatColor.GRAY + "Use /lore list" + 
                    (type != null ? " " + type : "") + 
                    " <page> to navigate pages");
        }

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

package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Search subcommand for finding lore entries by keyword.
 * Console-compatible for automated testing and CI/CD integration.
 *
 * Usage: /lore search <query> [--type <lore_type>] [--page <num>]
 */
public class LoreSearchSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private static final int RESULTS_PER_PAGE = 10;
    private static final String PREFIX = "[RVNKLore] ";

    public LoreSearchSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreSearchSubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendMessage(sender, ChatColor.RED + "Usage: /lore search <query> [--type <type>] [--page <num>]");
            sendMessage(sender, ChatColor.GRAY + "Example: /lore search dragon --type ITEM");
            sendMessage(sender, ChatColor.GRAY + "Types: " + getTypeList());
            return false;
        }

        // Parse arguments
        String query = null;
        LoreType typeFilter = null;
        int page = 1;

        List<String> argList = new ArrayList<>(Arrays.asList(args));

        // Extract --type flag
        int typeIndex = argList.indexOf("--type");
        if (typeIndex != -1 && typeIndex + 1 < argList.size()) {
            String typeStr = argList.get(typeIndex + 1).toUpperCase();
            try {
                typeFilter = LoreType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                sendMessage(sender, ChatColor.RED + "Invalid type: " + typeStr);
                sendMessage(sender, ChatColor.GRAY + "Valid types: " + getTypeList());
                return false;
            }
            argList.remove(typeIndex + 1);
            argList.remove(typeIndex);
        }

        // Extract --page flag
        int pageIndex = argList.indexOf("--page");
        if (pageIndex != -1 && pageIndex + 1 < argList.size()) {
            try {
                page = Integer.parseInt(argList.get(pageIndex + 1));
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                sendMessage(sender, ChatColor.RED + "Invalid page number");
                return false;
            }
            argList.remove(pageIndex + 1);
            argList.remove(pageIndex);
        }

        // Remaining args are the query
        if (argList.isEmpty()) {
            sendMessage(sender, ChatColor.RED + "Please provide a search query");
            return false;
        }
        query = String.join(" ", argList);

        // Perform search
        logger.debug("Searching for: " + query + " (type=" + typeFilter + ", page=" + page + ")");

        List<LoreEntry> results = searchLoreEntries(query, typeFilter);

        if (results.isEmpty()) {
            sendMessage(sender, ChatColor.YELLOW + "No results found for \"" + query + "\"");
            if (typeFilter != null) {
                sendMessage(sender, ChatColor.GRAY + "Try removing the type filter: /lore search " + query);
            }
            return true;
        }

        // Display results with pagination
        displayResults(sender, results, query, typeFilter, page);
        return true;
    }

    /**
     * Search lore entries by query with optional type filter.
     */
    private List<LoreEntry> searchLoreEntries(String query, LoreType typeFilter) {
        String lowerQuery = query.toLowerCase();

        List<LoreEntry> allEntries = plugin.getLoreManager().getAllLoreEntriesSync();

        return allEntries.stream()
            .filter(entry -> {
                // Match against name or description
                boolean nameMatch = entry.getName() != null &&
                    entry.getName().toLowerCase().contains(lowerQuery);
                boolean descMatch = entry.getDescription() != null &&
                    entry.getDescription().toLowerCase().contains(lowerQuery);
                boolean idMatch = entry.getId() != null &&
                    entry.getId().toLowerCase().contains(lowerQuery);

                return nameMatch || descMatch || idMatch;
            })
            .filter(entry -> typeFilter == null || entry.getType() == typeFilter)
            .sorted(Comparator.comparing(LoreEntry::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }

    /**
     * Display search results with pagination.
     */
    private void displayResults(CommandSender sender, List<LoreEntry> results,
                                String query, LoreType typeFilter, int page) {
        int totalResults = results.size();
        int totalPages = (int) Math.ceil((double) totalResults / RESULTS_PER_PAGE);

        if (page > totalPages) {
            page = totalPages;
        }

        int startIndex = (page - 1) * RESULTS_PER_PAGE;
        int endIndex = Math.min(startIndex + RESULTS_PER_PAGE, totalResults);

        // Header
        String filterStr = typeFilter != null ? " (type: " + typeFilter.name() + ")" : "";
        sendMessage(sender, ChatColor.GOLD + "=== Search Results for \"" + query + "\"" + filterStr + " ===");
        sendMessage(sender, ChatColor.GRAY + "Found " + totalResults + " result(s) - Page " + page + "/" + totalPages);
        sendMessage(sender, "");

        // Results
        for (int i = startIndex; i < endIndex; i++) {
            LoreEntry entry = results.get(i);
            String resultLine = formatResultLine(i + 1, entry);
            sendMessage(sender, resultLine);
        }

        // Footer with navigation hints
        sendMessage(sender, "");
        if (totalPages > 1) {
            if (page < totalPages) {
                sendMessage(sender, ChatColor.GRAY + "Use /lore search " + query +
                    (typeFilter != null ? " --type " + typeFilter.name() : "") +
                    " --page " + (page + 1) + " for more results");
            }
        }
    }

    /**
     * Format a single result line for display.
     */
    private String formatResultLine(int index, LoreEntry entry) {
        StringBuilder line = new StringBuilder();

        // Index
        line.append(ChatColor.WHITE).append(index).append(". ");

        // Type badge
        line.append(ChatColor.AQUA).append("[").append(entry.getType().name()).append("]").append(" ");

        // ID (truncated)
        String shortId = entry.getId().length() > 8 ?
            entry.getId().substring(0, 8) : entry.getId();
        line.append(ChatColor.GRAY).append(shortId).append(" ");

        // Name
        line.append(ChatColor.GOLD).append(entry.getName());

        // Approval status
        if (!entry.isApproved()) {
            line.append(ChatColor.RED).append(" [PENDING]");
        }

        // Author if available
        if (entry.getSubmittedBy() != null && !entry.getSubmittedBy().isEmpty()) {
            line.append(ChatColor.DARK_GRAY).append(" by ").append(entry.getSubmittedBy());
        }

        return line.toString();
    }

    /**
     * Send message with [RVNKLore] prefix for console compatibility.
     */
    private void sendMessage(CommandSender sender, String message) {
        // For console, strip color codes and add prefix
        if (!(sender instanceof org.bukkit.entity.Player)) {
            String plainMessage = ChatColor.stripColor(message);
            if (!plainMessage.isEmpty()) {
                sender.sendMessage(PREFIX + plainMessage);
            }
        } else {
            sender.sendMessage(message);
        }
    }

    /**
     * Get comma-separated list of lore types.
     */
    private String getTypeList() {
        return Arrays.stream(LoreType.values())
            .map(LoreType::name)
            .collect(Collectors.joining(", "));
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First arg is query - suggest common searches or "--type"
            String partial = args[0].toLowerCase();
            if ("--type".startsWith(partial)) {
                completions.add("--type");
            }
            if ("--page".startsWith(partial)) {
                completions.add("--page");
            }
            // Could also suggest recent search terms or common lore names
        } else if (args.length >= 2) {
            String lastArg = args[args.length - 2].toLowerCase();
            String partial = args[args.length - 1].toLowerCase();

            if ("--type".equals(lastArg)) {
                // Suggest lore types
                for (LoreType type : LoreType.values()) {
                    if (type.name().toLowerCase().startsWith(partial)) {
                        completions.add(type.name());
                    }
                }
            } else if ("--page".equals(lastArg)) {
                // Suggest page numbers
                completions.addAll(Arrays.asList("1", "2", "3", "4", "5"));
            } else {
                // Suggest flags
                if ("--type".startsWith(partial)) {
                    completions.add("--type");
                }
                if ("--page".startsWith(partial)) {
                    completions.add("--page");
                }
            }
        }

        return completions;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.search") || sender.hasPermission("rvnklore.user");
    }

    @Override
    public String getDescription() {
        return "Search lore entries by keyword";
    }
}

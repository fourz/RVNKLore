package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.search.LoreSearchService;
import org.fourz.RVNKLore.search.SearchCriteria;
import org.fourz.RVNKLore.search.SearchResult;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced search subcommand with advanced filtering and relevance scoring.
 * Console-compatible for automated testing and CI/CD integration.
 *
 * Usage: /lore search <query> [--type <TYPE>] [--limit <N>] [--page <N>] [--discovered|--undiscovered]
 *
 * Examples:
 *   /lore search dragon
 *   /lore search "ancient sword" --type ITEM --limit 10
 *   /lore search tower --type LANDMARK,CITY --discovered
 *   /lore search event --page 2
 */
public class LoreSearchSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final LoreSearchService searchService;
    private static final int DEFAULT_RESULTS_PER_PAGE = 10;
    private static final int MAX_RESULTS_PER_PAGE = 50;
    private static final String PREFIX = "[RVNKLore] ";

    public LoreSearchSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreSearchSubCommand");
        this.searchService = new LoreSearchService(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return false;
        }

        // Parse arguments
        SearchCriteria.Builder criteriaBuilder = new SearchCriteria.Builder();
        List<String> argList = new ArrayList<>(Arrays.asList(args));

        int page = 1;
        int limit = DEFAULT_RESULTS_PER_PAGE;

        // Extract --type flag (supports comma-separated types)
        int typeIndex = argList.indexOf("--type");
        if (typeIndex != -1 && typeIndex + 1 < argList.size()) {
            String typeStr = argList.get(typeIndex + 1).toUpperCase();
            try {
                // Support comma-separated types: --type ITEM,LANDMARK
                String[] types = typeStr.split(",");
                Set<LoreType> typeSet = new HashSet<>();
                for (String type : types) {
                    typeSet.add(LoreType.valueOf(type.trim()));
                }
                criteriaBuilder.typeFilters(typeSet);
            } catch (IllegalArgumentException e) {
                sendMessage(sender, ChatColor.RED + "Invalid type: " + typeStr);
                sendMessage(sender, ChatColor.GRAY + "Valid types: " + getTypeList());
                return false;
            }
            argList.remove(typeIndex + 1);
            argList.remove(typeIndex);
        }

        // Extract --discovered flag
        int discoveredIndex = argList.indexOf("--discovered");
        if (discoveredIndex != -1) {
            criteriaBuilder.discovered(true);
            argList.remove(discoveredIndex);
        }

        // Extract --undiscovered flag
        int undiscoveredIndex = argList.indexOf("--undiscovered");
        if (undiscoveredIndex != -1) {
            criteriaBuilder.discovered(false);
            argList.remove(undiscoveredIndex);
        }

        // Extract --limit flag
        int limitIndex = argList.indexOf("--limit");
        if (limitIndex != -1 && limitIndex + 1 < argList.size()) {
            try {
                limit = Integer.parseInt(argList.get(limitIndex + 1));
                if (limit < 1) limit = 1;
                if (limit > MAX_RESULTS_PER_PAGE) limit = MAX_RESULTS_PER_PAGE;
            } catch (NumberFormatException e) {
                sendMessage(sender, ChatColor.RED + "Invalid limit number");
                return false;
            }
            argList.remove(limitIndex + 1);
            argList.remove(limitIndex);
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

        // Remaining args are the query (optional - can list all with filters)
        String query = String.join(" ", argList).trim();
        criteriaBuilder.query(query);
        criteriaBuilder.page(page, limit);

        // Build criteria and execute search
        SearchCriteria criteria = criteriaBuilder.build();
        logger.debug("Executing search with criteria: " + criteria);

        List<SearchResult> results = searchService.search(criteria);
        int totalMatches = searchService.countMatches(criteria);

        if (results.isEmpty()) {
            sendMessage(sender, ChatColor.YELLOW + "No results found" +
                (query.isEmpty() ? "" : " for \"" + query + "\""));
            if (criteria.hasTypeFilter()) {
                sendMessage(sender, ChatColor.GRAY + "Try removing the type filter");
            }
            return true;
        }

        // Display results with pagination
        displayResults(sender, results, totalMatches, criteria, page, limit);
        return true;
    }

    /**
     * Display search results with pagination and relevance indicators.
     */
    private void displayResults(CommandSender sender, List<SearchResult> results,
                                int totalMatches, SearchCriteria criteria,
                                int page, int limit) {
        int totalPages = (int) Math.ceil((double) totalMatches / limit);

        // Header
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append(ChatColor.GOLD).append("=== Search Results");

        String query = criteria.getQuery();
        if (query != null && !query.isEmpty()) {
            headerBuilder.append(" for \"").append(query).append("\"");
        }

        if (criteria.hasTypeFilter()) {
            String types = criteria.getTypeFilters().stream()
                .map(LoreType::name)
                .collect(Collectors.joining(", "));
            headerBuilder.append(" (type: ").append(types).append(")");
        }

        if (criteria.hasDiscoveredFilter()) {
            String status = criteria.getDiscoveredFilter() ? "discovered" : "undiscovered";
            headerBuilder.append(" (").append(status).append(")");
        }

        headerBuilder.append(" ===");
        sendMessage(sender, headerBuilder.toString());
        sendMessage(sender, ChatColor.GRAY + "Found " + totalMatches + " result(s) - Page " + page + "/" + totalPages);
        sendMessage(sender, "");

        // Results
        int startIndex = (page - 1) * limit;
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            String resultLine = formatResultLine(startIndex + i + 1, result);
            sendMessage(sender, resultLine);
        }

        // Footer with navigation hints
        sendMessage(sender, "");
        if (totalPages > 1 && page < totalPages) {
            StringBuilder nextPageCmd = new StringBuilder("/lore search");
            if (query != null && !query.isEmpty()) {
                nextPageCmd.append(" ").append(query);
            }
            if (criteria.hasTypeFilter()) {
                String types = criteria.getTypeFilters().stream()
                    .map(LoreType::name)
                    .collect(Collectors.joining(","));
                nextPageCmd.append(" --type ").append(types);
            }
            if (criteria.hasDiscoveredFilter()) {
                nextPageCmd.append(criteria.getDiscoveredFilter() ? " --discovered" : " --undiscovered");
            }
            nextPageCmd.append(" --page ").append(page + 1);

            sendMessage(sender, ChatColor.GRAY + "Use " + nextPageCmd + " for more results");
        }
        sendMessage(sender, ChatColor.GRAY + "Tip: Use /lore get <id> to view full details");
    }

    /**
     * Format a single result line with relevance indicator.
     */
    private String formatResultLine(int index, SearchResult result) {
        LoreEntry entry = result.getEntry();
        StringBuilder line = new StringBuilder();

        // Index
        line.append(ChatColor.WHITE).append(index).append(". ");

        // Relevance indicator (stars for high scores)
        String scoreIndicator = result.getScoreIndicator();
        if (!scoreIndicator.isEmpty()) {
            line.append(ChatColor.YELLOW).append(scoreIndicator).append(" ");
        }

        // Type badge
        line.append(ChatColor.AQUA).append("[").append(entry.getType().name()).append("]").append(" ");

        // Name
        line.append(ChatColor.GOLD).append(entry.getDisplayName());

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
     * Send usage instructions to sender.
     */
    private void sendUsage(CommandSender sender) {
        sendMessage(sender, ChatColor.RED + "Usage: /lore search <query> [options]");
        sendMessage(sender, ChatColor.GRAY + "Options:");
        sendMessage(sender, ChatColor.GRAY + "  --type <TYPE,...>  Filter by lore type (comma-separated)");
        sendMessage(sender, ChatColor.GRAY + "  --discovered       Show only approved/discovered entries");
        sendMessage(sender, ChatColor.GRAY + "  --undiscovered     Show only pending entries");
        sendMessage(sender, ChatColor.GRAY + "  --limit <N>        Results per page (1-" + MAX_RESULTS_PER_PAGE + ")");
        sendMessage(sender, ChatColor.GRAY + "  --page <N>         Page number");
        sendMessage(sender, "");
        sendMessage(sender, ChatColor.GRAY + "Examples:");
        sendMessage(sender, ChatColor.GRAY + "  /lore search dragon");
        sendMessage(sender, ChatColor.GRAY + "  /lore search sword --type ITEM --discovered");
        sendMessage(sender, ChatColor.GRAY + "  /lore search tower --type LANDMARK,CITY");
        sendMessage(sender, "");
        sendMessage(sender, ChatColor.GRAY + "Types: " + getTypeList());
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
            // First arg is query - suggest flags
            String partial = args[0].toLowerCase();
            addFlagCompletions(completions, partial);

            // Also suggest common lore names
            if (!partial.startsWith("--")) {
                completions.addAll(searchService.searchNames(partial, 5));
            }
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
            } else if ("--limit".equals(lastArg)) {
                // Suggest common limits
                completions.addAll(Arrays.asList("10", "20", "50"));
            } else if ("--page".equals(lastArg)) {
                // Suggest page numbers
                completions.addAll(Arrays.asList("1", "2", "3", "4", "5"));
            } else {
                // Suggest flags
                addFlagCompletions(completions, partial);
            }
        }

        return completions;
    }

    /**
     * Add flag completions that match the partial input.
     */
    private void addFlagCompletions(List<String> completions, String partial) {
        if ("--type".startsWith(partial)) {
            completions.add("--type");
        }
        if ("--page".startsWith(partial)) {
            completions.add("--page");
        }
        if ("--limit".startsWith(partial)) {
            completions.add("--limit");
        }
        if ("--discovered".startsWith(partial)) {
            completions.add("--discovered");
        }
        if ("--undiscovered".startsWith(partial)) {
            completions.add("--undiscovered");
        }
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.search") || sender.hasPermission("rvnklore.user");
    }

    @Override
    public String getDescription() {
        return "Search lore entries with advanced filtering";
    }
}

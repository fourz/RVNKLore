package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.search.LoreSearchService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Subcommand for listing lore entries
 * Usage: /lore list [type] [page]
 */
public class LoreListSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final TabCompletionUtil tabCompletionUtil;
    private static final int ITEMS_PER_PAGE = 10;

    public LoreListSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.tabCompletionUtil = new TabCompletionUtil(new LoreSearchService(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        LoreType type = null;
        int page = 1;
        boolean pendingOnly = false;

        // Parse arguments — strip --pending flag first
        List<String> remaining = new ArrayList<>(Arrays.asList(args));
        if (remaining.remove("--pending")) {
            pendingOnly = true;
        }

        String[] filtered = remaining.toArray(new String[0]);

        if (filtered.length > 0) {
            try {
                type = LoreType.valueOf(filtered[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                try {
                    page = Integer.parseInt(filtered[0]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Invalid lore type or page number: " + filtered[0]);
                    return true;
                }
            }
        }

        if (filtered.length > 1 && type != null) {
            try {
                page = Integer.parseInt(filtered[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid page number: " + filtered[1]);
                return true;
            }
        }

        page = Math.max(1, page);

        // Get lore entries
        List<LoreEntry> entries;
        boolean isAdmin = sender.hasPermission("rvnklore.admin");

        if (pendingOnly) {
            // --pending: show unapproved entries (admin sees all, player sees own submissions)
            List<LoreEntry> all = new ArrayList<>(plugin.getDatabaseManager().getAllLoreEntries());
            entries = all.stream()
                    .filter(e -> !e.isApproved())
                    .collect(Collectors.toList());
            if (!isAdmin) {
                String senderName = sender.getName();
                entries = entries.stream()
                        .filter(e -> senderName.equalsIgnoreCase(e.getSubmittedBy()))
                        .collect(Collectors.toList());
            }
        } else if (isAdmin) {
            entries = type != null ?
                    plugin.getLoreManager().getLoreEntriesByTypeSync(type) :
                    new ArrayList<>(plugin.getDatabaseManager().getAllLoreEntries());
        } else {
            entries = type != null ?
                    plugin.getLoreManager().getLoreEntriesByTypeSync(type).stream()
                            .filter(LoreEntry::isApproved).collect(Collectors.toList()) :
                    plugin.getLoreManager().getApprovedLoreEntriesSync();
        }

        // Calculate pagination
        int totalPages = (int) Math.ceil(entries.size() / (double) ITEMS_PER_PAGE);
        if (totalPages > 0 && page > totalPages) {
            page = totalPages;
        }
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, entries.size());

        // Display header
        String pendingLabel = pendingOnly ? " [PENDING]" : "";
        sender.sendMessage(ChatColor.GOLD + "=== Lore Entries" +
                (type != null ? " (" + type + ")" : "") + pendingLabel +
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
                            ChatColor.YELLOW + entry.getDisplayName() +
                            ChatColor.GRAY + " (" + entry.getType() + ")");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + entry.getDisplayName() +
                            ChatColor.GRAY + " (" + entry.getType() + ")");
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
        return sender.hasPermission("rvnklore.list") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0];
            List<String> completions = new ArrayList<>();

            if ("--pending".startsWith(partial.toLowerCase())) {
                completions.add("--pending");
            }
            completions.addAll(tabCompletionUtil.completeEnum(LoreType.class, partial));
            completions.addAll(tabCompletionUtil.completeLoreEntryNames(partial));
            if ("1".startsWith(partial)) {
                completions.add("1");
            }

            return completions;
        } else if (args.length == 2) {
            // Second arg: if first arg is a type, suggest names filtered by that type
            try {
                LoreType type = LoreType.valueOf(args[0].toUpperCase());
                return tabCompletionUtil.completeLoreEntryNames(args[1], type);
            } catch (IllegalArgumentException e) {
                // Not a valid type, suggest page numbers
                return Arrays.asList("1", "2", "3");
            }
        }
        return new ArrayList<>();
    }
}

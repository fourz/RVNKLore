package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.search.LoreSearchService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Subcommand for approving lore entries.
 * Usage: /lore approve <name|id> [approve|reject]
 *
 * Supports lore entry names (case-insensitive), full UUIDs, and short ID prefixes.
 * Tab completion shows only pending (unapproved) entries.
 */
public class LoreApproveSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final TabCompletionUtil tabCompletionUtil;

    public LoreApproveSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreApproveSubCommand");
        this.tabCompletionUtil = new TabCompletionUtil(new LoreSearchService(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore approve <name|id>");
            return true;
        }

        // Join all args to support multi-word names (e.g., "Fort Ravenkeep")
        String idInput = String.join(" ", args);
        LoreEntry matchedEntry = null;

        // Try name lookup first (case-insensitive)
        matchedEntry = plugin.getLoreManager().getLoreEntryByNameSync(idInput);

        // Try to match by full UUID
        if (matchedEntry == null) {
            try {
                UUID id = UUID.fromString(idInput);
                matchedEntry = plugin.getLoreManager().getLoreEntrySync(id);
            } catch (IllegalArgumentException ignored) {}
        }

        // Try to match by short UUID (first 8 chars)
        if (matchedEntry == null && idInput.length() >= 8) {
            String shortUuidPart = idInput.substring(0, 8);
            for (LoreEntry entry : plugin.getLoreManager().findLoreEntriesSync(shortUuidPart)) {
                if (!entry.isApproved()) {
                    String entryShortId = entry.getId().toString().substring(0, 8);
                    if (entryShortId.equalsIgnoreCase(shortUuidPart)) {
                        matchedEntry = entry;
                        break;
                    }
                }
            }
        }

        if (matchedEntry == null) {
            sender.sendMessage(ChatColor.RED + "✖ No lore entry found matching: " + idInput);
            sender.sendMessage(ChatColor.GRAY + "   Use /lore search or /lore list to find entries");
            return true;
        }

        return processApproval(sender, matchedEntry.getUUID());
    }

    /**
     * Process the approval of a lore entry
     *
     * @param sender The command sender
     * @param id The UUID of the lore entry to approve
     * @return true if the command was processed
     */
    private boolean processApproval(CommandSender sender, UUID id) {
        LoreEntry entry = plugin.getLoreManager().getLoreEntrySync(id);

        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "✖ No lore entry found with ID: " + id);
            return true;
        }

        if (entry.isApproved()) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ This lore entry is already approved.");
            return true;
        }

        boolean success = plugin.getLoreManager().approveLoreEntrySync(id);

        if (success) {
            sender.sendMessage(ChatColor.GREEN + "✓ Lore entry approved successfully!");

            // Log the approval
            logger.info("Lore entry " + id + " (" + entry.getName() + ") approved by " + sender.getName());
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Failed to approve lore entry. Please check console for errors.");
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Approves a lore entry for public viewing";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Suggest only unapproved entry names
            String partial = args[0].toLowerCase();

            return plugin.getLoreManager().getAllLoreEntriesSync().stream()
                    .filter(entry -> !entry.isApproved())
                    .map(LoreEntry::getName)
                    .filter(name -> name != null && name.toLowerCase().startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(5)
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // Second arg: approve/reject action
            String partial = args[1].toLowerCase();
            List<String> actions = Arrays.asList("approve", "reject");
            return actions.stream()
                    .filter(action -> action.startsWith(partial))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}

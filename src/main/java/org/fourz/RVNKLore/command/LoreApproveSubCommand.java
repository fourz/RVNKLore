package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore approve <name|id> [reject]");
            return true;
        }

        // Detect optional action suffix: last arg may be "approve" or "reject"
        boolean reject = false;
        String[] nameArgs = args;
        String lastArg = args[args.length - 1].toLowerCase();
        if ("reject".equals(lastArg)) {
            reject = true;
            nameArgs = Arrays.copyOf(args, args.length - 1);
        } else if ("approve".equals(lastArg) && args.length > 1) {
            nameArgs = Arrays.copyOf(args, args.length - 1);
        }

        if (nameArgs.length == 0) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore approve <name|id> [reject]");
            return true;
        }

        String idInput = String.join(" ", nameArgs);
        LoreEntry matchedEntry = null;

        // Try name lookup first (case-insensitive, cache)
        matchedEntry = plugin.getLoreManager().getLoreEntryByNameSync(idInput);

        // Cache miss — fall back to full DB scan (covers unapproved entries not in cache)
        if (matchedEntry == null) {
            matchedEntry = plugin.getDatabaseManager().getAllLoreEntries().stream()
                    .filter(e -> e.getName() != null && e.getName().equalsIgnoreCase(idInput))
                    .findFirst().orElse(null);
        }

        // Try to match by full UUID
        if (matchedEntry == null) {
            try {
                UUID id = UUID.fromString(idInput);
                matchedEntry = plugin.getLoreManager().getLoreEntrySync(id);
            } catch (IllegalArgumentException ignored) {}
        }

        // Try to match by short UUID (first 8 chars) — DB scan
        if (matchedEntry == null && idInput.length() >= 8) {
            String shortUuidPart = idInput.substring(0, 8).toLowerCase();
            matchedEntry = plugin.getDatabaseManager().getAllLoreEntries().stream()
                    .filter(e -> !e.isApproved() && e.getId() != null &&
                                 e.getId().toLowerCase().startsWith(shortUuidPart))
                    .findFirst().orElse(null);
        }

        if (matchedEntry == null) {
            sender.sendMessage(ChatColor.RED + "✖ No lore entry found matching: " + idInput);
            sender.sendMessage(ChatColor.GRAY + "   Use /lore search or /lore list to find entries");
            return true;
        }

        if (reject) {
            return processRejection(sender, matchedEntry);
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
            logger.info("Lore entry '" + entry.getName() + "' (" + entry.getType() + ") approved by " + sender.getName() + " [id=" + id + "]");
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Failed to approve lore entry. Please check console for errors.");
        }

        return true;
    }

    private boolean processRejection(CommandSender sender, LoreEntry entry) {
        if (entry.isApproved()) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ This entry is already approved and cannot be rejected.");
            return true;
        }

        boolean success = plugin.getLoreManager().rejectLoreEntrySync(entry.getUUID());
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "✓ Lore entry rejected: " + entry.getName());
            logger.info("Lore entry '" + entry.getName() + "' (" + entry.getType() + ") rejected by " + sender.getName());

            // Notify submitter if online
            String submittedBy = entry.getSubmittedBy();
            if (submittedBy != null) {
                Player submitter = Bukkit.getPlayerExact(submittedBy);
                if (submitter != null && submitter.isOnline()) {
                    submitter.sendMessage(ChatColor.YELLOW + "⚠ Your lore submission '" + entry.getName() + "' was not approved.");
                }
            }
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Failed to reject lore entry. Check console for errors.");
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "Approve or reject a lore entry";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            // Suggest unapproved entry names + "reject" keyword
            List<String> completions = plugin.getLoreManager().getAllLoreEntriesSync().stream()
                    .filter(entry -> !entry.isApproved())
                    .map(LoreEntry::getName)
                    .filter(name -> name != null && name.toLowerCase().startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(5)
                    .collect(Collectors.toList());
            return completions;
        }

        if (args.length >= 2) {
            // Last arg may be the "reject" action suffix
            String partial = args[args.length - 1].toLowerCase();
            if ("reject".startsWith(partial)) {
                return List.of("reject");
            }
        }

        return new ArrayList<>();
    }
}

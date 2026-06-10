package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Subcommand for rejecting pending lore entries with an optional reason.
 *
 * Usage: /lore reject <name|uuid> [--reason <text>]
 *
 * Sets approval_status = 'REJECTED' and stores the reason in rejection_reason.
 * Only affects entries in PENDING state (cannot reject already-approved entries).
 */
public class LoreRejectSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreRejectSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreRejectSubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!plugin.getConfigManager().isApprovalWorkflowEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ Approval workflow is disabled — all entries are auto-approved.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore reject <name|uuid> [--reason <text>]");
            return true;
        }

        List<String> argList = new ArrayList<>(Arrays.asList(args));
        String reason = extractReason(argList);
        String nameInput = String.join(" ", argList).trim();

        if (nameInput.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore reject <name|uuid> [--reason <text>]");
            return true;
        }

        LoreEntry entry = findEntry(nameInput);

        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "✖ No lore entry found matching: " + nameInput);
            sender.sendMessage(ChatColor.GRAY + "   Use /lore list --pending to see pending entries.");
            return true;
        }

        if (entry.isApproved()) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ This entry is already approved and cannot be rejected.");
            return true;
        }

        String entryName = entry.getName();
        boolean success = plugin.getLoreManager().rejectLoreEntrySync(entry.getUUID(), reason);

        if (success) {
            sender.sendMessage(ChatColor.GREEN + "✓ Lore entry rejected: " + entryName);
            if (reason != null) {
                sender.sendMessage(ChatColor.GRAY + "   Reason: " + reason);
            }
            logger.info("Lore entry '" + entryName + "' [id=" + entry.getId() + "] rejected by "
                    + sender.getName() + (reason != null ? " reason=" + reason : ""));
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Failed to reject lore entry. Check console for errors.");
        }

        return true;
    }

    private LoreEntry findEntry(String nameInput) {
        LoreEntry entry = plugin.getLoreManager().getLoreEntryByNameSync(nameInput);

        if (entry == null) {
            entry = plugin.getDatabaseManager().getAllLoreEntries().stream()
                    .filter(e -> e.getName() != null && e.getName().equalsIgnoreCase(nameInput))
                    .findFirst().orElse(null);
        }

        if (entry == null) {
            try {
                UUID id = UUID.fromString(nameInput);
                entry = plugin.getLoreManager().getLoreEntrySync(id);
            } catch (IllegalArgumentException ignored) {}
        }

        if (entry == null && nameInput.length() >= 8) {
            String prefix = nameInput.substring(0, 8).toLowerCase();
            entry = plugin.getDatabaseManager().getAllLoreEntries().stream()
                    .filter(e -> !e.isApproved() && e.getId() != null
                            && e.getId().toLowerCase().startsWith(prefix))
                    .findFirst().orElse(null);
        }

        return entry;
    }

    /** Extract --reason <value> from arg list, removing both flag and value tokens. */
    private String extractReason(List<String> args) {
        int idx = args.indexOf("--reason");
        if (idx < 0) return null;
        args.remove(idx);
        if (idx >= args.size()) return null;
        // Collect all remaining tokens after --reason as the reason text
        List<String> reasonParts = new ArrayList<>(args.subList(idx, args.size()));
        for (int i = args.size() - 1; i >= idx; i--) {
            args.remove(i);
        }
        return String.join(" ", reasonParts);
    }

    @Override
    public String getDescription() {
        return "Reject a pending lore entry (--reason <text> optional)";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return LoreCommandUtil.isAdmin(sender);
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return plugin.getLoreManager().getAllLoreEntriesSync().stream()
                    .filter(e -> "PENDING".equalsIgnoreCase(e.getApprovalStatus()))
                    .map(LoreEntry::getName)
                    .filter(name -> name != null && name.toLowerCase().startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(5)
                    .collect(Collectors.toList());
        }

        String last = args[args.length - 1];
        if ("--reason".startsWith(last)) {
            List<String> flags = new ArrayList<>();
            flags.add("--reason");
            return flags;
        }

        return new ArrayList<>();
    }
}

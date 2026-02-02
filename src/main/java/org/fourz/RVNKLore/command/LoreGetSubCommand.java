package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.LoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Subcommand for getting lore entries by ID
 * Usage: /lore get <id>
 *
 * Supports both full UUIDs and short ID prefixes (as shown in /lore list).
 */
public class LoreGetSubCommand implements SubCommand {
    private final RVNKLore plugin;

    public LoreGetSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore get <id>");
            return true;
        }

        String idStr = args[0].toLowerCase();

        // First, try exact ID match
        Optional<LoreEntry> exactMatch = plugin.getLoreManager().getLoreById(idStr);
        LoreEntry entry = exactMatch.orElse(null);

        // If no exact match, try prefix search
        if (entry == null) {
            List<LoreEntry> matches = plugin.getLoreManager().findLoreEntriesSync(idStr);

            // Filter to only entries whose ID starts with the search term (not name matches)
            List<LoreEntry> idMatches = new ArrayList<>();
            for (LoreEntry e : matches) {
                if (e.getId().toLowerCase().startsWith(idStr)) {
                    idMatches.add(e);
                }
            }

            if (idMatches.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "✖ No lore entry found with ID starting with: " + idStr);
                return true;
            } else if (idMatches.size() == 1) {
                entry = idMatches.get(0);
            } else {
                // Multiple matches - show them to the user
                sender.sendMessage(ChatColor.YELLOW + "⚠ Multiple entries match '" + idStr + "'. Please be more specific:");
                for (LoreEntry e : idMatches) {
                    String shortId = e.getId().length() >= 8 ? e.getId().substring(0, 8) : e.getId();
                    sender.sendMessage(ChatColor.GRAY + "  - " + ChatColor.YELLOW + e.getName() +
                            ChatColor.GRAY + " (" + shortId + ")");
                }
                return true;
            }
        }

        // If the entry is not approved and the sender is not an admin, deny access
        if (!entry.isApproved() && !sender.hasPermission("rvnklore.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "⚠ That lore entry has not been approved yet.");
            return true;
        }

        // Display the lore to the player
        if (sender instanceof Player) {
            Player player = (Player) sender;
            LoreHandler handler = plugin.getLoreManager().getHandler(entry.getType());

            if (handler != null) {
                handler.displayLore(entry, player);
            } else {
                // Fallback display if no handler exists
                player.sendMessage(ChatColor.GOLD + "=== " + entry.getName() + " ===");
                player.sendMessage(ChatColor.GRAY + "   Type: " + ChatColor.YELLOW + entry.getType());
                player.sendMessage(ChatColor.WHITE + entry.getDescription());
            }

            // If requested as an item and the player has permission
            if (args.length > 1 && args[1].equalsIgnoreCase("item") &&
                    player.hasPermission("rvnklore.command.getitem")) {

                if (handler != null) {
                    player.getInventory().addItem(handler.createLoreItem(entry));
                    player.sendMessage(ChatColor.GREEN + "✓ Added lore item to your inventory.");
                } else {
                    player.sendMessage(ChatColor.RED + "✖ Cannot create item for this lore type.");
                }
            }
        } else {
            // Console display
            sender.sendMessage("=== " + entry.getName() + " ===");
            sender.sendMessage("Type: " + entry.getType());
            sender.sendMessage("Description: " + entry.getDescription());
            sender.sendMessage("Submitted by: " + entry.getSubmittedBy());
            sender.sendMessage("Approved: " + entry.isApproved());
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Gets a lore entry by ID (full or partial)";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.command.get") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        // No tab completions for UUIDs
        return new ArrayList<>();
    }
}

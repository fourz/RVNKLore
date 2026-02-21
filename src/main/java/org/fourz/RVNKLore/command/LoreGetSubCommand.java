package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.LoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.search.LoreSearchService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Subcommand for getting lore entries by name or ID.
 * Usage: /lore get <name|id>
 *
 * Supports lore entry names (case-insensitive), full UUIDs, and short ID prefixes.
 */
public class LoreGetSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final TabCompletionUtil tabCompletionUtil;

    public LoreGetSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.tabCompletionUtil = new TabCompletionUtil(new LoreSearchService(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore get <name|id>");
            return true;
        }

        String input = args[0];

        // If multiple args, join them as a name (supports "Fort Ravenkeep" etc.)
        if (args.length > 1 && !args[1].equalsIgnoreCase("item")) {
            StringBuilder nameBuilder = new StringBuilder(args[0]);
            for (int i = 1; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("item")) break;
                nameBuilder.append(" ").append(args[i]);
            }
            input = nameBuilder.toString();
        }

        // Try name lookup first (case-insensitive)
        LoreEntry entry = plugin.getLoreManager().getLoreEntryByNameSync(input);

        // Fallback to exact ID match
        if (entry == null) {
            Optional<LoreEntry> exactMatch = plugin.getLoreManager().getLoreById(input.toLowerCase());
            entry = exactMatch.orElse(null);
        }

        // Fallback to ID prefix search
        if (entry == null) {
            String idStr = input.toLowerCase();
            List<LoreEntry> matches = plugin.getLoreManager().findLoreEntriesSync(idStr);

            // Filter to only entries whose ID starts with the search term
            List<LoreEntry> idMatches = new ArrayList<>();
            for (LoreEntry e : matches) {
                if (e.getId().toLowerCase().startsWith(idStr)) {
                    idMatches.add(e);
                }
            }

            if (idMatches.size() == 1) {
                entry = idMatches.get(0);
            } else if (idMatches.size() > 1) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ Multiple entries match '" + input + "'. Please be more specific:");
                for (LoreEntry e : idMatches) {
                    sender.sendMessage(ChatColor.GRAY + "  - " + ChatColor.YELLOW + e.getName() +
                            ChatColor.GRAY + " (" + e.getType() + ")");
                }
                return true;
            }
        }

        if (entry == null) {
            sender.sendMessage(ChatColor.RED + "✖ Lore entry not found: " + input);
            sender.sendMessage(ChatColor.GRAY + "   Use /lore search or /lore list to find entries");
            return true;
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
            boolean hasItemArg = false;
            for (String arg : args) {
                if (arg.equalsIgnoreCase("item")) {
                    hasItemArg = true;
                    break;
                }
            }
            if (hasItemArg && player.hasPermission("rvnklore.command.getitem")) {
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
        return "Gets a lore entry by name or ID";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.command.get") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return tabCompletionUtil.completeLoreEntryNames(args[0]);
        }
        return new ArrayList<>();
    }
}

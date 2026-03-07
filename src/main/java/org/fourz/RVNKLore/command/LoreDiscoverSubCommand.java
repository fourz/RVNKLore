package org.fourz.RVNKLore.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.discovery.DiscoveryManager;
import org.fourz.RVNKLore.discovery.DiscoveryTriggerType;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Subcommand for manually granting or viewing lore discoveries.
 * Usage: /lore discover <player> <entry_id|entry_name>
 *        /lore discover list [player]
 */
public class LoreDiscoverSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreDiscoverSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreDiscoverSubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        DiscoveryManager discoveryManager = plugin.getDiscoveryManager();
        if (discoveryManager == null || !discoveryManager.isInitialized()) {
            sender.sendMessage(ChatColor.RED + "✖ Discovery system is not available.");
            return true;
        }

        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        // /lore discover list [player]
        if ("list".equalsIgnoreCase(args[0])) {
            return handleList(sender, args, discoveryManager);
        }

        // /lore discover <player> <entry_ref>
        if (args.length < 2) {
            showUsage(sender);
            return true;
        }

        String playerName = args[0];
        String entryRef = joinArgs(args, 1);

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "✖ Player not found or not online: " + playerName);
            return true;
        }

        Optional<LoreEntry> optEntry = findLoreEntry(entryRef);
        if (optEntry.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "✖ Lore entry not found: " + entryRef);
            return true;
        }

        LoreEntry entry = optEntry.get();
        sender.sendMessage(ChatColor.YELLOW + "⚙ Granting discovery '" + entry.getDisplayName() + "' to " + target.getName() + "...");

        discoveryManager.triggerDiscovery(
            target, entry,
            DiscoveryTriggerType.COMMAND,
            target.getLocation()
        ).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    sender.sendMessage(ChatColor.GREEN + "✓ " + target.getName() + " discovered: " + entry.getDisplayName());
                    logger.info("Discovery granted via command: " + entry.getDisplayName() + " -> " + target.getName() + " by " + sender.getName());
                } else {
                    sender.sendMessage(ChatColor.GRAY + "⚠ " + target.getName() + " has already discovered: " + entry.getDisplayName());
                }
            });
        });

        return true;
    }

    private boolean handleList(CommandSender sender, String[] args, DiscoveryManager discoveryManager) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "✖ Player not found or not online: " + args[1]);
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "✖ Specify a player name: /lore discover list <player>");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "⚙ Loading discoveries for " + target.getName() + "...");

        discoveryManager.getPlayerStats(target.getUniqueId()).thenAccept(stats -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.GOLD + "===== Discoveries: " + target.getName() + " =====");
                sender.sendMessage(ChatColor.GRAY + "Discovered: " + ChatColor.WHITE + stats.getDiscovered() + "/" + stats.getTotal()
                    + ChatColor.GRAY + " (" + String.format("%.1f", stats.getCompletionPercentage()) + "%)");
                sender.sendMessage(ChatColor.GRAY + "First discoveries: " + ChatColor.YELLOW + stats.getFirstDiscoveries());
            });
        });

        return true;
    }

    private Optional<LoreEntry> findLoreEntry(String reference) {
        // Try by UUID
        try {
            UUID uuid = UUID.fromString(reference);
            return plugin.getLoreManager().getLoreEntry(uuid).join();
        } catch (IllegalArgumentException ignored) {}

        // Try by partial ID
        Optional<LoreEntry> byId = plugin.getLoreManager().getLoreById(reference);
        if (byId.isPresent()) return byId;

        // Try by name
        try {
            return plugin.getLoreManager().getLoreEntryByName(reference).join();
        } catch (Exception e) {
            logger.debug("Could not find lore entry: " + reference);
        }

        return Optional.empty();
    }

    private String joinArgs(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "▶ Usage:");
        sender.sendMessage(ChatColor.RED + "  /lore discover <player> <entry_id|name>" + ChatColor.GRAY + " - Grant a discovery");
        sender.sendMessage(ChatColor.RED + "  /lore discover list [player]" + ChatColor.GRAY + " - View discovery stats");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            // Suggest "list" and online player names
            if ("list".startsWith(partial)) completions.add("list");
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            if ("list".equalsIgnoreCase(args[0])) {
                // Player names for list
                String partial = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(partial)) {
                        completions.add(player.getName());
                    }
                }
            } else {
                // Entry IDs (short form)
                String partial = args[1].toLowerCase();
                for (LoreEntry entry : plugin.getLoreManager().getAllLoreEntriesSync()) {
                    String shortId = entry.getId().substring(0, Math.min(8, entry.getId().length()));
                    if (shortId.startsWith(partial)) {
                        completions.add(shortId);
                    }
                    if (entry.getName().toLowerCase().startsWith(partial)) {
                        completions.add(entry.getName());
                    }
                }
            }
        }

        return completions;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.discover.grant") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public String getDescription() {
        return "Grant or view lore discoveries";
    }
}

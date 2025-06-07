package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;

import java.util.*;

/**
 * Parent subcommand for item-related operations.
 * Handles routing to child commands: give, info
 */
public class LoreItemSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, SubCommand> subCommands;
    
    public LoreItemSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemSubCommand");
        this.subCommands = new HashMap<>();
        
        // Register child commands with correct constructor
        subCommands.put("give", new LoreItemGiveSubCommand(plugin, plugin.getItemManager()));
        subCommands.put("info", new LoreItemInfoSubCommand(plugin, plugin.getItemManager()));
        
        logger.info("LoreItemSubCommand initialized with " + subCommands.size() + " child commands");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        // Special handling for /lore item info <short uuid>|<full uuid>
        if ("info".equals(subCommandName) && args.length == 2) {
            String idArg = args[1];
            // Try to match by short or full UUID
            org.fourz.RVNKLore.lore.LoreEntry matched = null;
            for (org.fourz.RVNKLore.lore.LoreEntry entry : plugin.getLoreManager().getAllLoreEntries()) {
                String uuid = entry.getId().toString();
                if (uuid.equalsIgnoreCase(idArg) || uuid.substring(0, 8).equalsIgnoreCase(idArg)) {
                    matched = entry;
                    break;
                }
            }
            if (matched != null) {
                sender.sendMessage(org.bukkit.ChatColor.GOLD + "===== Item Info: " + matched.getName() + " =====");
                sender.sendMessage(org.bukkit.ChatColor.YELLOW + "Type: " + matched.getType());
                sender.sendMessage(org.bukkit.ChatColor.YELLOW + "ID: " + matched.getId());
                if (matched.getDescription() != null && !matched.getDescription().isEmpty()) {
                    sender.sendMessage(org.bukkit.ChatColor.YELLOW + "Description: " + matched.getDescription());
                }
                if (matched.getSubmittedBy() != null) {
                    sender.sendMessage(org.bukkit.ChatColor.YELLOW + "Submitted by: " + matched.getSubmittedBy());
                }
                if (matched.getMetadata() != null && !matched.getMetadata().isEmpty()) {
                    sender.sendMessage(org.bukkit.ChatColor.YELLOW + "Metadata:");
                    matched.getMetadata().forEach((k, v) ->
                        sender.sendMessage(org.bukkit.ChatColor.GRAY + "  " + k + ": " + v)
                    );
                }
                return true;
            } else {
                sender.sendMessage(org.bukkit.ChatColor.RED + "✖ No lore entry found with ID: " + idArg);
                return true;
            }
        }

        if (subCommand == null) {
            sender.sendMessage(ChatColor.RED + "✖ Unknown item command: " + subCommandName);
            showUsage(sender);
            return true;
        }

        // Check permissions
        if (!subCommand.hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "✖ You don't have permission to use this command.");
            return true;
        }

        // Execute with remaining arguments
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        try {
            return subCommand.execute(sender, subArgs);
        } catch (Exception e) {
            String errorId = java.util.UUID.randomUUID().toString();
            logger.error("Error ID: " + errorId + " - Error executing item command: " + subCommandName, e);
            sender.sendMessage(ChatColor.RED + "✖ An error occurred (ID: " + errorId + "). Please report this to an administrator.");
            return false;
        }
    }

    /**
     * Display usage information for the item command.
     */
    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "▶ Usage: /lore item <subcommand>");
        sender.sendMessage(ChatColor.GRAY + "   Available subcommands:");
        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            sender.sendMessage(ChatColor.GRAY + "   • " + ChatColor.YELLOW + entry.getKey() + 
                             ChatColor.GRAY + " - " + entry.getValue().getDescription());
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String cmd : subCommands.keySet()) {
                if (subCommands.get(cmd).hasPermission(sender)) {
                    completions.add(cmd);
                }
            }
            return completions;
        } else if (args.length == 2 && "info".equalsIgnoreCase(args[0])) {
            // Tab completion for /lore item info <short uuid>
            List<String> completions = new ArrayList<>();
            for (org.fourz.RVNKLore.lore.LoreEntry entry : plugin.getLoreManager().getAllLoreEntries()) {
                String shortId = entry.getId().toString().substring(0, 8);
                completions.add(shortId);
                completions.add(entry.getId().toString());
            }
            return completions;
        } else if (args.length > 1) {
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);
            if (subCommand != null && subCommand.hasPermission(sender)) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCommand.getTabCompletions(sender, subArgs);
            }
        }
        return new ArrayList<>();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        // Allow access if user has permission for any child command
        return subCommands.values().stream().anyMatch(cmd -> cmd.hasPermission(sender));
    }

    @Override
    public String getDescription() {
        return "Manage lore items (give, info)";
    }
}

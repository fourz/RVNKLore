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
        
        // Special handling for /lore item list command
        if ("list".equals(subCommandName)) {
            return handleListCommand(sender, args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0]);
        }
        
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
                return displayLoreEntry(sender, matched);
            } else {
                sender.sendMessage(org.bukkit.ChatColor.RED + "✖ No lore entry found with ID: " + idArg);
                return true;
            }
        }

        SubCommand subCommand = subCommands.get(subCommandName);
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
     * Handles the "list" subcommand to display items sorted by creation date
     *
     * @param sender The command sender
     * @param args Additional arguments for the list command
     * @return true if the command was processed
     */
    private boolean handleListCommand(CommandSender sender, String[] args) {
        if (plugin.getItemManager() == null) {
            sender.sendMessage(ChatColor.RED + "✖ Item system is not available. Please try again later.");
            logger.error("ItemManager is null when trying to list items", null);
            return true;
        }
        
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "✖ Invalid page number: " + args[0]);
                return true;
            }
        }
        
        // Use DisplayFactory for consistent output formatting
        return org.fourz.RVNKLore.command.output.DisplayFactory.displayItemList(
            sender, 
            plugin.getItemManager(),
            page,
            true // Sort by newest first
        );
    }
    
    /**
     * Display a lore entry using the DisplayFactory
     *
     * @param sender The command sender
     * @param entry The lore entry to display
     * @return true if the command was processed
     */
    private boolean displayLoreEntry(CommandSender sender, org.fourz.RVNKLore.lore.LoreEntry entry) {
        return org.fourz.RVNKLore.command.output.DisplayFactory.displayLoreEntry(sender, entry);
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
            
            // Add "list" as a subcommand option
            completions.add("list");
            
            // Add other registered subcommands
            for (String cmd : subCommands.keySet()) {
                if (subCommands.get(cmd).hasPermission(sender)) {
                    completions.add(cmd);
                }
            }
            return completions;
        } else if (args.length == 2) {
            // Handle tab completions for the second argument
            String subCommandName = args[0].toLowerCase();
            
            if ("list".equals(subCommandName)) {
                // Suggest page numbers for list command
                return List.of("1", "2", "3");
            } else if ("info".equals(subCommandName)) {
                // Tab completion for /lore item info <short uuid>
                List<String> completions = new ArrayList<>();
                for (org.fourz.RVNKLore.lore.LoreEntry entry : plugin.getLoreManager().getAllLoreEntries()) {
                    String shortId = entry.getId().toString().substring(0, 8);
                    completions.add(shortId);
                }
                return completions;
            } else if (subCommands.containsKey(subCommandName)) {
                // Delegate to the appropriate subcommand
                SubCommand subCommand = subCommands.get(subCommandName);
                if (subCommand.hasPermission(sender)) {
                    return subCommand.getTabCompletions(sender, Arrays.copyOfRange(args, 1, args.length));
                }
            }
        } else if (args.length > 2) {
            String subCommandName = args[0].toLowerCase();
            if (!subCommandName.equals("list") && !subCommandName.equals("info")) {
                SubCommand subCommand = subCommands.get(subCommandName);
                if (subCommand != null && subCommand.hasPermission(sender)) {
                    String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                    return subCommand.getTabCompletions(sender, subArgs);
                }
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

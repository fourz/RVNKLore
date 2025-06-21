package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.subcommand.SubCommand;

import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.command.output.DisplayFactory;

import java.util.*;

/**
 * Parent subcommand for item-related operations.
 * Handles routing to child commands: give, info, list
 */
public class LoreItemSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, SubCommand> subCommands;
    
    public LoreItemSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemSubCommand");
        this.subCommands = new HashMap<>();
        
        // Register child commands with simplified constructor
        subCommands.put("give", new LoreItemGiveSubCommand(plugin));
        subCommands.put("info", new LoreItemInfoSubCommand(plugin));
        subCommands.put("list", new LoreItemListSubCommand(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();

        // Remove special handling for /lore item list        // Special handling for /lore item info list
        if ("info".equals(subCommandName) && args.length > 1 && "list".equalsIgnoreCase(args[1])) {
            // Delegate to LoreItemInfoSubCommand for /lore item info list
            return new LoreItemInfoSubCommand(plugin)
                .execute(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        SubCommand subCommand = subCommands.get(subCommandName);
        if (subCommand != null) {
            if (!subCommand.hasPermission(sender)) {
                sender.sendMessage(ChatColor.RED + "✖ You don't have permission to use this command.");
                return true;
            }
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            try {
                return subCommand.execute(sender, subArgs);
            } catch (Exception e) {
                String errorId = java.util.UUID.randomUUID().toString();
                logger.error("Error ID: " + errorId + " - Error executing item command: " + subCommandName, e);
                sender.sendMessage(ChatColor.RED + "✖ An error occurred (ID: " + errorId + "). Please report this to an administrator.");
                return false;
            }
        }        // Special handling for /lore item info <short uuid>|<full uuid>
        if ("info".equals(subCommandName) && args.length == 2) {
            // Extracted logic: delegate to LoreItemInfoSubCommand for info lookup
            return new LoreItemInfoSubCommand(plugin)
                .execute(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        sender.sendMessage(ChatColor.RED + "✖ Unknown item command: " + subCommandName);
        showUsage(sender);
        return true;
    }

    private void showUsage(CommandSender sender) {
        List<String> commands = new ArrayList<>();
        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            if (entry.getValue().hasPermission(sender)) {
                commands.add(ChatColor.YELLOW + entry.getKey() + ChatColor.GRAY + " - " + entry.getValue().getDescription());
            }
        }

        DisplayFactory.displayPaginatedList(
            sender,
            "Lore Item Commands",
            commands,
            1,
            10,
            cmd -> ChatColor.GRAY + "• " + cmd
        );
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
                return List.of("1", "2", "3");            } else if ("info".equals(subCommandName)) {
                // Tab completion for /lore item info <short uuid>
                // Delegate to the proper subcommand
                SubCommand infoSubCommand = subCommands.get("info");
                if (infoSubCommand != null && infoSubCommand.hasPermission(sender)) {
                    return infoSubCommand.getTabCompletions(sender, Arrays.copyOfRange(args, 1, args.length));
                }
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

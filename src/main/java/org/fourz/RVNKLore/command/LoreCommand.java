package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.subcommand.SubCommand;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.DatabaseManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main command handler for the /lore command.
 * Implements centralized command handling with async operation support.
 * Delegates to appropriate subcommands based on arguments.
 */
public class LoreCommand implements CommandExecutor, TabCompleter {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, SubCommand> subCommands = new HashMap<>();
    private final DatabaseManager databaseManager;

    public LoreCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreCommand");
        this.databaseManager = plugin.getDatabaseManager();
        registerSubCommands();
    }

    /**
     * Registers all subcommands
     */
    private void registerSubCommands() {
        logger.debug("Registering subcommands...");

        // Register real subcommands
        subCommands.put("add", new LoreAddSubCommand(plugin));
        subCommands.put("list", new LoreListSubCommand(plugin));
        subCommands.put("approve", new LoreApproveSubCommand(plugin, true));  // For approval
        subCommands.put("reject", new LoreApproveSubCommand(plugin, false));  // For rejection

        // Register placeholder subcommands for others
        registerPlaceholderCommand("edit", "rvnklore.lore.edit", "Edit a lore entry");
        registerPlaceholderCommand("delete", "rvnklore.lore.delete", "Delete a lore entry");
        registerPlaceholderCommand("info", "rvnklore.lore.info", "View lore entry information");
        registerPlaceholderCommand("search", "rvnklore.lore.search", "Search lore entries");
        registerPlaceholderCommand("nearby", "rvnklore.lore.nearby", "Find nearby lore entries");
        registerPlaceholderCommand("submit", "rvnklore.lore.submit", "Submit a lore entry");
        registerPlaceholderCommand("item", "rvnklore.lore.item", "Item management commands");

        // Add aliases
        registerAlias("create", "add");
        registerAlias("new", "add");
        registerAlias("update", "edit");
        registerAlias("modify", "edit");
        registerAlias("remove", "delete");
        registerAlias("view", "info");
        registerAlias("get", "info");
        registerAlias("find", "search");

        logger.debug("Registered " + subCommands.size() + " subcommands successfully");
    }

    /**
     * Registers a placeholder command that shows "coming soon" message.
     */
    private void registerPlaceholderCommand(String name, String permission, String description) {
        subCommands.put(name.toLowerCase(), new SubCommand() {
            @Override
            public boolean hasPermission(CommandSender sender) {
                return sender.hasPermission(permission);
            }
            
            @Override
            public boolean execute(CommandSender sender, String[] args) {
                sender.sendMessage("§e⚠ The '" + name + "' command is being updated to use the new system");
                sender.sendMessage("§7   This feature will be available soon");
                return true;
            }
            
            @Override
            public String getDescription() {
                return description;
            }
        });
    }

    /**
     * Registers a command alias that points to another command.
     */
    private void registerAlias(String alias, String targetCommand) {
        SubCommand target = subCommands.get(targetCommand.toLowerCase());
        if (target != null) {
            subCommands.put(alias.toLowerCase(), target);
        } else {
            logger.warning("Cannot register alias '" + alias + "' for non-existent command '" + targetCommand + "'");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommandName);
            showHelp(sender);
            return true;
        }

        if (!subCommand.hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        // Remove the subcommand name from args
        String[] subCommandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subCommandArgs, 0, args.length - 1);

        logger.debug("Executing subcommand: " + subCommandName);
        return subCommand.execute(sender, subCommandArgs);
    }

    /**
     * Shows help information to the sender
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== RVNKLore Commands =====");
        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            if (entry.getValue().hasPermission(sender)) {
                String command = entry.getKey();
                String description = entry.getValue().getDescription();
                sender.sendMessage(ChatColor.YELLOW + "/lore " + command + 
                    ChatColor.WHITE + " - " + description);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - suggest available subcommands based on permissions
            String partial = args[0].toLowerCase();
            for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
                boolean hasPerm = entry.getValue().hasPermission(sender);
                // logger.debug("TabComplete: checking permission for subcommand '" + entry.getKey() + "' for sender '" + sender.getName() + "': " + hasPerm);
                if (hasPerm && entry.getKey().toLowerCase().startsWith(partial)) {
                    completions.add(entry.getKey());
                }
            }
        } else if (args.length > 1) {
            // Delegate to subcommand for further tab completions
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);

            if (subCommand != null) {
                boolean hasPerm = subCommand.hasPermission(sender);
                // logger.debug("TabComplete: checking permission for subcommand '" + subCommandName + "' for sender '" + sender.getName() + "': " + hasPerm);
                if (hasPerm) {
                    // Remove the first argument and pass the rest to the subcommand
                    String[] subArgs = new String[args.length - 1];
                    System.arraycopy(args, 1, subArgs, 0, args.length - 1);

                    List<String> subCompletions = subCommand.getTabCompletions(sender, subArgs);
                    if (subCompletions != null) {
                        completions.addAll(subCompletions);
                    }
                }
            } else {
                // logger.debug("TabComplete: subcommand '" + subCommandName + "' not found for sender '" + sender.getName() + "'");
            }
        }

        return completions;
    }
}

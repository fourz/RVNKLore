package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.cosmetic.CosmeticCollectionSubCommand;
import org.fourz.RVNKLore.command.cosmetic.CosmeticGiveSubCommand;
import org.fourz.RVNKLore.debug.LogManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main command handler for the /lore command.
 * Dispatches to appropriate subcommands based on arguments.
 */
public class LoreCommand implements CommandExecutor, TabCompleter {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public LoreCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreCommand");
        registerSubCommands();
    }

    /**
     * Registers all subcommands
     */
    private void registerSubCommands() {
        logger.debug("Registering subcommands...");
          // Register all subcommands at once to reduce debug log spam
        Map<String, SubCommand> commands = new HashMap<>();
        commands.put("add", new LoreAddSubCommand(plugin));
        commands.put("get", new LoreGetSubCommand(plugin));
        commands.put("list", new LoreListSubCommand(plugin));
        commands.put("approve", new LoreApproveSubCommand(plugin));
        commands.put("reload", new LoreReloadSubCommand(plugin));
        commands.put("export", new LoreExportSubCommand(plugin));
        commands.put("debug", new LoreDebugSubCommand(plugin));
        
        // Add cosmetic management commands using the new ItemManager-based API
        if (plugin.getItemManager() != null && plugin.getItemManager().getCosmeticManager() != null) {
            commands.put("collection", new CosmeticCollectionSubCommand(plugin.getItemManager().getCosmeticManager()));
            commands.put("give", new CosmeticGiveSubCommand(plugin, plugin.getItemManager().getCosmeticManager()));
        }
        
        // Add all commands to the subCommands map
        commands.forEach(this::registerSubCommand);
        
        logger.debug("Registered " + commands.size() + " subcommands successfully");
    }

    /**
     * Registers a subcommand
     * 
     * @param name The name of the subcommand
     * @param subCommand The subcommand implementation
     */
    private void registerSubCommand(String name, SubCommand subCommand) {
        subCommands.put(name.toLowerCase(), subCommand);
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
     * 
     * @param sender Command sender to show help to
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== RVNKLore Commands =====");
        
        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            if (entry.getValue().hasPermission(sender)) {
                sender.sendMessage(ChatColor.YELLOW + "/lore " + entry.getKey() + 
                                   ChatColor.WHITE + " - " + entry.getValue().getDescription());
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete subcommand names
            String partial = args[0].toLowerCase();
            for (String subCommand : subCommands.keySet()) {
                if (subCommands.get(subCommand).hasPermission(sender) && 
                    subCommand.startsWith(partial)) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length > 1) {
            // Pass to subcommand for completion
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);
            
            if (subCommand != null && subCommand.hasPermission(sender)) {
                String[] subCommandArgs = new String[args.length - 1];
                System.arraycopy(args, 1, subCommandArgs, 0, args.length - 1);
                
                List<String> subCommandCompletions = subCommand.getTabCompletions(sender, subCommandArgs);
                if (subCommandCompletions != null) {
                    completions.addAll(subCommandCompletions);
                }
            }
        }

        return completions;
    }
}

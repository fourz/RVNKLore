package org.fourz.RVNKLore.command.faction;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.SubCommand;
import org.fourz.RVNKLore.command.output.DisplayFactory;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Parent subcommand for faction management operations.
 * Routes to child commands: addterritory, refresh, addmember, removemember
 */
public class LoreFactionSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, SubCommand> subCommands;

    public LoreFactionSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreFactionSubCommand");
        this.subCommands = new HashMap<>();

        subCommands.put("addterritory", new LoreFactionAddTerritorySubCommand(plugin));
        subCommands.put("refresh", new LoreFactionRefreshSubCommand(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            sender.sendMessage(ChatColor.RED + "\u2716 Unknown faction command: " + subCommandName);
            showUsage(sender);
            return true;
        }

        if (!subCommand.hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "\u2716 You don't have permission to use this command.");
            return true;
        }

        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        try {
            return subCommand.execute(sender, subArgs);
        } catch (Exception e) {
            String errorId = java.util.UUID.randomUUID().toString();
            logger.error("Error ID: " + errorId + " - Error executing faction command: " + subCommandName, e);
            sender.sendMessage(ChatColor.RED + "\u2716 An error occurred (ID: " + errorId + "). Please report this to an administrator.");
            return false;
        }
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
            "Faction Commands",
            commands,
            1,
            10,
            cmd -> ChatColor.GRAY + "\u2022 " + cmd
        );
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();
            for (String cmd : subCommands.keySet()) {
                if (subCommands.get(cmd).hasPermission(sender) && cmd.startsWith(partial)) {
                    completions.add(cmd);
                }
            }
            return completions;
        } else if (args.length > 1) {
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);
            if (subCommand != null && subCommand.hasPermission(sender)) {
                return subCommand.getTabCompletions(sender, Arrays.copyOfRange(args, 1, args.length));
            }
        }
        return new ArrayList<>();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return subCommands.values().stream().anyMatch(cmd -> cmd.hasPermission(sender));
    }

    @Override
    public String getDescription() {
        return "Manage faction territories and members";
    }
}

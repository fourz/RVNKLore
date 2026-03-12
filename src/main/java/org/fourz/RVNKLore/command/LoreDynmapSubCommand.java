package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.output.DisplayFactory;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Parent subcommand for dynmap integration operations.
 * Handles routing to child commands: diff, import
 *
 * Usage:
 * - /lore dynmap diff [marker-set]
 * - /lore dynmap import [marker-set] [--all|<name>]
 */
public class LoreDynmapSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, SubCommand> subCommands;

    public LoreDynmapSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreDynmapSubCommand");

        this.subCommands = new LinkedHashMap<>();
        subCommands.put("diff", new LoreDynmapDiffSubCommand(plugin));
        subCommands.put("import", new LoreDynmapImportSubCommand(plugin));

        logger.debug("LoreDynmapSubCommand initialized with " + subCommands.size() + " child commands");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!plugin.isDynmapAvailable()) {
            sender.sendMessage(ChatColor.RED + "✖ Dynmap integration is not available.");
            return true;
        }

        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            sender.sendMessage(ChatColor.RED + "✖ Unknown dynmap command: " + subCommandName);
            showUsage(sender);
            return true;
        }

        if (!subCommand.hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "✖ You don't have permission to use this command.");
            return true;
        }

        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        try {
            return subCommand.execute(sender, subArgs);
        } catch (Exception e) {
            String errorId = UUID.randomUUID().toString().substring(0, 8);
            logger.error("Error ID: " + errorId + " - Error executing dynmap command: " + subCommandName, e);
            sender.sendMessage(ChatColor.RED + "✖ An error occurred (ID: " + errorId + ").");
            return false;
        }
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== Dynmap Integration =====");
        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            if (entry.getValue().hasPermission(sender)) {
                sender.sendMessage(ChatColor.YELLOW + "/lore dynmap " + entry.getKey()
                    + ChatColor.GRAY + " - " + entry.getValue().getDescription());
            }
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String cmd : subCommands.keySet()) {
                if (subCommands.get(cmd).hasPermission(sender) && cmd.startsWith(partial)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length > 1) {
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);
            if (subCommand != null && subCommand.hasPermission(sender)) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCommand.getTabCompletions(sender, subArgs);
            }
        }

        return completions;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return subCommands.values().stream().anyMatch(cmd -> cmd.hasPermission(sender));
    }

    @Override
    public String getDescription() {
        return "Dynmap marker integration (diff, import)";
    }
}

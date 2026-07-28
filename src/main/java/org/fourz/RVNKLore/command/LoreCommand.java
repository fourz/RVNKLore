package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main command handler for the /lore command.
 * Dispatches to appropriate subcommands based on arguments.
 */
public class LoreCommand implements CommandExecutor, TabCompleter {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

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
        // Ordered insertion: player-facing first, admin last
        registerSubCommand("browse", new LoreBrowseSubCommand(plugin));
        registerSubCommand("get", new LoreGetSubCommand(plugin));
        registerSubCommand("search", new LoreSearchSubCommand(plugin));
        registerSubCommand("list", new LoreListSubCommand(plugin));
        registerSubCommand("add", new LoreAddSubCommand(plugin));
        registerSubCommand("approve", new LoreApproveSubCommand(plugin));
        registerSubCommand("reject", new LoreRejectSubCommand(plugin));
        registerSubCommand("delete", new LoreDeleteSubCommand(plugin));
        registerSubCommand("edit", new LoreEditSubCommand(plugin));
        registerSubCommand("reload", new LoreReloadSubCommand(plugin));
        registerSubCommand("export", new LoreExportSubCommand(plugin));
        registerSubCommand("import", new LoreImportSubCommand(plugin));
        registerSubCommand("debug", new LoreDebugSubCommand(plugin));

        if (plugin.getDiscoveryManager() != null) {
            registerSubCommand("discover", new LoreDiscoverSubCommand(plugin));
        }

        if (plugin.getAchievementManager() != null) {
            registerSubCommand("achievement", new LoreAchievementSubCommand(plugin, plugin.getAchievementManager()));
        }

        if (plugin.getConfigManager().isCollectionsEnabled()
                && plugin.getLoreManager().getItemManager() != null
                && plugin.getLoreManager().getItemManager().getCosmeticItem() != null) {
            registerSubCommand("collection", new LoreCollectionSubCommand(plugin));
            registerSubCommand("item", new LoreItemSubCommand(plugin));
        } else if (!plugin.getConfigManager().isCollectionsEnabled()) {
            logger.debug("Feature disabled: collections — /lore collection and /lore item not registered");
        }

        registerSubCommand("book", new LoreBookSubCommand(plugin));
        registerSubCommand("map", new LoreMapSubCommand(plugin));
        registerSubCommand("share", new LoreShareSubCommand(plugin));
        registerSubCommand("prefs", new LorePrefsSubCommand(plugin));
        registerSubCommand("dynmap", new LoreDynmapSubCommand(plugin));
        registerSubCommand("registerfaction", new LoreRegisterFactionSubCommand(plugin));
        registerSubCommand("faction", new org.fourz.RVNKLore.command.faction.LoreFactionSubCommand(plugin));

        logger.debug("Registered " + subCommands.size() + " subcommands successfully");
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

        logger.debug("Executing subcommand: " + subCommandName + " with " + subCommandArgs.length + " args");
        try {
            return subCommand.execute(sender, subCommandArgs);
        } catch (Throwable t) {
            // #1831: a DB stall/outage used to escape as an unhandled CommandException — the operator
            // got a raw stack trace and "An unexpected error occurred". Degrade cleanly instead, for
            // EVERY subcommand (many go through .join() sync wrappers that can throw on timeout).
            if (isDatabaseUnavailable(t)) {
                sender.sendMessage(ChatColor.RED + "The lore database is temporarily unavailable. "
                        + "Please try again in a moment.");
                logger.warning("Lore database unavailable during '/lore " + subCommandName + "': "
                        + rootCauseMessage(t));
            } else {
                sender.sendMessage(ChatColor.RED + "That command failed unexpectedly. Check the console for details.");
                logger.error("Unhandled error in '/lore " + subCommandName + "'",
                        t instanceof Exception ? (Exception) t : new RuntimeException(t));
            }
            return true; // handled — do not let Bukkit print an unhandled-exception trace
        }
    }

    /**
     * True when a throwable's cause chain indicates the database is unreachable rather than a genuine
     * command bug — connection timeouts, pool exhaustion, socket read timeouts. (#1831)
     */
    private boolean isDatabaseUnavailable(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof org.fourz.rvnkcore.api.exception.DatabaseException
                    || c instanceof java.sql.SQLTransientConnectionException
                    || c instanceof java.sql.SQLNonTransientConnectionException
                    || c instanceof java.net.SocketTimeoutException
                    || c instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            if (c.getCause() == c) break; // defensive: self-referential cause
        }
        return false;
    }

    /** Deepest cause message, for a one-line operator log instead of a full trace. (#1831) */
    private String rootCauseMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }

    /**
     * Shows help information to the sender.
     * Descriptions are sourced uniformly from each subcommand's {@link SubCommand#getDescription()}.
     *
     * @param sender Command sender to show help to
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== RVNKLore Commands =====");
        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            if (entry.getValue().hasPermission(sender)) {
                sender.sendMessage(ChatColor.YELLOW + "/lore " + entry.getKey()
                        + ChatColor.WHITE + " - " + entry.getValue().getDescription());
            }
        }
        sender.sendMessage(ChatColor.GRAY + "See /lore item and /lore collection for item and collection management.");
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

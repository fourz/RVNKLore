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
        Map<String, SubCommand> commands = new java.util.LinkedHashMap<>();
        commands.put("browse", new LoreBrowseSubCommand(plugin));
        commands.put("get", new LoreGetSubCommand(plugin));
        commands.put("search", new LoreSearchSubCommand(plugin));
        commands.put("list", new LoreListSubCommand(plugin));
        commands.put("add", new LoreAddSubCommand(plugin));
        commands.put("approve", new LoreApproveSubCommand(plugin));
        commands.put("delete", new LoreDeleteSubCommand(plugin));
        commands.put("reload", new LoreReloadSubCommand(plugin));
        commands.put("export", new LoreExportSubCommand(plugin));
        commands.put("import", new LoreImportSubCommand(plugin));
        commands.put("debug", new LoreDebugSubCommand(plugin));

        if (plugin.getDiscoveryManager() != null) {
            commands.put("discover", new LoreDiscoverSubCommand(plugin));
        }

        if (plugin.getAchievementManager() != null) {
            commands.put("achievement", new LoreAchievementSubCommand(plugin, plugin.getAchievementManager()));
        }

        if (plugin.getLoreManager().getItemManager() != null && plugin.getLoreManager().getItemManager().getCosmeticItem() != null) {
            commands.put("collection", new LoreCollectionSubCommand(plugin));
            commands.put("item", new LoreItemSubCommand(plugin));
        }

        commands.put("book", new LoreBookSubCommand(plugin));
        commands.put("prefs", new LorePrefsSubCommand(plugin));
        commands.put("dynmap", new LoreDynmapSubCommand(plugin));
        commands.put("registerfaction", new LoreRegisterFactionSubCommand(plugin));
        commands.put("faction", new org.fourz.RVNKLore.command.faction.LoreFactionSubCommand(plugin));

        // Register the /lore npc command for Citizens collection vendors (Phase 8)
        // TODO: Implement Citizens integration in future phase
        // if (plugin.getCitizensIntegration() != null && plugin.getCitizensIntegration().isEnabled()) {
        //     commands.put("npc", new LoreNPCSubCommand(plugin));
        // }

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

        logger.debug("Executing subcommand: " + subCommandName + " with " + subCommandArgs.length + " args");
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
                if ("collection".equals(entry.getKey())) {
                    sender.sendMessage(ChatColor.YELLOW + "/lore collection <view|claim> <collection_id>" +
                        ChatColor.WHITE + " - View or claim collection progress/rewards");
                } else if ("book".equals(entry.getKey())) {
                    sender.sendMessage(ChatColor.YELLOW + "/lore book <give|list> ..." +
                        ChatColor.WHITE + " - Create and manage lore books");
                } else if ("export".equals(entry.getKey())) {
                    sender.sendMessage(ChatColor.YELLOW + "/lore export [json|yaml] [type]" +
                        ChatColor.WHITE + " - Export lore entries to file");
                } else if ("import".equals(entry.getKey())) {
                    sender.sendMessage(ChatColor.YELLOW + "/lore import <file> [--preview]" +
                        ChatColor.WHITE + " - Import lore entries from file");
                } else if ("delete".equals(entry.getKey())) {
                    sender.sendMessage(ChatColor.YELLOW + "/lore delete <name> [confirm]" +
                        ChatColor.WHITE + " - Permanently delete a lore entry");
                } else if ("dynmap".equals(entry.getKey())) {
                    sender.sendMessage(ChatColor.YELLOW + "/lore dynmap <diff|import> [set]" +
                        ChatColor.WHITE + " - Dynmap marker integration");
                } else if ("registerfaction".equals(entry.getKey())) {
                    sender.sendMessage(ChatColor.YELLOW + "/lore registerfaction <name> <member1> [member2...]" +
                        ChatColor.WHITE + " - Register a faction at your claim");
                } else if ("faction".equals(entry.getKey())) {
                    sender.sendMessage(ChatColor.YELLOW + "/lore faction <addterritory|refresh> <name>" +
                        ChatColor.WHITE + " - Manage faction territories");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "/lore " + entry.getKey() +
                        ChatColor.WHITE + " - " + entry.getValue().getDescription());
                }
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

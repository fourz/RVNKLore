package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.output.DisplayFactory;
import org.fourz.RVNKLore.lore.item.book.LoreBookManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;

/**
 * Parent subcommand for book-related operations.
 * Handles routing to child commands: give, list
 *
 * Usage:
 * - /lore book give <player> <entry_id> [rarity]
 * - /lore book list [page] [type]
 */
public class LoreBookSubCommand implements SubCommand {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final LoreBookManager bookManager;
    private final Map<String, SubCommand> subCommands;

    public LoreBookSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreBookSubCommand");

        // Use plugin-level singleton to avoid double-instantiation
        this.bookManager = plugin.getLoreBookManager();

        // Register child commands
        this.subCommands = new HashMap<>();
        subCommands.put("give", new LoreBookGiveSubCommand(plugin, bookManager));
        subCommands.put("list", new LoreBookListSubCommand(plugin, bookManager));

        logger.debug("LoreBookSubCommand initialized with " + subCommands.size() + " child commands");
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
            sender.sendMessage(ChatColor.RED + "✖ Unknown book command: " + subCommandName);
            showUsage(sender);
            return true;
        }

        if (!subCommand.hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "✖ You don't have permission to use this command.");
            return true;
        }

        // Pass remaining args to subcommand
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        try {
            return subCommand.execute(sender, subArgs);
        } catch (Exception e) {
            String errorId = UUID.randomUUID().toString().substring(0, 8);
            logger.error("Error ID: " + errorId + " - Error executing book command: " + subCommandName, e);
            sender.sendMessage(ChatColor.RED + "✖ An error occurred (ID: " + errorId + "). Please report this to an administrator.");
            return false;
        }
    }

    /**
     * Display usage information for the book command.
     */
    private void showUsage(CommandSender sender) {
        List<String> commands = new ArrayList<>();

        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            if (entry.getValue().hasPermission(sender)) {
                commands.add(ChatColor.YELLOW + entry.getKey() + ChatColor.GRAY + " - " + entry.getValue().getDescription());
            }
        }

        DisplayFactory.displayPaginatedList(
            sender,
            "Lore Book Commands",
            commands,
            1,
            10,
            cmd -> ChatColor.GRAY + "• " + cmd
        );

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Examples:");
        sender.sendMessage(ChatColor.DARK_GRAY + "  /lore book list" + ChatColor.GRAY + " - List available books");
        sender.sendMessage(ChatColor.DARK_GRAY + "  /lore book give Steve a1b2c3d4" + ChatColor.GRAY + " - Give book to player");
        sender.sendMessage(ChatColor.DARK_GRAY + "  /lore book give Steve a1b2c3d4 epic" + ChatColor.GRAY + " - Give epic rarity book");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete subcommand names
            String partial = args[0].toLowerCase();
            for (String cmd : subCommands.keySet()) {
                if (subCommands.get(cmd).hasPermission(sender) && cmd.startsWith(partial)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length > 1) {
            // Delegate to subcommand
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
        // Allow access if user has permission for any child command
        return subCommands.values().stream().anyMatch(cmd -> cmd.hasPermission(sender));
    }

    @Override
    public String getDescription() {
        return "Manage lore books (give, list)";
    }

    /**
     * Get the book manager instance.
     * @return The LoreBookManager
     */
    public LoreBookManager getBookManager() {
        return bookManager;
    }

    /**
     * Shutdown the book command and manager.
     */
    public void shutdown() {
        if (bookManager != null) {
            bookManager.shutdown();
        }
    }
}

package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.config.LoreMode;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand for reloading the plugin
 * Usage: /lore reload
 */
public class LoreReloadSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreReloadSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreReloadSubCommand");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        logger.debug("Executing reload command");
        sender.sendMessage(ChatColor.YELLOW + "Reloading RVNKLore plugin...");
        
        // Drain any in-flight async discovery writes before reloading
        plugin.getDiscoveryManager().awaitPendingWrites();

        // Reload configuration
        plugin.getConfigManager().reloadConfig();

        // Update log level
        logger.setLogLevel(plugin.getConfigManager().getLogLevel());

        // Reload lore data
        plugin.getLoreManager().reloadLore();
        
        sender.sendMessage(ChatColor.GREEN + "RVNKLore plugin has been reloaded successfully!");

        // #1828: surface the active mode. full<->quiet is hot (dispatch reads getMode() live);
        // switching to off needs a restart because feature registration only happens in onEnable.
        LoreMode mode = plugin.getConfigManager().getMode();
        sender.sendMessage(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + mode.name().toLowerCase());
        if (mode == LoreMode.OFF) {
            sender.sendMessage(ChatColor.YELLOW + "Note: mode=off takes effect on the next restart "
                    + "(features are still running until then).");
        } else if (mode.suppressesNotifications()) {
            sender.sendMessage(ChatColor.GRAY + "Player-facing lore notifications are suppressed (quiet mode).");
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "Reloads the plugin configuration and lore data";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin") || sender.isOp();
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        // No additional arguments
        return new ArrayList<>();
    }
}

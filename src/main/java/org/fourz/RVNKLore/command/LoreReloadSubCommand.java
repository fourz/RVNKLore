package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;

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
        
        // Reload configuration
        plugin.getConfigManager().reloadConfig();
        
        // Update log level
        logger.setLogLevel(plugin.getConfigManager().getLogLevel());
        
        // Reload lore data
        plugin.getLoreManager().reloadLore();
        
        sender.sendMessage(ChatColor.GREEN + "RVNKLore plugin has been reloaded successfully!");
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

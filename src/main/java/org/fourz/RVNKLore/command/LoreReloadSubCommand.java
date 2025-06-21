package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.subcommand.SubCommand;
import org.fourz.RVNKLore.debug.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
        sender.sendMessage(ChatColor.YELLOW + "⚙ Reloading RVNKLore plugin...");
        
        // Reload configuration
        plugin.getConfigManager().reloadConfig();
        
        // Update log level
        logger.setLogLevel(plugin.getConfigManager().getLogLevel());
        
        // Reload database connections
        CompletableFuture<Void> databaseReload = plugin.getDatabaseManager().reload();
        
        databaseReload.thenRun(() -> {
            sender.sendMessage(ChatColor.GREEN + "✓ RVNKLore plugin has been reloaded successfully!");
        }).exceptionally(e -> {
            logger.error("Error reloading database", e);
            sender.sendMessage(ChatColor.RED + "✖ Error reloading database. Please check the console for details.");
            return null;
        });
        
        return true;
    }

    @Override
    public String getDescription() {
        return "Reloads the plugin configuration and data";
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

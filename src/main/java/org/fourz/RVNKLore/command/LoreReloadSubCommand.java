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
        CompletableFuture<Void> reloadFuture = plugin.getDatabaseManager().reloadAsync();

        reloadFuture.thenRun(() -> {
            sender.sendMessage(ChatColor.GREEN + "✅ RVNKLore plugin reloaded successfully!");
            logger.info("Plugin reloaded successfully");
        }).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "❌ Failed to reload RVNKLore plugin: " + ex.getMessage());
            logger.error("Failed to reload plugin", ex);
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

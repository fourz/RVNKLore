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
        
        // Config re-read is cheap and in-memory — keep it on the main thread so a mode change
        // (full<->quiet) applies immediately even if the lore-data reload below is slow. (#1830)
        plugin.getConfigManager().reloadConfig();
        logger.setLogLevel(plugin.getConfigManager().getLogLevel());
        
        sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");

        // #1828: surface the active mode. It is already applied at this point (config re-read above);
        // full<->quiet is hot, switching to off needs a restart (registration happens in onEnable).
        LoreMode mode = plugin.getConfigManager().getMode();
        sender.sendMessage(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + mode.name().toLowerCase());
        if (mode == LoreMode.OFF) {
            sender.sendMessage(ChatColor.YELLOW + "Note: mode=off takes effect on the next restart "
                    + "(features are still running until then).");
        } else if (mode.suppressesNotifications()) {
            sender.sendMessage(ChatColor.GRAY + "Player-facing lore notifications are suppressed (quiet mode).");
        }

        // #1830: the lore-data reload hits the database and MUST NOT run on the main thread — a stalled
        // DB previously blocked the server for the full connection timeout (25s+ watchdog stall on
        // Event). Drain pending writes + reload off-thread, then report back.
        sender.sendMessage(ChatColor.GRAY + "Reloading lore data in the background...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDiscoveryManager().awaitPendingWrites();
                plugin.getLoreManager().reloadLore();
                sender.sendMessage(ChatColor.GREEN + "Lore data reloaded successfully.");
            } catch (Exception e) {
                // #1831: degrade cleanly — no unhandled stack trace, previously loaded data stays live.
                sender.sendMessage(ChatColor.RED + "Lore data reload failed — the database may be "
                        + "unavailable. Config was applied; lore stays on the previously loaded data.");
                logger.warning("Lore data reload failed (config was applied): " + e.getMessage());
            }
        });
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

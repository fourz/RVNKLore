package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.util.DiagnosticUtil;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.command.subcommand.SubCommand;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Debug command for administrators to troubleshoot the plugin
 */
public class LoreDebugSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final DiagnosticUtil diagnosticUtil;
    private final LogManager logger;
    private final LoreManager loreManager;

    public LoreDebugSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.diagnosticUtil = new DiagnosticUtil(plugin);
        this.logger = LogManager.getInstance(plugin, "LoreDebugSubCommand");
        this.loreManager = LoreManager.getInstance(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "==== Lore Debug Commands ====");
            sender.sendMessage(ChatColor.YELLOW + "/lore debug diagnostics" + ChatColor.WHITE + " - Run system diagnostics");
            sender.sendMessage(ChatColor.YELLOW + "/lore debug check <id>" + ChatColor.WHITE + " - Check a specific lore entry");
            sender.sendMessage(ChatColor.YELLOW + "/lore debug handlers" + ChatColor.WHITE + " - List all registered handlers");
            sender.sendMessage(ChatColor.YELLOW + "/lore debug fix" + ChatColor.WHITE + " - Attempt to fix common issues");
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "diagnostics":
                runDiagnosticsAsync(sender);
                return true;
            case "check":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "&c▶ Usage: /lore debug check <id>");
                    return false;
                }
                checkLoreEntryAsync(sender, args[1]);
                return true;
            case "handlers":
                return listHandlers(sender);
            case "fix":
                return attemptFixes(sender);
            default:
                sender.sendMessage(ChatColor.RED + "&c✖ Unknown debug command: " + action);
                return false;
        }
    }

    private void runDiagnosticsAsync(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "⚙ Running diagnostics, please wait...");
        CompletableFuture.supplyAsync(() -> diagnosticUtil.runDiagnostics())
            .thenAccept(results -> {
                if (!(sender instanceof Player)) {
                    results.forEach(sender::sendMessage);
                    return;
                }
                Player player = (Player) sender;
                int linesPerPage = 10;
                int pages = (int) Math.ceil(results.size() / (double) linesPerPage);
                for (int i = 0; i < Math.min(linesPerPage, results.size()); i++) {
                    player.sendMessage(results.get(i));
                }
                if (pages > 1) {
                    player.sendMessage(ChatColor.YELLOW + "Showing page 1/" + pages + ". Use /lore debug diagnostics <page> to see more.");
                }
            })
            .exceptionally(e -> {
                logger.error("Error running diagnostics", e);
                sender.sendMessage(ChatColor.RED + "&c✖ Error running diagnostics.");
                return null;
            });
    }

    private void checkLoreEntryAsync(CommandSender sender, String id) {
        sender.sendMessage(ChatColor.YELLOW + "⚙ Checking lore entry: " + id);
        loreManager.getLoreEntry(id)
            .thenAccept(entry -> {
                if (entry == null) {
                    sender.sendMessage(ChatColor.RED + "&c✖ No lore entry found with ID: " + id);
                    return;
                }
                sender.sendMessage(ChatColor.GREEN + "✓ Found entry: " + entry.getName());
                sender.sendMessage(ChatColor.WHITE + "Type: " + entry.getType());
                // Simple validity check: name and type not null
                boolean valid = entry.getName() != null && entry.getType() != null;
                sender.sendMessage(ChatColor.WHITE + "Valid: " + valid);
                sender.sendMessage(ChatColor.WHITE + "Approved: " + entry.isApproved());
                if (entry.getLocation() != null) {
                    sender.sendMessage(ChatColor.WHITE + "Location: " +
                        entry.getLocation().getWorld().getName() + " at " +
                        String.format("%.1f, %.1f, %.1f",
                            entry.getLocation().getX(),
                            entry.getLocation().getY(),
                            entry.getLocation().getZ())
                    );
                } else {
                    sender.sendMessage(ChatColor.WHITE + "Location: None");
                }
                // Check if handler exists and can validate
                try {
                    boolean handlerValid = plugin.getHandlerFactory()
                        .getHandler(entry.getType())
                        .validateEntry(entry);
                    sender.sendMessage(ChatColor.WHITE + "Handler validation: " +
                        (handlerValid ? ChatColor.GREEN + "Passed" : ChatColor.RED + "Failed"));
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Handler error: " + e.getMessage());
                }
            })
            .exceptionally(e -> {
                logger.error("Error checking lore entry: " + id, e);
                sender.sendMessage(ChatColor.RED + "&c✖ Error checking lore entry.");
                return null;
            });
    }

    private boolean listHandlers(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Registered Lore Handlers:");
        plugin.getHandlerFactory().getAllHandlers().forEach((type, handler) -> {
            sender.sendMessage(ChatColor.GREEN + type.name() + ChatColor.WHITE +
                " - " + handler.getClass().getSimpleName());
        });
        return true;
    }

    private boolean attemptFixes(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "⚙ Attempting to fix common issues...");
        // 1. Reload handlers
        try {
            sender.sendMessage("Reloading handlers...");
            plugin.getHandlerFactory().reloadHandlers();
            sender.sendMessage(ChatColor.GREEN + "✓ Handlers reloaded successfully");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload handlers: " + e.getMessage());
        }
        // 2. Reload lore entries
        try {
            sender.sendMessage("Reloading lore entries...");
            loreManager.loadAllLoreEntries();
            sender.sendMessage(ChatColor.GREEN + "✓ Lore entries reloaded successfully");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload lore entries: " + e.getMessage());
        }
        // 3. Verify database connection
        try {
            boolean connected = plugin.getDatabaseManager() != null;
            sender.sendMessage("Database connection: " +
                (connected ? ChatColor.GREEN + "OK" : ChatColor.RED + "FAILED"));
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to check database: " + e.getMessage());
        }
        sender.sendMessage(ChatColor.YELLOW + "⚙ Fixes attempted. Run diagnostics to verify.");
        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("diagnostics", "check", "handlers", "fix");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            // Return a few lore entry IDs for convenience (sync, as async not supported in tab completion)
            List<String> entryIds = new ArrayList<>();
            loreManager.getAllEntriesAsync().thenAccept(entries -> {
                entries.stream().limit(5).forEach(entry -> entryIds.add(entry.getId()));
            }).join();
            return entryIds;
        }
        return null;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin");
    }

    @Override
    public String getDescription() {
        return "Debug and diagnostic tools for administrators";
    }
}

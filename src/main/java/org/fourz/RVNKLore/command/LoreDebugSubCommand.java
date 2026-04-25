package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.integration.dynmap.DynmapIntegration;
import org.fourz.RVNKLore.integration.dynmap.LoreMarkerManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.lore.player.NameChangeRecord;
import org.fourz.RVNKLore.util.DiagnosticUtil;
import org.fourz.rvnkcore.util.log.LogManager;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Debug command for administrators to troubleshoot the plugin
 */
public class LoreDebugSubCommand implements SubCommand {
    private static final List<String> LOG_LEVELS = Arrays.asList("DEBUG", "INFO", "WARN", "OFF");

    private final RVNKLore plugin;
    private final DiagnosticUtil diagnosticUtil;
    private final SeedSubCommand seedSubCommand;

    public LoreDebugSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.diagnosticUtil = new DiagnosticUtil(plugin);
        this.seedSubCommand = new SeedSubCommand(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "==== Lore Debug Commands ====");
            sender.sendMessage(ChatColor.YELLOW + "/lore debug diagnostics [--verbose]" + ChatColor.WHITE + " - Run system diagnostics");
            sender.sendMessage(ChatColor.YELLOW + "/lore debug check <id>" + ChatColor.WHITE + " - Check a specific lore entry");
            sender.sendMessage(ChatColor.YELLOW + "/lore debug handlers" + ChatColor.WHITE + " - List all registered handlers");
            sender.sendMessage(ChatColor.YELLOW + "/lore debug fix" + ChatColor.WHITE + " - Attempt to fix common issues");
            sender.sendMessage(ChatColor.YELLOW + "/lore debug player <player_name>" + ChatColor.WHITE + " - Show player lore diagnostics");
            sender.sendMessage(ChatColor.YELLOW + "/lore debug seed <action>" + ChatColor.WHITE + " - Seed test data");
            sender.sendMessage(ChatColor.YELLOW + "/lore debug loglevel [level]" + ChatColor.WHITE + " - View/change runtime log level");
            sender.sendMessage(ChatColor.YELLOW + "/lore debug dynmap [refresh]" + ChatColor.WHITE + " - Dynmap integration status / refresh markers");
            sender.sendMessage(ChatColor.YELLOW + "/lore debug setup" + ChatColor.WHITE + " - Bootstrap LuckPerms permission defaults");
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "diagnostics":
                return runDiagnostics(sender, args);

            case "check":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /lore debug check <id>");
                    return false;
                }
                return checkLoreEntry(sender, args[1]);

            case "handlers":
                return listHandlers(sender);

            case "fix":
                return attemptFixes(sender);

            case "player":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /lore debug player <player_name>");
                    return false;
                }
                return playerDiagnostics(sender, args[1]);

            case "seed":
                String[] seedArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
                return seedSubCommand.execute(sender, seedArgs);

            case "loglevel":
                return handleLogLevel(sender, args);

            case "dynmap":
                return dynmapDiagnostics(sender, args);

            case "setup":
                return executeSetup(sender);

            default:
                sender.sendMessage(ChatColor.RED + "Unknown debug command: " + action);
                return false;
        }
    }

    private boolean runDiagnostics(CommandSender sender, String[] args) {
        // Check for --verbose flag
        boolean verbose = false;
        if (args.length > 1 && args[1].equalsIgnoreCase("--verbose")) {
            verbose = true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("[RVNKLore] Running diagnostics" + (verbose ? " (verbose mode)" : "") + "...");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Running diagnostics" + (verbose ? " (verbose mode)" : "") + ", please wait...");
        }

        // Use new console-friendly diagnostics method
        diagnosticUtil.runDiagnostics(sender, verbose);

        return true;
    }

    private boolean checkLoreEntry(CommandSender sender, String id) {
        sender.sendMessage(ChatColor.YELLOW + "Checking lore entry: " + id);

        // Support short IDs (first 8 chars), consistent with /lore get
        java.util.Optional<org.fourz.RVNKLore.lore.LoreEntry> result = plugin.getLoreManager().getLoreById(id);
        if (!result.isPresent() && id.length() <= 8) {
            final String shortId = id.toLowerCase();
            result = plugin.getLoreManager().getAllLoreEntriesSync().stream()
                    .filter(e -> e.getId().length() >= 8 && e.getId().substring(0, 8).equalsIgnoreCase(shortId))
                    .findFirst();
        }

        result.ifPresentOrElse(
            entry -> {
                sender.sendMessage(ChatColor.GREEN + "Found entry: " + entry.getName());
                sender.sendMessage(ChatColor.WHITE + "Type: " + entry.getType());
                sender.sendMessage(ChatColor.WHITE + "Valid: " + entry.isValid());
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
            },
            () -> sender.sendMessage(ChatColor.RED + "No lore entry found with ID: " + id)
        );

        return true;
    }

    private boolean listHandlers(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Registered Lore Handlers:");

        // Show cached (active) handlers
        Map<LoreType, org.fourz.RVNKLore.handler.LoreHandler> cached = plugin.getHandlerFactory().getAllHandlers();
        cached.forEach((type, handler) -> {
            sender.sendMessage(ChatColor.GREEN + "  " + type.name() + ChatColor.WHITE +
                ": " + handler.getClass().getSimpleName());
        });

        // Show registered-but-not-yet-loaded handler classes
        Map<String, String> allRegistered = plugin.getHandlerFactory().getRegisteredHandlerClasses();
        int notLoaded = 0;
        for (Map.Entry<String, String> entry : allRegistered.entrySet()) {
            try {
                LoreType type = LoreType.valueOf(entry.getKey());
                if (!cached.containsKey(type)) {
                    notLoaded++;
                }
            } catch (IllegalArgumentException ignored) {
                // Non-LoreType keys (event/sign handlers) - always count as loaded via listeners
            }
        }
        if (notLoaded > 0) {
            sender.sendMessage(ChatColor.GRAY + "  " + notLoaded + " type(s) not yet accessed (will load on first use)");
        }
        sender.sendMessage(ChatColor.GRAY + "  Total registered: " + allRegistered.size() + " handler classes");

        return true;
    }

    private boolean attemptFixes(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Attempting to fix common issues...");

        // 1. Reload handlers
        try {
            sender.sendMessage("Reloading handlers...");
            plugin.getHandlerFactory().reloadHandlers();
            sender.sendMessage(ChatColor.GREEN + "Handlers reloaded successfully");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload handlers: " + e.getMessage());
        }

        // 2. Reload lore entries
        try {
            sender.sendMessage("Reloading lore entries...");
            plugin.getLoreManager().reloadLore();
            sender.sendMessage(ChatColor.GREEN + "Lore entries reloaded successfully");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload lore entries: " + e.getMessage());
        }

        // 3. Verify database connection
        try {
            boolean connected = plugin.getDatabaseManager().isConnected();
            sender.sendMessage("Database connection: " +
                (connected ? ChatColor.GREEN + "OK" : ChatColor.RED + "FAILED"));
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to check database: " + e.getMessage());
        }

        sender.sendMessage(ChatColor.YELLOW + "Fixes attempted. Run diagnostics to verify.");
        return true;
    }

    /**
     * Display player lore diagnostics.
     * Shows player UUID, name history, total discoveries, breakdown by type, and fallback status.
     *
     * @param sender The command sender (supports console execution)
     * @param playerName The name of the player to diagnose
     * @return true if diagnostics were displayed successfully
     */
    private boolean playerDiagnostics(CommandSender sender, String playerName) {
        // Resolve player UUID from name
        UUID playerUuid = resolvePlayerUuid(playerName);

        if (playerUuid == null) {
            sender.sendMessage(ChatColor.RED + "Unable to resolve player: " + playerName);
            sender.sendMessage(ChatColor.YELLOW + "Tip: Player must have joined the server at least once");
            return false;
        }

        sender.sendMessage(ChatColor.YELLOW + "Gathering player diagnostics...");

        // Async data retrieval
        CompletableFuture<Optional<String>> nameFuture =
            plugin.getPlayerManager().getPlayerName(playerUuid);
        CompletableFuture<List<NameChangeRecord>> nameHistoryFuture =
            plugin.getPlayerManager().getNameChangeHistory(playerUuid);
        CompletableFuture<List<LoreEntry>> discoveryFuture =
            plugin.getDiscoveryManager() != null && plugin.getDiscoveryManager().isInitialized()
                ? plugin.getDiscoveryManager().getPlayerDiscoveries(playerUuid)
                : CompletableFuture.completedFuture(new ArrayList<>());

        // Wait for all futures to complete
        CompletableFuture.allOf(nameFuture, nameHistoryFuture, discoveryFuture).thenRun(() -> {
            try {
                Optional<String> storedName = nameFuture.join();
                List<NameChangeRecord> nameHistory = nameHistoryFuture.join();
                List<LoreEntry> playerLore = discoveryFuture.join();

                // Calculate statistics
                Map<LoreType, Long> typeBreakdown = playerLore.stream()
                    .collect(Collectors.groupingBy(LoreEntry::getType, Collectors.counting()));

                int totalDiscoveries = playerLore.size();
                int totalLoreEntries = plugin.getLoreManager().getAllLoreEntriesSync().size();
                double completionPercentage = totalLoreEntries > 0
                    ? (totalDiscoveries * 100.0) / totalLoreEntries
                    : 0.0;

                // Format output for console/player
                String prefix = "[RVNKLore] ";
                sender.sendMessage(prefix + "=== PLAYER DIAGNOSTICS: " + playerName + " ===");
                sender.sendMessage(prefix + "UUID: " + playerUuid);

                // Name history
                if (nameHistory.isEmpty()) {
                    sender.sendMessage(prefix + "Name History: " + storedName.orElse(playerName) + " (no changes recorded)");
                } else {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    StringBuilder historyBuilder = new StringBuilder();
                    historyBuilder.append(storedName.orElse(playerName)).append(" (current)");

                    for (int i = nameHistory.size() - 1; i >= 0; i--) {
                        NameChangeRecord record = nameHistory.get(i);
                        String date = dateFormat.format(new Date(record.timestamp()));
                        historyBuilder.append(", ").append(record.previousName()).append(" (").append(date).append(")");
                    }

                    sender.sendMessage(prefix + "Name History: " + historyBuilder.toString());
                }

                // Total discoveries
                sender.sendMessage(prefix + "Total Discoveries: " + totalDiscoveries + "/" + totalLoreEntries +
                    String.format(" (%.1f%%)", completionPercentage));
                sender.sendMessage(prefix);

                // Breakdown by type
                sender.sendMessage(prefix + "Breakdown by Type:");
                if (typeBreakdown.isEmpty()) {
                    sender.sendMessage(prefix + "  No discoveries recorded");
                } else {
                    // Sort by count descending
                    typeBreakdown.entrySet().stream()
                        .sorted(Map.Entry.<LoreType, Long>comparingByValue().reversed())
                        .forEach(entry -> {
                            LoreType type = entry.getKey();
                            long count = entry.getValue();
                            long totalOfType = plugin.getLoreManager().getLoreEntriesByTypeSync(type).size();
                            double percentage = totalOfType > 0 ? (count * 100.0) / totalOfType : 0.0;

                            sender.sendMessage(prefix + String.format("  %s: %d/%d (%.1f%%)",
                                type.name(), count, totalOfType, percentage));
                        });
                }

                sender.sendMessage(prefix);

                // Recent discoveries
                List<LoreEntry> recentDiscoveries = playerLore.stream()
                    .sorted(Comparator.comparing(LoreEntry::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(5)
                    .collect(Collectors.toList());

                if (!recentDiscoveries.isEmpty()) {
                    sender.sendMessage(prefix + "Recent Discoveries (last 5):");
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    for (LoreEntry entry : recentDiscoveries) {
                        String date = entry.getCreatedAt() != null
                            ? dateFormat.format(entry.getCreatedAt())
                            : "Unknown";
                        sender.sendMessage(prefix + "  " + entry.getDisplayName() + " (" + entry.getType() + ") - " + date);
                    }
                    sender.sendMessage(prefix);
                }

                // Fallback mode status
                boolean fallbackMode = plugin.getPlayerManager().isInFallbackMode();
                sender.sendMessage(prefix + "Fallback Mode: " + (fallbackMode
                    ? ChatColor.RED + "ACTIVE (degraded database connectivity)"
                    : ChatColor.GREEN + "Inactive (normal operation)"));

            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error retrieving player diagnostics: " + e.getMessage());
                e.printStackTrace();
            }
        }).exceptionally(throwable -> {
            sender.sendMessage(ChatColor.RED + "Failed to retrieve player diagnostics: " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
        });

        return true;
    }

    /**
     * Display Dynmap integration diagnostics and handle refresh.
     */
    private boolean dynmapDiagnostics(CommandSender sender, String[] args) {
        String prefix = "[RVNKLore] ";
        DynmapIntegration integration = plugin.getDynmapIntegration();

        sender.sendMessage(prefix + "=== DYNMAP INTEGRATION ===");
        sender.sendMessage(prefix + "Config enabled: " + plugin.getConfigManager().isDynmapEnabled());
        sender.sendMessage(prefix + "Integration active: " + plugin.isDynmapAvailable());

        if (integration == null) {
            sender.sendMessage(prefix + "Integration object: null (not initialized)");
            return true;
        }

        sender.sendMessage(prefix + "Integration enabled: " + integration.isEnabled());

        LoreMarkerManager markerMgr = integration.getMarkerManager();
        if (markerMgr == null) {
            sender.sendMessage(prefix + "MarkerManager: null (Dynmap not connected)");

            // Check if Dynmap plugin exists but isn't hooked
            org.bukkit.plugin.Plugin dynmapPlugin = plugin.getServer().getPluginManager().getPlugin("dynmap");
            if (dynmapPlugin != null) {
                sender.sendMessage(prefix + "Dynmap plugin: " + dynmapPlugin.getDescription().getVersion() + " (loaded but not hooked)");
            } else {
                sender.sendMessage(prefix + "Dynmap plugin: NOT INSTALLED");
            }
            return true;
        }

        // Marker stats
        sender.sendMessage(prefix + "Marker set ID: " + plugin.getConfigManager().getDynmapMarkerSetId());
        sender.sendMessage(prefix + "Active markers: " + markerMgr.getMarkerCount());
        sender.sendMessage(prefix + "Only approved: " + plugin.getConfigManager().isDynmapOnlyApproved());
        sender.sendMessage(prefix + "Popup enabled: " + plugin.getConfigManager().isDynmapPopupEnabled());

        // Show eligible entries count
        long eligible = plugin.getLoreManager().getAllLoreEntriesSync().stream()
            .filter(markerMgr::shouldHaveMarker)
            .count();
        sender.sendMessage(prefix + "Eligible entries: " + eligible);

        // Location types
        sender.sendMessage(prefix + "Location types: " + LoreMarkerManager.getLocationTypes());

        // Handle refresh subcommand
        if (args.length > 1 && "refresh".equalsIgnoreCase(args[1])) {
            sender.sendMessage(prefix + "Refreshing all Dynmap markers...");
            markerMgr.cleanup();
            markerMgr.populateAllMarkers();
            sender.sendMessage(prefix + "Refresh complete - " + markerMgr.getMarkerCount() + " markers active");
        }

        return true;
    }

    /**
     * Handle the loglevel subcommand.
     * Usage: /lore debug loglevel [DEBUG|INFO|WARN|OFF]
     */
    private boolean handleLogLevel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            String currentLevel = plugin.getConfigManager().getConfig().getString("general.logLevel", "INFO");
            sender.sendMessage(ChatColor.GOLD + "Current log level: " + ChatColor.WHITE + currentLevel);
            sender.sendMessage(ChatColor.GRAY + "Usage: /lore debug loglevel <DEBUG|INFO|WARN|OFF>");
            return true;
        }

        String levelStr = args[1].toUpperCase();
        Level level = LogManager.parseLevel(levelStr);

        LogManager.setPluginLogLevel(plugin, level);

        plugin.getConfigManager().getConfig().set("general.logLevel", levelStr);
        plugin.saveConfig();

        sender.sendMessage(ChatColor.GREEN + "Log level set to: " + ChatColor.WHITE + levelStr);
        sender.sendMessage(ChatColor.GRAY + "(Saved to config.yml)");
        return true;
    }

    /**
     * Resolve a player name to UUID.
     * Uses Bukkit's OfflinePlayer lookup which works even if the player is offline.
     *
     * @param playerName The player name to resolve
     * @return The player's UUID, or null if not found
     */
    @SuppressWarnings("deprecation")
    private UUID resolvePlayerUuid(String playerName) {
        // Try online player first (most recent)
        Player onlinePlayer = plugin.getServer().getPlayer(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        // Fall back to offline player lookup
        // Note: This uses deprecated API but is necessary for offline player resolution
        org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerName);
        if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
            return offlinePlayer.getUniqueId();
        }

        return null;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("diagnostics", "check", "handlers", "fix", "player", "seed", "loglevel", "dynmap");
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("seed")) {
            return seedSubCommand.getTabCompletions(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("diagnostics")) {
            return Arrays.asList("--verbose");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("dynmap")) {
            return Arrays.asList("refresh");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("loglevel")) {
            return LOG_LEVELS;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            // Return a few lore entry IDs for convenience
            List<String> entryIds = new ArrayList<>();
            plugin.getLoreManager().getAllLoreEntriesSync().stream()
                .limit(5)
                .forEach(entry -> entryIds.add(entry.getId()));
            return entryIds;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            // Return online player names for tab completion
            return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
        }

        return null;
    }

    private boolean executeSetup(CommandSender sender) {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            sender.sendMessage(ChatColor.RED + "✖ LuckPerms is not installed — cannot apply permission defaults.");
            sender.sendMessage(ChatColor.GRAY + "Install LuckPerms and run this command again.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== RVNKLore Permission Setup ===");
        sender.sendMessage(ChatColor.GRAY + "Applying LuckPerms defaults...");

        String[][] assignments = {
            {"admin",     "rvnklore.admin",           "true"},
            {"default",   "rvnklore.use",             "true"},
            {"default",   "rvnklore.add",             "true"},
            {"default",   "rvnklore.get",             "true"},
            {"default",   "rvnklore.list",            "true"},
            {"default",   "rvnklore.search",          "true"},
            {"default",   "rvnklore.browse",          "true"},
            {"default",   "rvnklore.book",            "true"},
            {"default",   "rvnklore.achievement",     "true"},
            {"default",   "rvnklore.collection",      "true"},
            {"default",   "rvnklore.prefs",           "true"},
            {"default",   "rvnklore.share",           "true"},
        };

        org.bukkit.command.ConsoleCommandSender console = plugin.getServer().getConsoleSender();
        int ok = 0, fail = 0;

        for (String[] row : assignments) {
            String cmd = "lp group " + row[0] + " permission set " + row[1] + " " + row[2];
            try {
                plugin.getServer().dispatchCommand(console, cmd);
                sender.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.GRAY + row[0] + " " + ChatColor.DARK_GRAY + "← " + ChatColor.WHITE + row[1]);
                ok++;
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "✖ Failed: " + cmd + " (" + e.getMessage() + ")");
                fail++;
            }
        }

        sender.sendMessage(ChatColor.GRAY + "Done. " + ChatColor.WHITE + ok + " applied" +
                (fail > 0 ? ChatColor.RED + ", " + fail + " failed" : "") + ".");
        sender.sendMessage(ChatColor.DARK_GRAY + "Run " + ChatColor.GRAY + "lp editor" + ChatColor.DARK_GRAY + " to review or adjust group assignments.");
        return true;
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

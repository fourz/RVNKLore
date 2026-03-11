package org.fourz.RVNKLore.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.LoreTestDataGenerator;
import org.fourz.rvnkcore.testing.TestDataGenerator.DataCategory;
import org.fourz.rvnkcore.util.chat.ChatService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Debug subcommand for seeding test data into the RVNKLore database.
 *
 * <p>Usage:
 * <ul>
 *   <li>/lore debug seed minimal|standard|stress - Seed test data</li>
 *   <li>/lore debug seed cleanup - Remove all test data</li>
 *   <li>/lore debug seed cleanup [player-uuid] - Remove data for specific player</li>
 *   <li>/lore debug seed status - Show seeding status</li>
 * </ul>
 * </p>
 */
public class SeedSubCommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("minimal", "standard", "stress", "cleanup", "status");
    private static final String PERMISSION = "rvnklore.admin.seed";

    private final RVNKLore plugin;
    private final LogManager logger;
    private final ChatService chatService;
    private LoreTestDataGenerator generator;
    private boolean seeding = false;

    public SeedSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "SeedSubCommand");
        this.chatService = new ChatService();
    }

    @Override
    public String getDescription() {
        return "Seed test data into database";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(PERMISSION);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            chatService.sendError(sender, "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        // Initialize generator if needed
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager == null || !dbManager.isConnected()) {
            chatService.sendError(sender, "Database is not available. Cannot perform seed operations.");
            return true;
        }

        if (generator == null) {
            generator = new LoreTestDataGenerator(dbManager);
        }

        switch (action) {
            case "minimal":
            case "standard":
            case "stress":
                return executeSeed(sender, DataCategory.valueOf(action.toUpperCase()));
            case "cleanup":
                if (args.length > 1) {
                    return executeCleanupPlayer(sender, args[1]);
                }
                return executeCleanup(sender);
            case "status":
                return executeStatus(sender);
            default:
                chatService.sendError(sender, "Unknown action: " + action);
                showUsage(sender);
                return true;
        }
    }

    private void showUsage(CommandSender sender) {
        chatService.sendMessage(sender, "&6=== Lore Seed Commands ===");
        chatService.sendMessage(sender, "&7/lore debug seed minimal&8 - Seed 10 base records");
        chatService.sendMessage(sender, "&7/lore debug seed standard&8 - Seed 100 base records");
        chatService.sendMessage(sender, "&7/lore debug seed stress&8 - Seed 1000 base records");
        chatService.sendMessage(sender, "&7/lore debug seed cleanup&8 - Remove all test data");
        chatService.sendMessage(sender, "&7/lore debug seed cleanup <uuid>&8 - Remove player's test data");
        chatService.sendMessage(sender, "&7/lore debug seed status&8 - Show current status");
    }

    private boolean executeSeed(CommandSender sender, DataCategory category) {
        if (seeding) {
            chatService.sendError(sender, "A seed operation is already in progress.");
            return true;
        }

        seeding = true;
        chatService.sendInfo(sender, "Seeding " + category.name() + " test data...");

        generator.seed(category).thenAccept(count -> {
            seeding = false;
            if (count > 0) {
                chatService.sendSuccess(sender, "Seed complete: " + count + " total records created");
            } else {
                chatService.sendError(sender, "Seed failed. Check console for details.");
            }
        }).exceptionally(ex -> {
            seeding = false;
            chatService.sendError(sender, "Seed failed: " + ex.getMessage());
            logger.error("Seed operation failed: " + ex.getMessage());
            return null;
        });

        return true;
    }

    private boolean executeCleanup(CommandSender sender) {
        if (seeding) {
            chatService.sendError(sender, "A seed operation is in progress. Wait for it to complete.");
            return true;
        }

        seeding = true;
        chatService.sendInfo(sender, "Cleaning up all test data...");

        generator.cleanup().thenAccept(success -> {
            seeding = false;
            if (success) {
                chatService.sendSuccess(sender, "Cleanup complete");
            } else {
                chatService.sendError(sender, "Cleanup failed. Check console for details.");
            }
        }).exceptionally(ex -> {
            seeding = false;
            chatService.sendError(sender, "Cleanup failed: " + ex.getMessage());
            logger.error("Cleanup operation failed: " + ex.getMessage());
            return null;
        });

        return true;
    }

    private boolean executeCleanupPlayer(CommandSender sender, String uuidStr) {
        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            chatService.sendError(sender, "Invalid UUID format: " + uuidStr);
            return true;
        }

        if (seeding) {
            chatService.sendError(sender, "A seed operation is in progress. Wait for it to complete.");
            return true;
        }

        seeding = true;
        chatService.sendInfo(sender, "Cleaning up data for player: " + uuidStr.substring(0, 8) + "...");

        generator.cleanupByPlayer(playerUuid).thenAccept(count -> {
            seeding = false;
            chatService.sendSuccess(sender, "Cleaned up " + count + " records for player");
        }).exceptionally(ex -> {
            seeding = false;
            chatService.sendError(sender, "Cleanup failed: " + ex.getMessage());
            logger.error("Player cleanup operation failed: " + ex.getMessage());
            return null;
        });

        return true;
    }

    private boolean executeStatus(CommandSender sender) {
        DatabaseManager dbManager = plugin.getDatabaseManager();

        chatService.sendMessage(sender, "&6=== Lore Seed Status ===");
        chatService.sendMessage(sender, "&7Database Connected: " + (dbManager != null && dbManager.isConnected() ? "&aYes" : "&cNo"));
        chatService.sendMessage(sender, "&7Generator Initialized: " + (generator != null ? "&aYes" : "&7No"));
        chatService.sendMessage(sender, "&7Seeding In Progress: " + (seeding ? "&eYes" : "&7No"));

        if (dbManager != null) {
            chatService.sendMessage(sender, "&7Database Info: &f" + dbManager.getDatabaseInfo());
            chatService.sendMessage(sender, "&7Entry Count: &f" + dbManager.getEntryCount());
        }

        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String action : ACTIONS) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        }

        return completions;
    }
}

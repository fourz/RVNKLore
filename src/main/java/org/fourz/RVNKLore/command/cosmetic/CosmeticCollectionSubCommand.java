package org.fourz.RVNKLore.command.cosmetic;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.SubCommand;
import org.fourz.RVNKLore.cosmetic.*;
import org.fourz.RVNKLore.debug.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sub-command for managing and viewing head collections.
 * Provides player-facing interface for browsing collections and checking progress.
 */
public class CosmeticCollectionSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final CosmeticManager cosmeticManager;

    public CosmeticCollectionSubCommand(RVNKLore plugin, CosmeticManager cosmeticManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CosmeticCollectionSubCommand");
        this.cosmeticManager = cosmeticManager;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("&c▶ This command can only be used by players");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showCollectionOverview(player);
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "list":
                listCollections(player, args.length > 1 ? args[1] : null);
                break;
            case "view":
                if (args.length < 2) {
                    player.sendMessage("&c▶ Usage: /lore collection view <collection_id>");
                    return true;
                }
                viewCollection(player, args[1]);
                break;
            case "progress":
                showProgress(player);
                break;
            case "theme":
                if (args.length < 2) {
                    listThemes(player);
                } else {
                    showThemeCollections(player, args[1]);
                }
                break;
            default:
                showUsage(player);
                break;
        }

        return true;
    }

    /**
     * Show general overview of collections and player's progress.
     */
    private void showCollectionOverview(Player player) {
        player.sendMessage("&6⚙ &lHead Collections Overview");
        player.sendMessage("");

        Map<String, Double> progress = cosmeticManager.getPlayerCollectionProgress(player);
        int totalCollections = cosmeticManager.getAvailableCollections().size();
        int completedCollections = (int) progress.values().stream().mapToLong(p -> p >= 1.0 ? 1 : 0).sum();

        player.sendMessage("&a✓ Completed Collections: &f" + completedCollections + "&7/&f" + totalCollections);
        
        double overallProgress = progress.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        player.sendMessage("&e⚠ Overall Progress: &f" + String.format("%.1f%%", overallProgress * 100));
        
        player.sendMessage("");
        player.sendMessage("&7   Use &f/lore collection list &7to see all collections");
        player.sendMessage("&7   Use &f/lore collection progress &7for detailed progress");
        player.sendMessage("&7   Use &f/lore collection theme &7to browse by theme");
    }

    /**
     * List available collections, optionally filtered by theme.
     */
    private void listCollections(Player player, String themeFilter) {
        List<HeadCollection> collections;
        
        if (themeFilter != null) {
            CollectionTheme theme = CollectionTheme.fromDisplayName(themeFilter);
            if (theme == null) {
                player.sendMessage("&c✖ Unknown theme: " + themeFilter);
                listThemes(player);
                return;
            }
            collections = cosmeticManager.getCollectionsByTheme(theme);
            player.sendMessage("&6⚙ &lCollections - " + theme.getDisplayName());
        } else {
            collections = cosmeticManager.getAvailableCollections();
            player.sendMessage("&6⚙ &lAll Available Collections");
        }

        if (collections.isEmpty()) {
            player.sendMessage("&e⚠ No collections available" + (themeFilter != null ? " for theme: " + themeFilter : ""));
            return;
        }

        player.sendMessage("");
        Map<String, Double> progress = cosmeticManager.getPlayerCollectionProgress(player);

        for (HeadCollection collection : collections) {
            double completionPercent = progress.getOrDefault(collection.getId(), 0.0) * 100;
            String status = completionPercent >= 100.0 ? "&a✓" : "&e" + String.format("%.0f%%", completionPercent);
            
            player.sendMessage("&f" + collection.getName() + " &7(" + collection.getId() + ") " + status);
            player.sendMessage("&7   " + collection.getDescription());
            player.sendMessage("&7   " + collection.getHeadCount() + " heads • " + collection.getTheme().getDisplayName());
            
            if (collection.isSeasonal()) {
                player.sendMessage("&e   ⏰ Seasonal Collection");
            }
        }
        
        player.sendMessage("");
        player.sendMessage("&7   Use &f/lore collection view <id> &7for details");
    }

    /**
     * View detailed information about a specific collection.
     */
    private void viewCollection(Player player, String collectionId) {
        HeadCollection collection = cosmeticManager.getCollection(collectionId);
        if (collection == null) {
            player.sendMessage("&c✖ Collection not found: " + collectionId);
            return;
        }

        if (!collection.isAvailable()) {
            player.sendMessage("&e⚠ This collection is not currently available");
        }

        player.sendMessage("&6⚙ &l" + collection.getName());
        player.sendMessage("&7" + collection.getDescription());
        player.sendMessage("");

        // Show progress
        Map<String, Double> progress = cosmeticManager.getPlayerCollectionProgress(player);
        double completionPercent = progress.getOrDefault(collection.getId(), 0.0) * 100;
        boolean isComplete = completionPercent >= 100.0;
        
        player.sendMessage("&fProgress: " + (isComplete ? "&a✓ COMPLETE" : "&e" + String.format("%.1f%%", completionPercent)));
        player.sendMessage("&fTheme: &7" + collection.getTheme().getDisplayName());
        player.sendMessage("&fHeads: &7" + collection.getHeadCount());
        
        if (collection.isSeasonal()) {
            player.sendMessage("&fAvailability: &e⏰ Seasonal");
        }

        // Show requirements if any
        if (!collection.getRequirements().isEmpty()) {
            player.sendMessage("");
            player.sendMessage("&fRequirements:");
            for (String requirement : collection.getRequirements()) {
                player.sendMessage("&7  • " + requirement);
            }
        }

        // Show heads in collection
        player.sendMessage("");
        player.sendMessage("&fHeads in Collection:");
        
        for (HeadVariant variant : collection.getAllHeads()) {
            boolean owned = cosmeticManager.playerOwnsHead(player, variant.getId());
            String status = owned ? "&a✓" : "&c✖";
            String rarity = variant.getRarity().getColoredDisplayName();
            
            player.sendMessage("&7  " + status + " &f" + variant.getName() + " &7(" + rarity + "&7)");
            
            if (owned) {
                continue;
            }
            
            // Show acquisition info for unowned heads
            if (variant.requiresPermission()) {
                player.sendMessage("&7     Requires special permission");
            }
            
            if (variant.getRarity() == HeadRarity.EVENT) {
                player.sendMessage("&7     Event exclusive");
            }
        }

        // Show rewards if complete
        if (isComplete && collection.getRewards().hasRewards()) {
            player.sendMessage("");
            player.sendMessage("&a✓ Collection Complete! Rewards claimed.");
        }
    }

    /**
     * Show detailed progress for all collections.
     */
    private void showProgress(Player player) {
        player.sendMessage("&6⚙ &lYour Collection Progress");
        player.sendMessage("");

        Map<String, Double> progress = cosmeticManager.getPlayerCollectionProgress(player);
        
        if (progress.isEmpty()) {
            player.sendMessage("&e⚠ No collection progress to display");
            return;
        }

        for (Map.Entry<String, Double> entry : progress.entrySet()) {
            HeadCollection collection = cosmeticManager.getCollection(entry.getKey());
            if (collection == null) continue;

            double percent = entry.getValue() * 100;
            String status = percent >= 100.0 ? "&a✓ COMPLETE" : "&e" + String.format("%.1f%%", percent);
            
            player.sendMessage("&f" + collection.getName() + ": " + status);
            
            if (percent < 100.0) {
                int owned = (int) (collection.getHeadCount() * entry.getValue());
                player.sendMessage("&7   " + owned + "/" + collection.getHeadCount() + " heads collected");
            }
        }
    }

    /**
     * List all available themes.
     */
    private void listThemes(Player player) {
        player.sendMessage("&6⚙ &lAvailable Themes");
        player.sendMessage("");
        
        for (CollectionTheme theme : CollectionTheme.values()) {
            int collectionCount = cosmeticManager.getCollectionsByTheme(theme).size();
            if (collectionCount > 0) {
                player.sendMessage("&f" + theme.getDisplayName() + " &7(" + collectionCount + " collections)");
                player.sendMessage("&7   " + theme.getDescription());
            }
        }
        
        player.sendMessage("");
        player.sendMessage("&7   Use &f/lore collection theme <name> &7to view theme collections");
    }

    /**
     * Show collections for a specific theme.
     */
    private void showThemeCollections(Player player, String themeName) {
        CollectionTheme theme = CollectionTheme.fromDisplayName(themeName);
        if (theme == null) {
            player.sendMessage("&c✖ Unknown theme: " + themeName);
            listThemes(player);
            return;
        }

        listCollections(player, themeName);
    }

    /**
     * Show command usage information.
     */
    private void showUsage(Player player) {
        player.sendMessage("&c▶ Head Collection Commands:");
        player.sendMessage("&f/lore collection &7- Overview of all collections");
        player.sendMessage("&f/lore collection list [theme] &7- List collections");
        player.sendMessage("&f/lore collection view <id> &7- View collection details");
        player.sendMessage("&f/lore collection progress &7- Show your progress");
        player.sendMessage("&f/lore collection theme [name] &7- Browse by theme");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("list");
            completions.add("view");
            completions.add("progress");
            completions.add("theme");
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            
            if ("view".equals(action)) {
                // Add collection IDs
                for (HeadCollection collection : cosmeticManager.getAvailableCollections()) {
                    completions.add(collection.getId());
                }
            } else if ("list".equals(action) || "theme".equals(action)) {
                // Add theme names
                for (CollectionTheme theme : CollectionTheme.values()) {
                    completions.add(theme.getDisplayName());
                }
            }
        }

        return completions;
    }

    @Override
    public String getDescription() {
        return "Manage and view head collections";
    }

    public String getUsage() {
        return "/lore collection [list|view|progress|theme] [args...]";
    }
}

package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.lore.item.cosmetic.CosmeticsManager;
import org.fourz.RVNKLore.lore.item.cosmetic.HeadCollection;
import org.fourz.RVNKLore.lore.item.cosmetic.HeadVariant;
import org.fourz.RVNKLore.lore.item.cosmetic.HeadRarity;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionTheme;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sub-command for managing and viewing head collections.
 * Provides player-facing interface for browsing collections and checking progress.
 */
public class LoreCollectionSubCommand implements SubCommand {
    private final CosmeticsManager cosmeticItem;
    private final RVNKLore plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public LoreCollectionSubCommand(RVNKLore plugin) {        
        this.plugin = plugin;
        this.cosmeticItem = plugin.getItemManager().getCosmeticItem();
        
        // Register sub-commands
        subCommands.put("add", new LoreCollectionAddSubCommand(plugin));
        subCommands.put("list", new LoreCollectionListSubCommand(plugin));
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        // Only allow players with the base collection permission or admin
        return sender.hasPermission("rvnklore.command.collection") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Check if the first argument is a registered subcommand
        if (args.length > 0 && subCommands.containsKey(args[0].toLowerCase())) {
            String subCommandName = args[0].toLowerCase();
            SubCommand subCommand = subCommands.get(subCommandName);
            
            if (subCommand.hasPermission(sender)) {
                return subCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
            } else {
                sender.sendMessage(ChatColor.RED + "✖ You don't have permission to use this command");
                return true;
            }
        }

        // If not a subcommand, handle with existing logic
        if (!hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "✖ You don't have permission to use this command");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "▶ This command can only be used by players");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore collection <view|claim|list|add> [collection_id]");
            sender.sendMessage(ChatColor.GRAY + "   View, claim, list, or add item collections.");
            return true;
        }
        
        // Original switch case handling remains the same
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "view":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "▶ Usage: /lore collection view <collection_id>");
                    return true;
                }
                String collectionId = args[1];
                HeadCollection collection = cosmeticItem.getCollection(collectionId);
                if (collection == null) {
                    sender.sendMessage(ChatColor.RED + "✖ Collection not found: " + collectionId);
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "✓ Collection: " + ChatColor.YELLOW + collection.getName());
                sender.sendMessage(ChatColor.GRAY + "Description: " + ChatColor.WHITE + collection.getDescription());
                //sender.sendMessage(ChatColor.GRAY + "Items: " + ChatColor.WHITE + collection.getItemCount());
                break;
            case "claim":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "▶ Usage: /lore collection claim <collection_id>");
                    return true;
                }
                collectionId = args[1];
                collection = cosmeticItem.getCollection(collectionId);
                if (collection == null) {
                    sender.sendMessage(ChatColor.RED + "✖ Collection not found: " + collectionId);
                    return true;
                }
                Map<String, Double> progress = cosmeticItem.getPlayerCollectionProgress(player);
                double completionPercent = progress.getOrDefault(collection.getId(), 0.0) * 100;
                if (completionPercent < 100.0) {
                    player.sendMessage(ChatColor.YELLOW + "⚠ You must complete the collection to claim rewards.");
                    return true;
                }
                if (!collection.getRewards().hasRewards()) {
                    player.sendMessage(ChatColor.YELLOW + "⚠ No rewards available for this collection.");
                    return true;
                }
                // Award rewards (delegates to CosmeticItem)
                cosmeticItem.awardCollectionRewards(player, collection, collection.getRewards());
                sender.sendMessage(ChatColor.GREEN + "✓ Claimed rewards for collection: " + ChatColor.YELLOW + collectionId);
                break;
            case "list":
                List<HeadCollection> collections = cosmeticItem.getAvailableCollections();
                if (collections.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ No collections available");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "✓ Available Collections:");
                for (HeadCollection coll : collections) {
                    sender.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + coll.getId());
                }
                break;
            default:
                sender.sendMessage(ChatColor.RED + "✖ Unknown subcommand: " + sub);
                sender.sendMessage(ChatColor.GRAY + "   Use /lore collection <view|claim|list>");
                break;
        }
        return true;
    }

    /**
     * Show general overview of collections and player's progress.
     */
    private void showCollectionOverview(Player player) {
        player.sendMessage(ChatColor.YELLOW + "⚙ " + ChatColor.BOLD + "Head Collections Overview");
        player.sendMessage("");

        Map<String, Double> progress = cosmeticItem.getPlayerCollectionProgress(player);
        int totalCollections = cosmeticItem.getAvailableCollections().size();
        int completedCollections = (int) progress.values().stream().mapToLong(p -> p >= 1.0 ? 1 : 0).sum();

        player.sendMessage(ChatColor.GREEN + "✓ Completed Collections: " + ChatColor.WHITE + completedCollections + ChatColor.GRAY + "/" + ChatColor.WHITE + totalCollections);
        
        double overallProgress = progress.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        player.sendMessage(ChatColor.YELLOW + "⚠ Overall Progress: " + ChatColor.WHITE + String.format("%.1f%%", overallProgress * 100));
        
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "   Use " + ChatColor.WHITE + "/lore collection list " + ChatColor.GRAY + "to see all collections");
        player.sendMessage(ChatColor.GRAY + "   Use " + ChatColor.WHITE + "/lore collection progress " + ChatColor.GRAY + "for detailed progress");
        player.sendMessage(ChatColor.GRAY + "   Use " + ChatColor.WHITE + "/lore collection theme " + ChatColor.GRAY + "to browse by theme");
    }

    /**
     * List available collections, optionally filtered by theme.
     */
    private void listCollections(Player player, String themeFilter) {
        List<HeadCollection> collections;
        
        if (themeFilter != null) {
            CollectionTheme theme = CollectionTheme.fromDisplayName(themeFilter);
            if (theme == null) {
                player.sendMessage(ChatColor.RED + "✖ Unknown theme: " + themeFilter);
                listThemes(player);
                return;
            }
            collections = cosmeticItem.getCollectionsByTheme(theme);
            player.sendMessage(ChatColor.YELLOW + "⚙ " + ChatColor.BOLD + "Collections - " + theme.getDisplayName());
        } else {
            collections = cosmeticItem.getAvailableCollections();
            player.sendMessage(ChatColor.YELLOW + "⚙ " + ChatColor.BOLD + "All Available Collections");
        }

        if (collections.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "⚠ No collections available" + (themeFilter != null ? " for theme: " + themeFilter : ""));
            return;
        }

        player.sendMessage("");
        Map<String, Double> progress = cosmeticItem.getPlayerCollectionProgress(player);

        for (HeadCollection collection : collections) {
            double completionPercent = progress.getOrDefault(collection.getId(), 0.0) * 100;
            String status = completionPercent >= 100.0 ? ChatColor.GREEN + "✓" : ChatColor.YELLOW + String.format("%.0f%%", completionPercent);
            
            player.sendMessage(ChatColor.WHITE + collection.getName() + " " + ChatColor.GRAY + "(" + collection.getId() + ") " + status);
            player.sendMessage(ChatColor.GRAY + "   " + collection.getDescription());
            player.sendMessage(ChatColor.GRAY + "   " + collection.getHeadCount() + " heads • " + collection.getTheme().getDisplayName());
            
            if (collection.isSeasonal()) {
                player.sendMessage(ChatColor.YELLOW + "   ⏰ Seasonal Collection");
            }
        }
        
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "   Use " + ChatColor.WHITE + "/lore collection view <id> " + ChatColor.GRAY + "for details");
    }

    /**
     * View detailed information about a specific collection.
     */
    private void viewCollection(Player player, String collectionId) {
        HeadCollection collection = cosmeticItem.getCollection(collectionId);
        if (collection == null) {
            player.sendMessage(ChatColor.RED + "✖ Collection not found: " + collectionId);
            return;
        }

        if (!collection.isAvailable()) {
            player.sendMessage(ChatColor.YELLOW + "⚠ This collection is not currently available");
        }

        player.sendMessage(ChatColor.YELLOW + "⚙ " + ChatColor.BOLD + collection.getName());
        player.sendMessage(ChatColor.GRAY + collection.getDescription());
        player.sendMessage("");

        // Show progress
        Map<String, Double> progress = cosmeticItem.getPlayerCollectionProgress(player);
        double completionPercent = progress.getOrDefault(collection.getId(), 0.0) * 100;
        boolean isComplete = completionPercent >= 100.0;
        
        player.sendMessage(ChatColor.WHITE + "Progress: " + (isComplete ? ChatColor.GREEN + "✓ COMPLETE" : ChatColor.YELLOW + String.format("%.1f%%", completionPercent)));
        player.sendMessage(ChatColor.WHITE + "Theme: " + ChatColor.GRAY + collection.getTheme().getDisplayName());
        player.sendMessage(ChatColor.WHITE + "Heads: " + ChatColor.GRAY + collection.getHeadCount());
        
        if (collection.isSeasonal()) {
            player.sendMessage(ChatColor.WHITE + "Availability: " + ChatColor.YELLOW + "⏰ Seasonal");
        }

        // Show requirements if any
        if (!collection.getRequirements().isEmpty()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.WHITE + "Requirements:");
            for (String requirement : collection.getRequirements()) {
                player.sendMessage(ChatColor.GRAY + "  • " + requirement);
            }
        }

        // Show heads in collection
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "Heads in Collection:");
        
        for (HeadVariant variant : collection.getAllHeads()) {
            boolean owned = cosmeticItem.playerOwnsHead(player, variant.getId());
            String status = owned ? ChatColor.GREEN + "✓" : ChatColor.RED + "✖";
            String rarity = variant.getRarity().getColoredDisplayName();
            
            player.sendMessage(ChatColor.GRAY + "  " + status + " " + ChatColor.WHITE + variant.getName() + " " + ChatColor.GRAY + "(" + rarity + ChatColor.GRAY + ")");
            
            if (owned) {
                continue;
            }
            
            // Show acquisition info for unowned heads
            if (variant.requiresPermission()) {
                player.sendMessage(ChatColor.GRAY + "     Requires special permission");
            }
            
            if (variant.getRarity() == HeadRarity.EVENT) {
                player.sendMessage(ChatColor.GRAY + "     Event exclusive");
            }
        }

        // Show rewards if complete
        if (isComplete && collection.getRewards().hasRewards()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "✓ Collection Complete! Rewards claimed.");
        }
    }

    /**
     * Allow players to claim rewards for a completed collection.
     */
    private void claimCollectionReward(Player player, String collectionId) {
        HeadCollection collection = cosmeticItem.getCollection(collectionId);
        if (collection == null) {
            player.sendMessage(ChatColor.RED + "✖ Collection not found: " + collectionId);
            return;
        }
        Map<String, Double> progress = cosmeticItem.getPlayerCollectionProgress(player);
        double completionPercent = progress.getOrDefault(collection.getId(), 0.0) * 100;
        if (completionPercent < 100.0) {
            player.sendMessage(ChatColor.YELLOW + "⚠ You must complete the collection to claim rewards.");
            return;
        }
        if (!collection.getRewards().hasRewards()) {
            player.sendMessage(ChatColor.YELLOW + "⚠ No rewards available for this collection.");
            return;
        }
        // Award rewards (delegates to CosmeticItem)
        cosmeticItem.awardCollectionRewards(player, collection, collection.getRewards());
        player.sendMessage(ChatColor.GREEN + "✓ Rewards claimed for collection: " + collection.getName());
    }

    /**
     * Show detailed progress for all collections.
     */
    private void showProgress(Player player) {
        player.sendMessage(ChatColor.YELLOW + "⚙ " + ChatColor.BOLD + "Your Collection Progress");
        player.sendMessage("");

        // Use CollectionManager from ItemManager for progress tracking
        CollectionManager collectionManager = plugin.getItemManager().getCollectionManager();
        Map<String, ItemCollection> allCollections = collectionManager.getAllCollections();

        if (allCollections.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "⚠ No collections available");
            return;
        }

        for (org.fourz.RVNKLore.lore.item.collection.ItemCollection collection : allCollections.values()) {
            double progress = collectionManager.getPlayerProgress(player.getUniqueId(), collection.getId());
            double percent = progress * 100;

            String status = percent >= 100.0 ? ChatColor.GREEN + "✓ COMPLETE" : ChatColor.YELLOW + String.format("%.1f%%", percent);

            player.sendMessage(ChatColor.WHITE + collection.getName() + ": " + status);

            if (percent < 100.0) {
                int totalItems = collection.getItemCount();
                int ownedItems = (int) (totalItems * progress);
                player.sendMessage(ChatColor.GRAY + "   " + ownedItems + "/" + totalItems + " items collected");
            }
        }
    }

    /**
     * List all available themes.
     */
    private void listThemes(Player player) {
        player.sendMessage(ChatColor.YELLOW + "⚙ " + ChatColor.BOLD + "Available Themes");
        player.sendMessage("");
        
        for (CollectionTheme theme : CollectionTheme.values()) {
            int collectionCount = cosmeticItem.getCollectionsByTheme(theme).size();
            if (collectionCount > 0) {
                player.sendMessage(ChatColor.WHITE + theme.getDisplayName() + " " + ChatColor.GRAY + "(" + collectionCount + " collections)");
                player.sendMessage(ChatColor.GRAY + "   " + theme.getDescription());
            }
        }
        
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "   Use " + ChatColor.WHITE + "/lore collection theme <name> " + ChatColor.GRAY + "to view theme collections");
    }

    /**
     * Show collections for a specific theme.
     */
    private void showThemeCollections(Player player, String themeName) {
        CollectionTheme theme = CollectionTheme.fromDisplayName(themeName);
        if (theme == null) {
            player.sendMessage(ChatColor.RED + "✖ Unknown theme: " + themeName);
            listThemes(player);
            return;
        }

        listCollections(player, themeName);
    }

    /**
     * Show command usage information.
     */
    private void showUsage(Player player) {
        player.sendMessage(ChatColor.RED + "▶ Head Collection Commands:");
        player.sendMessage(ChatColor.WHITE + "/lore collection " + ChatColor.GRAY + "- Overview of all collections");
        player.sendMessage(ChatColor.WHITE + "/lore collection list [theme] " + ChatColor.GRAY + "- List collections");
        player.sendMessage(ChatColor.WHITE + "/lore collection view <id> " + ChatColor.GRAY + "- View collection details");
        player.sendMessage(ChatColor.WHITE + "/lore collection progress " + ChatColor.GRAY + "- Show your progress");
        player.sendMessage(ChatColor.WHITE + "/lore collection theme [name] " + ChatColor.GRAY + "- Browse by theme");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Add original commands
            completions.add("view");
            completions.add("claim");
            completions.add("list");
            // Add registered subcommands
            for (String cmd : subCommands.keySet()) {
                if (subCommands.get(cmd).hasPermission(sender)) {
                    completions.add(cmd);
                }
            }
            return completions;
        } else if (args.length > 1) {
            // If first arg is a registered subcommand, delegate completion
            String subCommandName = args[0].toLowerCase();
            if (subCommands.containsKey(subCommandName)) {
                SubCommand subCommand = subCommands.get(subCommandName);
                if (subCommand.hasPermission(sender)) {
                    return subCommand.getTabCompletions(sender, Arrays.copyOfRange(args, 1, args.length));
                }
                return completions;
            }
            
            // ...existing code...
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

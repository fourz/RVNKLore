package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.ItemCollectionDTO;
import org.fourz.RVNKLore.data.repository.CollectionRepository;
import org.fourz.RVNKLore.lore.item.collection.CollectionTheme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /lore collection add command.
 * Allows administrators to define and persist new item collections in the database.
 */
public class LoreCollectionAddSubCommand implements org.fourz.RVNKLore.command.subcommand.SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private final CollectionRepository collectionRepository;

    public LoreCollectionAddSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreCollectionAddSubCommand");
        this.databaseManager = plugin.getDatabaseManager();
        this.collectionRepository = databaseManager.getCollectionRepository();
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.collection.add");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "✖ You don't have permission to use this command");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore collection add <id> <theme> <name> [description]");
            sender.sendMessage(ChatColor.GRAY + "   Create a new collection with the given ID, theme, and name");
            sender.sendMessage(ChatColor.GRAY + "   Valid themes: " + String.join(", ", getThemeNames()));
            return true;
        }

        String collectionId = args[0].toLowerCase();
        String themeStr = args[1].toUpperCase();
        String name = args[2];
        String description = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "";

        // Validate collection ID (no spaces, alphanumeric + underscore only)
        if (!collectionId.matches("^[a-z0-9_]+$")) {
            sender.sendMessage(ChatColor.RED + "✖ Invalid collection ID. Use only lowercase letters, numbers, and underscores.");
            return true;
        }

        // Validate theme
        CollectionTheme theme;
        try {
            theme = CollectionTheme.valueOf(themeStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "✖ Invalid theme: " + themeStr);
            sender.sendMessage(ChatColor.GRAY + "   Valid themes: " + String.join(", ", getThemeNames()));
            return true;
        }

        // Check if collection already exists (async)
        collectionRepository.getAllCollections().thenAccept(collections -> {
            boolean exists = collections.stream().anyMatch(c -> c.getId().equalsIgnoreCase(collectionId));
            if (exists) {
                sender.sendMessage(ChatColor.RED + "✖ A collection with ID '" + collectionId + "' already exists.");
                return;
            }

            // Build DTO and save
            ItemCollectionDTO dto = new ItemCollectionDTO();
            dto.setId(collectionId);
            dto.setName(name);
            dto.setDescription(description);
            dto.setThemeId(theme.name().toLowerCase());
            dto.setActive(true);
            dto.setCreatedAt(System.currentTimeMillis());
            dto.setUpdatedAt(System.currentTimeMillis());
            collectionRepository.saveCollectionDTO(dto).thenAccept(saved -> {
                if (saved) {
                    sender.sendMessage(ChatColor.GREEN + "&a✓ Created collection: " + name + " (" + collectionId + ")");
                    sender.sendMessage(ChatColor.GRAY + "&7   Theme: " + theme.getDisplayName());
                    sender.sendMessage(ChatColor.GRAY + "&7   Use '/lore collection view " + collectionId + "' to see details.");
                } else {
                    sender.sendMessage(ChatColor.RED + "&c✖ Failed to save collection to database.");
                }
            }).exceptionally(e -> {
                logger.error("Error saving collection to database: " + collectionId, e);
                sender.sendMessage(ChatColor.RED + "&c✖ An error occurred while saving the collection.");
                return null;
            });
        }).exceptionally(e -> {
            logger.error("Error checking for existing collections", e);
            sender.sendMessage(ChatColor.RED + "&c✖ An error occurred while checking for existing collections.");
            return null;
        });
        return true;
    }

    private List<String> getThemeNames() {
        return Arrays.stream(CollectionTheme.values())
            .map(Enum::name)
            .collect(Collectors.toList());
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!hasPermission(sender)) {
            return completions;
        }
        
        if (args.length == 1) {
            // Suggest a default collection ID format
            completions.add("collection_name");
        } else if (args.length == 2) {
            // Suggest themes
            completions.addAll(getThemeNames());
        } else if (args.length == 3) {
            // Suggest a display name
            completions.add("\"Collection Display Name\"");
        } else if (args.length == 4) {
            // Suggest a description
            completions.add("\"Collection description goes here\"");
        }
        
        return completions;
    }

    @Override
    public String getDescription() {
        return "Add a new collection to the lore system";
    }
}

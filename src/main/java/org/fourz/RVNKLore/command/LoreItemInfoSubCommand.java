package org.fourz.RVNKLore.command;

import org.bukkit.command.CommandSender;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.command.output.DisplayFactory;
import org.fourz.RVNKLore.command.subcommand.SubCommand;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.ItemPropertiesDTO;
import org.fourz.RVNKLore.debug.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles the /lore item info <item_name> command.
 * Displays detailed information about a registered lore item using DisplayFactory.
 * Also supports lookup by UUID or short UUID.
 * Uses async database methods for retrieving data.
 */
public class LoreItemInfoSubCommand implements SubCommand {    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private final RVNKLore plugin;
    
    public LoreItemInfoSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreItemInfoSubCommand");
        this.databaseManager = plugin.getDatabaseManager();
    }

    public LoreItemInfoSubCommand(RVNKLore plugin, org.fourz.RVNKLore.lore.item.ItemManager itemManager) {
        // Legacy constructor - redirect to primary constructor
        this(plugin);
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.item.give") || sender.hasPermission("rvnklore.command.collection");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (databaseManager == null) {
                sender.sendMessage(org.bukkit.ChatColor.RED + "✖ Database system is not available. Please try again later.");
                logger.error("DatabaseManager is null when trying to list items", null);
                return true;
            }
            
            // List all items using async database method
            databaseManager.getAllItems().thenAccept(items -> {
                List<String> itemNames = new ArrayList<>();
                for (ItemPropertiesDTO item : items) {
                    itemNames.add(item.getDisplayName());
                }
                DisplayFactory.displayPaginatedList(
                    sender, 
                    "Available Items", 
                    itemNames, 
                    1, 
                    50, 
                    s -> org.bukkit.ChatColor.YELLOW + " - " + s
                );
            }).exceptionally(e -> {
                logger.error("Error retrieving items for info command", e);
                sender.sendMessage(org.bukkit.ChatColor.RED + "✖ An error occurred while retrieving items.");
                return null;
            });
            
            return true;
        }
        
        if (args.length > 1) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "▶ Usage: /lore item info <item_name>");
            sender.sendMessage(org.bukkit.ChatColor.GRAY + "   Display information about a registered item");
            return true;
        }
        
        String itemNameOrId = args[0];
        
        if (databaseManager == null) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "✖ Database system is not available. Please try again later.");
            logger.error("DatabaseManager is null when trying to get item info: " + itemNameOrId, null);
            return true;
        }

        // Try to match by UUID or short UUID for LoreEntry
        try {            // First try as full UUID
            try {
                UUID uuid = UUID.fromString(itemNameOrId);
                databaseManager.getLoreEntryRepository().getLoreEntryById(uuid).thenAccept(entryDTO -> {
                    if (entryDTO != null) {
                        // Convert DTO to domain object
                        org.fourz.RVNKLore.lore.LoreEntry loreEntry = entryDTO.toLoreEntry();
                        DisplayFactory.displayLoreEntry(sender, loreEntry);
                    } else {
                        displayItemByName(sender, itemNameOrId);
                    }
                }).exceptionally(e -> {
                    logger.error("Error retrieving lore entry by UUID: " + itemNameOrId, e);
                    displayItemByName(sender, itemNameOrId);
                    return null;
                });
                return true;
            } catch (IllegalArgumentException e) {
                // Not a valid full UUID, try with short UUID (first 8 characters)
                if (itemNameOrId.length() >= 8) {                    databaseManager.getLoreEntryRepository().getAllLoreEntries().thenAccept(entriesDTO -> {
                        boolean found = false;
                        for (org.fourz.RVNKLore.data.dto.LoreEntryDTO entryDTO : entriesDTO) {
                            // Get UUID string and check first 8 chars
                            String uuidStr = entryDTO.getUuidString();
                            if (uuidStr != null && uuidStr.length() >= 8) {
                                String shortId = uuidStr.substring(0, 8);
                                if (shortId.equalsIgnoreCase(itemNameOrId)) {
                                    DisplayFactory.displayLoreEntry(sender, entryDTO.toLoreEntry());
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (!found) {
                            displayItemByName(sender, itemNameOrId);
                        }
                    }).exceptionally(e2 -> {
                        logger.error("Error searching lore entries for short UUID: " + itemNameOrId, e2);
                        displayItemByName(sender, itemNameOrId);
                        return null;
                    });
                    return true;
                }
            }
            
            // If we get here, it's not a UUID or short UUID, try by name
            displayItemByName(sender, itemNameOrId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing item info command: " + itemNameOrId, e);
            sender.sendMessage(org.bukkit.ChatColor.RED + "✖ An error occurred while retrieving item info.");
            return true;
        }
    }
    
    /**
     * Tries to display an item by its name
     */
    private void displayItemByName(CommandSender sender, String itemName) {
        databaseManager.getAllItems().thenAccept(items -> {
            ItemPropertiesDTO matchedItem = null;
            for (ItemPropertiesDTO item : items) {
                if (item.getDisplayName().equalsIgnoreCase(itemName)) {
                    matchedItem = item;
                    break;
                }
            }
              if (matchedItem != null) {
                org.fourz.RVNKLore.lore.item.ItemProperties properties = matchedItem.toItemProperties();
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(properties.getMaterial());
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(properties.getDisplayName());
                    meta.setLore(properties.getLore());
                    if (properties.getCustomModelData() > 0) {
                        meta.setCustomModelData(properties.getCustomModelData());
                    }
                    item.setItemMeta(meta);
                }
                DisplayFactory.displayItem(sender, item, itemName);
            } else {
                sender.sendMessage(org.bukkit.ChatColor.RED + "✖ Item not found: " + itemName);
            }
        }).exceptionally(e -> {
            logger.error("Error retrieving items to match by name: " + itemName, e);
            sender.sendMessage(org.bukkit.ChatColor.RED + "✖ An error occurred while retrieving item info.");
            return null;
        });
    }

    @Override
    public String getDescription() {
        return "Get information about a lore item by name.";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        // Async tab completion is not supported in Bukkit/Spigot, so we return an empty list.
        // TODO: Implement smarter tab completion with caching if needed.
        return List.of();
    }
}

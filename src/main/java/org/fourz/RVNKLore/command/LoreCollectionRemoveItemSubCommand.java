package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.data.ItemRepository;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles the /lore collection removeitem command.
 * Allows administrators to remove items from existing collections and update the database.
 */
public class LoreCollectionRemoveItemSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final CollectionManager collectionManager;

    public LoreCollectionRemoveItemSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreCollectionRemoveItemSubCommand");
        this.collectionManager = plugin.getLoreManager().getItemManager().getCollectionManager();
    }

    @Override
    public String getDescription() {
        return "Remove an item from a collection";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.collection.removeitem") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "✖ You don't have permission to use this command");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "▶ This command can only be used by players");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore collection removeitem <collection_id> <material> [quantity]");
            sender.sendMessage(ChatColor.GRAY + "   Remove an item from a collection by material type");
            return true;
        }

        Player player = (Player) sender;
        String collectionId = args[0].toLowerCase();
        String materialStr = args[1].toUpperCase();
        int quantity = 1;

        if (args.length > 2) {
            try {
                quantity = Integer.parseInt(args[2]);
                if (quantity < 1) {
                    sender.sendMessage(ChatColor.RED + "✖ Quantity must be positive");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "✖ Invalid quantity: " + args[2]);
                return true;
            }
        }

        // Check if collection exists
        ItemCollection collection = collectionManager.getCollectionSync(collectionId);
        if (collection == null) {
            sender.sendMessage(ChatColor.RED + "✖ Collection not found: " + collectionId);
            return true;
        }

        // Validate material
        Material material;
        try {
            material = Material.valueOf(materialStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "✖ Invalid material: " + materialStr);
            return true;
        }

        try {
            // Create ItemStack to identify items to remove
            ItemStack item = new ItemStack(material, quantity);

            // Find and remove items from in-memory collection
            int removed = 0;
            List<ItemStack> collectionItems = collection.getItems();
            List<ItemStack> toRemove = new ArrayList<>();

            for (ItemStack collectionItem : collectionItems) {
                if (collectionItem.isSimilar(item)) {
                    toRemove.add(collectionItem);
                    removed++;
                    if (removed >= quantity) {
                        break;
                    }
                }
            }

            if (removed == 0) {
                sender.sendMessage(ChatColor.YELLOW + "⚠ No matching items found in collection: " + collection.getName());
                return true;
            }

            // Remove from in-memory collection
            for (ItemStack removeItem : toRemove) {
                collection.removeItem(removeItem);
            }

            // Try to remove from database
            if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
                ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());

                boolean deleted = repository.removeItemFromCollection(
                    getItemIdForMaterial(material),
                    getCollectionDatabaseId(collectionId)
                ).join();

                if (deleted) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Removed " + removed + "x " + materialStr + " from collection: " + collection.getName());
                    sender.sendMessage(ChatColor.GRAY + "   Item count: " + collection.getItemCount());
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ Removed item from collection in memory, but database delete failed.");
                    sender.sendMessage(ChatColor.GRAY + "   Changes may not persist across restarts.");
                }
            } else {
                sender.sendMessage(ChatColor.YELLOW + "⚠ Removed item from collection in memory only (database not available)");
                sender.sendMessage(ChatColor.GRAY + "   Changes will not persist across restarts.");
            }

            logger.info(player.getName() + " removed items from collection: " + collectionId);
            return true;
        } catch (Exception e) {
            logger.error("Error removing item from collection: " + collectionId, e);
            sender.sendMessage(ChatColor.RED + "✖ Error: " + e.getMessage());
            return true;
        }
    }

    /**
     * Get the database ID for a collection by its string ID.
     */
    private int getCollectionDatabaseId(String collectionId) {
        try {
            ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
            java.util.Map<Integer, String> allCollections = repository.getAllCollections().join();
            for (java.util.Map.Entry<Integer, String> entry : allCollections.entrySet()) {
                if (entry.getValue().equals(collectionId)) {
                    return entry.getKey();
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to lookup collection database ID: " + collectionId);
        }
        return -1;
    }

    /**
     * Get or create item ID for a material type.
     * For Phase 3, we use a simplified approach: materials are stored with their name as ID.
     */
    private int getItemIdForMaterial(Material material) {
        try {
            ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
            // Try to find existing item with this material
            // For now, return a placeholder - actual implementation would query lore_item table
            return material.ordinal(); // Use Material ordinal as temporary ID
        } catch (Exception e) {
            logger.debug("Failed to get item ID for material: " + material.name());
            return material.ordinal();
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete collection IDs
            completions.addAll(collectionManager.getAllCollectionsSync().keySet());
        } else if (args.length == 2) {
            // Get items in selected collection and suggest materials
            if (args.length > 0) {
                ItemCollection collection = collectionManager.getCollectionSync(args[0].toLowerCase());
                if (collection != null) {
                    for (ItemStack item : collection.getItems()) {
                        String materialName = item.getType().name();
                        if (!completions.contains(materialName)) {
                            completions.add(materialName);
                        }
                    }
                }
            }
        }

        return completions;
    }
}

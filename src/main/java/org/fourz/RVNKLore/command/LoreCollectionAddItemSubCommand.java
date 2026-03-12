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
 * Handles the /lore collection additem command.
 * Allows administrators to add items to existing collections and persist them to the database.
 */
public class LoreCollectionAddItemSubCommand implements SubCommand {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final CollectionManager collectionManager;

    public LoreCollectionAddItemSubCommand(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreCollectionAddItemSubCommand");
        this.collectionManager = plugin.getLoreManager().getItemManager().getCollectionManager();
    }

    @Override
    public String getDescription() {
        return "Add an item to a collection";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("rvnklore.admin.collection.additem");
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
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore collection additem <collection_id> <material> [quantity]");
            sender.sendMessage(ChatColor.GRAY + "   Add an item to a collection by material type");
            return true;
        }

        Player player = (Player) sender;
        String collectionId = args[0].toLowerCase();
        String materialStr = args[1].toUpperCase();
        int quantity = 1;

        if (args.length > 2) {
            try {
                quantity = Integer.parseInt(args[2]);
                if (quantity < 1 || quantity > 64) {
                    sender.sendMessage(ChatColor.RED + "✖ Quantity must be between 1 and 64");
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
            // Create ItemStack with the specified material and quantity
            ItemStack item = new ItemStack(material, quantity);

            // Add to in-memory collection
            collection.addItem(item);

            // Get the database IDs and persist to database
            if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
                ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());

                // Try to get collection ID from database
                int nextSequence = collection.getItemCount(); // Use current count as sequence number
                boolean saved = repository.addItemToCollection(
                    getItemIdForMaterial(material),
                    getCollectionDatabaseId(collectionId),
                    nextSequence,
                    null
                ).join();

                if (saved) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Added " + quantity + "x " + materialStr + " to collection: " + collection.getName());
                    sender.sendMessage(ChatColor.GRAY + "   Item count: " + collection.getItemCount());
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ Added item to collection in memory, but database save failed.");
                    sender.sendMessage(ChatColor.GRAY + "   Changes will not persist across restarts.");
                }
            } else {
                sender.sendMessage(ChatColor.YELLOW + "⚠ Added item to collection in memory only (database not available)");
                sender.sendMessage(ChatColor.GRAY + "   Changes will not persist across restarts.");
            }

            logger.info(player.getName() + " added item to collection: " + collectionId);
            return true;
        } catch (Exception e) {
            logger.error("Error adding item to collection: " + collectionId, e);
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
            // Complete material names
            for (Material material : Material.values()) {
                if (!material.isAir() && !material.isBlock()) {
                    completions.add(material.name());
                }
            }
        } else if (args.length == 3) {
            // Complete quantity suggestions
            completions.addAll(java.util.Arrays.asList("1", "8", "16", "32", "64"));
        }

        return completions;
    }
}

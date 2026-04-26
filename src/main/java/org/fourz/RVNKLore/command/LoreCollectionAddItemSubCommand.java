package org.fourz.RVNKLore.command;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.data.ItemRepository;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
        return sender.hasPermission("rvnklore.admin.collection.additem") || sender.hasPermission("rvnklore.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "✖ You don't have permission to use this command");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ Usage: /lore collection additem <collection_id> <entry_name|material> [quantity]");
            sender.sendMessage(ChatColor.GRAY + "   Add a lore entry or material item to a collection");
            return true;
        }

        String collectionId = args[0].toLowerCase();
        String itemArg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        // Check if collection exists
        ItemCollection collection = collectionManager.getCollectionSync(collectionId);
        if (collection == null) {
            sender.sendMessage(ChatColor.RED + "✖ Collection not found: " + collectionId);
            return true;
        }

        // Try lore entry name first
        LoreEntry entry = plugin.getLoreManager().getLoreEntryByNameSync(itemArg);
        if (entry == null) {
            // DB fallback (handles unapproved entries not in cache)
            entry = plugin.getDatabaseManager().getAllLoreEntries().stream()
                    .filter(e -> e.getName() != null && e.getName().equalsIgnoreCase(itemArg))
                    .findFirst().orElse(null);
        }
        if (entry != null) {
            UUID entryUuid = entry.getUUID();
            boolean added = collectionManager.addEntryToCollectionSync(collectionId, entryUuid);
            if (added) {
                sender.sendMessage(ChatColor.GREEN + "✓ Added lore entry '" + entry.getName() + "' to collection: " + collection.getName());
                logger.info(sender.getName() + " added entry " + entry.getName() + " to collection " + collectionId);
            } else {
                sender.sendMessage(ChatColor.RED + "✖ Failed to add entry to collection (already present or DB error)");
            }
            return true;
        }

        // Fall back to material (single arg only)
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "✖ Material-based additem requires a player. Use an entry name instead.");
            return true;
        }

        Player player = (Player) sender;
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
                sender.sendMessage(ChatColor.RED + "✖ No lore entry found and invalid material: " + itemArg);
                return true;
            }
        }

        Material material;
        try {
            material = Material.valueOf(materialStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "✖ No lore entry found and invalid material: " + itemArg);
            return true;
        }

        try {
            ItemStack item = new ItemStack(material, quantity);
            collection.addItem(item);

            if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
                ItemRepository repository = new ItemRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
                int nextSequence = collection.getItemCount();
                boolean saved = repository.addItemToCollection(
                    getItemIdForMaterial(material),
                    getCollectionDatabaseId(collectionId),
                    nextSequence,
                    null
                ).join();

                if (saved) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Added " + quantity + "x " + materialStr + " to collection: " + collection.getName());
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "⚠ Added item in memory only — database save failed.");
                }
            } else {
                sender.sendMessage(ChatColor.YELLOW + "⚠ Added item in memory only (database not available)");
            }

            logger.info(player.getName() + " added material " + materialStr + " to collection: " + collectionId);
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
            String partial = args[0].toLowerCase();
            collectionManager.getAllCollectionsSync().keySet().stream()
                    .filter(id -> id.startsWith(partial))
                    .forEach(completions::add);
        } else if (args.length >= 2) {
            // Suggest approved lore entry names first, then materials
            String partial = args[args.length - 1].toLowerCase();
            plugin.getLoreManager().getAllLoreEntriesSync().stream()
                    .filter(e -> e.isApproved() && e.getName() != null && e.getName().toLowerCase().startsWith(partial))
                    .map(e -> e.getName())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(10)
                    .forEach(completions::add);
            if (completions.isEmpty()) {
                for (Material material : Material.values()) {
                    if (!material.isAir() && material.name().toLowerCase().startsWith(partial)) {
                        completions.add(material.name());
                    }
                }
            }
        }

        return completions;
    }
}

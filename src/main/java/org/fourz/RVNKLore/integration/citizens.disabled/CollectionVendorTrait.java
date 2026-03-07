package org.fourz.RVNKLore.integration.citizens;

import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.util.DataKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Citizens NPC trait for collection item vendors.
 * NPCs with this trait display collection items for purchase or trade.
 */
public class CollectionVendorTrait extends Trait {

    private final RVNKLore plugin;
    private final LogManager logger;
    private String collectionId = "";
    private double priceMultiplier = 1.5;
    private boolean stackable = true;

    public CollectionVendorTrait(RVNKLore plugin) {
        super("collection_vendor");
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CollectionVendorTrait");
    }

    @Override
    public void onSpawn() {
        if (collectionId.isEmpty()) {
            logger.debug("NPC " + npc.getId() + " spawned without collection ID assigned");
        } else {
            logger.debug("Collection vendor NPC spawned for collection: " + collectionId);
        }
    }

    /**
     * Handle player right-click interaction with the NPC.
     */
    public void openShop(Player player) {
        if (collectionId.isEmpty()) {
            player.sendMessage("§cThis vendor is not configured.");
            return;
        }

        // Get the collection (async operation)
        CompletableFuture<Optional<ItemCollection>> collectionFuture = plugin.getLoreManager()
            .getItemManager()
            .getCollectionManager()
            .getCollection(collectionId);

        collectionFuture.thenAccept(collection -> {
            if (!collection.isPresent()) {
                player.sendMessage("§cCollection not found: " + collectionId);
                logger.warning("NPC " + npc.getId() + " references non-existent collection: " + collectionId);
                return;
            }

            // Schedule GUI open on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Open the shop GUI
                CollectionShopGUI shopGUI = new CollectionShopGUI(plugin, player, collection.get(), this);
                shopGUI.open();
            });
        }).exceptionally(ex -> {
            logger.warning("Error opening collection shop: " + ex.getMessage());
            player.sendMessage("§cCould not open shop. Please try again.");
            return null;
        });
    }

    /**
     * Process purchase of an item from the collection.
     */
    public boolean purchaseItem(Player player, ItemStack item, double price) {
        // TODO: Integrate with economy system (Vault)
        // For now, just add item to inventory

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cYour inventory is full!");
            return false;
        }

        player.getInventory().addItem(item);
        player.sendMessage("§a✓ Purchased " + item.getAmount() + "x " +
            (item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : item.getType().name()) +
            " for §6" + String.format("%.2f", price) + " coins");

        // Track purchase (distinguish from discovery)
        trackPurchase(player.getUniqueId(), item);

        return true;
    }

    /**
     * Track that a player purchased an item from this vendor.
     * Distinguishes purchased items from discovered items in collection tracking.
     */
    private void trackPurchase(UUID playerId, ItemStack item) {
        // TODO: Add purchase tracking to database
        // This could use a separate column in player_collection_items
        // or a new table tracking purchase_price and purchase_date
    }

    /**
     * Set the collection this vendor sells from.
     */
    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    /**
     * Get the collection ID this vendor sells from.
     */
    public String getCollectionId() {
        return collectionId;
    }

    /**
     * Set the price multiplier for items (e.g., 1.5 = 150% of item value).
     */
    public void setPriceMultiplier(double multiplier) {
        this.priceMultiplier = multiplier;
    }

    /**
     * Get the price multiplier.
     */
    public double getPriceMultiplier() {
        return priceMultiplier;
    }

    /**
     * Set whether items are stackable in vendor purchases.
     */
    public void setStackable(boolean stackable) {
        this.stackable = stackable;
    }

    /**
     * Check if items are stackable.
     */
    public boolean isStackable() {
        return stackable;
    }

    /**
     * Save trait data to Citizens storage.
     */
    @Override
    public void save(DataKey key) {
        key.setString("collection-id", collectionId);
        key.setDouble("price-multiplier", priceMultiplier);
        key.setBoolean("stackable", stackable);
    }

    /**
     * Load trait data from Citizens storage.
     */
    @Override
    public void load(DataKey key) {
        this.collectionId = key.getString("collection-id", "");
        this.priceMultiplier = key.getDouble("price-multiplier", 1.5);
        this.stackable = key.getBoolean("stackable", true);
    }
}

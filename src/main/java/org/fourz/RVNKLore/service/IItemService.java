package org.fourz.RVNKLore.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.ItemType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for lore item operations.
 * Exposes item creation and management for cross-plugin access via RVNKCore ServiceRegistry.
 */
public interface IItemService {

    /**
     * Create a lore item by name.
     *
     * @param itemName The name of the item to create
     * @return Future containing the created ItemStack, or empty if not found
     */
    CompletableFuture<Optional<ItemStack>> createLoreItem(String itemName);

    /**
     * Create a lore item with specific properties.
     *
     * @param type The item type
     * @param name The item name
     * @param properties The item properties
     * @return Future containing the created ItemStack
     */
    CompletableFuture<ItemStack> createLoreItem(ItemType type, String name, ItemProperties properties);

    /**
     * Give a lore item to a player.
     *
     * @param itemName The name of the item
     * @param player The player to give the item to
     * @return Future containing true if successful
     */
    CompletableFuture<Boolean> giveItemToPlayer(String itemName, Player player);

    /**
     * Get all registered item names.
     *
     * @return Future containing list of item names
     */
    CompletableFuture<List<String>> getAllItemNames();

    /**
     * Get all items with their properties.
     *
     * @return Future containing list of item properties
     */
    CompletableFuture<List<ItemProperties>> getAllItemsWithProperties();

    /**
     * Register a new lore item.
     *
     * @param loreEntryId The UUID of the associated lore entry
     * @param properties The item properties
     * @return Future containing true if successful
     */
    CompletableFuture<Boolean> registerLoreItem(UUID loreEntryId, ItemProperties properties);

    /**
     * Refresh the item cache from the database.
     *
     * @return Future that completes when cache is refreshed
     */
    CompletableFuture<Void> refreshCache();

    /**
     * Check if the service is in fallback mode due to errors.
     *
     * @return true if operating in degraded mode
     */
    boolean isInFallbackMode();
}

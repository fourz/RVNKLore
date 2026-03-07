package org.fourz.RVNKLore.service;

import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for item collection operations.
 * Exposes collection management for cross-plugin access via RVNKCore ServiceRegistry.
 */
public interface ICollectionService {

    /**
     * Create a new item collection.
     *
     * @param id The unique collection ID
     * @param name The collection display name
     * @param description The collection description
     * @return Future containing the created collection, or empty on failure
     */
    CompletableFuture<Optional<ItemCollection>> createCollection(String id, String name, String description);

    /**
     * Get a collection by its ID.
     *
     * @param id The collection ID
     * @return Future containing the collection, or empty if not found
     */
    CompletableFuture<Optional<ItemCollection>> getCollection(String id);

    /**
     * Get all collections.
     *
     * @return Future containing map of collection ID to collection
     */
    CompletableFuture<Map<String, ItemCollection>> getAllCollections();

    /**
     * Get collections by theme.
     *
     * @param themeId The theme ID to filter by
     * @return Future containing map of matching collections
     */
    CompletableFuture<Map<String, ItemCollection>> getCollectionsByTheme(String themeId);

    /**
     * Add an item to a collection.
     *
     * @param collectionId The collection ID
     * @param item The item to add
     * @return Future containing true if successful
     */
    CompletableFuture<Boolean> addItemToCollection(String collectionId, ItemStack item);

    /**
     * Remove an item from a collection.
     *
     * @param collectionId The collection ID
     * @param item The item to remove
     * @return Future containing true if successful
     */
    CompletableFuture<Boolean> removeItemFromCollection(String collectionId, ItemStack item);

    /**
     * Get items in a collection.
     *
     * @param collectionId The collection ID
     * @return Future containing list of items in the collection
     */
    CompletableFuture<List<ItemStack>> getCollectionItems(String collectionId);

    /**
     * Get player's progress on a collection.
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @return Future containing progress as percentage (0.0 to 1.0)
     */
    CompletableFuture<Double> getPlayerProgress(UUID playerId, String collectionId);

    /**
     * Update player's progress on a collection.
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @param progress The new progress value (0.0 to 1.0)
     * @return Future containing true if successful
     */
    CompletableFuture<Boolean> updatePlayerProgress(UUID playerId, String collectionId, double progress);

    /**
     * Grant collection completion reward to a player.
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @return Future containing true if reward granted
     */
    CompletableFuture<Boolean> grantCollectionReward(UUID playerId, String collectionId);

    /**
     * Save a collection to the database.
     *
     * @param collection The collection to save
     * @return Future containing true if successful
     */
    CompletableFuture<Boolean> saveCollection(ItemCollection collection);

    /**
     * Check if the service is in fallback mode due to errors.
     *
     * @return true if operating in degraded mode
     */
    boolean isInFallbackMode();

    /**
     * Track individual item discovery for a player in a collection.
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @param itemId The item ID being discovered
     * @return Future containing true if successfully recorded
     */
    CompletableFuture<Boolean> trackItemDiscovery(UUID playerId, String collectionId, int itemId);

    /**
     * Get all items discovered by a player in a collection.
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @return Future containing list of discovered item properties
     */
    CompletableFuture<List<ItemStack>> getPlayerCollectionItems(UUID playerId, String collectionId);

    /**
     * Get count of items collected by a player in a collection.
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @return Future containing count of discovered items
     */
    CompletableFuture<Integer> getCollectedItemCount(UUID playerId, String collectionId);

    /**
     * Get items NOT yet discovered by a player in a collection.
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @return Future containing list of missing item properties
     */
    CompletableFuture<List<ItemStack>> getMissingItems(UUID playerId, String collectionId);

    /**
     * Calculate progress based on individual items collected vs total.
     *
     * @param playerId The player UUID
     * @param collectionId The collection ID
     * @return Future containing progress as percentage (0.0 to 1.0)
     */
    CompletableFuture<Double> calculateItemBasedProgress(UUID playerId, String collectionId);
}

package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;
import org.json.simple.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for Item database operations.
 * Provides abstraction for CRUD operations on lore items and collections.
 *
 * All methods return CompletableFuture<T> for async operations per RVNKCore standard.
 */
public interface IItemRepository {

    // ==================== Item Operations ====================

    /**
     * Get an item by its ID.
     *
     * @param itemId The ID of the item to retrieve
     * @return CompletableFuture that completes with Optional containing the item properties, or empty if not found
     */
    CompletableFuture<Optional<ItemProperties>> getItemById(int itemId);

    /**
     * Get an item by its name.
     *
     * @param name The name of the item to retrieve
     * @return CompletableFuture that completes with Optional containing the item properties, or empty if not found
     */
    CompletableFuture<Optional<ItemProperties>> getItemByName(String name);

    /**
     * Get all items with a specific name.
     *
     * @param name The name of the items to retrieve
     * @return CompletableFuture that completes with a list of all items with the given name
     */
    CompletableFuture<List<ItemProperties>> getAllItemsByName(String name);

    /**
     * Get an item by its lore entry UUID.
     *
     * @param loreEntryId The UUID of the lore entry
     * @return CompletableFuture that completes with Optional containing the item properties, or empty if not found
     */
    CompletableFuture<Optional<ItemProperties>> getItemByLoreEntryId(String loreEntryId);

    /**
     * Get all items of a specific type.
     *
     * @param itemType The type of items to retrieve
     * @return CompletableFuture that completes with a list of item properties
     */
    CompletableFuture<List<ItemProperties>> getItemsByType(String itemType);

    /**
     * Get all items.
     *
     * @return CompletableFuture that completes with a list of all item properties
     */
    CompletableFuture<List<ItemProperties>> getAllItems();

    /**
     * Insert a new item into the database.
     *
     * @param properties The item properties to save
     * @return CompletableFuture that completes with the ID of the new item, or -1 if insert failed
     */
    CompletableFuture<Integer> insertItem(ItemProperties properties);

    /**
     * Update an existing item in the database.
     *
     * @param itemId The ID of the item to update
     * @param properties The updated item properties
     * @return CompletableFuture that completes with true if the update was successful
     */
    CompletableFuture<Boolean> updateItem(int itemId, ItemProperties properties);

    /**
     * Delete an item from the database.
     *
     * @param itemId The ID of the item to delete
     * @return CompletableFuture that completes with true if the delete was successful
     */
    CompletableFuture<Boolean> deleteItem(int itemId);

    /**
     * Delete an item by its name.
     *
     * @param name The name of the item to delete
     * @return CompletableFuture that completes with true if deletion was successful
     */
    CompletableFuture<Boolean> deleteItemByName(String name);

    /**
     * Get the current database ID for an item by name.
     *
     * @param name The name of the item
     * @return CompletableFuture that completes with the database ID, or -1 if not found
     */
    CompletableFuture<Integer> getCurrentItemId(String name);

    /**
     * Get all database IDs for items with a specific name.
     *
     * @param name The name of the items to find
     * @return CompletableFuture that completes with a list of database IDs, or empty list if none found
     */
    CompletableFuture<List<Integer>> getAllItemIdsByName(String name);

    // ==================== Collection Operations ====================

    /**
     * Get all items in a collection.
     *
     * @param collectionId The ID of the collection
     * @return CompletableFuture that completes with a list of item properties in the collection
     */
    CompletableFuture<List<ItemProperties>> getItemsByCollection(int collectionId);

    /**
     * Get all collections containing an item.
     *
     * @param itemId The ID of the item
     * @return CompletableFuture that completes with a map of collection IDs to collection names
     */
    CompletableFuture<Map<Integer, String>> getCollectionsByItem(int itemId);

    /**
     * Get all collections.
     *
     * @return CompletableFuture that completes with a map of collection IDs to collection names
     */
    CompletableFuture<Map<Integer, String>> getAllCollections();

    /**
     * Get collection details by ID.
     *
     * @param collectionId The ID of the collection to retrieve
     * @return CompletableFuture that completes with a map containing collection details
     */
    CompletableFuture<Map<String, String>> getCollectionDetails(int collectionId);

    /**
     * Create a new collection.
     *
     * @param name The name of the collection
     * @param description The collection description
     * @param theme The collection theme
     * @return CompletableFuture that completes with the ID of the new collection, or -1 if creation failed
     */
    CompletableFuture<Integer> createCollection(String name, String description, String theme);

    /**
     * Update collection properties.
     *
     * @param collectionId The ID of the collection to update
     * @param name The new name (or null to keep existing)
     * @param description The new description (or null to keep existing)
     * @param theme The new theme (or null to keep existing)
     * @return CompletableFuture that completes with true if update was successful
     */
    CompletableFuture<Boolean> updateCollection(int collectionId, String name, String description, String theme);

    /**
     * Add an item to a collection.
     *
     * @param itemId The ID of the item
     * @param collectionId The ID of the collection
     * @param sequenceNumber The order in which to display the item
     * @param itemConfig Additional configuration for the item in this collection
     * @return CompletableFuture that completes with true if the addition was successful
     */
    CompletableFuture<Boolean> addItemToCollection(int itemId, int collectionId, int sequenceNumber, JSONObject itemConfig);

    /**
     * Remove an item from a collection.
     *
     * @param itemId The ID of the item
     * @param collectionId The ID of the collection
     * @return CompletableFuture that completes with true if the removal was successful
     */
    CompletableFuture<Boolean> removeItemFromCollection(int itemId, int collectionId);

    /**
     * Add multiple items to a collection in a single transaction.
     *
     * @param collectionId The collection ID
     * @param itemIds List of item IDs to add
     * @param startingSequence Starting sequence number (optional)
     * @return CompletableFuture that completes with true if all items were added successfully
     */
    CompletableFuture<Boolean> addItemsToCollection(int collectionId, List<Integer> itemIds, Integer startingSequence);

    /**
     * Update the sequence numbers for items in a collection.
     *
     * @param collectionId The collection ID
     * @param itemSequences Map of item IDs to their new sequence numbers
     * @return CompletableFuture that completes with true if the update was successful
     */
    CompletableFuture<Boolean> updateCollectionSequences(int collectionId, Map<Integer, Integer> itemSequences);

    /**
     * Save a collection to the database.
     *
     * @param collection The collection to save
     * @return CompletableFuture that completes with true if successfully saved
     */
    CompletableFuture<Boolean> saveCollection(ItemCollection collection);

    /**
     * Load all collections from the database.
     *
     * @return CompletableFuture that completes with a list of all collections
     */
    CompletableFuture<List<ItemCollection>> loadAllCollections();

    // ==================== Player Progress Operations ====================

    /**
     * Get a player's progress for a specific collection.
     *
     * @param playerId The player's UUID as string
     * @param collectionId The collection identifier
     * @return CompletableFuture that completes with progress value between 0.0 and 1.0
     */
    CompletableFuture<Double> getPlayerCollectionProgress(String playerId, String collectionId);

    /**
     * Update a player's progress for a collection.
     *
     * @param playerId The player's UUID as string
     * @param collectionId The collection identifier
     * @param progress Progress value between 0.0 and 1.0
     * @return CompletableFuture that completes with true if successfully updated
     */
    CompletableFuture<Boolean> updatePlayerCollectionProgress(String playerId, String collectionId, double progress);

    /**
     * Mark a collection as completed by a player.
     *
     * @param playerId The player's UUID as string
     * @param collectionId The collection identifier
     * @param completedAt Timestamp when the collection was completed
     * @return CompletableFuture that completes with true if successfully marked as completed
     */
    CompletableFuture<Boolean> markCollectionCompleted(String playerId, String collectionId, long completedAt);

    /**
     * Get all collections completed by a player.
     *
     * @param playerId The player's UUID as string
     * @return CompletableFuture that completes with a list of collection IDs completed by the player
     */
    CompletableFuture<List<String>> getCompletedCollections(String playerId);

    /**
     * Get progress for all collections for a player.
     *
     * @param playerId The player's UUID as string
     * @return CompletableFuture that completes with a map of collection IDs to progress values
     */
    CompletableFuture<Map<String, Double>> getAllPlayerProgress(String playerId);

    /**
     * Check if the repository is operating in fallback mode.
     * Fallback mode indicates degraded operation due to database connectivity issues.
     *
     * @return true if in fallback mode, false otherwise
     */
    boolean isInFallbackMode();
}

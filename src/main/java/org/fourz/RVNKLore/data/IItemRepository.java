package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;
import org.json.simple.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * Repository interface for Item database operations.
 * Provides abstraction for CRUD operations on lore items and collections.
 */
public interface IItemRepository {

    // ==================== Item Operations ====================

    /**
     * Get an item by its ID.
     *
     * @param itemId The ID of the item to retrieve
     * @return The item properties, or null if not found
     */
    ItemProperties getItemById(int itemId);

    /**
     * Get an item by its name.
     *
     * @param name The name of the item to retrieve
     * @return The item properties, or null if not found
     */
    ItemProperties getItemByName(String name);

    /**
     * Get all items with a specific name.
     *
     * @param name The name of the items to retrieve
     * @return A list of all items with the given name
     */
    List<ItemProperties> getAllItemsByName(String name);

    /**
     * Get an item by its lore entry UUID.
     *
     * @param loreEntryId The UUID of the lore entry
     * @return The item properties, or null if not found
     */
    ItemProperties getItemByLoreEntryId(String loreEntryId);

    /**
     * Get all items of a specific type.
     *
     * @param itemType The type of items to retrieve
     * @return A list of item properties
     */
    List<ItemProperties> getItemsByType(String itemType);

    /**
     * Get all items.
     *
     * @return A list of all item properties
     */
    List<ItemProperties> getAllItems();

    /**
     * Insert a new item into the database.
     *
     * @param properties The item properties to save
     * @return The ID of the new item, or -1 if insert failed
     */
    int insertItem(ItemProperties properties);

    /**
     * Update an existing item in the database.
     *
     * @param itemId The ID of the item to update
     * @param properties The updated item properties
     * @return True if the update was successful
     */
    boolean updateItem(int itemId, ItemProperties properties);

    /**
     * Delete an item from the database.
     *
     * @param itemId The ID of the item to delete
     * @return True if the delete was successful
     */
    boolean deleteItem(int itemId);

    /**
     * Delete an item by its name.
     *
     * @param name The name of the item to delete
     * @return True if deletion was successful
     */
    boolean deleteItemByName(String name);

    /**
     * Get the current database ID for an item by name.
     *
     * @param name The name of the item
     * @return The database ID, or -1 if not found
     */
    int getCurrentItemId(String name);

    /**
     * Get all database IDs for items with a specific name.
     *
     * @param name The name of the items to find
     * @return List of database IDs, or empty list if none found
     */
    List<Integer> getAllItemIdsByName(String name);

    // ==================== Collection Operations ====================

    /**
     * Get all items in a collection.
     *
     * @param collectionId The ID of the collection
     * @return A list of item properties in the collection
     */
    List<ItemProperties> getItemsByCollection(int collectionId);

    /**
     * Get all collections containing an item.
     *
     * @param itemId The ID of the item
     * @return A map of collection IDs to collection names
     */
    Map<Integer, String> getCollectionsByItem(int itemId);

    /**
     * Get all collections.
     *
     * @return A map of collection IDs to collection names
     */
    Map<Integer, String> getAllCollections();

    /**
     * Get collection details by ID.
     *
     * @param collectionId The ID of the collection to retrieve
     * @return A map containing collection details
     */
    Map<String, String> getCollectionDetails(int collectionId);

    /**
     * Create a new collection.
     *
     * @param name The name of the collection
     * @param description The collection description
     * @param theme The collection theme
     * @return The ID of the new collection, or -1 if creation failed
     */
    int createCollection(String name, String description, String theme);

    /**
     * Update collection properties.
     *
     * @param collectionId The ID of the collection to update
     * @param name The new name (or null to keep existing)
     * @param description The new description (or null to keep existing)
     * @param theme The new theme (or null to keep existing)
     * @return True if update was successful
     */
    boolean updateCollection(int collectionId, String name, String description, String theme);

    /**
     * Add an item to a collection.
     *
     * @param itemId The ID of the item
     * @param collectionId The ID of the collection
     * @param sequenceNumber The order in which to display the item
     * @param itemConfig Additional configuration for the item in this collection
     * @return True if the addition was successful
     */
    boolean addItemToCollection(int itemId, int collectionId, int sequenceNumber, JSONObject itemConfig);

    /**
     * Remove an item from a collection.
     *
     * @param itemId The ID of the item
     * @param collectionId The ID of the collection
     * @return True if the removal was successful
     */
    boolean removeItemFromCollection(int itemId, int collectionId);

    /**
     * Add multiple items to a collection in a single transaction.
     *
     * @param collectionId The collection ID
     * @param itemIds List of item IDs to add
     * @param startingSequence Starting sequence number (optional)
     * @return True if all items were added successfully
     */
    boolean addItemsToCollection(int collectionId, List<Integer> itemIds, Integer startingSequence);

    /**
     * Update the sequence numbers for items in a collection.
     *
     * @param collectionId The collection ID
     * @param itemSequences Map of item IDs to their new sequence numbers
     * @return True if the update was successful
     */
    boolean updateCollectionSequences(int collectionId, Map<Integer, Integer> itemSequences);

    /**
     * Save a collection to the database.
     *
     * @param collection The collection to save
     * @return True if successfully saved
     */
    boolean saveCollection(ItemCollection collection);

    /**
     * Load all collections from the database.
     *
     * @return List of all collections
     */
    List<ItemCollection> loadAllCollections();

    // ==================== Player Progress Operations ====================

    /**
     * Get a player's progress for a specific collection.
     *
     * @param playerId The player's UUID as string
     * @param collectionId The collection identifier
     * @return Progress value between 0.0 and 1.0
     */
    double getPlayerCollectionProgress(String playerId, String collectionId);

    /**
     * Update a player's progress for a collection.
     *
     * @param playerId The player's UUID as string
     * @param collectionId The collection identifier
     * @param progress Progress value between 0.0 and 1.0
     * @return True if successfully updated
     */
    boolean updatePlayerCollectionProgress(String playerId, String collectionId, double progress);

    /**
     * Mark a collection as completed by a player.
     *
     * @param playerId The player's UUID as string
     * @param collectionId The collection identifier
     * @param completedAt Timestamp when the collection was completed
     * @return True if successfully marked as completed
     */
    boolean markCollectionCompleted(String playerId, String collectionId, long completedAt);

    /**
     * Get all collections completed by a player.
     *
     * @param playerId The player's UUID as string
     * @return List of collection IDs completed by the player
     */
    List<String> getCompletedCollections(String playerId);

    /**
     * Get progress for all collections for a player.
     *
     * @param playerId The player's UUID as string
     * @return Map of collection IDs to progress values
     */
    Map<String, Double> getAllPlayerProgress(String playerId);

    /**
     * Check if the repository is operating in fallback mode.
     * Fallback mode indicates degraded operation due to database connectivity issues.
     *
     * @return true if in fallback mode, false otherwise
     */
    boolean isInFallbackMode();
}
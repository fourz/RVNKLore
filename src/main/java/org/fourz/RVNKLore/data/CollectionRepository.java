package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.dto.ItemCollectionDTO;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for collection-related database operations.
 * Handles CRUD operations for item collections and player collection progress.
 * All operations are asynchronous using CompletableFuture.
 */
public class CollectionRepository {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;

    /**
     * Create a new CollectionRepository
     *
     * @param plugin The RVNKLore plugin instance
     * @param databaseManager The database manager for async operations
     */
    public CollectionRepository(RVNKLore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CollectionRepository");
        this.databaseManager = databaseManager;
    }

    /**
     * Get all collections from the database
     *
     * @return CompletableFuture with a list of ItemCollectionDTOs
     */
    public CompletableFuture<List<ItemCollectionDTO>> getAllCollections() {
        return databaseManager.getAllCollections();
    }

    /**
     * Get a collection by ID
     *
     * @param id The collection ID
     * @return CompletableFuture with the ItemCollectionDTO or null if not found
     */
    public CompletableFuture<ItemCollectionDTO> getCollectionById(String id) {
        return databaseManager.getCollection(id);
    }

    /**
     * Save a collection to the database
     *
     * @param collection The collection to save
     * @return CompletableFuture with true if successful
     */
    public CompletableFuture<Boolean> saveCollection(ItemCollection collection) {
        if (collection == null) {
            logger.warning("Cannot save null collection");
            return CompletableFuture.completedFuture(false);
        }

        ItemCollectionDTO dto = ItemCollectionDTO.fromCollection(collection);
        return databaseManager.saveCollection(dto);
    }

    /**
     * Delete a collection from the database
     *
     * @param id The collection ID to delete
     * @return CompletableFuture with true if successful
     */
    public CompletableFuture<Boolean> deleteCollection(String id) {
        return databaseManager.deleteCollection(id);
    }

    /**
     * Get collections by theme
     *
     * @param themeId The theme ID
     * @return CompletableFuture with a list of ItemCollectionDTOs
     */
    public CompletableFuture<List<ItemCollectionDTO>> getCollectionsByTheme(String themeId) {
        return databaseManager.getCollectionsByTheme(themeId);
    }

    /**
     * Get a player's progress for a collection
     *
     * @param playerUuid The player UUID
     * @param collectionId The collection ID
     * @return CompletableFuture with the progress value (0.0-1.0)
     */
    public CompletableFuture<Double> getPlayerCollectionProgress(String playerUuid, String collectionId) {
        return databaseManager.getPlayerCollectionProgress(playerUuid, collectionId);
    }

    /**
     * Update a player's progress for a collection
     *
     * @param playerUuid The player UUID
     * @param collectionId The collection ID
     * @param progress The progress value (0.0-1.0)
     * @return CompletableFuture with true if successful
     */
    public CompletableFuture<Boolean> updatePlayerCollectionProgress(String playerUuid, String collectionId, double progress) {
        return databaseManager.updatePlayerCollectionProgress(playerUuid, collectionId, progress);
    }

    /**
     * Mark a collection as completed by a player
     *
     * @param playerUuid The player UUID
     * @param collectionId The collection ID
     * @param timestamp The completion timestamp
     * @return CompletableFuture with true if successful
     */
    public CompletableFuture<Boolean> markCollectionCompleted(String playerUuid, String collectionId, long timestamp) {
        return databaseManager.markCollectionCompleted(playerUuid, collectionId, timestamp);
    }
}

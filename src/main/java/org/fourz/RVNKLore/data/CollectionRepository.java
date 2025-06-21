package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.dto.ItemCollectionDTO;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for collection-related database operations.
 * Handles CRUD operations for item collections and player collection progress.
 * All operations are delegated to DatabaseManager and are asynchronous using CompletableFuture.
 */
public class CollectionRepository {    private final LogManager logger;
    private final DatabaseManager databaseManager;

    /**
     * Create a new CollectionRepository
     *
     * @param plugin The RVNKLore plugin instance
     * @param databaseManager The database manager for async operations
     */    public CollectionRepository(RVNKLore plugin, DatabaseManager databaseManager) {
        this.logger = LogManager.getInstance(plugin, "CollectionRepository");
        this.databaseManager = databaseManager;
    }

    /**
     * Get all collections from the database
     *
     * @return CompletableFuture with a list of ItemCollectionDTOs
     */
    public CompletableFuture<List<ItemCollectionDTO>> getAllCollections() {
        return databaseManager.getAllCollections()
            .exceptionally(e -> {
                logger.error("Error retrieving all collections", e);
                return List.of();
            });
    }

    /**
     * Get a collection by ID
     *
     * @param id The collection ID
     * @return CompletableFuture with the ItemCollectionDTO or null if not found
     */
    public CompletableFuture<ItemCollectionDTO> getCollectionById(String id) {
        return databaseManager.getCollection(id)
            .exceptionally(e -> {
                logger.error("Error retrieving collection by ID: " + id, e);
                return null;
            });
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
        return databaseManager.saveCollection(dto)
            .exceptionally(e -> {
                logger.error("Error saving collection: " + collection.getId(), e);
                return false;
            });
    }

    /**
     * Save a collection DTO directly to the database
     *
     * @param dto The collection DTO to save
     * @return CompletableFuture with true if successful
     */
    public CompletableFuture<Boolean> saveCollectionDTO(ItemCollectionDTO dto) {
        if (dto == null) {
            logger.warning("Cannot save null collection DTO");
            return CompletableFuture.completedFuture(false);
        }

        return databaseManager.saveCollection(dto)
            .exceptionally(e -> {
                logger.error("Error saving collection DTO: " + dto.getId(), e);
                return false;
            });
    }

    /**
     * Delete a collection from the database
     *
     * @param id The collection ID to delete
     * @return CompletableFuture with true if successful
     */
    public CompletableFuture<Boolean> deleteCollection(String id) {
        return databaseManager.deleteCollection(id)
            .exceptionally(e -> {
                logger.error("Error deleting collection: " + id, e);
                return false;
            });
    }

    /**
     * Get collections by theme
     *
     * @param themeId The theme ID
     * @return CompletableFuture with a list of ItemCollectionDTOs
     */
    public CompletableFuture<List<ItemCollectionDTO>> getCollectionsByTheme(String themeId) {
        return databaseManager.getCollectionsByTheme(themeId)
            .exceptionally(e -> {
                logger.error("Error retrieving collections by theme: " + themeId, e);
                return List.of();
            });
    }

    /**
     * Get all collections associated with a player
     *
     * @param playerUuid The player UUID
     * @return CompletableFuture with a list of ItemCollectionDTOs
     */
    public CompletableFuture<List<ItemCollectionDTO>> getPlayerCollections(UUID playerUuid) {
        return databaseManager.getPlayerCollections(playerUuid.toString())
            .exceptionally(e -> {
                logger.error("Error retrieving collections for player: " + playerUuid, e);
                return List.of();
            });
    }

    /**
     * Get a player's progress for a collection
     *
     * @param playerUuid The player UUID
     * @param collectionId The collection ID
     * @return CompletableFuture with the progress value (0.0-1.0)
     */
    public CompletableFuture<Double> getPlayerCollectionProgress(UUID playerUuid, String collectionId) {
        return databaseManager.getPlayerCollectionProgress(playerUuid.toString(), collectionId)
            .exceptionally(e -> {
                logger.error("Error retrieving collection progress for player: " + 
                    playerUuid + ", collection: " + collectionId, e);
                return 0.0;
            });
    }

    /**
     * Update a player's progress for a collection
     *
     * @param playerUuid The player UUID
     * @param collectionId The collection ID
     * @param progress The progress value (0.0-1.0)
     * @return CompletableFuture with true if successful
     */
    public CompletableFuture<Boolean> updatePlayerCollectionProgress(UUID playerUuid, String collectionId, double progress) {
        return databaseManager.updatePlayerCollectionProgress(playerUuid.toString(), collectionId, progress)
            .exceptionally(e -> {
                logger.error("Error updating collection progress for player: " + 
                    playerUuid + ", collection: " + collectionId, e);
                return false;
            });
    }

    /**
     * Mark a collection as completed by a player
     *
     * @param playerUuid The player UUID
     * @param collectionId The collection ID
     * @param timestamp The completion timestamp
     * @return CompletableFuture with true if successful
     */
    public CompletableFuture<Boolean> markCollectionCompleted(UUID playerUuid, String collectionId, long timestamp) {
        return databaseManager.markCollectionCompleted(playerUuid.toString(), collectionId, timestamp)
            .exceptionally(e -> {
                logger.error("Error marking collection as completed for player: " + 
                    playerUuid + ", collection: " + collectionId, e);
                return false;
            });
    }

    /**
     * Get all completed collections for a player
     *
     * @param playerUuid The player UUID
     * @return CompletableFuture with a list of completed collection IDs and completion timestamps
     */
    public CompletableFuture<List<ItemCollectionDTO>> getCompletedCollections(UUID playerUuid) {
        return databaseManager.getCompletedCollections(playerUuid.toString())
            .exceptionally(e -> {
                logger.error("Error retrieving completed collections for player: " + playerUuid, e);
                return List.of();
            });
    }
}

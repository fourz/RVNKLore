package org.fourz.RVNKLore.data.repository;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.ItemCollectionDTO;
import org.fourz.RVNKLore.data.query.QueryBuilder;
import org.fourz.RVNKLore.data.query.QueryExecutor;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;

/**
 * Repository for collection-related database operations.
 * Handles CRUD operations for item collections and player collection progress.
 * All operations are delegated to DatabaseManager and are asynchronous using CompletableFuture.
 */
public class CollectionRepository {
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private final QueryExecutor queryExecutor;

    /**
     * Create a new CollectionRepository
     *
     * @param plugin The RVNKLore plugin instance
     * @param databaseManager The database manager for async operations
     */
    public CollectionRepository(RVNKLore plugin, DatabaseManager databaseManager) {
        this.logger = LogManager.getInstance(plugin, "CollectionRepository");
        this.databaseManager = databaseManager;
        this.queryExecutor = databaseManager.getQueryExecutor();
    }

    /**
     * Get all collections from the database
     *
     * @return CompletableFuture with a list of ItemCollectionDTOs
     */
    public CompletableFuture<List<ItemCollectionDTO>> getAllCollections() {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("id", "name", "description", "theme_id")
            .from("item_collection")
            .orderBy("name", true);

        return queryExecutor.executeQueryList(query, ItemCollectionDTO.class)
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
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("id", "name", "description", "theme_id")
            .from("item_collection")
            .where("id = ?", id);

        return queryExecutor.executeQuery(query, ItemCollectionDTO.class)
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
        return saveCollectionDTO(dto);
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

        QueryBuilder query = databaseManager.getQueryBuilder()
            .insert("item_collection", true) // Allow upsert for collections
            .columns("id", "name", "description", "theme_id")
            .values(dto.getId(), dto.getName(), dto.getDescription(), dto.getThemeId());

        return queryExecutor.executeUpdate(query)
            .thenApply(rowsAffected -> rowsAffected > 0)
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
        QueryBuilder query = databaseManager.getQueryBuilder()
            .deleteFrom("item_collection")
            .where("id = ?", id);

        return queryExecutor.executeUpdate(query)
            .thenApply(rowsAffected -> rowsAffected > 0)
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
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("id", "name", "description", "theme_id")
            .from("item_collection")
            .where("theme_id = ?", themeId)
            .orderBy("name", true);

        return queryExecutor.executeQueryList(query, ItemCollectionDTO.class)
            .exceptionally(e -> {
                logger.error("Error retrieving collections by theme: " + themeId, e);
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
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("progress")
            .from("player_collection")
            .where("player_uuid = ? AND collection_id = ?", 
                  playerUuid.toString(), collectionId);

        return queryExecutor.<Double>executeQuery(query, Double.class)
            .thenApply(progress -> progress != null ? progress : 0.0)
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
        QueryBuilder query = databaseManager.getQueryBuilder()
            .insert("player_collection", true) // Use upsert
            .columns("player_uuid", "collection_id", "progress")
            .values(playerUuid.toString(), collectionId, progress);

        return queryExecutor.executeUpdate(query)
            .thenApply(rowsAffected -> rowsAffected > 0)
            .exceptionally(e -> {
                logger.error("Error updating collection progress for player: " + 
                    playerUuid + ", collection: " + collectionId, e);
                return false;
            });
    }

    /**
     * Get all collections associated with a player
     *
     * @param playerUuid The player UUID
     * @return CompletableFuture with a list of ItemCollectionDTOs
     */
    public CompletableFuture<List<ItemCollectionDTO>> getPlayerCollections(UUID playerUuid) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("c.id", "c.name", "c.description", "c.theme_id", 
                   "pc.progress", "pc.completion_time")
            .from("item_collection c")
            .leftJoin("player_collection pc", 
                     "pc.collection_id = c.id AND pc.player_uuid = ?", 
                     playerUuid.toString())
            .orderBy("c.name", true);

        return queryExecutor.executeQueryList(query, ItemCollectionDTO.class)
            .exceptionally(e -> {
                logger.error("Error retrieving collections for player: " + playerUuid, e);
                return List.of();
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
        QueryBuilder query = databaseManager.getQueryBuilder()
            .insert("player_collection", true)
            .columns("player_uuid", "collection_id", "progress", "completion_time")
            .values(playerUuid.toString(), collectionId, 1.0, timestamp);

        return queryExecutor.executeUpdate(query)
            .thenApply(rowsAffected -> rowsAffected > 0)
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
     * @return CompletableFuture with a list of completed collection DTOs
     */
    public CompletableFuture<List<ItemCollectionDTO>> getCompletedCollections(UUID playerUuid) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("c.id", "c.name", "c.description", "c.theme_id", 
                   "pc.progress", "pc.completion_time")
            .from("item_collection c")
            .innerJoin("player_collection pc", 
                      "pc.collection_id = c.id AND pc.player_uuid = ? AND pc.progress >= 1.0", 
                      playerUuid.toString())
            .orderBy("pc.completion_time", false);

        return queryExecutor.executeQueryList(query, ItemCollectionDTO.class)
            .exceptionally(e -> {
                logger.error("Error retrieving completed collections for player: " + playerUuid, e);
                return List.of();
            });
    }
}

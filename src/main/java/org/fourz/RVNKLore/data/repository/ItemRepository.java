package org.fourz.RVNKLore.data.repository;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.ItemPropertiesDTO;
import org.fourz.RVNKLore.data.query.DefaultQueryExecutor;
import org.fourz.RVNKLore.data.query.QueryBuilder;
import org.fourz.RVNKLore.debug.LogManager;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Repository class for database operations related to lore items.
 * Provides methods to create, read, update, and delete item records.
 * 
 * This class handles:
 * - Basic item CRUD operations for lore items
 * - Collection management
 * - Item metadata storage and retrieval
 * - JSON property serialization/deserialization
 */
public class ItemRepository {
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private final DefaultQueryExecutor queryExecutor;
    private final QueryBuilder queryBuilder;

    /**
     * Create a new ItemRepository instance
     * 
     * @param plugin The RVNKLore plugin instance
     * @param databaseManager The new async DatabaseManager
     */
    public ItemRepository(RVNKLore plugin, DatabaseManager databaseManager) {
        this.logger = LogManager.getInstance(plugin, "ItemRepository");
        this.databaseManager = databaseManager;
        this.queryExecutor = databaseManager.getQueryExecutor();
        this.queryBuilder = databaseManager.getQueryBuilder();
    }

    /**
     * Get an item by ID (async).
     */
    public CompletableFuture<ItemPropertiesDTO> getItemById(int id) {
        if (!databaseManager.validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_item")
                                        .where("id = ?", id);
        
        return queryExecutor.executeQuery(query, ItemPropertiesDTO.class);
    }

    /**
     * Get an item by lore entry ID (async).
     */
    public CompletableFuture<ItemPropertiesDTO> getItemByLoreEntry(int loreEntryId) {
        if (!databaseManager.validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_item")
                                        .where("lore_entry_id = ?", loreEntryId);
        
        return queryExecutor.executeQuery(query, ItemPropertiesDTO.class);
    }

    /**
     * Get items by type (async).
     */
    public CompletableFuture<List<ItemPropertiesDTO>> getItemsByType(String type) {
        if (!databaseManager.validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_item")
                                        .where("item_type = ?", type)
                                        .orderBy("created_at", false);
        
        return queryExecutor.executeQueryList(query, ItemPropertiesDTO.class);
    }

    /**
     * Get all items (async).
     * Retrieves all items from the database, sorted by creation date in descending order.
     * 
     * @return A future containing a list of all item properties DTOs
     */
    public CompletableFuture<List<ItemPropertiesDTO>> getAllItems() {
        if (!databaseManager.validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_item")
                                        .orderBy("created_at", false); // Sort by newest first
        
        return queryExecutor.executeQueryList(query, ItemPropertiesDTO.class);
    }

    /**
     * Save an item (async).
     */
    public CompletableFuture<Integer> saveItem(ItemPropertiesDTO dto) {
        if (!databaseManager.validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }

        return queryExecutor.executeTransaction(conn -> {
            int itemId;
            
            try {
                if (dto.getId() > 0) {
                    // Update existing item
                    QueryBuilder query = queryBuilder.update("lore_item")
                        .set("lore_entry_id", dto.getLoreEntryId())
                        .set("item_type", dto.getItemType() != null ? dto.getItemType().name() : null)
                        .set("material", dto.getMaterial())
                        .set("display_name", dto.getDisplayName())
                        .set("lore", dto.getLore() != null ? String.join("\\n", dto.getLore()) : null)
                        .set("custom_model_data", dto.getCustomModelData())
                        .set("rarity", dto.getRarity())
                        .set("is_obtainable", dto.isObtainable())
                        .set("glow", dto.isGlow())
                        .set("skull_texture", dto.getSkullTexture())
                        .set("texture_data", dto.getTextureData())
                        .set("owner_name", dto.getOwnerName())
                        .set("collection_id", dto.getCollectionId())
                        .set("theme_id", dto.getThemeId())
                        .set("rarity_level", dto.getRarityLevel())
                        .set("collection_sequence", dto.getCollectionSequence())
                        .set("nbt_data", dto.getNbtData())
                        .set("created_by", dto.getCreatedBy())
                        .where("id = ?", dto.getId());

                    try (var stmt = conn.prepareStatement(query.build())) {
                        for (int i = 0; i < query.getParameters().length; i++) {
                            stmt.setObject(i + 1, query.getParameters()[i]);
                        }
                        int rowsAffected = stmt.executeUpdate();
                        
                        if (rowsAffected <= 0) {
                            String error = "Failed to update item with ID: " + dto.getId();
                            logger.error(error, new SQLException(error));
                            throw new SQLException(error);
                        }
                        
                        itemId = dto.getId();
                    }
                } else {
                    // Insert new item
                    QueryBuilder query = queryBuilder.insert("lore_item", true) // Allow upsert since items can be updated
                        .columns("lore_entry_id", "item_type", "material", "display_name", 
                                "lore", "custom_model_data", "rarity", "is_obtainable", 
                                "glow", "skull_texture", "texture_data", "owner_name", 
                                "collection_id", "theme_id", "rarity_level", 
                                "collection_sequence", "nbt_data", "created_by")
                        .values(dto.getLoreEntryId(), 
                               dto.getItemType() != null ? dto.getItemType().name() : null,
                               dto.getMaterial(),
                               dto.getDisplayName(),
                               dto.getLore() != null ? String.join("\\n", dto.getLore()) : null,
                               dto.getCustomModelData(),
                               dto.getRarity(),
                               dto.isObtainable(),
                               dto.isGlow(),
                               dto.getSkullTexture(),
                               dto.getTextureData(),
                               dto.getOwnerName(),
                               dto.getCollectionId(),
                               dto.getThemeId(),
                               dto.getRarityLevel(),
                               dto.getCollectionSequence(),
                               dto.getNbtData(),
                               dto.getCreatedBy());

                    try (var stmt = conn.prepareStatement(query.build(), java.sql.Statement.RETURN_GENERATED_KEYS)) {
                        for (int i = 0; i < query.getParameters().length; i++) {
                            stmt.setObject(i + 1, query.getParameters()[i]);
                        }
                        
                        int rows = stmt.executeUpdate();
                        if (rows == 0) {
                            String error = "Failed to insert new item (no rows affected)";
                            logger.error(error, new SQLException(error));
                            throw new SQLException(error);
                        }
                        
                        try (var rs = stmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                itemId = rs.getInt(1);
                            } else {
                                String error = "Failed to get generated item ID after insert";
                                logger.error(error, new SQLException(error));
                                throw new SQLException(error);
                            }
                        }
                    }
                }
                
                return itemId;
            } catch (SQLException e) {
                logger.error("Error saving item", e);
                throw new RuntimeException(e);
            }
        }).exceptionally(ex -> {
            logger.error("Error saving item", ex);
            throw new CompletionException(ex);
        });
    }

    /**
     * Delete an item (async).
     */
    public CompletableFuture<Boolean> deleteItem(int id) {
        if (!databaseManager.validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.deleteFrom("lore_item")
                                        .where("id = ?", id);
        
        return queryExecutor.executeUpdate(query)
                          .thenApply(rowsAffected -> rowsAffected > 0);
    }
}

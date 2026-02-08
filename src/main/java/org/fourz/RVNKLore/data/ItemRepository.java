package org.fourz.RVNKLore.data;

import org.bukkit.Material;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.exception.LoreException;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.ItemType;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository class for database operations related to lore items.
 * Provides methods to create, read, update, and delete item records.
 *
 * This class handles:
 * - Basic item CRUD operations for lore items
 * - Collection management
 * - Item metadata storage and retrieval
 * - JSON property serialization/deserialization
 *
 * All methods return CompletableFuture<T> for async operations per RVNKCore standard.
 */
public class ItemRepository implements IItemRepository {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseConnection dbConnection;
    private final DatabaseHelper dbHelper;
    private final FallbackTracker fallbackTracker;

    /**
     * Create a new ItemRepository instance
     *
     * @param plugin The RVNKLore plugin instance
     * @param dbConnection The database connection to use
     */
    public ItemRepository(RVNKLore plugin, DatabaseConnection dbConnection) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ItemRepository");
        this.dbConnection = dbConnection;
        this.dbHelper = new DatabaseHelper(plugin);
        this.fallbackTracker = new FallbackTracker(plugin);

        // Initialize database tables
        initializeTables();

        logger.info("ItemRepository initialized");
    }

    /** Helper to get prefixed table name */
    private String t(String baseName) {
        return dbConnection.table(baseName);
    }

    /**
     * Verify required database tables exist.
     *
     * Note: Table creation is handled by DatabaseConnection.createTables().
     * This method only verifies the tables exist to fail-fast if schema is missing.
     * See docs/standard/rvnklore-schema.md for authoritative schema reference.
     */
    private void initializeTables() {
        // Required tables for ItemRepository operations (with prefix applied)
        String[] requiredTables = {t("lore_item"), t("collection"), t("collection_item"), t("player_collection_progress")};

        try (Connection conn = dbConnection.getConnection()) {
            for (String tableName : requiredTables) {
                if (!tableExists(conn, tableName)) {
                    logger.warning("Required table '" + tableName + "' does not exist. " +
                            "Ensure DatabaseConnection.createTables() is called before ItemRepository initialization.");
                }
            }
            logger.info("Item database tables verified");
        } catch (SQLException e) {
            logger.error("Failed to verify item database tables", e);
        }
    }

    /**
     * Check if a table exists in the database.
     * Uses dialect-specific SQL for cross-database compatibility.
     *
     * @param conn The database connection
     * @param tableName The name of the table to check
     * @return true if the table exists
     */
    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = dbConnection.getDialect().getTableExistsQuery(tableName);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ==================== Item Operations ====================

    /**
     * Get an item by its ID
     *
     * @param itemId The ID of the item to retrieve
     * @return CompletableFuture that completes with Optional containing the item properties, or empty if not found
     */
    @Override
    public CompletableFuture<Optional<ItemProperties>> getItemById(int itemId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("lore_item") + " WHERE id = ?";

            try {
                ItemProperties result = dbHelper.executeQuery(sql,
                    stmt -> stmt.setInt(1, itemId),
                    rs -> {
                        if (rs.next()) {
                            return resultSetToItemProperties(rs);
                        }
                        return null;
                    });
                return Optional.ofNullable(result);
            } catch (LoreException e) {
                logger.error("Failed to get item by ID: " + itemId, e);
                return Optional.empty();
            }
        });
    }

    /**
     * Get an item by its name
     * Note: If multiple items have the same name, this will return the first one found
     *
     * @param name The name of the item to retrieve
     * @return CompletableFuture that completes with Optional containing the item properties, or empty if not found
     */
    @Override
    public CompletableFuture<Optional<ItemProperties>> getItemByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("lore_item") + " WHERE name = ? LIMIT 1";

            try {
                ItemProperties result = dbHelper.executeQuery(sql,
                    stmt -> stmt.setString(1, name),
                    rs -> {
                        if (rs.next()) {
                            return resultSetToItemProperties(rs);
                        }
                        return null;
                    });
                return Optional.ofNullable(result);
            } catch (LoreException e) {
                logger.error("Failed to get item by name: " + name, e);
                return Optional.empty();
            }
        });
    }

    /**
     * Get all items with a specific name
     *
     * @param name The name of the items to retrieve
     * @return CompletableFuture that completes with a list of all items with the given name
     */
    @Override
    public CompletableFuture<List<ItemProperties>> getAllItemsByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("lore_item") + " WHERE name = ?";

            try {
                return dbHelper.executeQuery(sql,
                    stmt -> stmt.setString(1, name),
                    rs -> {
                        List<ItemProperties> items = new ArrayList<>();
                        while (rs.next()) {
                            ItemProperties item = resultSetToItemProperties(rs);
                            if (item != null) {
                                items.add(item);
                            }
                        }
                        return items;
                    });
            } catch (LoreException e) {
                logger.error("Failed to get all items by name: " + name, e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Get an item by its lore entry UUID
     *
     * @param loreEntryId The UUID of the lore entry
     * @return CompletableFuture that completes with Optional containing the item properties, or empty if not found
     */
    @Override
    public CompletableFuture<Optional<ItemProperties>> getItemByLoreEntryId(String loreEntryId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("lore_item") + " WHERE lore_entry_id = ?";

            try {
                ItemProperties result = dbHelper.executeQuery(sql,
                    stmt -> stmt.setString(1, loreEntryId),
                    rs -> {
                        if (rs.next()) {
                            return resultSetToItemProperties(rs);
                        }
                        return null;
                    });
                return Optional.ofNullable(result);
            } catch (LoreException e) {
                logger.error("Failed to get item by lore entry ID: " + loreEntryId, e);
                return Optional.empty();
            }
        });
    }

    /**
     * Get all items of a specific type
     *
     * @param itemType The type of items to retrieve
     * @return CompletableFuture that completes with a list of item properties
     */
    @Override
    public CompletableFuture<List<ItemProperties>> getItemsByType(String itemType) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("lore_item") + " WHERE item_type = ?";

            try {
                return dbHelper.executeQuery(sql,
                    stmt -> stmt.setString(1, itemType),
                    rs -> {
                        List<ItemProperties> items = new ArrayList<>();
                        while (rs.next()) {
                            ItemProperties item = resultSetToItemProperties(rs);
                            if (item != null) {
                                items.add(item);
                            }
                        }
                        return items;
                    });
            } catch (LoreException e) {
                logger.error("Failed to get items by type: " + itemType, e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Get all items
     *
     * @return CompletableFuture that completes with a list of all item properties
     */
    @Override
    public CompletableFuture<List<ItemProperties>> getAllItems() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("lore_item") + "";

            try {
                return dbHelper.executeQuery(sql,
                    stmt -> {},
                    rs -> {
                        List<ItemProperties> items = new ArrayList<>();
                        while (rs.next()) {
                            ItemProperties item = resultSetToItemProperties(rs);
                            if (item != null) {
                                items.add(item);
                            }
                        }
                        return items;
                    });
            } catch (LoreException e) {
                logger.error("Failed to get all items", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Insert a new item into the database.
     * Uses dialect-aware generated key retrieval for MySQL/SQLite compatibility.
     *
     * @param properties The item properties to save
     * @return CompletableFuture that completes with the ID of the new item, or -1 if insert failed
     */
    @Override
    @SuppressWarnings({"unchecked"})
    public CompletableFuture<Integer> insertItem(ItemProperties properties) {
        return CompletableFuture.supplyAsync(() -> {
            // Base INSERT without RETURNING clause - dialect handles key retrieval
            String sql = "INSERT INTO " + t("lore_item") + " (name, item_type, rarity, material, " +
                        "is_obtainable, custom_model_data, item_properties, created_by, nbt_data, lore_entry_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try {
                return dbHelper.executeInsertWithGeneratedKey(sql, "id",
                    stmt -> {
                        stmt.setString(1, properties.getDisplayName());
                        stmt.setString(2, properties.getItemType() != null ? properties.getItemType().name() : "STANDARD");
                        stmt.setString(3, properties.getRarity() != null ? properties.getRarity() : "COMMON");
                        stmt.setString(4, properties.getMaterial().name());
                        stmt.setBoolean(5, properties.isObtainable());
                        if (properties.getCustomModelData() > 0) {
                            stmt.setInt(6, properties.getCustomModelData());
                        } else {
                            stmt.setNull(6, java.sql.Types.INTEGER);
                        }
                        // Convert custom properties to JSON
                        JSONObject jsonProps = new JSONObject();
                        if (properties.hasCustomProperties()) {
                            jsonProps.putAll(properties.getAllCustomProperties());
                        }
                        if (properties.getLore() != null && !properties.getLore().isEmpty()) {
                            jsonProps.put("lore_text", properties.getLore());
                        }
                        if (properties.isGlow()) {
                            jsonProps.put("is_glow", true);
                        }
                        if (properties.getSkullTexture() != null) {
                            jsonProps.put("skull_texture", properties.getSkullTexture());
                        }
                        stmt.setString(7, jsonProps.toJSONString());
                        stmt.setString(8, properties.getCreatedBy());
                        stmt.setString(9, properties.getNbtData());
                        if (properties.getLoreEntryId() != null && !properties.getLoreEntryId().isEmpty()) {
                            stmt.setString(10, properties.getLoreEntryId());
                        } else {
                            stmt.setNull(10, java.sql.Types.VARCHAR);
                        }
                    });
            } catch (LoreException e) {
                logger.error("Failed to insert item: " + properties.getDisplayName(), e);
                return -1;
            }
        });
    }

    /**
     * Update an existing item in the database
     *
     * @param itemId The ID of the item to update
     * @param properties The updated item properties
     * @return CompletableFuture that completes with true if the update was successful
     */
    @Override
    @SuppressWarnings({"unchecked"})
    public CompletableFuture<Boolean> updateItem(int itemId, ItemProperties properties) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + t("lore_item") + " SET " +
                        "name = ?, item_type = ?, rarity = ?, " +
                        "material = ?, is_obtainable = ?, custom_model_data = ?, " +
                        "item_properties = ?, updated_at = CURRENT_TIMESTAMP, " +
                        "nbt_data = ?, lore_entry_id = ? " +
                        "WHERE id = ?";

            try {
                int rowsAffected = dbHelper.executeUpdate(sql,
                    stmt -> {
                        stmt.setString(1, properties.getDisplayName());
                        stmt.setString(2, properties.getItemType() != null ? properties.getItemType().name() : "STANDARD");
                        stmt.setString(3, properties.getRarity() != null ? properties.getRarity() : "COMMON");
                        stmt.setString(4, properties.getMaterial().name());
                        stmt.setBoolean(5, properties.isObtainable());
                        if (properties.getCustomModelData() > 0) {
                            stmt.setInt(6, properties.getCustomModelData());
                        } else {
                            stmt.setNull(6, java.sql.Types.INTEGER);
                        }
                        // Convert custom properties to JSON
                        JSONObject jsonProps = new JSONObject();
                        if (properties.hasCustomProperties()) {
                            jsonProps.putAll(properties.getAllCustomProperties());
                        }
                        if (properties.getLore() != null && !properties.getLore().isEmpty()) {
                            jsonProps.put("lore_text", properties.getLore());
                        }
                        if (properties.isGlow()) {
                            jsonProps.put("is_glow", true);
                        }
                        if (properties.getSkullTexture() != null) {
                            jsonProps.put("skull_texture", properties.getSkullTexture());
                        }
                        stmt.setString(7, jsonProps.toJSONString());
                        // Set NBT data
                        stmt.setString(8, properties.getNbtData());
                        // Set lore entry ID if available, otherwise null
                        if (properties.getLoreEntryId() != null && !properties.getLoreEntryId().isEmpty()) {
                            stmt.setString(9, properties.getLoreEntryId());
                        } else {
                            stmt.setNull(9, java.sql.Types.VARCHAR);
                        }
                        stmt.setInt(10, itemId);
                    });

                return rowsAffected > 0;
            } catch (LoreException e) {
                logger.error("Failed to update item: " + itemId, e);
                return false;
            }
        });
    }

    /**
     * Delete an item from the database
     *
     * @param itemId The ID of the item to delete
     * @return CompletableFuture that completes with true if the delete was successful
     */
    @Override
    public CompletableFuture<Boolean> deleteItem(int itemId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("lore_item") + " WHERE id = ?";

            try {
                int rowsAffected = dbHelper.executeUpdate(sql,
                    stmt -> stmt.setInt(1, itemId));

                return rowsAffected > 0;
            } catch (LoreException e) {
                logger.error("Failed to delete item: " + itemId, e);
                return false;
            }
        });
    }

    /**
     * Delete an item by its name
     *
     * @param name The name of the item to delete
     * @return CompletableFuture that completes with true if deletion was successful
     */
    @Override
    public CompletableFuture<Boolean> deleteItemByName(String name) {
        return getCurrentItemId(name).thenCompose(itemId -> {
            if (itemId == -1) {
                return CompletableFuture.completedFuture(false);
            }
            return deleteItem(itemId);
        });
    }

    /**
     * Get the current database ID for an item by name
     * Note: If multiple items have the same name, this will return the first one found
     * For uniquely identifying items, use the UUID-based methods instead
     *
     * @param name The name of the item
     * @return CompletableFuture that completes with the database ID, or -1 if not found
     */
    @Override
    public CompletableFuture<Integer> getCurrentItemId(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id FROM " + t("lore_item") + " WHERE name = ? LIMIT 1";

            try {
                return dbHelper.executeQuery(sql,
                    stmt -> stmt.setString(1, name),
                    rs -> {
                        if (rs.next()) {
                            return rs.getInt("id");
                        }
                        return -1;
                    });
            } catch (LoreException e) {
                logger.error("Failed to get item ID for name: " + name, e);
                return -1;
            }
        });
    }

    /**
     * Get the current database IDs for all items with a specific name
     *
     * @param name The name of the items to find
     * @return CompletableFuture that completes with a list of database IDs, or empty list if none found
     */
    @Override
    public CompletableFuture<List<Integer>> getAllItemIdsByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id FROM " + t("lore_item") + " WHERE name = ?";

            try {
                return dbHelper.executeQuery(sql,
                    stmt -> stmt.setString(1, name),
                    rs -> {
                        List<Integer> ids = new ArrayList<>();
                        while (rs.next()) {
                            ids.add(rs.getInt("id"));
                        }
                        return ids;
                    });
            } catch (LoreException e) {
                logger.error("Failed to get item IDs for name: " + name, e);
                return new ArrayList<>();
            }
        });
    }

    // ==================== Collection Operations ====================

    /**
     * Get all items in a collection
     *
     * @param collectionId The ID of the collection
     * @return CompletableFuture that completes with a list of item properties in the collection
     */
    @Override
    public CompletableFuture<List<ItemProperties>> getItemsByCollection(int collectionId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT i.*, ci.sequence_number, ci.item_config " +
                        "FROM " + t("lore_item") + " i " +
                        "JOIN " + t("collection_item") + " ci ON i.id = ci.item_id " +
                        "WHERE ci.collection_id = ? " +
                        "ORDER BY ci.sequence_number";

            try {
                return dbHelper.executeQuery(sql,
                    stmt -> stmt.setInt(1, collectionId),
                    rs -> {
                        List<ItemProperties> items = new ArrayList<>();
                        while (rs.next()) {
                            ItemProperties item = resultSetToItemProperties(rs);
                            if (item != null) {
                                // Add collection-specific properties
                                try {
                                    String itemConfig = rs.getString("item_config");
                                    if (itemConfig != null && !itemConfig.isEmpty()) {
                                        JSONParser parser = new JSONParser();
                                        JSONObject config = (JSONObject) parser.parse(itemConfig);

                                        // Store collection sequence number
                                        item.setCollectionSequence(rs.getInt("sequence_number"));

                                        // Apply any collection-specific overrides
                                        if (config.containsKey("custom_display_name")) {
                                            item.setDisplayName((String) config.get("custom_display_name"));
                                        }

                                        // Add all config as custom properties
                                        for (Object key : config.keySet()) {
                                            item.setCustomProperty((String) key, config.get(key));
                                        }
                                    }
                                } catch (ParseException e) {
                                    logger.warning("Failed to parse item_config JSON for item: " + item.getDisplayName());
                                }

                                items.add(item);
                            }
                        }
                        return items;
                    });
            } catch (LoreException e) {
                logger.error("Failed to get items by collection: " + collectionId, e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Get all collections containing an item
     *
     * @param itemId The ID of the item
     * @return CompletableFuture that completes with a map of collection IDs to collection names
     */
    @Override
    public CompletableFuture<Map<Integer, String>> getCollectionsByItem(int itemId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT c.id, c.name " +
                        "FROM " + t("collection") + " c " +
                        "JOIN " + t("collection_item") + " ci ON c.id = ci.collection_id " +
                        "WHERE ci.item_id = ?";

            try {
                return dbHelper.executeQuery(sql,
                    stmt -> stmt.setInt(1, itemId),
                    rs -> {
                        Map<Integer, String> collections = new HashMap<>();
                        while (rs.next()) {
                            collections.put(rs.getInt("id"), rs.getString("name"));
                        }
                        return collections;
                    });
            } catch (LoreException e) {
                logger.error("Failed to get collections by item: " + itemId, e);
                return new HashMap<>();
            }
        });
    }

    /**
     * Get all collections
     *
     * @return CompletableFuture that completes with a map of collection IDs to collection names
     */
    @Override
    public CompletableFuture<Map<Integer, String>> getAllCollections() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id, name FROM " + t("collection") + "";

            try {
                return dbHelper.executeQuery(sql,
                    stmt -> {},
                    rs -> {
                        Map<Integer, String> collections = new HashMap<>();
                        while (rs.next()) {
                            collections.put(rs.getInt("id"), rs.getString("name"));
                        }
                        return collections;
                    });
            } catch (LoreException e) {
                logger.error("Failed to get all collections", e);
                return new HashMap<>();
            }
        });
    }

    /**
     * Get collection details by ID
     *
     * @param collectionId The ID of the collection to retrieve
     * @return CompletableFuture that completes with a map containing collection details (name, description, theme)
     */
    @Override
    public CompletableFuture<Map<String, String>> getCollectionDetails(int collectionId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT name, description, theme FROM " + t("collection") + " WHERE id = ?";

            try {
                return dbHelper.executeQuery(sql,
                    stmt -> stmt.setInt(1, collectionId),
                    rs -> {
                        Map<String, String> details = new HashMap<>();
                        if (rs.next()) {
                            details.put("name", rs.getString("name"));
                            details.put("description", rs.getString("description"));
                            details.put("theme", rs.getString("theme"));
                        }
                        return details;
                    });
            } catch (LoreException e) {
                logger.error("Failed to get collection details: " + collectionId, e);
                return new HashMap<>();
            }
        });
    }

    /**
     * Create a new collection.
     * Uses dialect-aware generated key retrieval for MySQL/SQLite compatibility.
     *
     * @param name The name of the collection
     * @param description The collection description
     * @param theme The collection theme
     * @return CompletableFuture that completes with the ID of the new collection, or -1 if creation failed
     */
    @Override
    public CompletableFuture<Integer> createCollection(String name, String description, String theme) {
        return CompletableFuture.supplyAsync(() -> {
            // Base INSERT without RETURNING clause - dialect handles key retrieval
            String sql = "INSERT INTO " + t("collection") + " (name, description, theme) VALUES (?, ?, ?)";

            try {
                return dbHelper.executeInsertWithGeneratedKey(sql, "id",
                    stmt -> {
                        stmt.setString(1, name);
                        stmt.setString(2, description);
                        stmt.setString(3, theme);
                    });
            } catch (LoreException e) {
                logger.error("Failed to create collection: " + name, e);
                return -1;
            }
        });
    }

    /**
     * Update collection properties
     *
     * @param collectionId The ID of the collection to update
     * @param name The new name (or null to keep existing)
     * @param description The new description (or null to keep existing)
     * @param theme The new theme (or null to keep existing)
     * @return CompletableFuture that completes with true if update was successful
     */
    @Override
    public CompletableFuture<Boolean> updateCollection(int collectionId, String name, String description, String theme) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sql = new StringBuilder("UPDATE " + t("collection") + " SET updated_at = CURRENT_TIMESTAMP");
            List<String> params = new ArrayList<>();

            if (name != null) {
                sql.append(", name = ?");
                params.add(name);
            }
            if (description != null) {
                sql.append(", description = ?");
                params.add(description);
            }
            if (theme != null) {
                sql.append(", theme = ?");
                params.add(theme);
            }

            sql.append(" WHERE id = ?");

            try {
                int rowsAffected = dbHelper.executeUpdate(sql.toString(),
                    stmt -> {
                        int paramIndex = 1;
                        for (String param : params) {
                            stmt.setString(paramIndex++, param);
                        }
                        stmt.setInt(paramIndex, collectionId);
                    });

                return rowsAffected > 0;
            } catch (LoreException e) {
                logger.error("Failed to update collection: " + collectionId, e);
                return false;
            }
        });
    }

    /**
     * Add an item to a collection
     *
     * @param itemId The ID of the item
     * @param collectionId The ID of the collection
     * @param sequenceNumber The order in which to display the item
     * @param itemConfig Additional configuration for the item in this collection
     * @return CompletableFuture that completes with true if the addition was successful
     */
    @Override
    public CompletableFuture<Boolean> addItemToCollection(int itemId, int collectionId, int sequenceNumber, JSONObject itemConfig) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + t("collection_item") + " (collection_id, item_id, sequence_number, item_config) " +
                        "VALUES (?, ?, ?, ?)";

            try {
                int rowsAffected = dbHelper.executeUpdate(sql,
                    stmt -> {
                        stmt.setInt(1, collectionId);
                        stmt.setInt(2, itemId);
                        stmt.setInt(3, sequenceNumber);
                        stmt.setString(4, itemConfig != null ? itemConfig.toJSONString() : null);
                    });

                return rowsAffected > 0;
            } catch (LoreException e) {
                logger.error("Failed to add item " + itemId + " to collection " + collectionId, e);
                return false;
            }
        });
    }

    /**
     * Remove an item from a collection
     *
     * @param itemId The ID of the item
     * @param collectionId The ID of the collection
     * @return CompletableFuture that completes with true if the removal was successful
     */
    @Override
    public CompletableFuture<Boolean> removeItemFromCollection(int itemId, int collectionId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("collection_item") + " WHERE item_id = ? AND collection_id = ?";

            try {
                int rowsAffected = dbHelper.executeUpdate(sql,
                    stmt -> {
                        stmt.setInt(1, itemId);
                        stmt.setInt(2, collectionId);
                    });

                return rowsAffected > 0;
            } catch (LoreException e) {
                logger.error("Failed to remove item " + itemId + " from collection " + collectionId, e);
                return false;
            }
        });
    }

    /**
     * Add multiple items to a collection in a single transaction
     *
     * @param collectionId The collection ID
     * @param itemIds List of item IDs to add
     * @param startingSequence Starting sequence number (optional, defaults to existing max + 1)
     * @return CompletableFuture that completes with true if all items were added successfully
     */
    @Override
    public CompletableFuture<Boolean> addItemsToCollection(int collectionId, List<Integer> itemIds, Integer startingSequence) {
        return CompletableFuture.supplyAsync(() -> {
            if (itemIds == null || itemIds.isEmpty()) {
                return true;
            }

            try (Connection conn = dbConnection.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // Get current max sequence if not provided
                    int nextSequence = startingSequence != null ? startingSequence :
                        getCurrentMaxSequence(collectionId, conn) + 1;

                    // Add all items
                    String sql = "INSERT INTO " + t("collection_item") + " (collection_id, item_id, sequence_number) VALUES (?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        for (Integer itemId : itemIds) {
                            stmt.setInt(1, collectionId);
                            stmt.setInt(2, itemId);
                            stmt.setInt(3, nextSequence++);
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }

                    conn.commit();
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    logger.error("Failed to add items to collection", e);
                    return false;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                logger.error("Database connection error when adding items to collection", e);
                return false;
            }
        });
    }

    /**
     * Update the sequence numbers for items in a collection
     *
     * @param collectionId The collection ID
     * @param itemSequences Map of item IDs to their new sequence numbers
     * @return CompletableFuture that completes with true if the update was successful
     */
    @Override
    public CompletableFuture<Boolean> updateCollectionSequences(int collectionId, Map<Integer, Integer> itemSequences) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + t("collection_item") + " SET sequence_number = ? WHERE collection_id = ? AND item_id = ?";

            try (Connection conn = dbConnection.getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (Map.Entry<Integer, Integer> entry : itemSequences.entrySet()) {
                        stmt.setInt(1, entry.getValue());
                        stmt.setInt(2, collectionId);
                        stmt.setInt(3, entry.getKey());
                        stmt.addBatch();
                    }

                    stmt.executeBatch();
                    conn.commit();
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    logger.error("Failed to update collection sequences", e);
                    return false;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                logger.error("Database connection error when updating collection sequences", e);
                return false;
            }
        });
    }

    /**
     * Save a collection to the database.
     * Uses dialect-aware REPLACE for MySQL/SQLite compatibility.
     *
     * @param collection The collection to save
     * @return CompletableFuture that completes with true if successfully saved
     */
    @Override
    public CompletableFuture<Boolean> saveCollection(ItemCollection collection) {
        return CompletableFuture.supplyAsync(() -> {
            if (collection == null) {
                return false;
            }

            // Generate dialect-specific REPLACE SQL
            String[] columns = {"collection_id", "name", "description", "theme_id", "is_active", "created_at"};
            String sql = dbConnection.getDialect().getReplaceSQL("collection", columns);

            try {
                return dbHelper.executeUpdate(sql, stmt -> {
                    stmt.setString(1, collection.getId());
                    stmt.setString(2, collection.getName());
                    stmt.setString(3, collection.getDescription());
                    stmt.setString(4, collection.getThemeId());
                    stmt.setBoolean(5, collection.isActive());
                    stmt.setLong(6, collection.getCreatedAt());
                }) > 0;
            } catch (LoreException e) {
                logger.error("Failed to save collection: " + collection.getId(), e);
                return false;
            }
        });
    }

    /**
     * Load all collections from the database
     *
     * @return CompletableFuture that completes with a list of all collections
     */
    @Override
    public CompletableFuture<List<ItemCollection>> loadAllCollections() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT collection_id, name, description, theme_id, is_active, created_at FROM " + t("collection") + "";

            try {
                return dbHelper.executeQuery(sql, null, rs -> {
                    List<ItemCollection> collections = new ArrayList<>();
                    while (rs.next()) {
                        ItemCollection collection = new ItemCollection(
                                rs.getString("collection_id"),
                                rs.getString("name"),
                                rs.getString("description")
                        );
                        collection.setThemeId(rs.getString("theme_id"));
                        collection.setActive(rs.getBoolean("is_active"));
                        collections.add(collection);
                    }
                    return collections;
                });
            } catch (LoreException e) {
                logger.error("Failed to load collections", e);
                return new ArrayList<>();
            }
        });
    }

    // ==================== Player Progress Operations ====================

    /**
     * Get a player's progress for a specific collection
     *
     * @param playerId The player's UUID as string
     * @param collectionId The collection identifier
     * @return CompletableFuture that completes with progress value between 0.0 and 1.0
     */
    @Override
    public CompletableFuture<Double> getPlayerCollectionProgress(String playerId, String collectionId) {
        return CompletableFuture.supplyAsync(() -> {
            if (playerId == null || collectionId == null) {
                return 0.0;
            }

            String sql = "SELECT progress FROM " + t("player_collection_progress") + " WHERE player_id = ? AND collection_id = ?";

            try {
                return dbHelper.executeQuery(sql, stmt -> {
                    stmt.setString(1, playerId);
                    stmt.setString(2, collectionId);
                }, rs -> {
                    if (rs.next()) {
                        return rs.getDouble("progress");
                    }
                    return 0.0;
                });
            } catch (LoreException e) {
                logger.error("Failed to get collection progress for player " + playerId, e);
                return 0.0;
            }
        });
    }

    /**
     * Update a player's progress for a collection.
     * Uses dialect-aware upsert for MySQL/SQLite compatibility.
     *
     * @param playerId The player's UUID as string
     * @param collectionId The collection identifier
     * @param progress Progress value between 0.0 and 1.0
     * @return CompletableFuture that completes with true if successfully updated
     */
    @Override
    public CompletableFuture<Boolean> updatePlayerCollectionProgress(String playerId, String collectionId, double progress) {
        return CompletableFuture.supplyAsync(() -> {
            if (playerId == null || collectionId == null) {
                return false;
            }

            // Ensure progress is between 0 and 1
            final double percent = Math.max(0.0, Math.min(1.0, progress));
            long currentTime = System.currentTimeMillis();

            // Generate dialect-specific upsert SQL
            String[] keyColumns = {"player_id", "collection_id"};
            String[] allColumns = {"player_id", "collection_id", "progress", "last_updated"};
            String[] updateColumns = {"progress", "last_updated"};
            String sql = dbConnection.getDialect().getUpsertSQL(
                    t("player_collection_progress"), keyColumns, allColumns, updateColumns);

            try {
                return dbHelper.executeUpdate(sql, stmt -> {
                    // Insert values (always required)
                    stmt.setString(1, playerId);
                    stmt.setString(2, collectionId);
                    stmt.setDouble(3, percent);
                    stmt.setLong(4, currentTime);

                    // SQLite needs duplicate binding for update values; MySQL uses VALUES()
                    if (dbConnection.getDialect().upsertNeedsDuplicateBinding()) {
                        stmt.setDouble(5, percent);
                        stmt.setLong(6, currentTime);
                    }
                }) > 0;
            } catch (LoreException e) {
                logger.error("Failed to update collection progress for player " + playerId, e);
                return false;
            }
        });
    }

    /**
     * Mark a collection as completed by a player
     *
     * @param playerId The player's UUID as string
     * @param collectionId The collection identifier
     * @param completedAt Timestamp when the collection was completed
     * @return CompletableFuture that completes with true if successfully marked as completed
     */
    @Override
    public CompletableFuture<Boolean> markCollectionCompleted(String playerId, String collectionId, long completedAt) {
        return CompletableFuture.supplyAsync(() -> {
            if (playerId == null || collectionId == null) {
                return false;
            }

            String sql = "UPDATE " + t("player_collection_progress") + " SET progress = 1.0, completed_at = ?, last_updated = ? " +
                         "WHERE player_id = ? AND collection_id = ?";

            try {
                return dbHelper.executeUpdate(sql, stmt -> {
                    stmt.setLong(1, completedAt);
                    stmt.setLong(2, completedAt);
                    stmt.setString(3, playerId);
                    stmt.setString(4, collectionId);
                }) > 0;
            } catch (LoreException e) {
                logger.error("Failed to mark collection as completed for player " + playerId, e);
                return false;
            }
        });
    }

    /**
     * Get all collections completed by a player
     *
     * @param playerId The player's UUID as string
     * @return CompletableFuture that completes with a list of collection IDs completed by the player
     */
    @Override
    public CompletableFuture<List<String>> getCompletedCollections(String playerId) {
        return CompletableFuture.supplyAsync(() -> {
            if (playerId == null) {
                return new ArrayList<>();
            }

            String sql = "SELECT collection_id FROM " + t("player_collection_progress") + " " +
                         "WHERE player_id = ? AND progress >= 1.0 AND completed_at IS NOT NULL";

            try {
                return dbHelper.executeQuery(sql, stmt -> {
                    stmt.setString(1, playerId);
                }, rs -> {
                    List<String> collections = new ArrayList<>();
                    while (rs.next()) {
                        collections.add(rs.getString("collection_id"));
                    }
                    return collections;
                });
            } catch (LoreException e) {
                logger.error("Failed to get completed collections for player " + playerId, e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Get progress for all collections for a player
     *
     * @param playerId The player's UUID as string
     * @return CompletableFuture that completes with a map of collection IDs to progress values
     */
    @Override
    public CompletableFuture<Map<String, Double>> getAllPlayerProgress(String playerId) {
        return CompletableFuture.supplyAsync(() -> {
            if (playerId == null) {
                return new HashMap<>();
            }

            String sql = "SELECT collection_id, progress FROM " + t("player_collection_progress") + " WHERE player_id = ?";

            try {
                return dbHelper.executeQuery(sql, stmt -> {
                    stmt.setString(1, playerId);
                }, rs -> {
                    Map<String, Double> progress = new HashMap<>();
                    while (rs.next()) {
                        progress.put(rs.getString("collection_id"), rs.getDouble("progress"));
                    }
                    return progress;
                });
            } catch (LoreException e) {
                logger.error("Failed to get all collection progress for player " + playerId, e);
                return new HashMap<>();
            }
        });
    }

    /**
     * Check if the repository is operating in fallback mode.
     * Delegates to the FallbackTracker which manages failure counting and recovery.
     *
     * @return true if in fallback mode due to database connectivity issues
     */
    @Override
    public boolean isInFallbackMode() {
        return fallbackTracker.isInFallbackMode();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Helper method to get the current maximum sequence number in a collection
     */
    private int getCurrentMaxSequence(int collectionId, Connection conn) throws SQLException {
        String sql = "SELECT MAX(sequence_number) FROM " + t("collection_item") + " WHERE collection_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, collectionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int maxSeq = rs.getInt(1);
                    if (!rs.wasNull()) {
                        return maxSeq;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Convert a database result set to ItemProperties
     *
     * @param rs The result set to convert
     * @return The item properties, or null if conversion failed
     */
    @SuppressWarnings("unchecked")
    private ItemProperties resultSetToItemProperties(ResultSet rs) {
        try {
            String materialStr = rs.getString("material");
            Material material;
            try {
                material = Material.valueOf(materialStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown material in database: " + materialStr + ", defaulting to STONE");
                material = Material.STONE;
            }

            // Use display_name since legacy 'name' column may not exist
            String name = rs.getString("name");

            ItemProperties props = new ItemProperties(material, name);

            // Set item type if it exists
            String itemTypeStr = rs.getString("item_type");
            if (itemTypeStr != null) {
                try {
                    ItemType itemType = ItemType.valueOf(itemTypeStr);
                    props.setItemType(itemType);
                } catch (IllegalArgumentException e) {
                    logger.warning("Unknown item type in database: " + itemTypeStr);
                }
            }

            // Set rarity
            props.setRarity(rs.getString("rarity"));

            // Set obtainable status
            props.setObtainable(rs.getBoolean("is_obtainable"));

            // Set custom model data if present
            int modelData = rs.getInt("custom_model_data");
            if (!rs.wasNull()) {
                props.setCustomModelData(modelData);
            }

            // Set database ID
            props.setDatabaseId(rs.getInt("id"));

            // Set NBT data if present
            try {
                String nbtData = rs.getString("nbt_data");
                if (nbtData != null) {
                    props.setNbtData(nbtData);
                }
            } catch (SQLException e) {
                // Column might not exist in older schema, ignore
            }

            // Set lore entry ID if present
            try {
                String loreEntryId = rs.getString("lore_entry_id");
                if (loreEntryId != null) {
                    props.setLoreEntryId(loreEntryId);
                }
            } catch (SQLException e) {
                // Column might not exist in older schema, ignore
            }

            // Process JSON properties
            String jsonData = rs.getString("item_properties");
            if (jsonData != null && !jsonData.isEmpty()) {
                try {
                    JSONParser parser = new JSONParser();
                    JSONObject jsonProps = (JSONObject) parser.parse(jsonData);

                    // Extract specific properties
                    if (jsonProps.containsKey("is_glow")) {
                        props.setGlow((Boolean) jsonProps.get("is_glow"));
                    }

                    if (jsonProps.containsKey("skull_texture")) {
                        props.setSkullTexture((String) jsonProps.get("skull_texture"));
                    }

                    if (jsonProps.containsKey("lore_text")) {
                        Object loreObj = jsonProps.get("lore_text");
                        if (loreObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> loreList = (List<String>) loreObj;
                            props.setLore(loreList);
                        }
                    }

                    // Add all properties as custom properties
                    for (Object key : jsonProps.keySet()) {
                        props.setCustomProperty((String) key, jsonProps.get(key));
                    }
                } catch (ParseException e) {
                    logger.warning("Failed to parse JSON for item: " + props.getDisplayName());
                }
            }

            return props;
        } catch (SQLException e) {
            logger.error("Error converting result set to item properties", e);
            return null;
        }
    }
}

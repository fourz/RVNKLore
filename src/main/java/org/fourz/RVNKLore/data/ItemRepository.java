package org.fourz.RVNKLore.data;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.exception.LoreException;
import org.fourz.RVNKLore.exception.LoreException.LoreExceptionType;
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

/**
 * Repository class for database operations related to lore items.
 * Provides methods to create, read, update, and delete item records.
 * 
 * This class handles:
 * - Basic item CRUD operations
 * - Collection management
 * - Item metadata storage and retrieval
 * - JSON property serialization/deserialization
 */
public class ItemRepository {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseConnection dbConnection;
    private final DatabaseHelper dbHelper;
    
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
        
        // Initialize database tables
        initializeTables();
        
        logger.info("ItemRepository initialized");
    }
    
    /**
     * Initialize required database tables
     */
    private void initializeTables() {
        try {
            Connection conn = dbConnection.getConnection();
            
            // Create lore_item table if it doesn't exist
            String createItemTable = "CREATE TABLE IF NOT EXISTS lore_item (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name VARCHAR(64) NOT NULL UNIQUE, " +
                    "description TEXT, " +
                    "item_type VARCHAR(32) NOT NULL, " +
                    "rarity VARCHAR(32) NOT NULL, " +
                    "material VARCHAR(64) NOT NULL, " +
                    "is_obtainable BOOLEAN DEFAULT 1, " +
                    "custom_model_data INTEGER, " +
                    "item_properties TEXT, " +
                    "created_by VARCHAR(64), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            
            // Create collection table if it doesn't exist
            String createCollectionTable = "CREATE TABLE IF NOT EXISTS collection (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name VARCHAR(64) NOT NULL UNIQUE, " +
                    "description TEXT, " +
                    "theme VARCHAR(32), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
                    
            // Create collection_item table for managing relationships
            String createCollectionItemTable = "CREATE TABLE IF NOT EXISTS collection_item (" +
                    "collection_id INTEGER, " +
                    "item_id INTEGER, " +
                    "sequence_number INTEGER DEFAULT 0, " +
                    "item_config TEXT, " +
                    "PRIMARY KEY (collection_id, item_id), " +
                    "FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (item_id) REFERENCES lore_item(id) ON DELETE CASCADE" +
                    ")";
                    
            // Create player_collection_progress table for tracking player progress
            String createPlayerProgressTable = "CREATE TABLE IF NOT EXISTS player_collection_progress (" +
                    "player_id VARCHAR(36), " +  // UUIDs as strings
                    "collection_id INTEGER, " +
                    "progress REAL DEFAULT 0.0, " +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "completed_at TIMESTAMP, " +
                    "PRIMARY KEY (player_id, collection_id), " +
                    "FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE" +
                    ")";
            
            // Execute the table creation statements
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createItemTable);
                stmt.execute(createCollectionTable);
                stmt.execute(createCollectionItemTable);
                stmt.execute(createPlayerProgressTable);
                logger.info("Item database tables created/verified");
            }
        } catch (SQLException e) {
            logger.error("Failed to initialize item database tables", e);
        }
    }
    
    /**
     * Get an item by its ID
     * 
     * @param itemId The ID of the item to retrieve
     * @return The item properties, or null if not found
     */
    public ItemProperties getItemById(int itemId) {
        String sql = "SELECT * FROM lore_item WHERE id = ?";
        
        try {
            return dbHelper.executeQuery(sql, 
                stmt -> stmt.setInt(1, itemId),
                rs -> {
                    if (rs.next()) {
                        return resultSetToItemProperties(rs);
                    }
                    return null;
                });
        } catch (LoreException e) {
            logger.error("Failed to get item by ID: " + itemId, e);
            return null;
        }
    }
    
    /**
     * Get an item by its name
     * 
     * @param name The name of the item to retrieve
     * @return The item properties, or null if not found
     */
    public ItemProperties getItemByName(String name) {
        String sql = "SELECT * FROM lore_item WHERE name = ?";
        
        try {
            return dbHelper.executeQuery(sql, 
                stmt -> stmt.setString(1, name),
                rs -> {
                    if (rs.next()) {
                        return resultSetToItemProperties(rs);
                    }
                    return null;
                });
        } catch (LoreException e) {
            logger.error("Failed to get item by name: " + name, e);
            return null;
        }
    }
    
    /**
     * Get all items of a specific type
     * 
     * @param itemType The type of items to retrieve
     * @return A list of item properties
     */
    public List<ItemProperties> getItemsByType(String itemType) {
        String sql = "SELECT * FROM lore_item WHERE item_type = ?";
        
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
    }
    
    /**
     * Get all items
     * 
     * @return A list of all item properties
     */
    public List<ItemProperties> getAllItems() {
        String sql = "SELECT * FROM lore_item";
        
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
    }
    
    /**
     * Get all items in a collection
     * 
     * @param collectionId The ID of the collection
     * @return A list of item properties in the collection
     */
    public List<ItemProperties> getItemsByCollection(int collectionId) {
        String sql = "SELECT i.*, ci.sequence_number, ci.item_config " +
                    "FROM lore_item i " +
                    "JOIN collection_item ci ON i.id = ci.item_id " +
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
    }
    
    /**
     * Get all collections containing an item
     * 
     * @param itemId The ID of the item
     * @return A map of collection IDs to collection names
     */
    public Map<Integer, String> getCollectionsByItem(int itemId) {
        String sql = "SELECT c.id, c.name " +
                    "FROM collection c " +
                    "JOIN collection_item ci ON c.id = ci.collection_id " +
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
    }
    
    /**
     * Get all collections
     * 
     * @return A map of collection IDs to collection names
     */
    public Map<Integer, String> getAllCollections() {
        String sql = "SELECT id, name FROM collection";
        
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
    }
    
    /**
     * Insert a new item into the database
     * 
     * @param properties The item properties to save
     * @return The ID of the new item, or -1 if insert failed
     */
    public int insertItem(ItemProperties properties) {
        String sql = "INSERT INTO lore_item (name, description, item_type, rarity, material, " +
                    "is_obtainable, custom_model_data, item_properties, created_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        
        try {
            return dbHelper.executeQuery(sql, 
                stmt -> {
                    stmt.setString(1, properties.getDisplayName());
                    stmt.setString(2, properties.getDescription());
                    stmt.setString(3, properties.getItemType() != null ? properties.getItemType().name() : "STANDARD");
                    stmt.setString(4, properties.getRarity() != null ? properties.getRarity() : "COMMON");
                    stmt.setString(5, properties.getMaterial().name());
                    stmt.setBoolean(6, properties.isObtainable());
                    if (properties.getCustomModelData() > 0) {
                        stmt.setInt(7, properties.getCustomModelData());
                    } else {
                        stmt.setNull(7, java.sql.Types.INTEGER);
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
                    
                    stmt.setString(8, jsonProps.toJSONString());
                    stmt.setString(9, properties.getCreatedBy());
                },
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                    return -1;
                });
        } catch (LoreException e) {
            logger.error("Failed to insert item: " + properties.getDisplayName(), e);
            return -1;
        }
    }
    
    /**
     * Update an existing item in the database
     * 
     * @param itemId The ID of the item to update
     * @param properties The updated item properties
     * @return True if the update was successful
     */
    public boolean updateItem(int itemId, ItemProperties properties) {
        String sql = "UPDATE lore_item SET " +
                    "name = ?, description = ?, item_type = ?, rarity = ?, " +
                    "material = ?, is_obtainable = ?, custom_model_data = ?, " +
                    "item_properties = ?, updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id = ?";
        
        try {
            int rowsAffected = dbHelper.executeUpdate(sql, 
                stmt -> {
                    stmt.setString(1, properties.getDisplayName());
                    stmt.setString(2, properties.getDescription());
                    stmt.setString(3, properties.getItemType() != null ? properties.getItemType().name() : "STANDARD");
                    stmt.setString(4, properties.getRarity() != null ? properties.getRarity() : "COMMON");
                    stmt.setString(5, properties.getMaterial().name());
                    stmt.setBoolean(6, properties.isObtainable());
                    if (properties.getCustomModelData() > 0) {
                        stmt.setInt(7, properties.getCustomModelData());
                    } else {
                        stmt.setNull(7, java.sql.Types.INTEGER);
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
                    
                    stmt.setString(8, jsonProps.toJSONString());
                    stmt.setInt(9, itemId);
                });
            
            return rowsAffected > 0;
        } catch (LoreException e) {
            logger.error("Failed to update item: " + itemId, e);
            return false;
        }
    }
    
    /**
     * Delete an item from the database
     * 
     * @param itemId The ID of the item to delete
     * @return True if the delete was successful
     */
    public boolean deleteItem(int itemId) {
        String sql = "DELETE FROM lore_item WHERE id = ?";
        
        try {
            int rowsAffected = dbHelper.executeUpdate(sql, 
                stmt -> stmt.setInt(1, itemId));
            
            return rowsAffected > 0;
        } catch (LoreException e) {
            logger.error("Failed to delete item: " + itemId, e);
            return false;
        }
    }
    
    /**
     * Get an item's database ID by its unique name
     * 
     * @param name The name of the item
     * @return The item ID, or -1 if not found
     */
    public int getCurrentItemId(String name) {
        String sql = "SELECT id FROM lore_item WHERE name = ?";
        
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
    }

    /**
     * Delete an item by its unique name
     * 
     * @param name The name of the item to delete
     * @return True if deletion was successful
     */
    public boolean deleteItemByName(String name) {
        int itemId = getCurrentItemId(name);
        if (itemId == -1) {
            return false;
        }
        return deleteItem(itemId);
    }
    
    /**
     * Add an item to a collection
     * 
     * @param itemId The ID of the item
     * @param collectionId The ID of the collection
     * @param sequenceNumber The order in which to display the item
     * @param itemConfig Additional configuration for the item in this collection
     * @return True if the addition was successful
     */
    public boolean addItemToCollection(int itemId, int collectionId, int sequenceNumber, JSONObject itemConfig) {
        String sql = "INSERT INTO collection_item (collection_id, item_id, sequence_number, item_config) " +
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
    }
    
    /**
     * Remove an item from a collection
     * 
     * @param itemId The ID of the item
     * @param collectionId The ID of the collection
     * @return True if the removal was successful
     */
    public boolean removeItemFromCollection(int itemId, int collectionId) {
        String sql = "DELETE FROM collection_item WHERE item_id = ? AND collection_id = ?";
        
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
    }
    
    /**
     * Get collection details by ID
     * 
     * @param collectionId The ID of the collection to retrieve
     * @return A map containing collection details (name, description, theme)
     */
    public Map<String, String> getCollectionDetails(int collectionId) {
        String sql = "SELECT name, description, theme FROM collection WHERE id = ?";
        
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
    }

    /**
     * Create a new collection
     * 
     * @param name The name of the collection
     * @param description The collection description
     * @param theme The collection theme
     * @return The ID of the new collection, or -1 if creation failed
     */
    public int createCollection(String name, String description, String theme) {
        String sql = "INSERT INTO collection (name, description, theme) VALUES (?, ?, ?) RETURNING id";
        
        try {
            return dbHelper.executeQuery(sql, 
                stmt -> {
                    stmt.setString(1, name);
                    stmt.setString(2, description);
                    stmt.setString(3, theme);
                },
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                    return -1;
                });
        } catch (LoreException e) {
            logger.error("Failed to create collection: " + name, e);
            return -1;
        }
    }

    /**
     * Update collection properties
     * 
     * @param collectionId The ID of the collection to update
     * @param name The new name (or null to keep existing)
     * @param description The new description (or null to keep existing)
     * @param theme The new theme (or null to keep existing)
     * @return True if update was successful
     */
    public boolean updateCollection(int collectionId, String name, String description, String theme) {
        StringBuilder sql = new StringBuilder("UPDATE collection SET updated_at = CURRENT_TIMESTAMP");
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
    }
    
    /**
     * Update the sequence numbers for items in a collection
     * 
     * @param collectionId The collection ID
     * @param itemSequences Map of item IDs to their new sequence numbers
     * @return True if the update was successful
     */
    public boolean updateCollectionSequences(int collectionId, Map<Integer, Integer> itemSequences) {
        String sql = "UPDATE collection_item SET sequence_number = ? WHERE collection_id = ? AND item_id = ?";
        
        try {
            Connection conn = dbConnection.getConnection();
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
    }
    
    /**
     * Add multiple items to a collection in a single transaction
     * 
     * @param collectionId The collection ID
     * @param itemIds List of item IDs to add
     * @param startingSequence Starting sequence number (optional, defaults to existing max + 1)
     * @return True if all items were added successfully
     */
    public boolean addItemsToCollection(int collectionId, List<Integer> itemIds, Integer startingSequence) {
        if (itemIds == null || itemIds.isEmpty()) {
            return true;
        }
        
        try {
            Connection conn = dbConnection.getConnection();
            conn.setAutoCommit(false);
            
            try {
                // Get current max sequence if not provided
                int nextSequence = startingSequence != null ? startingSequence :
                    getCurrentMaxSequence(collectionId, conn) + 1;
                
                // Add all items
                String sql = "INSERT INTO collection_item (collection_id, item_id, sequence_number) VALUES (?, ?, ?)";
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
    }
    
    /**
     * Helper method to get the current maximum sequence number in a collection
     */
    private int getCurrentMaxSequence(int collectionId, Connection conn) throws SQLException {
        String sql = "SELECT MAX(sequence_number) FROM collection_item WHERE collection_id = ?";
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
            
            ItemProperties props = new ItemProperties(material, rs.getString("name"));
            props.setDescription(rs.getString("description"));
            
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

    /**
     * Get a player's progress for a specific collection
     * 
     * @param playerId The player's UUID as string
     * @param collectionId The collection identifier
     * @return Progress value between 0.0 and 1.0
     */
    public double getPlayerCollectionProgress(String playerId, String collectionId) {
        if (playerId == null || collectionId == null) {
            return 0.0;
        }

        String sql = "SELECT progress FROM player_collection_progress WHERE player_id = ? AND collection_id = ?";
        
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
    }

    /**
     * Update a player's progress for a collection
     * 
     * @param playerId The player's UUID as string
     * @param collectionId The collection identifier
     * @param progress Progress value between 0.0 and 1.0
     * @return True if successfully updated
     */
    public boolean updatePlayerCollectionProgress(String playerId, String collectionId, double progress) {
        if (playerId == null || collectionId == null) {
            return false;
        }
        
        // Ensure progress is between 0 and 1
        final double percent = Math.max(0.0, Math.min(1.0, progress));
        long currentTime = System.currentTimeMillis();
        
        String sql = "INSERT INTO player_collection_progress (player_id, collection_id, progress, last_updated) " +
                     "VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT(player_id, collection_id) DO UPDATE SET " +
                     "progress = ?, last_updated = ?";
        
        try {
            return dbHelper.executeUpdate(sql, stmt -> {
                stmt.setString(1, playerId);
                stmt.setString(2, collectionId);
                stmt.setDouble(3, percent);
                stmt.setLong(4, currentTime);
                stmt.setDouble(5, percent);
                stmt.setLong(6, currentTime);
            }) > 0;
        } catch (LoreException e) {
            logger.error("Failed to update collection progress for player " + playerId, e);
            return false;
        }
    }

    /**
     * Mark a collection as completed by a player
     * 
     * @param playerId The player's UUID as string
     * @param collectionId The collection identifier
     * @param completedAt Timestamp when the collection was completed
     * @return True if successfully marked as completed
     */
    public boolean markCollectionCompleted(String playerId, String collectionId, long completedAt) {
        if (playerId == null || collectionId == null) {
            return false;
        }
        
        String sql = "UPDATE player_collection_progress SET progress = 1.0, completed_at = ?, last_updated = ? " +
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
    }

    /**
     * Get all collections completed by a player
     * 
     * @param playerId The player's UUID as string
     * @return List of collection IDs completed by the player
     */
    public List<String> getCompletedCollections(String playerId) {
        if (playerId == null) {
            return new ArrayList<>();
        }
        
        String sql = "SELECT collection_id FROM player_collection_progress " +
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
    }

    /**
     * Get progress for all collections for a player
     * 
     * @param playerId The player's UUID as string
     * @return Map of collection IDs to progress values
     */
    public Map<String, Double> getAllPlayerProgress(String playerId) {
        if (playerId == null) {
            return new HashMap<>();
        }
        
        String sql = "SELECT collection_id, progress FROM player_collection_progress WHERE player_id = ?";
        
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
    }
    
    /**
     * Save a collection to the database
     * 
     * @param collection The collection to save
     * @return True if successfully saved
     */
    public boolean saveCollection(ItemCollection collection) {
        if (collection == null) {
            return false;
        }
        
        String sql = "INSERT OR REPLACE INTO collection (collection_id, name, description, theme_id, is_active, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        
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
    }

    /**
     * Load all collections from the database
     * 
     * @return List of all collections
     */
    public List<ItemCollection> loadAllCollections() {
        String sql = "SELECT collection_id, name, description, theme_id, is_active, created_at FROM collection";
        
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
    }
    
   

}

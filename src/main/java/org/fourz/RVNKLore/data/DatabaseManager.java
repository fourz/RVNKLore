package org.fourz.RVNKLore.data;

import org.bukkit.Location;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.config.ConfigManager;
import org.fourz.RVNKLore.data.connection.ConnectionProvider;
import org.fourz.RVNKLore.data.connection.MySQLConnectionProvider;
import org.fourz.RVNKLore.data.connection.SQLiteConnectionProvider;
import org.fourz.RVNKLore.data.dto.ItemPropertiesDTO;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.data.dto.LoreSubmissionDTO;
import org.fourz.RVNKLore.data.query.DefaultQueryExecutor;
import org.fourz.RVNKLore.data.query.MySQLQueryBuilder;
import org.fourz.RVNKLore.data.query.QueryBuilder;
import org.fourz.RVNKLore.data.query.SQLiteQueryBuilder;
import org.fourz.RVNKLore.data.query.MySQLSchemaQueryBuilder;
import org.fourz.RVNKLore.data.query.SQLiteSchemaQueryBuilder;
import org.fourz.RVNKLore.data.query.SchemaQueryBuilder;
import org.fourz.RVNKLore.data.repository.CollectionRepository;
import org.fourz.RVNKLore.data.repository.ItemRepository;
import org.fourz.RVNKLore.data.repository.LoreEntryRepository;
import org.fourz.RVNKLore.data.repository.SubmissionRepository;
import org.fourz.RVNKLore.data.repository.PlayerRepository;
import org.fourz.RVNKLore.data.service.DatabaseHealthService;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.dto.PlayerDTO;
import org.fourz.RVNKLore.data.dto.NameChangeRecordDTO;
import org.fourz.RVNKLore.data.dto.ItemCollectionDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Central hub for all database operations.
 * Handles connection management, query execution, and transaction management.
 *
 * COPILOT INSTRUCTIONS EXCEPTIONS NOTE: The ConfigManager class is the exception to the DTO configuration pattern.
 * Database settings are accessed directly from ConfigManager, and the ConnectionProvider
 * implementations retrieve their configuration settings directly from ConfigManager.
 */
public class DatabaseManager {    private final RVNKLore plugin;
    private final LogManager logger;
    private ConnectionProvider connectionProvider;
    private final DatabaseType databaseType;
    private QueryBuilder queryBuilder;
    private DefaultQueryExecutor queryExecutor;
    private final ConfigManager configManager;
    private final ExecutorService executor;
    private DatabaseHealthService healthService;
    private String storageType;
    private DatabaseSetup databaseSetup;
    private SchemaQueryBuilder schemaQueryBuilder;
    private ItemRepository itemRepository;
    private PlayerRepository playerRepository;
    private CollectionRepository collectionRepository;
    private LoreEntryRepository loreEntryRepository;
    private SubmissionRepository submissionRepository;
    
    /**
     * Database types supported by the plugin.
     */
    public enum DatabaseType {
        MYSQL, SQLITE
    }
    
    /**
     * Create a new DatabaseManager instance.
     * 
     * IMPLEMENTATION NOTE: This class is an exception to the DTO configuration pattern.
     * Database settings are accessed directly from ConfigManager, and the ConnectionProvider
     * implementations retrieve their configuration settings directly from ConfigManager
     * instead of using DTOs passed from outside.
     *
     * @param plugin The RVNKLore plugin instance
     */
    public DatabaseManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DatabaseManager");
        this.executor = Executors.newCachedThreadPool();
        this.configManager = plugin.getConfigManager();
        
        // Get storage type directly from ConfigManager
        String storageType = configManager.getStorageType();
        this.databaseType = "mysql".equalsIgnoreCase(storageType) ? 
            DatabaseType.MYSQL : DatabaseType.SQLITE;
        
        // Initialize connection provider based on type - providers get settings directly from ConfigManager
        if (databaseType == DatabaseType.MYSQL) {
            this.connectionProvider = new MySQLConnectionProvider(plugin);
        } else {
            this.connectionProvider = new SQLiteConnectionProvider(plugin);
        }
        
        // Initialize query builder and executor
        if (databaseType == DatabaseType.MYSQL) {
            this.queryBuilder = new MySQLQueryBuilder();
            this.schemaQueryBuilder = new MySQLSchemaQueryBuilder();
        } else {
            this.queryBuilder = new SQLiteQueryBuilder();
            this.schemaQueryBuilder = new SQLiteSchemaQueryBuilder();
        }
        
        this.queryExecutor = new DefaultQueryExecutor(plugin, connectionProvider);
        
        // Initialize health service
        this.healthService = new DatabaseHealthService(this, plugin);
    }

    /**
     * Initialize the database connection and schema.
     * This method should be called during plugin startup.
     */
    public void initialize() {
        logger.info("Initializing database connection...");
        
        // Initialize connection provider based on storage type
        initializeConnectionProvider();
        
        // Create appropriate query builder based on storage type
        if (databaseType == DatabaseType.MYSQL) {
            queryBuilder = new MySQLQueryBuilder();
            schemaQueryBuilder = new MySQLSchemaQueryBuilder();
        } else {
            queryBuilder = new SQLiteQueryBuilder();
            schemaQueryBuilder = new SQLiteSchemaQueryBuilder();
        }
        
        // Initialize query executor
        queryExecutor = new DefaultQueryExecutor(plugin, connectionProvider);
        
        // Initialize database setup
        databaseSetup = new DatabaseSetup(plugin, connectionProvider, schemaQueryBuilder, queryExecutor);
          // Initialize repositories
        this.itemRepository = new ItemRepository(plugin, this);
        this.playerRepository = new PlayerRepository(plugin, this);
        this.collectionRepository = new CollectionRepository(plugin, this);
        this.loreEntryRepository = new LoreEntryRepository(plugin, this);
        this.submissionRepository = new SubmissionRepository(plugin, this);
        
        // Initialize database schema
        databaseSetup.initializeTables()
            .thenRun(() -> {
                logger.info("Database schema initialized successfully");
                validateTables();
            })
            .exceptionally(e -> {
                logger.error("Failed to initialize database schema", e);
                return null;
            });
            
        // Start health check service
        healthService = new DatabaseHealthService(this, plugin);
        //healthService.startHealthCheck();
        
        logger.info("Database initialized with " + databaseType + " storage");
    }
    
    // LORE ENTRY OPERATIONS
      /**
     * Get a lore entry by ID.
     *
     * @param id The ID of the lore entry
     * @return A future containing the lore entry DTO, or null if not found
     */
    public CompletableFuture<LoreEntryDTO> getLoreEntry(int id) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        // Delegate to LoreEntryRepository
        return loreEntryRepository.getLoreEntryById(id);
    }
      /**
     * Get all lore entries.
     *
     * @return A future containing a list of lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> getAllLoreEntries() {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        // Delegate to LoreEntryRepository
        return loreEntryRepository.getAllLoreEntries();
    }
    
    /**
     * Get lore entries by type.
     *
     * @param type The type of lore entries to retrieve
     * @return A future containing a list of lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> getLoreEntriesByType(String type) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        // Delegate to LoreEntryRepository
        return loreEntryRepository.getLoreEntriesByType(type);
    }
      /**
     * Save a lore entry.
     *
     * @param dto The lore entry DTO to save
     * @return A future containing the saved lore entry ID
     */
    public CompletableFuture<Integer> saveLoreEntry(LoreEntryDTO dto) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        // Delegate to LoreEntryRepository
        return loreEntryRepository.saveLoreEntry(dto);
    }
      /**
     * Delete a lore entry.
     *
     * @param id The ID of the lore entry to delete
     * @return A future containing true if the entry was deleted, false otherwise
     */
    public CompletableFuture<Boolean> deleteLoreEntry(int id) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        // Delegate to LoreEntryRepository
        return loreEntryRepository.deleteLoreEntry(id);
    }
    
    /**
     * Search lore entries by keyword.
     *
     * @param keyword The keyword to search for
     * @return A future containing a list of matching lore entry DTOs
     */    /**
     * Search lore entries by their content in submissions.
     *
     * @param keyword The keyword to search for
     * @return A future containing a list of matching lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> searchLoreEntriesInSubmissions(String keyword) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        // Create a search term with wildcards
        String searchTerm = "%" + keyword + "%";
        
        QueryBuilder query = queryBuilder.select("e.*")
                                        .from("lore_entry e")
                                        .join("lore_submission s", "s.entry_id = e.id")
                                        .where("s.is_current_version = TRUE AND (s.content LIKE ? OR e.entry_type LIKE ?)", 
                                              searchTerm, searchTerm)
                                        .orderBy("s.submitted_at", false);
        
        return queryExecutor.executeQueryList(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error searching lore submissions", e);
                return List.of();
            });
    }
    
    // LORE SUBMISSION OPERATIONS
    
    /**
     * Get a lore submission by ID.
     *
     * @param id The ID of the lore submission
     * @return A future containing the lore submission DTO, or null if not found
     */    /**
     * Get a lore submission by ID.
     *
     * @param id The ID of the submission
     * @return A future containing the lore submission DTO, or null if not found
     */
    public CompletableFuture<LoreSubmissionDTO> getLoreSubmission(int id) {
        // Delegate to SubmissionRepository
        return submissionRepository.getLoreSubmission(id);
    }

    /**
     * Get the current version of a lore submission for an entry.
     *
     * @param entryId The ID of the lore entry
     * @return A future containing the lore submission DTO, or null if not found
     */
    public CompletableFuture<LoreSubmissionDTO> getCurrentSubmission(int entryId) {
        // Delegate to SubmissionRepository
        return submissionRepository.getCurrentSubmission(entryId);
    }
    
    /**
     * Get all submissions for a lore entry.
     *
     * @param entryId The ID of the lore entry
     * @return A future containing a list of lore submission DTOs
     */
    public CompletableFuture<List<LoreSubmissionDTO>> getSubmissionsForEntry(int entryId) {
        // Delegate to SubmissionRepository
        return submissionRepository.getSubmissionsForEntry(entryId);
    }
    
    /**
     * Save a lore submission.
     *
     * @param dto The lore submission DTO to save
     * @return A future containing the saved lore submission ID
     */
    public CompletableFuture<Integer> saveLoreSubmission(LoreSubmissionDTO dto) {
        // Delegate to SubmissionRepository
        return submissionRepository.saveLoreSubmission(dto);
    }
      /**
     * Approve a lore submission.
     *
     * @param submissionId The ID of the submission to approve
     * @param approverUuid The UUID of the staff member approving the submission
     * @return A future containing true if the submission was approved, false otherwise
     */
    public CompletableFuture<Boolean> approveSubmission(int submissionId, String approverUuid) {
        // Delegate to SubmissionRepository
        return submissionRepository.approveSubmission(submissionId, approverUuid);
    }
    
    /**
     * Approves a lore submission and creates the associated lore entry.
     */
    public CompletableFuture<Boolean> approveLoreSubmission(int submissionId, String approverUuid) {
        // Delegate to SubmissionRepository
        return submissionRepository.approveLoreSubmission(submissionId, approverUuid);
    }
      /**
     * Updates a lore submission's approval status and related fields.
     */
    public CompletableFuture<Boolean> updateLoreSubmission(LoreSubmissionDTO submission) {
        // Delegate to SubmissionRepository
        return submissionRepository.saveLoreSubmission(submission)
            .thenApply(id -> id > 0);
    }
    
    /**
     * Search for lore submissions by keyword.
     *
     * @param keyword The keyword to search for
     * @return A future containing a list of matching lore submissions
     */
    public CompletableFuture<List<LoreSubmissionDTO>> searchLoreSubmissions(String keyword) {
        // Delegate to SubmissionRepository
        return submissionRepository.searchLoreSubmissions(keyword);
    }
    
    // ITEM OPERATIONS
    
    /**
     * Get an item by ID.
     *
     * @param id The ID of the item
     * @return A future containing the item properties DTO, or null if not found
     */
    public CompletableFuture<ItemPropertiesDTO> getItem(int id) {
        return itemRepository.getItemById(id);
    }
    
    /**
     * Get an item by lore entry ID.
     *
     * @param loreEntryId The lore entry ID
     * @return A future containing the item properties DTO, or null if not found
     */
    public CompletableFuture<ItemPropertiesDTO> getItemByLoreEntry(int loreEntryId) {
        return itemRepository.getItemByLoreEntry(loreEntryId);
    }
    
    /**
     * Get items by type.
     *
     * @param type The item type
     * @return A future containing a list of item properties DTOs
     */
    public CompletableFuture<List<ItemPropertiesDTO>> getItemsByType(String type) {
        return itemRepository.getItemsByType(type);
    }
    
    /**
     * Save an item with robust error handling and logging.
     *
     * @param dto The item properties DTO to save
     * @return A future containing the saved item ID, or failed future on error
     */
    public CompletableFuture<Integer> saveItem(ItemPropertiesDTO dto) {
        return itemRepository.saveItem(dto);
    }

    /**
     * Delete an item.
     *
     * @param id The ID of the item to delete
     * @return A future containing true if the item was deleted, false otherwise
     */
    public CompletableFuture<Boolean> deleteItem(int id) {
        return itemRepository.deleteItem(id);
    }
      // PLAYER OPERATIONS

    /**
     * Get a player by UUID.
     *
     * @param uuid The UUID of the player
     * @return A future containing the PlayerDTO, or null if not found
     */
    public CompletableFuture<PlayerDTO> getPlayerByUuid(UUID uuid) {
        return playerRepository.getPlayerByUuid(uuid);
    }

    /**
     * Get players by name.
     *
     * @param name The name of the player
     * @return A future containing a list of PlayerDTOs
     */
    public CompletableFuture<List<PlayerDTO>> getPlayersByName(String name) {
        return playerRepository.getPlayersByName(name);
    }

    /**
     * Save a player.
     *
     * @param dto The PlayerDTO to save
     * @return A future containing the saved player UUID
     */
    public CompletableFuture<UUID> savePlayer(PlayerDTO dto) {
        return playerRepository.savePlayer(dto);
    }

    /**
     * Delete a player by UUID.
     *
     * @param uuid The UUID of the player to delete
     * @return A future containing true if the player was deleted, false otherwise
     */
    public CompletableFuture<Boolean> deletePlayer(UUID uuid) {
        return playerRepository.deletePlayer(uuid);
    }
    
    // COLLECTION OPERATIONS

    /**
     * Gets all collections from the database.
     *
     * @return A future containing a list of item collection DTOs
     */
    public CompletableFuture<List<ItemCollectionDTO>> getAllCollections() {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                       .from("item_collection")
                                       .orderBy("created_at", false);
        
        return queryExecutor.executeQueryList(query, ItemCollectionDTO.class);
    }

    /**
     * Gets a collection by ID.
     *
     * @param id The collection ID
     * @return A future containing the item collection DTO, or null if not found
     */
    public CompletableFuture<ItemCollectionDTO> getCollection(String id) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                       .from("item_collection")
                                       .where("id = ?", id);
        
        return queryExecutor.executeQuery(query, ItemCollectionDTO.class);
    }

    /**
     * Saves a collection (insert or update).
     *
     * @param dto The item collection DTO to save
     * @return A future containing true if successful
     */
    public CompletableFuture<Boolean> saveCollection(ItemCollectionDTO dto) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }

        return queryExecutor.executeTransaction(conn -> {
            try {
                // Check if collection exists
                QueryBuilder checkQuery = queryBuilder.select("COUNT(*)")
                                                   .from("item_collection")
                                                   .where("id = ?", dto.getId());

                try (var stmt = conn.prepareStatement(checkQuery.build())) {
                    for (int i = 0; i < checkQuery.getParameters().length; i++) {
                        stmt.setObject(i + 1, checkQuery.getParameters()[i]);
                    }
                    
                    try (var rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            // Update existing collection
                            QueryBuilder updateQuery = queryBuilder.update("item_collection")
                                                          .set("name", dto.getName())
                                                          .set("description", dto.getDescription())
                                                          .set("theme_id", dto.getThemeId())
                                                          .set("is_active", dto.isActive())
                                                          .set("updated_at", new java.sql.Timestamp(System.currentTimeMillis()))
                                                          .where("id = ?", dto.getId());
                        
                            try (var updateStmt = conn.prepareStatement(updateQuery.build())) {
                                for (int i = 0; i < updateQuery.getParameters().length; i++) {
                                    updateStmt.setObject(i + 1, updateQuery.getParameters()[i]);
                                }
                                updateStmt.executeUpdate();
                            }
                        } else {
                            // Insert new collection
                            QueryBuilder insertQuery = queryBuilder.insertInto("item_collection")
                                                          .columns("id", "name", "description", "theme_id", 
                                                                  "is_active", "created_at", "updated_at")
                                                          .values(dto.getId(), dto.getName(), dto.getDescription(), 
                                                                  dto.getThemeId(), dto.isActive(), 
                                                                  new java.sql.Timestamp(dto.getCreatedAt()), 
                                                                  new java.sql.Timestamp(System.currentTimeMillis()));
                        
                            try (var insertStmt = conn.prepareStatement(insertQuery.build(), java.sql.Statement.RETURN_GENERATED_KEYS)) {
                                for (int i = 0; i < insertQuery.getParameters().length; i++) {
                                    insertStmt.setObject(i + 1, insertQuery.getParameters()[i]);
                                }
                                insertStmt.executeUpdate();
                            }
                        }
                    }
                }
                
                // Handle serialized items if present
                if (dto.getSerializedItems() != null && !dto.getSerializedItems().isEmpty()) {
                    // Delete existing items first
                    QueryBuilder deleteItemsQuery = queryBuilder.deleteFrom("collection_item")
                                                       .where("collection_id = ?", dto.getId());
                
                    try (var deleteStmt = conn.prepareStatement(deleteItemsQuery.build())) {
                        for (int i = 0; i < deleteItemsQuery.getParameters().length; i++) {
                            deleteStmt.setObject(i + 1, deleteItemsQuery.getParameters()[i]);
                        }
                        deleteStmt.executeUpdate();
                    }
                
                    // Insert new items
                    for (int i = 0; i < dto.getSerializedItems().size(); i++) {
                        String serializedItem = dto.getSerializedItems().get(i);
                        
                        QueryBuilder insertItemQuery = queryBuilder.insertInto("collection_item")
                                                         .columns("collection_id", "item_data", "sequence")
                                                         .values(dto.getId(), serializedItem, i);
                        
                        try (var insertItemStmt = conn.prepareStatement(insertItemQuery.build())) {
                            for (int j = 0; j < insertItemQuery.getParameters().length; j++) {
                                insertItemStmt.setObject(j + 1, insertItemQuery.getParameters()[j]);
                            }
                            insertItemStmt.executeUpdate();
                        }
                    }
                }
                
                return true;
            } catch (SQLException e) {
                logger.error("Error saving collection: " + dto.getId(), e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Deletes a collection.
     *
     * @param id The collection ID to delete
     * @return A future containing true if successful
     */
    public CompletableFuture<Boolean> deleteCollection(String id) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        return queryExecutor.executeTransaction(conn -> {
            try {
                // Delete collection items first
                QueryBuilder deleteItemsQuery = queryBuilder.deleteFrom("collection_item")
                                                   .where("collection_id = ?", id);
            
                try (var deleteItemsStmt = conn.prepareStatement(deleteItemsQuery.build())) {
                    for (int i = 0; i < deleteItemsQuery.getParameters().length; i++) {
                        deleteItemsStmt.setObject(i + 1, deleteItemsQuery.getParameters()[i]);
                    }
                    deleteItemsStmt.executeUpdate();
                }
                
                // Delete collection
                QueryBuilder deleteCollectionQuery = queryBuilder.deleteFrom("item_collection")
                                                            .where("id = ?", id);
            
                try (var deleteCollectionStmt = conn.prepareStatement(deleteCollectionQuery.build())) {
                    for (int i = 0; i < deleteCollectionQuery.getParameters().length; i++) {
                        deleteCollectionStmt.setObject(i + 1, deleteCollectionQuery.getParameters()[i]);
                    }
                    int rowsAffected = deleteCollectionStmt.executeUpdate();
                    
                    return rowsAffected > 0;
                }
            } catch (SQLException e) {
                logger.error("Error deleting collection: " + id, e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Gets collections by theme.
     *
     * @param themeId The theme ID
     * @return A future containing a list of item collection DTOs
     */
    public CompletableFuture<List<ItemCollectionDTO>> getCollectionsByTheme(String themeId) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                       .from("item_collection")
                                       .where("theme_id = ?", themeId)
                                       .orderBy("created_at", false);
        
        return queryExecutor.executeQueryList(query, ItemCollectionDTO.class);
    }

    /**
     * Gets collections associated with a player.
     *
     * @param playerUuid The player UUID as a string
     * @return A future containing a list of item collection DTOs
     */
    public CompletableFuture<List<ItemCollectionDTO>> getPlayerCollections(String playerUuid) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("c.*")
                                       .from("item_collection c")
                                       .join("player_collection pc", "c.id = pc.collection_id")
                                       .where("pc.player_uuid = ?", playerUuid)
                                       .orderBy("c.created_at", false);
        
        return queryExecutor.executeQueryList(query, ItemCollectionDTO.class);
    }

    /**
     * Gets a player's progress for a collection.
     *
     * @param playerUuid The player UUID as a string
     * @param collectionId The collection ID
     * @return A future containing the progress value (0.0-1.0)
     */
    public CompletableFuture<Double> getPlayerCollectionProgress(String playerUuid, String collectionId) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("progress")
                                       .from("player_collection")
                                       .where("player_uuid = ? AND collection_id = ?", playerUuid, collectionId);
        
        return queryExecutor.executeQuery(query, Double.class)
                          .thenApply(result -> result != null ? result : 0.0);
    }

    /**
     * Updates a player's progress for a collection.
     *
     * @param playerUuid The player UUID as a string
     * @param collectionId The collection ID
     * @param progress The progress value (0.0-1.0)
     * @return A future containing true if successful
     */
    public CompletableFuture<Boolean> updatePlayerCollectionProgress(String playerUuid, String collectionId, double progress) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        return queryExecutor.executeTransaction(conn -> {
            try {
                // Check if record exists
                QueryBuilder checkQuery = queryBuilder.select("COUNT(*)")
                                                   .from("player_collection")
                                                   .where("player_uuid = ? AND collection_id = ?", playerUuid, collectionId);
                
                boolean exists = false;
                try (var checkStmt = conn.prepareStatement(checkQuery.build())) {
                    for (int i = 0; i < checkQuery.getParameters().length; i++) {
                        checkStmt.setObject(i + 1, checkQuery.getParameters()[i]);
                    }
                    
                    try (var rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            exists = rs.getInt(1) > 0;
                        }
                    }
                }
                
                if (exists) {
                    // Update existing record
                    QueryBuilder updateQuery = queryBuilder.update("player_collection")
                                                  .set("progress", progress)
                                                  .set("updated_at", new java.sql.Timestamp(System.currentTimeMillis()))
                                                  .where("player_uuid = ? AND collection_id = ?", playerUuid, collectionId);
                
                    try (var updateStmt = conn.prepareStatement(updateQuery.build())) {
                        for (int i = 0; i < updateQuery.getParameters().length; i++) {
                            updateStmt.setObject(i + 1, updateQuery.getParameters()[i]);
                        }
                        return updateStmt.executeUpdate() > 0;
                    }
                } else {
                    // Insert new record
                    QueryBuilder insertQuery = queryBuilder.insertInto("player_collection")
                                                  .columns("player_uuid", "collection_id", "progress", 
                                                          "created_at", "updated_at")
                                                  .values(playerUuid, collectionId, progress, 
                                                          new java.sql.Timestamp(System.currentTimeMillis()),
                                                          new java.sql.Timestamp(System.currentTimeMillis()));
                
                    try (var insertStmt = conn.prepareStatement(insertQuery.build())) {
                        for (int i = 0; i < insertQuery.getParameters().length; i++) {
                            insertStmt.setObject(i + 1, insertQuery.getParameters()[i]);
                        }
                        return insertStmt.executeUpdate() > 0;
                    }
                }
            } catch (SQLException e) {
                logger.error("Error updating player collection progress", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Marks a collection as completed by a player.
     *
     * @param playerUuid The player UUID as a string
     * @param collectionId The collection ID
     * @param timestamp The completion timestamp
     * @return A future containing true if successful
     */
    public CompletableFuture<Boolean> markCollectionCompleted(String playerUuid, String collectionId, long timestamp) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        return queryExecutor.executeTransaction(conn -> {
            try {
                // Check if record exists
                QueryBuilder checkQuery = queryBuilder.select("COUNT(*)")
                                                   .from("player_collection")
                                                   .where("player_uuid = ? AND collection_id = ?", playerUuid, collectionId);
                
                boolean exists = false;
                try (var checkStmt = conn.prepareStatement(checkQuery.build())) {
                    for (int i = 0; i < checkQuery.getParameters().length; i++) {
                        checkStmt.setObject(i + 1, checkQuery.getParameters()[i]);
                    }
                    
                    try (var rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            exists = rs.getInt(1) > 0;
                        }
                    }
                }
                
                if (exists) {
                    // Update existing record
                    QueryBuilder updateQuery = queryBuilder.update("player_collection")
                                                  .set("progress", 1.0)
                                                  .set("completed_at", new java.sql.Timestamp(timestamp))
                                                  .set("is_completed", true)
                                                  .set("updated_at", new java.sql.Timestamp(System.currentTimeMillis()))
                                                  .where("player_uuid = ? AND collection_id = ?", playerUuid, collectionId);
                
                    try (var updateStmt = conn.prepareStatement(updateQuery.build())) {
                        for (int i = 0; i < updateQuery.getParameters().length; i++) {
                            updateStmt.setObject(i + 1, updateQuery.getParameters()[i]);
                        }
                        return updateStmt.executeUpdate() > 0;
                    }
                } else {
                    // Insert new record
                    QueryBuilder insertQuery = queryBuilder.insertInto("player_collection")
                                                  .columns("player_uuid", "collection_id", "progress", 
                                                          "completed_at", "is_completed",
                                                          "created_at", "updated_at")
                                                  .values(playerUuid, collectionId, 1.0,
                                                          new java.sql.Timestamp(timestamp), true,
                                                          new java.sql.Timestamp(System.currentTimeMillis()),
                                                          new java.sql.Timestamp(System.currentTimeMillis()));
                
                    try (var insertStmt = conn.prepareStatement(insertQuery.build())) {
                        for (int i = 0; i < insertQuery.getParameters().length; i++) {
                            insertStmt.setObject(i + 1, insertQuery.getParameters()[i]);
                        }
                        return insertStmt.executeUpdate() > 0;
                    }
                }
            } catch (SQLException e) {
                logger.error("Error marking collection as completed", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Gets all completed collections for a player.
     *
     * @param playerUuid The player UUID as a string
     * @return A future containing a list of completed collection DTOs
     */
    public CompletableFuture<List<ItemCollectionDTO>> getCompletedCollections(String playerUuid) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("c.*")
                                       .from("item_collection c")
                                       .join("player_collection pc", "c.id = pc.collection_id")
                                       .where("pc.player_uuid = ? AND pc.is_completed = TRUE", playerUuid)
                                       .orderBy("pc.completed_at", false);
        
        return queryExecutor.executeQueryList(query, ItemCollectionDTO.class);
    }    /**
     * Attempt to reconnect to the database.
     * 
     * @throws SQLException If reconnection fails
     */
    public void reconnect() throws SQLException {
        logger.info("Attempting to reconnect to database...");
        close();
        
        if (databaseType == DatabaseType.MYSQL) {
            ((MySQLConnectionProvider) connectionProvider).initializeConnectionPool();
        } else {
            ((SQLiteConnectionProvider) connectionProvider).initializeConnection();
        }
        
        // Initialize database schema
        databaseSetup.initializeTables()
            .thenRun(() -> {
                logger.info("Database schema initialized successfully after reconnect");
                validateTables();
            })
            .exceptionally(e -> {
                logger.error("Failed to initialize database schema after reconnect", e);
                return null;
            });
    }

    /**
     * Validate the current database connection.
     * 
     * @return true if the connection is valid, false otherwise
     */
    public boolean validateConnection() {
        return connectionProvider != null && connectionProvider.validateConnection();
    }
    
    /**
     * Close the database connection and clean up resources.
     */
    public void close() {
        try {
            if (connectionProvider != null) {
                connectionProvider.close();
                logger.info("Database connection closed");
            }
        } catch (SQLException e) {
            logger.error("Error closing database connection", e);
        }
    }

    /**
     * Start the database health check service.
     */
    public void startHealthService() {
        if (healthService == null) {
            healthService = new DatabaseHealthService(this, plugin);
            healthService.start();
        }
    }

    /**
     * Stop the database health check service.
     */
    public void stopHealthService() {
        if (healthService != null) {
            healthService.stop();
            healthService = null;
        }
    }

    /**
     * Find nearby lore entries within a radius of a location.
     *
     * @param location The base location to search from
     * @param radius The radius in blocks to search within
     * @return A future containing a list of nearby lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> findNearbyLoreEntries(Location location, double radius) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(new SQLException("Database connection is not valid"));
        }
        
        QueryBuilder query = queryBuilder.select("*")
            .from("lore_entry")
            .where("world = ?", location.getWorld().getName())
            .where("is_approved = ?", true)
            .where(String.format("SQRT(POW(x - ?, 2) + POW(y - ?, 2) + POW(z - ?, 2)) <= ?",
                location.getX(), location.getY(), location.getZ(), radius));
        
        return queryExecutor.executeQueryList(query, LoreEntryDTO.class);
    }

    /**
     * Find lore entries in a specific world.
     *
     * @param worldName The name of the world
     * @return A future containing a list of lore entry DTOs in the world
     */
    public CompletableFuture<List<LoreEntryDTO>> findLoreEntriesInWorld(String worldName) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(new SQLException("Database connection is not valid"));
        }
        
        QueryBuilder query = queryBuilder.select("*")
            .from("lore_entry")
            .where("world = ?", worldName)
            .where("is_approved = ?", true);
        
        return queryExecutor.executeQueryList(query, LoreEntryDTO.class);
    }

    /**
     * Find lore entries by submitter name.
     *
     * @param submitter The name of the submitter
     * @return A future containing a list of lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> findLoreEntriesBySubmitter(String submitter) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(new SQLException("Database connection is not valid"));
        }
        
        QueryBuilder query = queryBuilder.select("*")
            .from("lore_entry")
            .where("submitted_by = ?", submitter);
        
        return queryExecutor.executeQueryList(query, LoreEntryDTO.class);
    }

    /**
     * Get a lore entry by UUID.
     *
     * @param uuid The UUID of the lore entry
     * @return A future containing the lore entry DTO, or null if not found
     */
    public CompletableFuture<LoreEntryDTO> getLoreEntryById(UUID uuid) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(new SQLException("Database connection is not valid"));
        }
        
        QueryBuilder query = queryBuilder.select("*")
            .from("lore_entry")
            .where("uuid = ?", uuid.toString());
        
        return queryExecutor.executeQuery(query, LoreEntryDTO.class);
    }

    /**
     * Find pending (unapproved) lore entries.
     *
     * @return A future containing a list of pending lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> findPendingLoreEntries() {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(new SQLException("Database connection is not valid"));
        }
        
        QueryBuilder query = queryBuilder.select("*")
            .from("lore_entry")
            .where("is_approved = ?", false)
            .orderBy("created_at", true);
        
        return queryExecutor.executeQueryList(query, LoreEntryDTO.class);
    }

    /**
     * Find recent lore entries, sorted by creation date.
     *
     * @param count The maximum number of entries to return
     * @return A future containing a list of recent lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> findRecentLoreEntries(int count) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(new SQLException("Database connection is not valid"));
        }
        
        QueryBuilder query = queryBuilder.select("*")
            .from("lore_entry")
            .where("is_approved = ?", true)
            .orderBy("created_at", false)
            .limit(count);
        
        return queryExecutor.executeQueryList(query, LoreEntryDTO.class);
    }

    /**
     * Search lore entries by text in their name or description.
     *
     * @param searchText The text to search for
     * @return A future containing a list of matching lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> searchLoreEntries(String searchText) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(new SQLException("Database connection is not valid"));
        }
        
        String pattern = "%" + searchText.toLowerCase() + "%";
        QueryBuilder query = queryBuilder.select("*")
            .from("lore_entry")
            .where("LOWER(name) LIKE ? OR LOWER(description) LIKE ?", pattern, pattern)
            .where("is_approved = ?", true)
            .orderBy("created_at", false);
        
        return queryExecutor.executeQueryList(query, LoreEntryDTO.class);
    }

    /**
     * Find lore entries specific to a player by their UUID.
     *
     * @param playerUuid The player's UUID
     * @return A future containing a list of player-specific lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> findPlayerSpecificLoreEntries(UUID playerUuid) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(new SQLException("Database connection is not valid"));
        }
        
        QueryBuilder query = queryBuilder.select("*")
            .from("lore_entry")
            .where("is_approved = ?", true)
            .where("metadata @> ?", String.format("{\"player_uuid\": \"%s\"}", playerUuid.toString()));
        
        return queryExecutor.executeQueryList(query, LoreEntryDTO.class);
    }
    
    /**
     * Initialize the connection provider based on the storage type.
     */
    private void initializeConnectionProvider() {        // Get storage type from configuration
        storageType = plugin.getConfigManager().getConfig().getString("storage.type", "sqlite").toLowerCase();
          if (storageType.equalsIgnoreCase("mysql")) {            // Get MySQL settings from configuration
            // Note: MySQLConnectionProvider gets settings directly from ConfigManager
            
            // Create MySQL connection provider
            connectionProvider = new MySQLConnectionProvider(plugin);
        } else {            // Default to SQLite
            // Note: SQLiteConnectionProvider gets settings directly from ConfigManager
            
            // Create SQLite connection provider
            connectionProvider = new SQLiteConnectionProvider(plugin);
        }
    }

    /**
     * Validate that all required database tables exist.
     */
    private void validateTables() {
        logger.info("Validating database tables...");
        
        try {
            // Check if essential tables exist
            boolean playerTableExists = tableExists("player");
            boolean loreEntryTableExists = tableExists("lore_entry");
            boolean loreSubmissionTableExists = tableExists("lore_submission");
            boolean itemPropertiesTableExists = tableExists("item_properties");
            boolean loreCollectionTableExists = tableExists("lore_collection");
            
            // Log validation results
            logger.info("Table validation results:");
            logger.info("- player: " + (playerTableExists ? "exists" : "missing"));
            logger.info("- lore_entry: " + (loreEntryTableExists ? "exists" : "missing"));
            logger.info("- lore_submission: " + (loreSubmissionTableExists ? "exists" : "missing"));
            logger.info("- item_properties: " + (itemPropertiesTableExists ? "exists" : "missing"));
            logger.info("- lore_collection: " + (loreCollectionTableExists ? "exists" : "missing"));
            
            // If any essential table is missing, initialize tables
            if (!playerTableExists || !loreEntryTableExists || !loreSubmissionTableExists || 
                !itemPropertiesTableExists || !loreCollectionTableExists) {
                logger.warning("Some essential tables are missing. Initializing database tables...");
                databaseSetup.initializeTables()
                    .thenRun(() -> logger.info("Database tables initialized successfully after validation"))
                    .exceptionally(e -> {
                        logger.error("Failed to initialize database tables after validation", e);
                        return null;
                    });
            } else {
                logger.info("All essential database tables exist");
            }
        } catch (SQLException e) {
            logger.error("Failed to validate database tables", e);
        }
    }

    /**
     * Check if a table exists in the database.
     *
     * @param tableName The name of the table to check
     * @return True if the table exists, false otherwise
     * @throws SQLException if an SQL error occurs
     */
    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = connectionProvider.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Get table metadata
            try (ResultSet tables = metaData.getTables(
                    connection.getCatalog(), 
                    connection.getSchema(), 
                    tableName, 
                    new String[]{"TABLE"})) {
                
                return tables.next();
            }
        }
    }

    public ConnectionProvider getConnectionProvider() {
        return this.connectionProvider;
    }
    
    public ItemRepository getItemRepository() {
        return this.itemRepository;
    }
    
    public PlayerRepository getPlayerRepository() {
        return this.playerRepository;
    }
    
    public CollectionRepository getCollectionRepository() {
        return this.collectionRepository;
    }

    /**
     * Gets the query builder instance.
     */
    public QueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    /**
     * Gets the query executor instance.
     */
    public DefaultQueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    /**
     * Executes a query with a custom result mapper.
     */
    private <T> CompletableFuture<T> executeQueryWithMapper(QueryBuilder query, ResultSetMapper<T> mapper) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            try (var conn = connectionProvider.getConnection();
                 var stmt = conn.prepareStatement(query.build())) {
                
                // Set parameters
                Object[] params = query.getParameters();
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                // Execute and map
                try (var rs = stmt.executeQuery()) {
                    return mapper.map(rs);
                }
            } catch (SQLException e) {
                logger.error("Error executing query with mapper", e);
                throw new CompletionException(e);
            }
        }, executor);
    }

    @FunctionalInterface
    private interface ResultSetMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /**
     * Executes a query with a custom result mapper.
     */
    public <T> CompletableFuture<T> executeQuery(QueryBuilder query, ResultSetMapper<T> mapper) {
        return executeQueryWithMapper(query, mapper);
    }

    /**
     * Saves player metadata and returns success status.
     */
    public CompletableFuture<Boolean> savePlayerMetadata(PlayerDTO dto) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean isUpdate = dto.getEntryId() != null;
                
                QueryBuilder query;
                if (isUpdate) {
                    query = queryBuilder.update("lore_submission")
                        .set("content", dto.toJson())
                        .where("entry_id = ? AND is_current_version = TRUE", dto.getEntryId());
                } else {
                    // First create the lore entry
                    String entryType = "PLAYER";
                    QueryBuilder entryQuery = queryBuilder.insertInto("lore_entry")
                        .columns("entry_type", "created_at")
                        .values(entryType, new Timestamp(System.currentTimeMillis()));
                    
                    int entryId = queryExecutor.executeInsert(entryQuery).join();
                    dto.setEntryId(entryId);

                    // Then create the submission
                    query = queryBuilder.insertInto("lore_submission")
                        .columns("entry_id", "content", "is_current_version", "submitted_at")
                        .values(entryId, dto.toJson(), true, new Timestamp(System.currentTimeMillis()));
                }

                int result = queryExecutor.executeUpdate(query).join();
                return result > 0;
            } catch (Exception e) {
                logger.error("Error saving player metadata", e);
                return false;
            }
        }, executor);
    }

    /**
     * Checks if a player exists in the database.
     */
    public CompletableFuture<Boolean> playerExists(UUID playerUuid) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }

        QueryBuilder query = queryBuilder.select("COUNT(*) as count")
            .from("lore_submission s")
            .join("lore_entry e", "e.id = s.entry_id")
            .where("e.entry_type = ? AND s.is_current_version = TRUE AND s.content LIKE ?",
                  "PLAYER", "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");

        return executeQueryWithMapper(query, rs -> rs.next() && rs.getInt("count") > 0)
            .exceptionally(e -> {
                logger.error("Error checking if player exists", e);
                return false;
            });
    }

    /**
     * Gets the stored player name from the database.
     */
    public CompletableFuture<String> getStoredPlayerName(UUID playerUuid) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }

        QueryBuilder query = queryBuilder.select("s.content")
            .from("lore_submission s")
            .join("lore_entry e", "e.id = s.entry_id")
            .where("e.entry_type = ? AND s.is_current_version = TRUE AND s.content LIKE ?",
                  "PLAYER", "%\"player_uuid\":\"" + playerUuid.toString() + "\"%")
            .limit(1);

        return executeQueryWithMapper(query, rs -> {
            if (rs.next()) {
                String content = rs.getString("content");
                return PlayerDTO.extractPlayerNameFromContent(content);
            }
            return null;
        }).exceptionally(e -> {
            logger.error("Error getting stored player name", e);
            return null;
        });
    }

    /**
     * Gets all lore entries for a player.
     */
    public CompletableFuture<List<PlayerDTO>> getPlayerLoreEntries(UUID playerUuid) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }

        QueryBuilder query = queryBuilder.select("s.entry_id", "s.content")
            .from("lore_submission s")
            .join("lore_entry e", "e.id = s.entry_id")
            .where("e.entry_type = ? AND s.content LIKE ?",
                  "PLAYER", "%\"player_uuid\":\"" + playerUuid.toString() + "\"%")
            .orderBy("s.submitted_at", false);

        return executeQueryWithMapper(query, rs -> {
            List<PlayerDTO> results = new ArrayList<>();
            while (rs.next()) {
                results.add(PlayerDTO.fromResultSet(rs));
            }
            return results;
        }).exceptionally(e -> {
            logger.error("Error getting player lore entries", e);
            return List.of();
        });
    }

    /**
     * Gets player lore entries by type.
     */
    public CompletableFuture<List<PlayerDTO>> getPlayerLoreEntriesByType(UUID playerUuid, String entryType) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }

        QueryBuilder query = queryBuilder.select("s.entry_id", "s.content")
            .from("lore_submission s")
            .join("lore_entry e", "e.id = s.entry_id")
            .where("e.entry_type = ? AND s.content LIKE ? AND s.content LIKE ?",
                  "PLAYER", 
                  "%\"player_uuid\":\"" + playerUuid.toString() + "\"%",
                  "%\"entry_type\":\"" + entryType + "\"%")
            .orderBy("s.submitted_at", false);

        return executeQueryWithMapper(query, rs -> {
            List<PlayerDTO> results = new ArrayList<>();
            while (rs.next()) {
                results.add(PlayerDTO.fromResultSet(rs));
            }
            return results;
        }).exceptionally(e -> {
            logger.error("Error getting player lore entries by type", e);
            return List.of();
        });
    }

    /**
     * Gets the name change history for a player.
     */
    public CompletableFuture<List<NameChangeRecordDTO>> getNameChangeHistory(UUID playerUuid) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }

        QueryBuilder query = queryBuilder.select("s.submitted_at", "s.content")
            .from("lore_submission s")
            .join("lore_entry e", "e.id = s.entry_id")
            .where("e.entry_type = ? AND s.content LIKE ? AND s.content LIKE ?",
                  "PLAYER", 
                  "%\"player_uuid\":\"" + playerUuid.toString() + "\"%",
                  "%\"entry_type\":\"NAME_CHANGE\"%")
            .orderBy("s.submitted_at", false);

        return executeQueryWithMapper(query, rs -> {
            List<NameChangeRecordDTO> results = new ArrayList<>();
            while (rs.next()) {
                results.add(new NameChangeRecordDTO(rs));
            }
            return results;
        }).exceptionally(e -> {
            logger.error("Error getting name change history", e);
            return List.of();        });
    }
}

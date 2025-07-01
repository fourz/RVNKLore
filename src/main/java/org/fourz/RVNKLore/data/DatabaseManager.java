package org.fourz.RVNKLore.data;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.config.ConfigManager;
import org.fourz.RVNKLore.data.config.DatabaseConfig;
import org.fourz.RVNKLore.data.connection.ConnectionProvider;
import org.fourz.RVNKLore.data.connection.MySQLConnectionProvider;
import org.fourz.RVNKLore.data.connection.SQLiteConnectionProvider;
import org.fourz.RVNKLore.data.dto.ItemPropertiesDTO;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.data.dto.LoreSubmissionDTO;
import org.fourz.RVNKLore.data.query.DefaultQueryExecutor;
import org.fourz.RVNKLore.data.query.MySQLQueryBuilder;
import org.fourz.RVNKLore.data.query.QueryBuilder;
import org.fourz.RVNKLore.data.query.QueryExecutor;
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
import org.fourz.RVNKLore.lore.LoreEntry;

import java.sql.*;


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
    private final DatabaseConfig databaseConfig;
    private final DatabaseType databaseType;
    private ConnectionProvider connectionProvider;
    private DatabaseSetup databaseSetup;
    private QueryBuilder queryBuilder;
    private QueryExecutor queryExecutor;
    private SchemaQueryBuilder schemaQueryBuilder;
    private ItemRepository itemRepository;
    private PlayerRepository playerRepository;
    private CollectionRepository collectionRepository;
    private LoreEntryRepository loreEntryRepository;
    private SubmissionRepository submissionRepository;
    private DatabaseHealthService healthService;
    // Add a field to track schema validation state
    private volatile boolean schemaValidated = false;

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
        this.databaseConfig = plugin.getConfigManager().getDatabaseConfig();
        this.databaseType = databaseConfig.getType();
        this.databaseSetup = new DatabaseSetup(plugin);
        initializeInternal();
    }

    /**
     * Initialize the database connection and setup required components.
     */
    public void initialize() {
        logger.info("Initializing database manager...");
        
        // Initialize query builder and executor based on database type
        if (databaseType == DatabaseType.MYSQL) {
            queryBuilder = new MySQLQueryBuilder();
            schemaQueryBuilder = new MySQLSchemaQueryBuilder();
        } else {
            queryBuilder = new SQLiteQueryBuilder();
            schemaQueryBuilder = new SQLiteSchemaQueryBuilder();
        }
        
        queryExecutor = new DefaultQueryExecutor(plugin, connectionProvider);
        
        // Initialize repositories
        itemRepository = new ItemRepository(plugin, this);
        playerRepository = new PlayerRepository(plugin, this);
        collectionRepository = new CollectionRepository(plugin, this);
        loreEntryRepository = new LoreEntryRepository(plugin, this);
        submissionRepository = new SubmissionRepository(plugin, this);

        // Initialize the database connection
        try {
            if (databaseType == DatabaseType.SQLITE) {
                // For SQLite, we initialize the connection here once
                ((SQLiteConnectionProvider) connectionProvider).initializeConnection();
            }
            
            // Initialize health service
            healthService = new DatabaseHealthService(this, plugin);
            healthService.start();

            // Only run schema setup and validation once at startup
            if (!schemaValidated) {
                try {
                    databaseSetup.initializeTables().join();
                    logger.info("Database schema setup complete");
                    validateTables();
                    schemaValidated = true;
                    logger.info("Database validation complete - all tables exist");
                } catch (Exception e) {
                    logger.error("Database schema setup/validation failed", e);
                    throw new RuntimeException("Database schema setup/validation failed", e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to initialize database: " + e.getMessage(), e);
        }
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
     * Search lore entries by keyword in submissions.
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
        return submissionRepository.searchLoreEntriesInSubmissions(keyword);
    }
    
    // LORE SUBMISSION OPERATIONS
    
    /**
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
      /**
     * Save player metadata.
     *
     * @param dto The PlayerDTO containing player data
     * @return CompletableFuture<Boolean> indicating success
     */
    public CompletableFuture<Boolean> savePlayerMetadata(PlayerDTO dto) {
        if (!validateConnection()) {
            return CompletableFuture.completedFuture(false);
        }
        return playerRepository.savePlayerMetadata(dto);
    }

    /**
     * Check if a player exists by UUID.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture<Boolean> indicating existence
     */
    public CompletableFuture<Boolean> playerExists(UUID playerUuid) {
        if (!validateConnection()) {
            return CompletableFuture.completedFuture(false);
        }
        return playerRepository.playerExists(playerUuid);
    }

    /**
     * Get the stored player name for a UUID.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture<String> with the player name or null
     */
    public CompletableFuture<String> getStoredPlayerName(UUID playerUuid) {
        if (!validateConnection()) {
            return CompletableFuture.completedFuture(null);
        }
        return playerRepository.getStoredPlayerName(playerUuid);
    }

    /**
     * Get all lore entries for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture<List<PlayerDTO>>
     */
    public CompletableFuture<List<PlayerDTO>> getPlayerLoreEntries(UUID playerUuid) {
        if (!validateConnection()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return playerRepository.getPlayerLoreEntries(playerUuid);
    }

    /**
     * Get player lore entries by type.
     *
     * @param playerUuid The player's UUID
     * @param entryType The entry type
     * @return CompletableFuture<List<PlayerDTO>>
     */
    public CompletableFuture<List<PlayerDTO>> getPlayerLoreEntriesByType(UUID playerUuid, String entryType) {
        if (!validateConnection()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return playerRepository.getPlayerLoreEntriesByType(playerUuid, entryType);
    }

    /**
     * Get name change history for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture<List<NameChangeRecordDTO>>
     */
    public CompletableFuture<List<NameChangeRecordDTO>> getNameChangeHistory(UUID playerUuid) {
        if (!validateConnection()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return playerRepository.getNameChangeHistory(playerUuid);
    }
    
    /**
     * Check if the database connection is valid
     */
    public boolean isConnectionValid() {
        try {
            return connectionProvider.isValid();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Attempt to reconnect to the database
     */
    public void reconnect() {
        logger.info("Attempting to reconnect to database...");
        try {
            // Close the existing connection first
            if (connectionProvider != null) {
                connectionProvider.close();
                logger.info("Database connection closed");
            }
            
            // Recreate the connection provider
            if (databaseType == DatabaseType.MYSQL) {
                connectionProvider = new MySQLConnectionProvider(plugin);
            } else {
                connectionProvider = new SQLiteConnectionProvider(plugin);
            }
            
            // Initialize the connection without schema setup
            if (databaseType == DatabaseType.SQLITE && connectionProvider instanceof SQLiteConnectionProvider) {
                ((SQLiteConnectionProvider) connectionProvider).initializeConnection();
            }
            
            // Only run schema setup and validation once at startup or on reload
            if (!schemaValidated) {
                try {
                    // Setup schema
                    DatabaseSetup databaseSetup = new DatabaseSetup(plugin, connectionProvider, 
                                                                  schemaQueryBuilder, queryExecutor);
                    databaseSetup.initializeTables().get(); // Use get() to make it synchronous
                    
                    logger.info("Database schema initialized successfully after reconnect");
                    
                    // Validate tables
                    validateTables();
                    
                    // Mark schema as validated
                    schemaValidated = true;
                } catch (Exception e) {
                    logger.error("Failed to initialize database schema after reconnect", e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to reconnect to database", e);
        }
    }

    /**
     * Initialize the database connection and setup
     */
    /**
     * Initialize the database and validate schema
     */
    private void initializeInternal() {
        // Initialize connection provider
        if (databaseType == DatabaseType.MYSQL) {
            connectionProvider = new MySQLConnectionProvider(plugin);
        } else {
            connectionProvider = new SQLiteConnectionProvider(plugin);
        }
        
        // Initialize database components
        initialize();
    }

    /**
     * Reload the database connection and schema.
     * This explicitly resets the schema validation flag to force
     * revalidation on the next initialization.
     */
    public void reload() {
        logger.info("Reloading database manager...");
        schemaValidated = false;
        close();
        initializeInternal();
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
     * Validate that all required database tables exist.
     * Only run this at startup or explicit reload.
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
            
            // Log validation results at DEBUG level only
            logger.debug("Table validation results:");
            logger.debug("- player: " + (playerTableExists ? "exists" : "missing"));
            logger.debug("- lore_entry: " + (loreEntryTableExists ? "exists" : "missing"));
            logger.debug("- lore_submission: " + (loreSubmissionTableExists ? "exists" : "missing"));
            logger.debug("- item_properties: " + (itemPropertiesTableExists ? "exists" : "missing"));
            logger.debug("- lore_collection: " + (loreCollectionTableExists ? "exists" : "missing"));
            
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
                logger.info("Database validation complete - all tables exist");
            }
        } catch (SQLException e) {
            logger.error("Database table validation failed", e);
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
    }    /**

    /**
     * Get all lore entries by type and approval status.
     * 
     * @param type The type of lore entries to retrieve
     * @param approved Whether to retrieve only approved entries
     * @return A CompletableFuture containing a list of matching lore entries
     */
    public CompletableFuture<List<LoreEntryDTO>> getLoreEntriesByTypeAndApproved(String type, boolean approved) {
        return loreEntryRepository.getLoreEntriesByTypeAndApproved(type, approved);
    }
      /**
     * Search lore entries by text in their name or description.
     *
     * @param searchText The text to search for
     * @return A future containing a list of matching lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> searchLoreEntries(String searchText) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        return loreEntryRepository.searchLoreEntries(searchText);
    }

    /**
     * Find recent lore entries, sorted by creation date.
     *
     * @param count The maximum number of entries to return
     * @return A future containing a list of recent lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> findRecentLoreEntries(int count) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        return loreEntryRepository.findRecentLoreEntries(count);
    }

    /**
     * Find pending (unapproved) lore entries.
     *
     * @return A future containing a list of pending lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> findPendingLoreEntries() {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        return loreEntryRepository.findPendingLoreEntries();
    }

    /**
     * Get a lore entry by UUID.
     *
     * @param uuid The UUID of the lore entry
     * @return A future containing the lore entry DTO, or null if not found
     */
    public CompletableFuture<LoreEntryDTO> getLoreEntryById(UUID uuid) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        return loreEntryRepository.getLoreEntryById(uuid);
    }

    /**
     * Find lore entries by submitter name.
     *
     * @param submitter The name of the submitter
     * @return A future containing a list of lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> findLoreEntriesBySubmitter(String submitter) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        return loreEntryRepository.findLoreEntriesBySubmitter(submitter);
    }

    /**
     * Find lore entries in a specific world.
     *
     * @param worldName The name of the world
     * @return A future containing a list of lore entry DTOs in the world
     */
    public CompletableFuture<List<LoreEntryDTO>> findLoreEntriesInWorld(String worldName) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        return loreEntryRepository.findLoreEntriesInWorld(worldName);
    }

    /**
     * Find nearby lore entries within a radius of a location.
     *
     * @param location The base location to search from
     * @param radius The radius in blocks to search within
     * @return A future containing a list of nearby lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> findNearbyLoreEntries(org.bukkit.Location location, double radius) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        return loreEntryRepository.findNearbyLoreEntries(location, radius);
    }

    /**
     * Get all items from the database.
     * Retrieves all items, sorted by creation date in descending order.
     *
     * @return A future containing a list of all item properties DTOs
     */
    public CompletableFuture<List<ItemPropertiesDTO>> getAllItems() {
        return itemRepository.getAllItems();
    }
    
    /**
     * Get a LoreEntry domain object by ID.
     * Convenience method that retrieves the DTO and converts it to a domain object.
     *
     * @param id The ID of the lore entry
     * @return A future containing the LoreEntry domain object, or null if not found
     */
    public CompletableFuture<LoreEntry> getLoreEntryDomain(int id) {
        return getLoreEntry(id).thenApply(dto -> {
            if (dto == null) {
                return null;
            }
            return LoreEntry.fromDTO(dto);
        });
    }

    /**
     * Get a LoreEntry domain object by UUID.
     * Convenience method that retrieves the DTO and converts it to a domain object.
     *
     * @param uuid The UUID of the lore entry
     * @return A future containing the LoreEntry domain object, or null if not found
     */
    public CompletableFuture<LoreEntry> getLoreEntryDomain(UUID uuid) {
        return getLoreEntryById(uuid).thenApply(dto -> {
            if (dto == null) {
                return null;
            }
            return LoreEntry.fromDTO(dto);
        });
    }

    /**
     * Get all lore entries by approval status.
     *
     * @param approved Whether to retrieve only approved entries
     * @return A CompletableFuture containing a list of matching lore entries
     */
    public CompletableFuture<List<LoreEntryDTO>> getLoreEntriesByApproved(boolean approved) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        return loreEntryRepository.getLoreEntriesByApproved(approved);
    }

    /**
     * Get the LoreEntry repository instance.
     *
     * @return The LoreEntryRepository instance
     */
    public LoreEntryRepository getLoreEntryRepository() {
        return this.loreEntryRepository;
    }
    
    /**
     * Checks if the database connection is valid.
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        try (Connection conn = connectionProvider.getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            logger.error("Database connection check failed", e);
            return false;
        }
    }

    /**
     * Returns basic database info string (driver/version).
     * @return info string or null if unavailable
     */
    public String getDatabaseInfo() {
        try (Connection conn = connectionProvider.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            return meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
        } catch (SQLException e) {
            logger.error("Failed to get database info", e);
            return null;
        }
    }

    /**
     * Checks if the database is in read-only mode.
     * @return true if read-only, false otherwise
     */
    public boolean isReadOnly() {
        try (Connection conn = connectionProvider.getConnection()) {
            return conn.isReadOnly();
        } catch (SQLException e) {
            logger.error("Failed to check read-only status", e);
            return false;
        }
    }

    /**
     * Returns the last connection error message, if any.
     * @return last error string or null
     */
    public String getLastConnectionError() {
        // This is a stub; implement error tracking if needed
        // For now, return null
        return null;
    }

    /**
     * Initialize database connection
     */
    private void initializeConnection() {
        if (connectionProvider == null) {
            connectionProvider = createConnectionProvider();
        }
        // Test the connection
        try (Connection conn = connectionProvider.getConnection()) {
            if (!conn.isValid(5)) { // 5 second timeout
                throw new SQLException("Database connection is invalid");
            }
        } catch (SQLException e) {
            logger.error("Failed to initialize database connection", e);
            throw new RuntimeException("Failed to initialize database connection", e);
        }
    }

    /**
     * Validate and update database schema
     */
    private void validateAndUpdateSchema() {
        try (Connection conn = connectionProvider.getConnection()) {
            if (databaseSetup == null) {
                databaseSetup = new DatabaseSetup(plugin);
            }
            databaseSetup.validateAndUpdateSchema(conn);
        } catch (SQLException e) {
            logger.error("Failed to validate and update database schema", e);
            throw new RuntimeException("Failed to validate and update database schema", e);
        }
    }

    /**
     * Create appropriate connection provider based on configuration
     */
    private ConnectionProvider createConnectionProvider() {
        if (databaseType == DatabaseType.MYSQL) {
            return new MySQLConnectionProvider(databaseConfig);
        } else {
            return new SQLiteConnectionProvider(databaseConfig);
        }
    }
}

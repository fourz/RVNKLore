package org.fourz.RVNKLore.data;

import java.util.List;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.config.DatabaseConfig;
import org.fourz.RVNKLore.data.connection.ConnectionProvider;
import org.fourz.RVNKLore.data.connection.MySQLConnectionProvider;
import org.fourz.RVNKLore.data.connection.SQLiteConnectionProvider;
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
import org.fourz.RVNKLore.data.service.QueryService;
import org.fourz.RVNKLore.debug.LogManager;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Central hub for all database operations.
 * Handles connection management, query execution, and transaction management.
 */
public class DatabaseManager {    
    private final RVNKLore plugin;
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
    private QueryService queryService;
    private volatile boolean schemaValidated = false;

    // Constructor
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
        // Use ConfigManager to get config data (already loaded)
        this.databaseConfig = new DatabaseConfig(plugin.getConfigManager());
        this.databaseType = databaseConfig.getType();
        
        // Step 1: Create connection provider first
        createConnectionProvider();
        
        // Step 2: Create DatabaseSetup with minimal dependencies
        this.databaseSetup = new DatabaseSetup(plugin, connectionProvider);
        
        // Step 3: Initialization is now explicit; do not call initialize() here
        // Step 4: Do not call initializeConnection() here
    }

    /**
     * Creates the appropriate connection provider based on database type.
     */
    private void createConnectionProvider() {
        if (databaseType == DatabaseType.MYSQL) {
            connectionProvider = new MySQLConnectionProvider(plugin);
        } else {
            connectionProvider = new SQLiteConnectionProvider(plugin);
        }
        logger.debug("Connection provider created: " + connectionProvider.getClass().getSimpleName());
    }

    /**
     * Initializes the database manager and core components. Sets up appropriate builders,
     * creates repositories, and validates the schema.
     */
    public void initialize() {
        logger.info("Initializing database manager...");

        // Select query builders based on database type
        if (databaseType == DatabaseType.MYSQL) {
            queryBuilder = new MySQLQueryBuilder();
            schemaQueryBuilder = new MySQLSchemaQueryBuilder();
        } else {
            queryBuilder = new SQLiteQueryBuilder();
            schemaQueryBuilder = new SQLiteSchemaQueryBuilder();
        }

        // Initialize components and repositories
        queryExecutor = new DefaultQueryExecutor(plugin, connectionProvider);
        databaseSetup.initialize(schemaQueryBuilder, queryExecutor);
        itemRepository = new ItemRepository(plugin, this);
        playerRepository = new PlayerRepository(plugin, this);
        collectionRepository = new CollectionRepository(plugin, this);
        loreEntryRepository = new LoreEntryRepository(plugin, this);
        submissionRepository = new SubmissionRepository(plugin, this);
        this.queryService = new QueryService(connectionProvider, logger);

        try {
            // Initialize SQLite connection if needed
            if (databaseType == DatabaseType.SQLITE) {
                ((SQLiteConnectionProvider) connectionProvider).initializeConnection();
            }

            healthService = new DatabaseHealthService(this, plugin);
            healthService.start();

            // Run schema validation
            if (!schemaValidated) {
                try {
                    databaseSetup.initializeTables().join();
                    logger.info("Database schema setup complete");
                    databaseSetup.validateSchema().join();
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

    // Core Database Operations
    
    /**
     * Check if the database connection is valid
     */
    public boolean isConnectionValid() {
        try (Connection conn = connectionProvider.getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Attempt to reconnect to the database
     * 
     * @return true if reconnection was successful, false otherwise
     */
    public boolean reconnect() {
        logger.info("Attempting to reconnect to database...");
        try {
            // Close existing connection
            if (connectionProvider != null) {
                connectionProvider.close();
            }
            
            // Create a new connection provider
            createConnectionProvider();
            
            // For SQLite, we need to initialize the connection explicitly
            if (databaseType == DatabaseType.SQLITE) {
                ((SQLiteConnectionProvider) connectionProvider).initializeConnection();
            }
            
            // Simple validation without schema checks
            try (Connection conn = connectionProvider.getConnection()) {
                boolean valid = conn != null && !conn.isClosed();
                if (valid) {
                    logger.info("Reconnection successful");
                } else {
                    logger.warning("Reconnection failed - connection invalid");
                }
                return valid;
            }
        } catch (Exception e) {
            logger.error("Reconnection failed with error", e);
            throw new RuntimeException("Failed to reconnect to database", e);
        }
    }

    /**
     * Reload the database connection and schema
     */
    public void reload() {
        schemaValidated = false;
        reconnect();
        // Force schema validation on reload
        validateSchemaIfNeeded();
    }

    public CompletableFuture<Void> reloadAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                reload();
            } catch (Exception e) {
                logger.error("Error reloading database", e);
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Validates that the database connection is valid.
     * This is called before each database operation.
     *
     * @return true if the connection is valid, false otherwise
     */
    public boolean validateConnection() {
        try {
            // For routine checks, just verify the connection is valid
            if (connectionProvider != null) {
                // The isValid method might not be directly available, use a ping-style check
                try (Connection conn = connectionProvider.getConnection()) {
                    return conn != null && !conn.isClosed();
                }
            }
            return false;
        } catch (Exception e) {
            logger.warning("Connection validation failed: " + e.getMessage());
            // Try to reconnect
            return reconnect();
        }
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

    // Health Service Management
    
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

    public ConnectionProvider getConnectionProvider() {
        return this.connectionProvider;
    }

    public LoreEntryRepository getLoreEntryRepository() {
        return this.loreEntryRepository;
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

    public SubmissionRepository getSubmissionRepository() {
        return this.submissionRepository;
    }

    /**
     * Gets the query builder instance.
     */
    public QueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    /**
     * Gets the query executor for database operations.
     * Returns the interface type rather than the implementation.
     *
     * @return The QueryExecutor interface
     */
    public QueryExecutor getQueryExecutor() {
        if (queryExecutor == null) {
            throw new IllegalStateException("QueryExecutor not initialized - call initialize() first");
        }
        return queryExecutor;
    }

    /** expose the new service for repos or callers */
    public QueryService getQueryService() {
        return queryService;
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

    // Private Helpers
    
    private void validateSchemaIfNeeded() {
        if (!schemaValidated) {
            try {
                logger.info("Validating database schema...");
                
                boolean tablesExist = databaseSetup.validateSchema().join();
                
                if (!tablesExist) {
                    logger.info("Schema validation failed, creating tables...");
                    databaseSetup.initializeTables().join();
                    logger.info("Database schema setup complete");
                }
                
                schemaValidated = true;
                logger.info("Database validation complete - all tables exist");
            } catch (Exception e) {
                logger.error("Database schema validation failed", e);
                throw new RuntimeException("Database schema validation failed", e);
            }
        }
    }
}
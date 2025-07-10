package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.config.DatabaseConfig;
import org.fourz.RVNKLore.data.connection.DatabaseConnection;
import org.fourz.RVNKLore.data.connection.provider.ConnectionProvider;
import org.fourz.RVNKLore.data.connection.provider.MySQLConnectionProvider;
import org.fourz.RVNKLore.data.connection.provider.SQLiteConnectionProvider;
import org.fourz.RVNKLore.data.connection.SQLiteConnection;
import org.fourz.RVNKLore.data.connection.MySQLConnection;
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
    private DatabaseConnection databaseConnection;
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
        this.databaseConfig = new DatabaseConfig(plugin.getConfigManager());
        this.databaseType = databaseConfig.getType();
        
        // Constructor only initializes essential fields
        // All initialization is deferred to initialize() for consistency
    }
    
    /**
     * Initializes the database manager and all components in a clear, sequential order.
     * This is the single entry point for complete database initialization.
     */
    public void initialize() {
        logger.info("Initializing database manager...");
        try {
            // Step 1: Create connection provider (file creation happens in SQLiteConnectionProvider constructor)
            createConnectionProvider();
            
            // Step 2: Setup database components
            setupQueryBuilders();
            setupDatabaseSetup();
            
            // Step 3: Start health monitoring early to catch any connection issues
            startHealthMonitoring();
            
            // Step 4: Initialize repositories and services
            setupRepositoriesAndServices();
            
            // Step 5: Perform schema validation and setup
            setupAndValidateSchema();
            
            logger.info("Database manager initialized successfully.");
        } catch (Exception e) {
            logger.error("Failed to initialize database: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates the appropriate connection provider based on database type.
     * For SQLite, this ensures the database file exists in the constructor.
     */
    private void createConnectionProvider() {
        logger.info("Creating connection provider for " + databaseType + "...");
        if (databaseType == DatabaseType.MYSQL) {
            this.connectionProvider = new MySQLConnectionProvider(plugin);
            this.databaseConnection = new MySQLConnection(plugin);
            logger.info("MySQL connection provider created");
        } else {
            this.connectionProvider = new SQLiteConnectionProvider(plugin);
            this.databaseConnection = new SQLiteConnection(plugin);
            logger.info("SQLite connection provider created with file ready");
        }
    }
    
    /**
     * Sets up the database setup component.
     */
    private void setupDatabaseSetup() {
        this.databaseSetup = new DatabaseSetup(plugin, connectionProvider);
        logger.debug("Database setup component created");
    }
    
    /**
     * Sets up query builders based on the database type.
     */
    private void setupQueryBuilders() {
        if (databaseType == DatabaseType.MYSQL) {
            queryBuilder = new MySQLQueryBuilder();
            schemaQueryBuilder = new MySQLSchemaQueryBuilder();
        } else {
            queryBuilder = new SQLiteQueryBuilder();
            schemaQueryBuilder = new SQLiteSchemaQueryBuilder();
        }
    }

    /**
     * Sets up repositories and core services.
     */
    private void setupRepositoriesAndServices() {
        queryExecutor = new DefaultQueryExecutor(plugin, connectionProvider);
        databaseSetup.initialize(schemaQueryBuilder, queryExecutor);
        itemRepository = new ItemRepository(plugin, this);
        playerRepository = new PlayerRepository(plugin, this);
        collectionRepository = new CollectionRepository(plugin, this);
        loreEntryRepository = new LoreEntryRepository(plugin, this);
        submissionRepository = new SubmissionRepository(plugin, this);
        this.queryService = new QueryService(connectionProvider, logger);
    }

    /**
     * Starts the database health monitoring service.
     */
    private void startHealthMonitoring() {
        healthService = new DatabaseHealthService(this, plugin);
        healthService.start();
    }

    /**
     * Sets up and validates the database schema.
     */
    private void setupAndValidateSchema() {
        if (!schemaValidated) {
            logger.info("Performing full schema initialization/validation");
            boolean initializationResult = databaseSetup.performFullInitialization().join();
            if (initializationResult) {
                schemaValidated = true;
                logger.info("Database schema validation successful");
            } else {
                throw new RuntimeException("Database schema initialization/validation failed");
            }
        }
    }

    // Core Database Operations
    
    /**
     * Check if the database connection is valid using the connection provider's health check.
     * For SQLite, this uses a simplified validation that only checks if the connection exists
     * and isn't closed, avoiding unreliable JDBC isValid() checks.
     *
     * @return true if the connection appears valid
     */
    public boolean isConnectionValid() {
        return connectionProvider.isHealthy();
    }

    /**
     * Attempt to reconnect to the database.
     * Delegates to the DatabaseConnection implementation for the actual logic.
     *
     * @return true if reconnection was successful
     */
    public boolean reconnect() {
        return databaseConnection.reconnect();
    }

    /**
     * Validates the database connection using appropriate checks for the database type.
     * For SQLite, this uses a simplified validation to avoid false negatives.
     * For MySQL, this performs a full validation including testing the connection.
     *
     * @return true if the connection is valid
     */
    public boolean validateConnection() {
        return connectionProvider.validateConnection();
    }

    /**
     * Reload the database connection and schema
     */
    public void reload() {
        schemaValidated = false;
        reconnect();
        // Schema validation is handled by DatabaseSetup.validateSchema() and initializeTables()
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
     * Close the database connection and clean up resources.
     */
    public void close() {
        if (databaseConnection != null) {
            databaseConnection.close();
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
        return connectionProvider;
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
        return databaseConnection.isConnected();
    }

    /**
     * Returns basic database info string (driver/version).
     * @return info string or null if unavailable
     */
    public String getDatabaseInfo() {
        return databaseConnection.getDatabaseInfo();
    }

    /**
     * Checks if the database is in read-only mode.
     * @return true if read-only, false otherwise
     */
    public boolean isReadOnly() {
        return databaseConnection.isReadOnly();
    }

    /**
     * Returns the last connection error message, if any.
     * @return last error string or null
     */
    public String getLastConnectionError() {
        return databaseConnection.getLastConnectionError();
    }

    // Private Helpers
}
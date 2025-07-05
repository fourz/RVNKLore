
package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.config.DatabaseConfig;
import org.fourz.RVNKLore.data.connection.DatabaseConnection;
import org.fourz.RVNKLore.data.connection.provider.ConnectionProvider;
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
        // Step 1: Create connection provider
        if (databaseType == DatabaseType.MYSQL) {
            this.connectionProvider = new org.fourz.RVNKLore.data.connection.provider.MySQLConnectionProvider(plugin);
            this.databaseConnection = new org.fourz.RVNKLore.data.connection.MySQLConnection(plugin);
        } else {
            this.connectionProvider = new org.fourz.RVNKLore.data.connection.provider.SQLiteConnectionProvider(plugin);
            this.databaseConnection = new org.fourz.RVNKLore.data.connection.SQLiteConnection(plugin);
        }
        // Step 2: Create DatabaseSetup with connection provider
        this.databaseSetup = new DatabaseSetup(plugin, connectionProvider);
        // Step 3: Initialization is now explicit; do not call initialize() here
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
            // For SQLite, initialize connection explicitly
            if (databaseType == DatabaseType.SQLITE) {
                ((org.fourz.RVNKLore.data.connection.provider.SQLiteConnectionProvider) connectionProvider).initializeConnection();
            }
            healthService = new DatabaseHealthService(this, plugin);
            healthService.start();
            // Run complete database initialization through DatabaseSetup
            if (!schemaValidated) {
                boolean initializationResult = databaseSetup.performFullInitialization().join();
                if (initializationResult) {
                    schemaValidated = true;
                } else {
                    throw new RuntimeException("Database initialization failed");
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
        return databaseConnection.isConnected();
    }

    /**
     * Attempt to reconnect to the database
     * 
     * @return true if reconnection was successful, false otherwise
     */
    public boolean reconnect() {
        return databaseConnection.reconnect();
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
     * Validates that the database connection is valid.
     * This is called before each database operation.
     *
     * @return true if the connection is valid, false otherwise
     */
    public boolean validateConnection() {
        return databaseConnection.isConnected();
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
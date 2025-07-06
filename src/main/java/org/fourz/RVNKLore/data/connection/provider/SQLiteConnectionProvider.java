package org.fourz.RVNKLore.data.connection.provider;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite implementation of the ConnectionProvider interface.
 * Manages a single SQLite connection with thread safety.
 * Optimized for SQLite-specific behaviors including file creation and connection validation.
 */
public class SQLiteConnectionProvider implements ConnectionProvider {
    private final RVNKLore plugin;
    private final LogManager logger;
    private Connection connection;
    private String lastConnectionError;
    private final ReentrantLock connectionLock = new ReentrantLock();
    private final File databaseFile;
    private final Map<String, Object> settings;
    private boolean initialized = false;

    /**
     * Create a new SQLite connection provider.
     * Note: This constructor only initializes fields. Call ensureDatabaseFileExists() 
     * before attempting any connection operations.
     *
     * @param plugin The RVNKLore plugin instance
     */
    public SQLiteConnectionProvider(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "SQLiteConnectionProvider");
        
        // Get SQLite settings directly from ConfigManager
        this.settings = plugin.getConfigManager().getSQLiteSettings();
        
        // Initialize database file reference (but don't create yet)
        File dataFolder = new File(plugin.getDataFolder(), "database");
        this.databaseFile = new File(dataFolder, settings.get("database").toString());
    }

    /**
     * Ensures the database directory and file exist and are accessible.
     * This method is idempotent and can be safely called multiple times.
     */
    public void ensureDatabaseFileExists() {
        File dataFolder = this.databaseFile.getParentFile();
        
        // Create database directory if needed
        if (!dataFolder.exists()) {
            boolean created = dataFolder.mkdirs();
            if (created) {
                logger.info("Created database directory: " + dataFolder.getAbsolutePath());
            } else {
                logger.error("Failed to create database directory: " + dataFolder.getAbsolutePath(), null);
            }
        }

        // Create the database file if needed
        if (!databaseFile.exists()) {
            try {
                boolean created = databaseFile.createNewFile();
                if (created) {
                    logger.info("Created SQLite database file: " + databaseFile.getAbsolutePath());
                } else {
                    logger.warning("Could not create SQLite database file (it may already exist): " + databaseFile.getAbsolutePath());
                }
            } catch (Exception e) {
                logger.error("Error creating SQLite database file: " + databaseFile.getAbsolutePath(), e);
            }
        } else {
            logger.debug("SQLite database file already exists: " + databaseFile.getAbsolutePath());
        }
    }

    /**
     * Initialize the SQLite connection.
     * This method is synchronized to prevent concurrent initialization.
     * It also uses a lock to ensure thread safety.
     */
    public synchronized void initializeConnection() {
        if (initialized && isValid()) {
            logger.debug("SQLite connection already initialized and valid, skipping initialization");
            return;
        }

        connectionLock.lock();
        try {
            // Load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            if (!initialized) {
                logger.debug("Initializing SQLite connection...");
            } else {
                logger.debug("Re-initializing SQLite connection...");
            }

            // Ensure database file exists
            ensureDatabaseFileExists();

            // Configure connection properties
            Properties properties = new Properties();
            properties.setProperty("foreign_keys", "true");
            properties.setProperty("busy_timeout", String.valueOf(settings.get("busyTimeout")));
            if ((Boolean) settings.get("walMode")) {
                properties.setProperty("journal_mode", "WAL");
            }
            properties.setProperty("synchronous", (String) settings.get("synchronous"));

            // Get the connection
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            if (connection == null || !isValid()) {
                connection = DriverManager.getConnection(url, properties);
                // Configure SQLite connection
                connection.setAutoCommit(false);
                // Additional PRAGMA settings
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA foreign_keys = ON");
                    statement.execute("PRAGMA cache_size = -4000");
                    statement.execute("PRAGMA temp_store = MEMORY");
                    connection.commit();
                }
            }

            // Test connection
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                if (!initialized) {
                    logger.info("SQLite connection established: " + databaseFile.getName());
                } else {
                    logger.debug("SQLite connection re-established");
                }
            }

            initialized = true;
            lastConnectionError = null;
        } catch (ClassNotFoundException e) {
            lastConnectionError = "SQLite JDBC driver not found";
            logger.error("SQLite JDBC driver not found", e);
            connection = null;
        } catch (SQLException e) {
            lastConnectionError = e.getMessage();
            logger.error("Failed to initialize SQLite connection", e);
            connection = null;
        } catch (Exception e) {
            lastConnectionError = e.getMessage();
            logger.error("Error initializing SQLite database", e);
            connection = null;
        } finally {
            connectionLock.unlock();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        connectionLock.lock();
        try {
            if (connection == null || connection.isClosed()) {
                initializeConnection();
                if (connection == null) {
                    throw new SQLException("Failed to initialize SQLite connection");
                }
            }
            return connection;
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Closes the SQLite connection and releases resources.
     * This method ensures that all connections are properly closed,
     * transactions are committed or rolled back, and resources are released.
     */
    @Override
    public void close() {
        connectionLock.lock();
        try {
            if (connection != null) {
                try {
                    // Attempt to commit any pending transactions before closing
                    if (!connection.isClosed() && !connection.getAutoCommit()) {
                        try {
                            connection.commit();
                            logger.debug("Committed pending transactions before closing");
                        } catch (SQLException e) {
                            logger.warning("Failed to commit transactions before closing: " + e.getMessage());
                            try {
                                connection.rollback();
                                logger.debug("Rolled back pending transactions");
                            } catch (SQLException rollbackEx) {
                                logger.warning("Failed to rollback transactions: " + rollbackEx.getMessage());
                            }
                        }
                    }
                    
                    // Close the connection
                    if (!connection.isClosed()) {
                        logger.info("Closing SQLite connection: " + databaseFile.getName());
                        connection.close();
                    }
                } catch (SQLException e) {
                    lastConnectionError = e.getMessage();
                    logger.error("Error closing SQLite connection", e);
                } finally {
                    // Ensure connection is nullified even if close throws an exception
                    connection = null;
                    initialized = false;
                }
            }
        } finally {
            connectionLock.unlock();
        }
    }


    /**
     * Checks if the SQLite connection is valid (open and not closed).
     * This is the only check needed for SQLite health.
     */
    private boolean isValid() {
        return connection != null && !isClosedQuietly();
    }

    /**
     * Helper to check if the connection is closed, suppressing exceptions.
     */
    private boolean isClosedQuietly() {
        try {
            return connection == null || connection.isClosed();
        } catch (SQLException e) {
            lastConnectionError = e.getMessage();
            logger.debug("Connection validity check failed: " + e.getMessage());
            return true;
        }
    }

    /**
     * Checks if the SQLite connection is healthy (open and not closed).
     */
    @Override
    public boolean isHealthy() {
        connectionLock.lock();
        try {
            return isValid();
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Validates the SQLite connection by checking its state and attempting a test query.
     * DRY/KISS: Only checks if the connection is open and can execute a simple query.
     */
    @Override
    public boolean validateConnection() {
        connectionLock.lock();
        try {
            if (!isValid()) {
                return false;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return true;
            } catch (SQLException e) {
                lastConnectionError = e.getMessage();
                logger.debug("Validation query failed: " + e.getMessage());
                return false;
            }
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Get the last connection error message.
     *
     * @return The last connection error message
     */
    public String getLastConnectionError() {
        return lastConnectionError;
    }
    
    /**
     * Returns the database file for this SQLite connection.
     *
     * @return The database file
     */
    public File getDatabaseFile() {
        return databaseFile;
    }
    
    /**
     * Get connection statistics for monitoring and debugging purposes.
     * This provides information about the SQLite connection state and database file.
     *
     * @return A string containing connection statistics
     */
    public String getConnectionStats() {
        connectionLock.lock();
        try {
            if (connection == null) {
                return "Database: " + databaseFile.getName() + ", Status: Not initialized";
            }
            
            boolean validConnection = isValid();
            boolean canExecuteQuery = false;
            
            if (validConnection) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SELECT 1");
                    canExecuteQuery = true;
                } catch (SQLException e) {
                    // If query fails, mark as unable to execute
                }
            }
            
            boolean fileExists = databaseFile.exists();
            long fileSize = fileExists ? databaseFile.length() : 0;
            
            return String.format(
                "Database: %s, Size: %d KB, Connected: %s, Can query: %s",
                databaseFile.getName(),
                fileSize / 1024,
                validConnection ? "Yes" : "No",
                canExecuteQuery ? "Yes" : "No"
            );
        } finally {
            connectionLock.unlock();
        }
    }
}

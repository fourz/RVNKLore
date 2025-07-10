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
     * This constructor ensures the database file exists but does not initialize
     * the connection. The connection will be initialized on first use.
     *
     * @param plugin The RVNKLore plugin instance
     */
    public SQLiteConnectionProvider(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "SQLiteConnectionProvider");
        this.settings = plugin.getConfigManager().getSQLiteSettings();
        // Place lore.db in the plugin root folder (KISS principle)
        String dbName = settings.get("database").toString();
        this.databaseFile = new File(plugin.getDataFolder(), dbName);
        // Ensure the file exists immediately in the constructor
        ensureFileExists();
    }

    /**
     * Ensures the database file exists by creating it if necessary.
     * This is called from the constructor to guarantee file existence before any operations.
     * @throws RuntimeException if the file cannot be created
     */
    private void ensureFileExists() {
        try {
            File parent = databaseFile.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new RuntimeException("Cannot create plugin data folder: " + parent);
            }
            if (!databaseFile.exists() && !databaseFile.createNewFile()) {
                throw new RuntimeException("Cannot create database file: " + databaseFile);
            }
            logger.info("SQLite database file ready: " + databaseFile.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure SQLite database file exists", e);
        }
    }



    /**
     * Initialize the SQLite connection.
     * This method is synchronized to prevent concurrent initialization.
     * It also uses a lock to ensure thread safety.
     */
    public synchronized void initializeConnection() {
        if (initialized && isValid()) {
            return;
        }

        connectionLock.lock();
        try {
            // Double-check after acquiring the lock
            if (initialized && isValid()) {
                return;
            }
            
            // Close any existing connection
            closeExistingConnection();
            
            // Load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            
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
            
            // Create new connection
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

            // Test connection
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                logger.info("SQLite connection established: " + databaseFile.getName());
            }

            initialized = true;
            lastConnectionError = null;
        } catch (ClassNotFoundException e) {
            lastConnectionError = "SQLite JDBC driver not found";
            logger.error("SQLite JDBC driver not found", e);
            connection = null;
            initialized = false;
        } catch (SQLException e) {
            lastConnectionError = e.getMessage();
            logger.error("Failed to initialize SQLite connection: " + e.getMessage(), e);
            connection = null;
            initialized = false;
        } catch (Exception e) {
            lastConnectionError = e.getMessage();
            logger.error("Error initializing SQLite database: " + e.getMessage(), e);
            connection = null;
            initialized = false;
        } finally {
            connectionLock.unlock();
        }
    }
    
    /**
     * Close any existing connection before creating a new one.
     */
    private void closeExistingConnection() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    // Try to commit any pending transactions
                    if (!connection.getAutoCommit()) {
                        try {
                            connection.commit();
                        } catch (SQLException e) {
                            try {
                                connection.rollback();
                            } catch (SQLException ex) {
                                // Just continue
                            }
                        }
                    }
                    connection.close();
                }
            } catch (SQLException e) {
                logger.warning("Error closing existing connection: " + e.getMessage());
            } finally {
                connection = null;
            }
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        connectionLock.lock();
        try {
            // Ensure we have a valid connection
            if (!isValid()) {
                initializeConnection();
                
                if (connection == null) {
                    throw new SQLException("Failed to initialize SQLite connection: " + 
                        (lastConnectionError != null ? lastConnectionError : "Unknown error"));
                }
            }
            return connection;
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Closes the SQLite connection and releases resources.
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
                            } catch (SQLException rollbackEx) {
                                logger.warning("Failed to rollback transactions: " + rollbackEx.getMessage());
                            }
                        }
                    }
                    
                    // Close the connection
                    if (!connection.isClosed()) {
                        connection.close();
                        logger.info("Closed SQLite connection: " + databaseFile.getName());
                    }
                } catch (SQLException e) {
                    lastConnectionError = e.getMessage();
                    logger.error("Error closing SQLite connection", e);
                } finally {
                    connection = null;
                    initialized = false;
                }
            }
        } finally {
            connectionLock.unlock();
        }
    }


    /**
     * Checks if the SQLite connection is valid.
     * This performs a basic check to see if the connection exists and is not closed.
     */
    private boolean isValid() {
        if (connection == null) {
            return false;
        }
        
        try {
            return !connection.isClosed();
        } catch (SQLException e) {
            lastConnectionError = e.getMessage();
            logger.debug("Connection validity check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the SQLite connection is healthy.
     * This is a lightweight check that verifies the connection exists and is not closed.
     */
    @Override
    public boolean isHealthy() {
        connectionLock.lock();
        try {
            if (!isValid()) {
                try {
                    logger.debug("Connection invalid during health check, attempting to reinitialize");
                    initializeConnection();
                    return isValid();
                } catch (Exception e) {
                    return false;
                }
            }
            return true;
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Validates the SQLite connection by checking its state and attempting a test query.
     * This is a more thorough check than isHealthy() as it actually runs a query.
     */
    @Override
    public boolean validateConnection() {
        connectionLock.lock();
        try {
            if (!isValid()) {
                initializeConnection();
            }
            
            if (connection == null) {
                return false;
            }
            
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return true;
            } catch (SQLException e) {
                lastConnectionError = e.getMessage();
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
     * Get connection statistics for monitoring purposes.
     *
     * @return A string containing connection statistics
     */
    public String getConnectionStats() {
        connectionLock.lock();
        try {
            boolean connected = isValid();
            boolean queryWorks = false;
            
            if (connected) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SELECT 1");
                    queryWorks = true;
                } catch (SQLException e) {
                    // Query failed
                }
            }
            
            long fileSize = databaseFile.exists() ? databaseFile.length() : 0;
            
            return String.format(
                "Database: %s, Size: %d KB, Connected: %s, Can query: %s",
                databaseFile.getName(),
                fileSize / 1024,
                connected ? "Yes" : "No",
                queryWorks ? "Yes" : "No"
            );
        } finally {
            connectionLock.unlock();
        }
    }
}

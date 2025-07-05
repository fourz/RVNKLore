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
     *
     * @param plugin The RVNKLore plugin instance
     */
    public SQLiteConnectionProvider(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "SQLiteConnectionProvider");
        
        // Get SQLite settings directly from ConfigManager
        this.settings = plugin.getConfigManager().getSQLiteSettings();
        
        // Setup database file
        File dataFolder = new File(plugin.getDataFolder(), "database");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        this.databaseFile = new File(dataFolder, (String) settings.get("database"));
    }

    /**
     * Initialize the SQLite connection.
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

            // Create the database file if it doesn't exist
            if (!databaseFile.exists()) {
                if (!initialized) {
                    logger.info("Creating new SQLite database file: " + databaseFile.getAbsolutePath());
                } else {
                    logger.debug("Creating new SQLite database file: " + databaseFile.getAbsolutePath());
                }
                databaseFile.createNewFile();
            }

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
                    logger.debug("SQLite connection established: " + databaseFile.getName());
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

    @Override
    public void close() {
        connectionLock.lock();
        try {
            if (connection != null && !connection.isClosed()) {
                logger.info("Closing SQLite connection...");
                connection.close();
                connection = null;
            }
        } catch (SQLException e) {
            lastConnectionError = e.getMessage();
            logger.error("Error closing SQLite connection", e);
        } finally {
            connectionLock.unlock();
        }
    }


    /**
     * Checks if the SQLite connection is usable.
     * For SQLite, we only check if the connection exists and isn't closed.
     * We don't use JDBC's isValid() as it's unreliable for SQLite.
     *
     * @return true if we have a non-null, non-closed connection
     */
    private boolean isValid() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            lastConnectionError = e.getMessage();
            logger.debug("Connection check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the SQLite connection is healthy.
     * For SQLite, a connection is considered healthy if it exists and isn't closed.
     * We avoid using JDBC's isValid() check as it can cause false negatives with SQLite.
     *
     * @return true if the connection appears healthy
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
     * This is a more thorough check than isHealthy() but still avoids unreliable JDBC methods.
     *
     * @return true if the connection is valid and can execute a test query
     */
    @Override
    public boolean validateConnection() {
        connectionLock.lock();
        try {
            if (!isValid()) {
                return false;
            }
            // Only do a lightweight test query if we think the connection is valid
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
     * Get connection statistics.
     *
     * @return A string containing connection statistics
     */
    public String getConnectionStats() {
        connectionLock.lock();
        try {
            if (connection == null) {
                return "Connection not initialized";
            }
            
            boolean isValid = false;
            try {
                isValid = !connection.isClosed() && connection.isValid(1);
            } catch (SQLException e) {
                // Ignore
            }
            
            boolean connected = false;
            try {
                connected = connection != null && !connection.isClosed();
            } catch (SQLException e) {
                // If an exception occurs, treat as not connected
                connected = false;
            }
            return String.format(
                "File: %s, Connected: %s, Valid: %s",
                databaseFile.getName(),
                connected,
                isValid
            );
        } finally {
            connectionLock.unlock();
        }
    }
}

package org.fourz.RVNKLore.data.connection;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.config.ConfigManager;
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
        
        initializeConnection();
    }

    /**
     * Initialize the SQLite connection.
     */
    public void initializeConnection() {
        try {
            // Load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            
            logger.info("Initializing SQLite connection...");
            
            // Create the database file if it doesn't exist
            if (!databaseFile.exists()) {
                logger.info("Creating new SQLite database file: " + databaseFile.getAbsolutePath());
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
                logger.info("SQLite connection established successfully to " + databaseFile.getAbsolutePath());
            }
            
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

    @Override
    public boolean isHealthy() {
        connectionLock.lock();
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            lastConnectionError = e.getMessage();
            logger.error("SQLite connection health check failed", e);
            return false;
        } finally {
            connectionLock.unlock();
        }
    }

    @Override
    public boolean validateConnection() {
        return isHealthy();
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
     * Get the database file.
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

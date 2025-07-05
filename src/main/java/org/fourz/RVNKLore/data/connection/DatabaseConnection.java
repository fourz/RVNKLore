package org.fourz.RVNKLore.data.connection;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.Debug;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

/**
 * Abstract base class for database connections
 */
public abstract class DatabaseConnection {
    protected final RVNKLore plugin;
    protected final Debug debug;
    protected Connection connection;
    protected String lastConnectionError = null;
    
    public DatabaseConnection(RVNKLore plugin) {
        this.plugin = plugin;
        Level logLevel = plugin.getConfigManager().getLogLevel();
        this.debug = Debug.createDebugger(plugin, "DatabaseConnection", logLevel);
    }
    
    /**
     * Initialize the database connection
     */
    public abstract void initialize() throws SQLException, ClassNotFoundException;
    
    // All schema/table creation logic has been migrated to DatabaseSetup. This class is now only responsible for connection management.
    
    /**
     * Close the database connection
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                debug.debug("Database connection closed");
            } catch (SQLException e) {
                debug.error("Failed to close database connection", e);
            }
        }
    }
    
    /**
     * Check if the database connection is active and valid
     */
    public boolean isConnected() {
        try {
            if (connection != null && !connection.isClosed()) {
                // Test the connection with a simple query
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SELECT 1");
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            debug.error("Database connection check failed", e);
            return false;
        }
    }
    
    /**
     * Reconnect to the database
     */
    public boolean reconnect() {
        debug.warning("Attempting to reconnect to database...");
        try {
            lastConnectionError = null;
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            initialize();
            // Table/schema creation is now handled by DatabaseSetup, not here.
            boolean connected = isConnected();
            if (connected) {
                debug.info("Successfully reconnected to database");
            } else {
                debug.warning("Failed to reconnect to database");
            }
            return connected;
        } catch (Exception e) {
            lastConnectionError = e.getMessage();
            debug.error("Failed to reconnect to database", e);
            return false;
        }
    }
    
    /**
     * Get the active database connection
     */
    public Connection getConnection() {
        return connection;
    }
    
    /**
     * Get information about the connected database
     */
    public abstract String getDatabaseInfo();
    
    /**
     * Check if the database is in read-only mode
     */
    public abstract boolean isReadOnly();
    
    /**
     * Get the last connection error message
     */
    public String getLastConnectionError() {
        return lastConnectionError;
    }
}

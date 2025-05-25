package org.fourz.RVNKLore.data;

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
    
    /**
     * Create necessary database tables
     */
    public void createTables() throws SQLException {
        debug.debug("Creating database tables...");
        String createLoreTable = "CREATE TABLE IF NOT EXISTS lore_entries (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "type VARCHAR(20) NOT NULL, " +
                "name TEXT NOT NULL, " +
                "description TEXT, " +
                "nbt_data TEXT, " +
                "world VARCHAR(64), " +
                "x DOUBLE, " +
                "y DOUBLE, " +
                "z DOUBLE, " +
                "submitted_by VARCHAR(36) NOT NULL, " +
                "approved BOOLEAN DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        
        String createMetadataTable = "CREATE TABLE IF NOT EXISTS lore_metadata (" +
                "lore_id VARCHAR(36) NOT NULL, " +
                "meta_key VARCHAR(64) NOT NULL, " +
                "meta_value TEXT, " +
                "PRIMARY KEY (lore_id, meta_key), " +
                "FOREIGN KEY (lore_id) REFERENCES lore_entries(id) ON DELETE CASCADE" +
                ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createLoreTable);
            stmt.execute(createMetadataTable);
            debug.debug("Database tables created/verified");
        }
    }
    
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
            createTables();
            
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

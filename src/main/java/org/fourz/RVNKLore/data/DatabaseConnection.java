package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Abstract base class for database connections
 */
public abstract class DatabaseConnection {
    protected final RVNKLore plugin;
    protected final LogManager logger;
    protected Connection connection;
    protected String lastConnectionError = null;
    
    public DatabaseConnection(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DatabaseConnection");
    }
    
    /**
     * Initialize the database connection
     */
    public abstract void initialize() throws SQLException, ClassNotFoundException;
    
    /**
     * Create necessary database tables
     */    
    public abstract void createTables() throws SQLException;
    
    /**
     * Close the database connection
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.debug("Database connection closed");
            } catch (SQLException e) {
                logger.error("Failed to close database connection", e);
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
            logger.error("Database connection check failed", e);
            return false;
        }
    }
    
    /**
     * Reconnect to the database
     */
    public boolean reconnect() {
        logger.warning("Attempting to reconnect to database...");
        
        try {
            lastConnectionError = null;
            
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            
            initialize();
            createTables();
            
            boolean connected = isConnected();
            if (connected) {
                logger.info("Successfully reconnected to database");
            } else {
                logger.warning("Failed to reconnect to database");
            }
            
            return connected;
        } catch (Exception e) {
            lastConnectionError = e.getMessage();
            logger.error("Failed to reconnect to database", e);
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
     * Get the database type (sqlite or mysql)
     */
    public abstract String getDatabaseType();
    
    /**
     * Get the last connection error message
     */
    public String getLastConnectionError() {
        return lastConnectionError;
    }
}

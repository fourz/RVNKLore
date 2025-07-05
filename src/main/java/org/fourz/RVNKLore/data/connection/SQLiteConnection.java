package org.fourz.RVNKLore.data.connection;

import org.fourz.RVNKLore.RVNKLore;

import java.sql.*;

/**
 * SQLite implementation of database connection
 */
public class SQLiteConnection extends DatabaseConnection {
    private final String dbPath;
    
    public SQLiteConnection(RVNKLore plugin) {
        super(plugin);
        this.dbPath = plugin.getDataFolder().getAbsolutePath() + "/lore.db";
    }
    
    @Override
    public void initialize() throws SQLException, ClassNotFoundException {
        debug.debug("Initializing SQLite connection...");
        lastConnectionError = null;
        
        Class.forName("org.sqlite.JDBC");
        String url = "jdbc:sqlite:" + dbPath;
        connection = DriverManager.getConnection(url);
        
        // Enable foreign keys support
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        
        debug.debug("Connected to SQLite database");
    }
    
    @Override
    public String getDatabaseInfo() {
        if (connection == null) {
            return "No active SQLite connection";
        }
        
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            StringBuilder info = new StringBuilder();
            
            info.append("SQLite: ")
                .append(metaData.getDatabaseProductName())
                .append(" ")
                .append(metaData.getDatabaseProductVersion());
            
            // Get SQLite pragma info
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                if (rs.next()) {
                    info.append(", Journal Mode: ").append(rs.getString(1));
                }
            } catch (Exception e) {
                // Not critical if this fails
            }
            
            return info.toString();
        } catch (SQLException e) {
            debug.error("Failed to get database info", e);
            return "Error retrieving SQLite info: " + e.getMessage();
        }
    }
    
    @Override
    public boolean isReadOnly() {
        if (connection == null) {
            return true; // No connection means effectively read-only
        }
        
        try {
            // Check if we can write to the database
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS rw_test (id INTEGER)");
                stmt.execute("INSERT INTO rw_test VALUES (1)");
                stmt.execute("DELETE FROM rw_test WHERE id = 1");
                return false; // If we get here, it's not read-only
            }
        } catch (SQLException e) {
            debug.debug("Database appears to be read-only: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * Checks if the SQLite connection is valid (non-null and not closed).
     * This avoids JDBC isValid() and any test queries, which can cause false positives.
     *
     * @return true if the connection is open, false otherwise
     */
    public boolean isValid() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            lastConnectionError = e.getMessage();
            debug.debug("Connection check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Attempts to reconnect to the SQLite database by closing and re-initializing the connection.
     * Uses only isValid() for validation to avoid false positives.
     *
     * @return true if reconnection was successful, false otherwise
     */
    @Override
    public boolean reconnect() {
        try {
            if (isValid()) {
                connection.close();
            }
            initialize();
            // Validate the new connection (non-null and not closed)
            return isValid();
        } catch (Exception e) {
            lastConnectionError = e.getMessage();
            debug.error("Failed to reconnect to SQLite database", e);
            return false;
        }
    }
}

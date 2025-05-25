package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;

import java.sql.*;

/**
 * MySQL implementation of database connection
 */
public class MySQLConnection extends DatabaseConnection {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    
    public MySQLConnection(RVNKLore plugin) {
        super(plugin);
        this.host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        this.port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        this.database = plugin.getConfig().getString("storage.mysql.database", "minecraft");
        this.username = plugin.getConfig().getString("storage.mysql.username", "root");
        this.password = plugin.getConfig().getString("storage.mysql.password", "");
    }
    
    @Override
    public void initialize() throws SQLException, ClassNotFoundException {
        debug.debug("Initializing MySQL connection...");
        lastConnectionError = null;
        
        Class.forName("com.mysql.jdbc.Driver");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";
        connection = DriverManager.getConnection(url, username, password);
        debug.debug("Connected to MySQL database");
    }
    
    @Override
    public String getDatabaseInfo() {
        if (connection == null) {
            return "No active MySQL connection";
        }
        
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            StringBuilder info = new StringBuilder();
            
            info.append("MySQL: ")
                .append(metaData.getDatabaseProductName())
                .append(" ")
                .append(metaData.getDatabaseProductVersion());
            
            // Check server variables for additional info
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'version%'")) {
                while (rs.next()) {
                    info.append(", ").append(rs.getString(1)).append(": ").append(rs.getString(2));
                }
            } catch (Exception e) {
                // Not critical if this fails
            }
            
            return info.toString();
        } catch (SQLException e) {
            debug.error("Failed to get database info", e);
            return "Error retrieving MySQL info: " + e.getMessage();
        }
    }
    
    @Override
    public boolean isReadOnly() {
        if (connection == null) {
            return true; // No connection means effectively read-only
        }
        
        try {
            return connection.isReadOnly();
        } catch (SQLException e) {
            debug.error("Error checking if database is read-only", e);
            return true; // Assume read-only in case of error
        }
    }
}

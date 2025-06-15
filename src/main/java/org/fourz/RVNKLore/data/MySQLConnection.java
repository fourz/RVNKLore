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
        logger.debug("Initializing MySQL connection...");
        lastConnectionError = null;
        
        Class.forName("com.mysql.jdbc.Driver");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";
        connection = DriverManager.getConnection(url, username, password);
        logger.debug("Connected to MySQL database");
    }
    
    @Override
    public void createTables() throws SQLException {
        logger.debug("Creating database tables in MySQL...");
        
        // --- Core Lore Schema Tables ---
        String createLoreEntryTable = "CREATE TABLE IF NOT EXISTS lore_entry (" +
                "id CHAR(36) PRIMARY KEY, " + 
                "entry_type VARCHAR(50) NOT NULL, " +
                "name VARCHAR(100) NOT NULL, " +
                "CONSTRAINT uq_lore_entry_name_type UNIQUE (name, entry_type)" +
                ")";
                
        String createLoreSubmissionTable = "CREATE TABLE IF NOT EXISTS lore_submission (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "entry_id CHAR(36) NOT NULL, " +
                "slug VARCHAR(150) NOT NULL, " +
                "visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', " +
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', " +
                "submitter_uuid CHAR(36) NOT NULL, " +
                "submission_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', " +
                "approved_by CHAR(36), " +
                "approved_at TIMESTAMP NULL, " +
                "view_count INT NOT NULL DEFAULT 0, " +
                "last_viewed_at TIMESTAMP NULL, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP NULL, " +
                "content_version INT NOT NULL DEFAULT 1, " +
                "is_current_version BOOLEAN NOT NULL DEFAULT FALSE, " +
                "content TEXT, " +
                "CONSTRAINT uq_lore_submission_entry_version UNIQUE (entry_id, content_version), " +
                "CONSTRAINT uq_lore_submission_slug UNIQUE (slug), " +
                "FOREIGN KEY (entry_id) REFERENCES lore_entry(id) ON DELETE CASCADE" +
                ")";
                
        String createLoreItemTable = "CREATE TABLE IF NOT EXISTS lore_item (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "name VARCHAR(64) NOT NULL, " +
                "short_uuid VARCHAR(12), " +
                "lore_entry_id CHAR(36) NOT NULL, " +
                "material VARCHAR(50) NOT NULL, " +
                "item_type VARCHAR(50) NOT NULL, " +
                "rarity VARCHAR(20) NOT NULL, " +
                "is_obtainable BOOLEAN DEFAULT TRUE, " +
                "custom_model_data INT, " +
                "season_id INT, " +
                "is_vote_reward BOOLEAN NOT NULL DEFAULT FALSE, " +
                "item_properties TEXT, " +
                "drop_settings TEXT, " +
                "created_by VARCHAR(64), " +
                "nbt_data TEXT, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "CONSTRAINT uq_lore_item_entry UNIQUE (lore_entry_id), " +
                "FOREIGN KEY (entry_id) REFERENCES lore_entry(id) ON DELETE CASCADE" +
                ")";
        
        // Execute table creation statements with MySQL's syntax
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createLoreEntryTable);
            stmt.execute(createLoreSubmissionTable);
            stmt.execute(createLoreItemTable);
            
            // Create indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_lore_submission_entry_id ON lore_submission(entry_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_lore_item_entry_id ON lore_item(lore_entry_id)");
            
            // Create other required tables
            // ... (other table creation statements similar to SQLite but with MySQL syntax)
            
            logger.debug("MySQL database tables created/verified");
        }
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
            logger.error("Failed to get database info", e);
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
            logger.error("Error checking if database is read-only", e);
            return true; // Assume read-only in case of error
        }
    }
    
    @Override
    public String getDatabaseType() {
        return "mysql";
    }
}

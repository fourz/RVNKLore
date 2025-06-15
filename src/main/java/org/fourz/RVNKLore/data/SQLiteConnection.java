package org.fourz.RVNKLore.data;

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
        logger.debug("Initializing SQLite connection...");
        lastConnectionError = null;
        
        Class.forName("org.sqlite.JDBC");
        String url = "jdbc:sqlite:" + dbPath;
        connection = DriverManager.getConnection(url);
        
        // Enable foreign keys support
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        
        logger.debug("Connected to SQLite database");
    }
    
    @Override
    public void createTables() throws SQLException {
        logger.debug("Creating database tables...");
        
        // --- Core Lore Schema Tables ---
        String createLoreEntryTable = "CREATE TABLE IF NOT EXISTS lore_entry (" +
                "id CHAR(36) PRIMARY KEY, " + // switch to UUID primary key
                "entry_type VARCHAR(50) NOT NULL, " +
                "name VARCHAR(100) NOT NULL, " +
                "CONSTRAINT uq_lore_entry_name_type UNIQUE (name, entry_type)" +
                ")";
                
        String createLoreSubmissionTable = "CREATE TABLE IF NOT EXISTS lore_submission (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "entry_id CHAR(36) NOT NULL, " + // align with UUID key
                "slug VARCHAR(150) NOT NULL, " +
                "visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', " +
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', " +
                "submitter_uuid CHAR(36) NOT NULL, " +
                "submission_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', " +
                "approved_by CHAR(36), " +
                "approved_at TIMESTAMP, " +
                "view_count INTEGER NOT NULL DEFAULT 0, " +
                "last_viewed_at TIMESTAMP, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP, " +
                "content_version INTEGER NOT NULL DEFAULT 1, " +
                "is_current_version BOOLEAN NOT NULL DEFAULT FALSE, " +
                "content TEXT, " +
                "CONSTRAINT uq_lore_submission_entry_version UNIQUE (entry_id, content_version), " +
                "CONSTRAINT uq_lore_submission_slug UNIQUE (slug), " +
                "CONSTRAINT ck_lore_submission_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DRAFT', 'PENDING_APPROVAL')), " +
                "CONSTRAINT ck_lore_submission_visibility CHECK (visibility IN ('PUBLIC', 'STAFF_ONLY', 'HIDDEN')), " +
                "FOREIGN KEY (entry_id) REFERENCES lore_entry(id) ON DELETE CASCADE" +
                ")";
        String createLoreItemTable = "CREATE TABLE IF NOT EXISTS lore_item (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name VARCHAR(64) NOT NULL, " +
                "short_uuid VARCHAR(12), " +
                "lore_entry_id CHAR(36) NOT NULL, " + // make non-null and align type
                "material VARCHAR(50) NOT NULL, " +
                "item_type VARCHAR(50) NOT NULL, " +
                "rarity VARCHAR(20) NOT NULL, " +
                "is_obtainable BOOLEAN DEFAULT 1, " +
                "custom_model_data INTEGER, " +
                "season_id INTEGER, " +
                "is_vote_reward BOOLEAN NOT NULL DEFAULT FALSE, " +
                "item_properties TEXT, " +
                "drop_settings TEXT, " +
                "created_by VARCHAR(64), " +
                "nbt_data TEXT, " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "CONSTRAINT uq_lore_item_entry UNIQUE (lore_entry_id), " +
                "FOREIGN KEY (lore_entry_id) REFERENCES lore_entry(id) ON DELETE CASCADE" +
                ")";
                
        // Keep existing metadata table for backward compatibility during transition
        String createMetadataTable = "CREATE TABLE IF NOT EXISTS lore_metadata (" +
                "lore_id VARCHAR(36) NOT NULL, " +
                "meta_key VARCHAR(64) NOT NULL, " +
                "meta_value TEXT, " +
                "PRIMARY KEY (lore_id, meta_key), " +
                "FOREIGN KEY (lore_id) REFERENCES lore_entries(id) ON DELETE CASCADE" +
                ")";
        
        // Create index for fast lookups
        String createLoreSubmissionEntryIndex = 
                "CREATE INDEX IF NOT EXISTS idx_lore_submission_entry_id ON lore_submission(entry_id)";
        
        String createLoreItemEntryIndex = 
                "CREATE INDEX IF NOT EXISTS idx_lore_item_entry_id ON lore_item(lore_entry_id)";
        
        try (Statement stmt = connection.createStatement()) {
            // Create new schema tables
            stmt.execute(createLoreEntryTable);
            stmt.execute(createLoreSubmissionTable);
            stmt.execute(createLoreItemTable);
            stmt.execute(createLoreSubmissionEntryIndex);
            stmt.execute(createLoreItemEntryIndex);
            
            // Create legacy table for backward compatibility
            stmt.execute("CREATE TABLE IF NOT EXISTS lore_entries (" +
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
                ")");
            stmt.execute(createMetadataTable);
            
            // --- Collection System Tables ---
            String createCollectionTable = "CREATE TABLE IF NOT EXISTS collection (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "collection_id TEXT UNIQUE NOT NULL, " +
                "name TEXT NOT NULL, " +
                "description TEXT, " +
                "theme_id TEXT, " +
                "is_active BOOLEAN DEFAULT 1, " +
                "created_at INTEGER NOT NULL" +
            ")";
            String createPlayerCollectionProgressTable = "CREATE TABLE IF NOT EXISTS player_collection_progress (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "player_id TEXT NOT NULL, " +
                "collection_id TEXT NOT NULL, " +
                "progress REAL DEFAULT 0.0, " +
                "completed_at INTEGER, " +
                "last_updated INTEGER NOT NULL, " +
                "UNIQUE(player_id, collection_id)" +
            ")";
            String createCollectionRewardTable = "CREATE TABLE IF NOT EXISTS collection_reward (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "collection_id TEXT NOT NULL, " +
                "reward_type TEXT NOT NULL, " +
                "reward_data TEXT, " +
                "is_claimed BOOLEAN DEFAULT 0" +
            ")";
            stmt.execute(createCollectionTable);
            stmt.execute(createPlayerCollectionProgressTable);
            stmt.execute(createCollectionRewardTable);
            logger.debug("Database tables created/verified");
        }
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
            logger.error("Failed to get database info", e);
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
            logger.debug("Database appears to be read-only: " + e.getMessage());
            return true;
        }
    }
    
    @Override
    public String getDatabaseType() {
        return "sqlite";
    }
}

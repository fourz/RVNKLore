package org.fourz.RVNKLore.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.util.Debug;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.logging.Level;

/**
 * Manages database connections and operations for the lore system
 */
public class DatabaseManager {
    private final RVNKLore plugin;
    private final Debug debug;
    private Connection connection;
    private final String storageType;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    
    public DatabaseManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "DatabaseManager", Level.FINE);
        
        // Load database configuration
        storageType = plugin.getConfigManager().getStorageType();
        host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        database = plugin.getConfig().getString("storage.mysql.database", "minecraft");
        username = plugin.getConfig().getString("storage.mysql.username", "root");
        password = plugin.getConfig().getString("storage.mysql.password", "");
        
        initializeDatabase();
    }
    
    /**
     * Initialize the database connection and tables
     */
    private void initializeDatabase() {
        debug.debug("Initializing database...");
        try {
            if (storageType.equalsIgnoreCase("mysql")) {
                Class.forName("com.mysql.jdbc.Driver");
                String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";
                connection = DriverManager.getConnection(url, username, password);
                debug.debug("Connected to MySQL database");
            } else {
                // Default to SQLite
                Class.forName("org.sqlite.JDBC");
                String url = "jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/lore.db";
                connection = DriverManager.getConnection(url);
                debug.debug("Connected to SQLite database");
            }
            
            createTables();
        } catch (SQLException | ClassNotFoundException e) {
            debug.error("Failed to initialize database", e);
        }
    }
    
    /**
     * Create necessary database tables if they don't exist
     */
    private void createTables() throws SQLException {
        debug.debug("Checking/creating database tables...");
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
     * Add a new lore entry to the database with metadata
     */
    public boolean addLoreEntry(LoreEntry entry) {
        // First add the main entry
        String sql = "INSERT INTO lore_entries (id, type, name, description, nbt_data, world, x, y, z, submitted_by, approved) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try {
            // Enable transaction
            connection.setAutoCommit(false);
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, entry.getId().toString());
                stmt.setString(2, entry.getType().name());
                stmt.setString(3, entry.getName());
                stmt.setString(4, entry.getDescription());
                stmt.setString(5, entry.getNbtData());
                
                Location loc = entry.getLocation();
                if (loc != null) {
                    stmt.setString(6, loc.getWorld().getName());
                    stmt.setDouble(7, loc.getX());
                    stmt.setDouble(8, loc.getY());
                    stmt.setDouble(9, loc.getZ());
                } else {
                    stmt.setNull(6, Types.VARCHAR);
                    stmt.setNull(7, Types.DOUBLE);
                    stmt.setNull(8, Types.DOUBLE);
                    stmt.setNull(9, Types.DOUBLE);
                }
                
                stmt.setString(10, entry.getSubmittedBy());
                stmt.setBoolean(11, entry.isApproved());
                
                stmt.executeUpdate();
                
                // Now add any metadata
                addMetadataForEntry(entry);
                
                // Commit the transaction
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                debug.error("Failed to add lore entry to database", e);
                return false;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            debug.error("Transaction error when adding lore entry", e);
            return false;
        }
    }
    
    /**
     * Helper method to add metadata for an entry
     */
    private void addMetadataForEntry(LoreEntry entry) throws SQLException {
        Map<String, String> metadata = entry.getAllMetadata();
        if (metadata.isEmpty()) {
            return;
        }
        
        String sql = "INSERT INTO lore_metadata (lore_id, meta_key, meta_value) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (Map.Entry<String, String> meta : metadata.entrySet()) {
                stmt.setString(1, entry.getId().toString());
                stmt.setString(2, meta.getKey());
                stmt.setString(3, meta.getValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
    
    /**
     * Update an entry and its metadata
     */
    public boolean updateLoreEntry(LoreEntry entry) {
        String sql = "UPDATE lore_entries SET name = ?, description = ?, nbt_data = ?, " +
                "world = ?, x = ?, y = ?, z = ?, approved = ? WHERE id = ?";
        
        try {
            connection.setAutoCommit(false);
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, entry.getName());
                stmt.setString(2, entry.getDescription());
                stmt.setString(3, entry.getNbtData());
                
                Location loc = entry.getLocation();
                if (loc != null) {
                    stmt.setString(4, loc.getWorld().getName());
                    stmt.setDouble(5, loc.getX());
                    stmt.setDouble(6, loc.getY());
                    stmt.setDouble(7, loc.getZ());
                } else {
                    stmt.setNull(4, Types.VARCHAR);
                    stmt.setNull(5, Types.DOUBLE);
                    stmt.setNull(6, Types.DOUBLE);
                    stmt.setNull(7, Types.DOUBLE);
                }
                
                stmt.setBoolean(8, entry.isApproved());
                stmt.setString(9, entry.getId().toString());
                
                stmt.executeUpdate();
                
                // Update metadata: first delete existing, then add new
                deleteMetadataForEntry(entry.getId().toString());
                addMetadataForEntry(entry);
                
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                debug.error("Failed to update lore entry in database", e);
                return false;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            debug.error("Transaction error when updating lore entry", e);
            return false;
        }
    }
    
    /**
     * Delete metadata for an entry
     */
    private void deleteMetadataForEntry(String id) throws SQLException {
        String sql = "DELETE FROM lore_metadata WHERE lore_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Get all lore entries from the database
     * 
     * @return A list of all lore entries
     */
    public List<LoreEntry> getAllLoreEntries() {
        List<LoreEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM lore_entries";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                entries.add(resultSetToLoreEntry(rs));
            }
        } catch (SQLException e) {
            debug.error("Failed to get lore entries from database", e);
        }
        
        return entries;
    }
    
    /**
     * Convert a database result set to a LoreEntry object with metadata
     * 
     * @param rs The result set to convert
     * @return The created LoreEntry
     */
    private LoreEntry resultSetToLoreEntry(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        LoreType type = LoreType.valueOf(rs.getString("type"));
        String name = rs.getString("name");
        String description = rs.getString("description");
        String nbtData = rs.getString("nbt_data");
        
        // Handle location
        Location location = null;
        String worldName = rs.getString("world");
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                location = new Location(world, x, y, z);
            }
        }
        
        String submittedBy = rs.getString("submitted_by");
        boolean approved = rs.getBoolean("approved");
        Timestamp createdAt = rs.getTimestamp("created_at");
        
        LoreEntry entry = new LoreEntry(id, type, name, description, nbtData, location, submittedBy, approved, createdAt);
        
        // Load metadata
        loadMetadataForEntry(entry);
        
        return entry;
    }
    
    /**
     * Load metadata for a lore entry
     */
    private void loadMetadataForEntry(LoreEntry entry) throws SQLException {
        String sql = "SELECT meta_key, meta_value FROM lore_metadata WHERE lore_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, entry.getId().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("meta_key");
                    String value = rs.getString("meta_value");
                    entry.addMetadata(key, value);
                }
            }
        }
    }
    
    /**
     * Export all lore entries to JSON format
     * 
     * @return JSON string containing all lore entries
     */
    public String exportLoreEntriesToJson() {
        List<LoreEntry> entries = getAllLoreEntries();
        List<JSONObject> jsonEntries = new ArrayList<>();
        
        for (LoreEntry entry : entries) {
            jsonEntries.add(entry.toJson());
        }
        
        JSONObject result = new JSONObject();
        result.put("lore_entries", jsonEntries);
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(result);
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

    public boolean exportLoreEntriesToFile(List<LoreEntry> entries, String filePath) throws Exception {
        debug.debug("Exporting " + entries.size() + " lore entries to file: " + filePath);
        
        try {
            java.io.File file = new java.io.File(filePath);
            // Ensure directory exists
            file.getParentFile().mkdirs();
            
            List<JSONObject> jsonEntries = new ArrayList<>();
            for (LoreEntry entry : entries) {
                jsonEntries.add(entry.toJson());
            }
            
            JSONObject result = new JSONObject();
            result.put("lore_entries", jsonEntries);
            result.put("exported_at", new Date().toString());
            result.put("entry_count", entries.size());
            
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonContent = gson.toJson(result);
            
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                writer.write(jsonContent);
            }
            
            debug.info("Exported " + entries.size() + " lore entries to " + filePath);
            return true;
        } catch (Exception e) {
            debug.error("Failed to export lore entries to file", e);
            throw e;
        }
    }
    
    /**
     * Check if the database connection is active and valid
     * 
     * @return True if connected, false otherwise
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
     * Get lore entries by type
     * 
     * @param type The type of lore entries to retrieve
     * @return A list of matching lore entries
     */
    public List<LoreEntry> getLoreEntriesByType(LoreType type) {
        List<LoreEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM lore_entries WHERE type = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, type.name());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(resultSetToLoreEntry(rs));
                }
            }
        } catch (SQLException e) {
            debug.error("Failed to get lore entries by type: " + type, e);
        }
        
        return entries;
    }

    /**
     * Delete a lore entry by ID
     * 
     * @param id The UUID of the entry to delete
     * @return true if successful, false otherwise
     */
    public boolean deleteLoreEntry(UUID id) {
        String sql = "DELETE FROM lore_entries WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id.toString());
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            debug.error("Failed to delete lore entry: " + id, e);
            return false;
        }
    }

    /**
     * Execute database backup
     * 
     * @param backupPath the path where to store the backup
     * @return true if successful, false otherwise
     */
    public boolean backupDatabase(String backupPath) {
        debug.debug("Backing up database to: " + backupPath);
        
        if (storageType.equalsIgnoreCase("sqlite")) {
            try {
                String dbPath = plugin.getDataFolder().getAbsolutePath() + "/lore.db";
                java.io.File sourceFile = new java.io.File(dbPath);
                java.io.File destFile = new java.io.File(backupPath);
                
                // Ensure parent directory exists
                destFile.getParentFile().mkdirs();
                
                // Copy the file
                java.nio.file.Files.copy(
                    sourceFile.toPath(), 
                    destFile.toPath(), 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
                
                debug.info("SQLite database backup created at: " + backupPath);
                return true;
            } catch (Exception e) {
                debug.error("Failed to backup SQLite database", e);
                return false;
            }
        } else {
            // For MySQL, we export to a SQL dump file
            try {
                // Export all data to JSON as a simple backup
                List<LoreEntry> allEntries = getAllLoreEntries();
                return exportLoreEntriesToFile(allEntries, backupPath);
            } catch (Exception e) {
                debug.error("Failed to backup MySQL database", e);
                return false;
            }
        }
    }

    /**
     * Search lore entries by keyword in name or description
     * 
     * @param keyword The keyword to search for
     * @return A list of matching lore entries
     */
    public List<LoreEntry> searchLoreEntries(String keyword) {
        List<LoreEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM lore_entries WHERE name LIKE ? OR description LIKE ?";
        String searchTerm = "%" + keyword + "%";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, searchTerm);
            stmt.setString(2, searchTerm);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(resultSetToLoreEntry(rs));
                }
            }
        } catch (SQLException e) {
            debug.error("Failed to search lore entries for: " + keyword, e);
        }
        
        return entries;
    }

    /**
     * Reconnect to the database if the connection is lost
     * 
     * @return true if the connection was reestablished, false otherwise
     */
    public boolean reconnect() {
        debug.warning("Attempting to reconnect to database...");
        
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            
            initializeDatabase();
            
            boolean connected = isConnected();
            if (connected) {
                debug.info("Successfully reconnected to database");
            } else {
                debug.warning("Failed to reconnect to database");
            }
            
            return connected;
        } catch (SQLException e) {
            debug.error("Failed to reconnect to database", e);
            return false;
        }
    }

    /**
     * Get the active database connection
     * 
     * @return The database connection
     */
    public Connection getConnection() {
        return connection;
    }
}

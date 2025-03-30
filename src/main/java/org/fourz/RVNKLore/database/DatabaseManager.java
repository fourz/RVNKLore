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
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createLoreTable);
            debug.debug("Database tables created/verified");
        }
    }
    
    /**
     * Add a new lore entry to the database
     * 
     * @param entry The lore entry to add
     * @return True if successful, false otherwise
     */
    public boolean addLoreEntry(LoreEntry entry) {
        String sql = "INSERT INTO lore_entries (id, type, name, description, nbt_data, world, x, y, z, submitted_by, approved) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
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
            return true;
        } catch (SQLException e) {
            debug.error("Failed to add lore entry to database", e);
            return false;
        }
    }
    
    /**
     * Update an existing lore entry in the database
     * 
     * @param entry The lore entry to update
     * @return True if successful, false otherwise
     */
    public boolean updateLoreEntry(LoreEntry entry) {
        String sql = "UPDATE lore_entries SET name = ?, description = ?, nbt_data = ?, " +
                "world = ?, x = ?, y = ?, z = ?, approved = ? WHERE id = ?";
        
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
            return true;
        } catch (SQLException e) {
            debug.error("Failed to update lore entry in database", e);
            return false;
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
     * Convert a database result set to a LoreEntry object
     * 
     * @param rs The result set to convert
     * @return The created LoreEntry
     */
    private LoreEntry resultSetToLoreEntry(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        LoreType type = LoreType.fromString(rs.getString("type"));
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
        
        return new LoreEntry(id, type, name, description, nbtData, location, submittedBy, approved, createdAt);
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
}

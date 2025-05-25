package org.fourz.RVNKLore.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.Debug;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages database connections and operations for the lore system.
 * Acts as a facade for the various database components.
 */
public class DatabaseManager {
    private final RVNKLore plugin;
    private final Debug debug;
    private final DatabaseConnectionFactory connectionFactory;
    private DatabaseConnection connection;
    private LoreEntryRepository loreRepository;
    private DatabaseBackupService backupService;
    private DatabaseHelper databaseHelper;
    private volatile boolean connectionValid = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    
    /**
     * Create a new DatabaseManager instance
     * 
     * @param plugin The RVNKLore plugin instance
     */
    public DatabaseManager(RVNKLore plugin) {
        this.plugin = plugin;
        // Use the configured log level from ConfigManager instead of hardcoding Level.FINE
        this.debug = Debug.createDebugger(plugin, "DatabaseManager", plugin.getConfigManager().getLogLevel());
        
        // Initialize components
        this.connectionFactory = new DatabaseConnectionFactory(plugin);
        initializeDatabase();
        this.databaseHelper = new DatabaseHelper(plugin);
    }
    
    /**
     * Initialize the database connection and related components
     */
    private void initializeDatabase() {
        debug.debug("Initializing database...");
        try {
            // Create and initialize the connection
            connection = connectionFactory.createConnection();
            connection.initialize();
            connection.createTables();
            
            // Initialize repositories and services using the connection
            loreRepository = new LoreEntryRepository(plugin, connection);
            backupService = new DatabaseBackupService(plugin, connection);
            
            connectionValid = true;
            reconnectAttempts = 0;
            debug.debug("Database initialized successfully");
        } catch (Exception e) {
            connectionValid = false;
            debug.error("Failed to initialize database", e);
        }
    }
    
    /**
     * Add a new lore entry to the database
     * 
     * @param entry The lore entry to add
     * @return true if successful, false otherwise
     */
    public boolean addLoreEntry(LoreEntry entry) {
        if (!validateConnection()) {
            debug.warning("Database connection invalid, cannot add lore entry");
            return false;
        }
        return loreRepository.addLoreEntry(entry);
    }
    
    /**
     * Update an existing lore entry in the database
     * 
     * @param entry The lore entry to update
     * @return true if successful, false otherwise
     */
    public boolean updateLoreEntry(LoreEntry entry) {
        if (!validateConnection()) {
            debug.warning("Database connection invalid, cannot update lore entry");
            return false;
        }
        return loreRepository.updateLoreEntry(entry);
    }
    
    /**
     * Get all lore entries from the database
     * 
     * @return A list of all lore entries
     */
    public List<LoreEntry> getAllLoreEntries() {
        return loreRepository.getAllLoreEntries();
    }
    
    /**
     * Get lore entries by type
     * 
     * @param type The type of lore entries to retrieve
     * @return A list of matching lore entries
     */
    public List<LoreEntry> getLoreEntriesByType(LoreType type) {
        return loreRepository.getLoreEntriesByType(type);
    }
    
    /**
     * Delete a lore entry by ID
     * 
     * @param id The UUID of the entry to delete
     * @return true if successful, false otherwise
     */
    public boolean deleteLoreEntry(UUID id) {
        if (!validateConnection()) {
            debug.warning("Database connection invalid, cannot delete lore entry");
            return false;
        }
        return loreRepository.deleteLoreEntry(id);
    }
    
    /**
     * Search lore entries by keyword in name or description
     * 
     * @param keyword The keyword to search for
     * @return A list of matching lore entries
     */
    public List<LoreEntry> searchLoreEntries(String keyword) {
        return loreRepository.searchLoreEntries(keyword);
    }
    
    /**
     * Get the number of entries in the database
     * 
     * @return The total number of lore entries
     */
    public int getEntryCount() {
        return loreRepository.getEntryCount();
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
     * Export lore entries to a file
     * 
     * @param entries The lore entries to export
     * @param filePath The file to export to
     * @return true if successful, false otherwise
     */
    public boolean exportLoreEntriesToFile(List<LoreEntry> entries, String filePath) {
        try {
            debug.debug("Exporting " + entries.size() + " lore entries to file: " + filePath);
            
            File file = new File(filePath);
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
            
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(jsonContent);
            }
            
            debug.info("Exported " + entries.size() + " lore entries to " + filePath);
            return true;
        } catch (Exception e) {
            debug.error("Failed to export lore entries to file", e);
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
        return backupService.backupDatabase(backupPath);
    }
    
    /**
     * Check if the database connection is active and valid
     * 
     * @return True if connected, false otherwise
     */
    public boolean isConnected() {
        return connection != null && connection.isConnected();
    }
    
    /**
     * Reconnect to the database if the connection is lost
     * 
     * @return true if the connection was reestablished, false otherwise
     */
    public boolean reconnect() {
        if (connection != null) {
            return connection.reconnect();
        }
        initializeDatabase();
        return isConnected();
    }
    
    /**
     * Close the database connection
     */
    public void close() {
        if (connection != null) {
            connection.close();
        }
    }
    
    /**
     * Get the active database connection
     * 
     * @return The database connection
     */
    public Connection getConnection() {
        return connection != null ? connection.getConnection() : null;
    }
    
    /**
     * Get information about the connected database
     * 
     * @return A string with database metadata information
     */
    public String getDatabaseInfo() {
        return connection != null ? connection.getDatabaseInfo() : "No database connection";
    }
    
    /**
     * Check if the database is in read-only mode
     * 
     * @return true if the database is read-only, false otherwise
     */
    public boolean isReadOnly() {
        return connection == null || connection.isReadOnly();
    }
    
    /**
     * Get the last connection error message
     * 
     * @return The last connection error message, or null if none
     */
    public String getLastConnectionError() {
        return connection != null ? connection.getLastConnectionError() : "No database connection";
    }
    
    /**
     * Get the database helper instance
     * 
     * @return The database helper
     */
    public DatabaseHelper getDatabaseHelper() {
        return databaseHelper;
    }
    
    /**
     * Validates and attempts to fix database connection if needed
     */
    private boolean validateConnection() {
        if (connectionValid && isConnected()) {
            return true;
        }
        
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            debug.severe("Maximum reconnection attempts reached. Database operations disabled.");
            return false;
        }
        
        debug.warning("Database connection invalid, attempting reconnect");
        boolean reconnected = reconnect();
        if (reconnected) {
            connectionValid = true;
            reconnectAttempts = 0;
            return true;
        } else {
            reconnectAttempts++;
            connectionValid = false;
            return false;
        }
    }
}

package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.exception.LoreException;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

/**
 * Top-level manager for database operations.
 * Coordinates the interaction between connection, factories, helpers and repositories.
 */
public class DatabaseManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseConnectionFactory connectionFactory;
    private DatabaseConnection connection;
    private DatabaseHelper databaseHelper;
    private LoreEntryRepository loreRepository;
    private PlayerRepository playerRepository;
    private ItemRepository itemRepository;
    private DatabaseBackupService backupService;
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
        this.logger = LogManager.getInstance(plugin, "DatabaseManager");
        
        // Initialize components
        this.connectionFactory = new DatabaseConnectionFactory(plugin);
        initializeDatabase();
    }
    
    /**
     * Initialize the database connection and related components
     */
    private void initializeDatabase() {
        logger.info("Initializing database...");
        try {
            // Create and initialize the connection
            connection = connectionFactory.createConnection();
            connection.initialize();
            connection.createTables();
            
            // Create helper
            databaseHelper = new DatabaseHelper(plugin);
            
            // Initialize repositories using the connection factory and helper
            loreRepository = new LoreEntryRepository(plugin, connectionFactory, databaseHelper);
            playerRepository = new PlayerRepository(plugin, connectionFactory, databaseHelper);
            itemRepository = new ItemRepository(plugin, connectionFactory, databaseHelper);
            
            // Initialize backup service
            backupService = new DatabaseBackupService(plugin, connection);
            
            connectionValid = true;
            reconnectAttempts = 0;
            logger.info("Database initialized successfully");
        } catch (Exception e) {
            connectionValid = false;
            logger.error("Failed to initialize database", e);
        }
    }
    
    /**
     * Add a new lore entry to the database
     * 
     * @param entry The lore entry to add
     * @return true if successful, false otherwise
     */
    public boolean addLoreEntry(LoreEntry entry) {
        try {
            return loreRepository.addLoreEntry(entry);
        } catch (LoreException e) {
            logger.error("Failed to add lore entry", e);
            return false;
        }
    }
    
    /**
     * Update an existing lore entry in the database
     * 
     * @param entry The lore entry to update
     * @return true if successful, false otherwise
     */
    public boolean updateLoreEntry(LoreEntry entry) {
        try {
            return loreRepository.updateLoreEntry(entry);
        } catch (LoreException e) {
            logger.error("Failed to update lore entry", e);
            return false;
        }
    }
    
    /**
     * Get all lore entries from the database
     * 
     * @return A list of all lore entries
     */
    public List<LoreEntry> getAllLoreEntries() {
        try {
            return loreRepository.getAllLoreEntries();
        } catch (LoreException e) {
            logger.error("Failed to get all lore entries", e);
            return List.of();
        }
    }
    
    /**
     * Get lore entries by type
     * 
     * @param type The type of lore entries to retrieve
     * @return A list of matching lore entries
     */
    public List<LoreEntry> getLoreEntriesByType(LoreType type) {
        try {
            return loreRepository.getLoreEntriesByType(type);
        } catch (LoreException e) {
            logger.error("Failed to get lore entries by type: " + type, e);
            return List.of();
        }
    }
    
    /**
     * Delete a lore entry by ID
     * 
     * @param id The UUID of the entry to delete
     * @return true if successful, false otherwise
     */
    public boolean deleteLoreEntry(UUID id) {
        try {
            return loreRepository.deleteLoreEntry(id);
        } catch (LoreException e) {
            logger.error("Failed to delete lore entry", e);
            return false;
        }
    }
    
    /**
     * Search lore entries by keyword in name or description
     * 
     * @param keyword The keyword to search for
     * @return A list of matching lore entries
     */
    public List<LoreEntry> searchLoreEntries(String keyword) {
        try {
            return loreRepository.searchLoreEntries(keyword);
        } catch (LoreException e) {
            logger.error("Failed to search lore entries", e);
            return List.of();
        }
    }
    
    /**
     * Get the number of entries in the database
     * 
     * @return The total number of lore entries
     */
    public int getEntryCount() {
        try {
            return loreRepository.getEntryCount();
        } catch (LoreException e) {
            logger.error("Failed to get entry count", e);
            return 0;
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
            boolean success = connection.reconnect();
            if (success) {
                connectionValid = true;
                reconnectAttempts = 0;
            }
            return success;
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
     * Get the database connection object
     * 
     * @return The DatabaseConnection instance
     */
    public DatabaseConnection getDatabaseConnection() {
        return connection;
    }
    
    /**
     * Get the database connection factory
     * 
     * @return The DatabaseConnectionFactory instance
     */
    public DatabaseConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }
    
    /**
     * Get the lore entry repository
     * 
     * @return The LoreEntryRepository instance
     */
    public LoreEntryRepository getLoreRepository() {
        return loreRepository;
    }
    
    /**
     * Get the player repository
     * 
     * @return The PlayerRepository instance
     */
    public PlayerRepository getPlayerRepository() {
        return playerRepository;
    }
    
    /**
     * Get the item repository
     * 
     * @return The ItemRepository instance
     */
    public ItemRepository getItemRepository() {
        return itemRepository;
    }
}

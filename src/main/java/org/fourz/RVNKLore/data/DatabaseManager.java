package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.data.FallbackTracker;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.data.model.LoreLocation;
import org.fourz.RVNKLore.data.repository.AchievementRepository;
import org.fourz.RVNKLore.data.repository.CollectionRewardRepository;
import org.fourz.RVNKLore.data.repository.DiscoveryRepository;
import org.fourz.RVNKLore.data.repository.LocationRepository;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.lore.player.PlayerRepository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages database connections and operations for the lore system.
 * Acts as a facade for the various database components.
 *
 * <p>Supports automatic fallback to SQLite when MySQL connection fails,
 * with configurable failure thresholds and recovery timing.</p>
 *
 * NOTE: This class provides synchronous wrappers around async repository methods
 * for backward compatibility. Direct use of repository interfaces is recommended
 * for new code requiring async operations.
 */
public class DatabaseManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseConnectionFactory connectionFactory;
    private final FallbackTracker fallbackTracker;
    private DatabaseConnection connection;
    private DatabaseHelper databaseHelper;
    private LoreEntryRepository loreRepository;
    private PlayerRepository playerRepository;
    private ItemRepository itemRepository;
    private LocationRepository locationRepository;
    private DiscoveryRepository discoveryRepository;
    private AchievementRepository achievementRepository;
    private CollectionRewardRepository collectionRewardRepository;
    private DatabaseBackupService backupService;
    private volatile boolean connectionValid = false;
    private volatile boolean inFallbackMode = false;
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
        this.fallbackTracker = new FallbackTracker(
                plugin.getConfig().getInt("database.fallback.maxFailuresBeforeFallback", 3),
                plugin.getConfig().getInt("database.fallback.recoveryTimeMinutes", 5) * 60 * 1000L,
                LogManager.getInstance(plugin, "FallbackTracker"));
        initializeDatabase();
    }

    /**
     * Initialize the database connection and related components.
     * If primary connection fails and fallback is enabled, attempts SQLite fallback.
     */
    private void initializeDatabase() {
        logger.debug("Initializing database...");
        try {
            // Create and initialize the connection
            connection = connectionFactory.createConnection();
            connection.initialize();
            connection.createTables();

            // Initialize repositories and services using the connection
            loreRepository = new LoreEntryRepository(plugin, connection);
            locationRepository = new LocationRepository(plugin, connection);
            discoveryRepository = new DiscoveryRepository(plugin, connection);
            achievementRepository = new AchievementRepository(plugin, connection);
            collectionRewardRepository = new CollectionRewardRepository(plugin, connection);
            backupService = new DatabaseBackupService(plugin, connection);

            connectionValid = true;
            inFallbackMode = false;
            reconnectAttempts = 0;
            fallbackTracker.recordSuccess();
            logger.info("Database initialized successfully");
        } catch (Exception e) {
            connectionValid = false;
            fallbackTracker.recordFailure();
            logger.error("Failed to initialize database", e);

            // Attempt SQLite fallback if enabled
            if (connectionFactory.isFallbackEnabled()) {
                attemptFallbackConnection();
            }
        }
    }

    /**
     * Attempts to establish a SQLite fallback connection when primary fails.
     */
    private void attemptFallbackConnection() {
        logger.warning("Primary database connection failed - attempting SQLite fallback");
        try {
            // Clean up any partial primary connection
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {}
            }

            // Create fallback SQLite connection
            connection = connectionFactory.createFallbackConnection();
            connection.initialize();
            connection.createTables();

            // Initialize repositories with fallback connection
            loreRepository = new LoreEntryRepository(plugin, connection);
            locationRepository = new LocationRepository(plugin, connection);
            discoveryRepository = new DiscoveryRepository(plugin, connection);
            achievementRepository = new AchievementRepository(plugin, connection);
            collectionRewardRepository = new CollectionRewardRepository(plugin, connection);
            backupService = new DatabaseBackupService(plugin, connection);

            connectionValid = true;
            inFallbackMode = true;
            reconnectAttempts = 0;

            logger.warning("=== RUNNING IN FALLBACK MODE ===");
            logger.warning("SQLite fallback connection established successfully");
            logger.warning("Data will be stored locally until MySQL connection is restored");
            logger.warning("Recovery will be attempted in " +
                plugin.getConfig().getInt("storage.fallback.recoveryTimeMinutes", 5) + " minutes");
        } catch (Exception fallbackError) {
            connectionValid = false;
            inFallbackMode = false;
            logger.error("SQLite fallback connection also failed", fallbackError);
            logger.error("Plugin will operate in limited mode - some features may not work");
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
            logger.warning("Database connection invalid, cannot add lore entry");
            return false;
        }
        // Synchronous wrapper for async operation
        return loreRepository.addLoreEntry(entry).join();
    }

    /**
     * Update an existing lore entry in the database
     *
     * @param entry The lore entry to update
     * @return true if successful, false otherwise
     */
    public boolean updateLoreEntry(LoreEntry entry) {
        if (!validateConnection()) {
            logger.warning("Database connection invalid, cannot update lore entry");
            return false;
        }
        // Synchronous wrapper for async operation
        return loreRepository.updateLoreEntry(entry).join();
    }

    /**
     * Get all lore entries from the database
     *
     * @return A list of all lore entries
     */
    public List<LoreEntry> getAllLoreEntries() {
        // Synchronous wrapper for async operation
        return loreRepository.getAllLoreEntries().join();
    }

    /**
     * Get lore entries by type
     *
     * @param type The type of lore entries to retrieve
     * @return A list of matching lore entries
     */
    public List<LoreEntry> getLoreEntriesByType(LoreType type) {
        // Synchronous wrapper for async operation
        return loreRepository.getLoreEntriesByType(type).join();
    }

    /**
     * Delete a lore entry by ID
     *
     * @param id The UUID of the entry to delete
     * @return true if successful, false otherwise
     */
    public boolean deleteLoreEntry(UUID id) {
        if (!validateConnection()) {
            logger.warning("Database connection invalid, cannot delete lore entry");
            return false;
        }
        // Synchronous wrapper for async operation
        return loreRepository.deleteLoreEntry(id).join();
    }

    /**
     * Approve a lore entry by updating its approval status.
     * Uses the dedicated approval query (UPDATE only) instead of creating a new submission version.
     *
     * @param entryId The UUID string of the entry to approve
     * @param approvedBy The UUID string of the approver
     * @return true if successful, false otherwise
     */
    public boolean approveLoreEntry(String entryId, String approvedBy) {
        if (!validateConnection()) {
            logger.warning("Database connection invalid, cannot approve lore entry");
            return false;
        }
        return loreRepository.approveLoreEntry(entryId, approvedBy).join();
    }

    /**
     * Search lore entries by keyword in name or description
     *
     * @param keyword The keyword to search for
     * @return A list of matching lore entries
     */
    public List<LoreEntry> searchLoreEntries(String keyword) {
        // Synchronous wrapper for async operation
        return loreRepository.searchLoreEntries(keyword).join();
    }

    /**
     * Get the number of entries in the database
     *
     * @return The total number of lore entries
     */
    public int getEntryCount() {
        // Synchronous wrapper for async operation
        return loreRepository.getEntryCount().join();
    }

    /**
     * Export all lore entries to JSON format
     *
     * @return JSON string containing all lore entries
     */
    @SuppressWarnings("unchecked") // JSONObject from json-simple doesn't support generics
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
    @SuppressWarnings("unchecked") // JSONObject from json-simple doesn't support generics
    public boolean exportLoreEntriesToFile(List<LoreEntry> entries, String filePath) {
        try {
            logger.debug("Exporting " + entries.size() + " lore entries to file: " + filePath);

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

            logger.info("Exported " + entries.size() + " lore entries to " + filePath);
            return true;
        } catch (Exception e) {
            logger.error("Failed to export lore entries to file", e);
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
     * Check if running in fallback mode (SQLite instead of configured MySQL).
     *
     * @return true if using fallback SQLite connection
     */
    public boolean isInFallbackMode() {
        return inFallbackMode && connectionValid;
    }

    /**
     * Check if fallback mode is available as a recovery option.
     *
     * @return true if fallback is enabled in configuration
     */
    public boolean isFallbackEnabled() {
        return connectionFactory.isFallbackEnabled();
    }

    /**
     * Get the FallbackTracker for diagnostics.
     *
     * @return The FallbackTracker instance
     */
    public FallbackTracker getFallbackTracker() {
        return fallbackTracker;
    }

    /**
     * Reconnect to the database if the connection is lost.
     * If in fallback mode and recovery time has elapsed, attempts to reconnect to primary.
     *
     * @return true if the connection was reestablished, false otherwise
     */
    public boolean reconnect() {
        // If in fallback mode, check if we should attempt primary reconnection
        if (inFallbackMode && !fallbackTracker.isInFallbackMode()) {
            logger.info("Attempting to reconnect to primary database...");
            return attemptPrimaryReconnection();
        }

        if (connection != null) {
            boolean success = connection.reconnect();
            if (success) {
                connectionValid = true;
                reconnectAttempts = 0;
                fallbackTracker.recordSuccess();
            } else {
                fallbackTracker.recordFailure();
            }
            return success;
        }
        initializeDatabase();
        return isConnected();
    }

    /**
     * Attempts to reconnect to the primary (MySQL) database from fallback mode.
     *
     * @return true if primary connection was reestablished
     */
    private boolean attemptPrimaryReconnection() {
        try {
            // Create a new primary connection
            DatabaseConnection primaryConnection = connectionFactory.createConnection();
            primaryConnection.initialize();
            primaryConnection.createTables();

            // If successful, switch from fallback to primary
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {}
            }

            connection = primaryConnection;
            loreRepository = new LoreEntryRepository(plugin, connection);
            locationRepository = new LocationRepository(plugin, connection);
            discoveryRepository = new DiscoveryRepository(plugin, connection);
            achievementRepository = new AchievementRepository(plugin, connection);
            collectionRewardRepository = new CollectionRewardRepository(plugin, connection);
            backupService = new DatabaseBackupService(plugin, connection);

            connectionValid = true;
            inFallbackMode = false;
            reconnectAttempts = 0;
            fallbackTracker.recordSuccess();

            logger.info("=== PRIMARY DATABASE RESTORED ===");
            logger.info("Successfully reconnected to primary database");
            logger.info("Plugin is now operating in normal mode");
            return true;
        } catch (Exception e) {
            logger.warning("Primary database still unavailable: " + e.getMessage());
            fallbackTracker.recordFailure();
            return false;
        }
    }

    // ==================== Location Repository Facade ====================

    /**
     * Save a lore location record.
     */
    public LoreLocation saveLoreLocation(LoreLocation location) {
        if (!validateConnection()) return null;
        return locationRepository.save(location).join();
    }

    /**
     * Get all locations for a lore entry.
     */
    public List<LoreLocation> getLocationsByEntry(String entryId) {
        return locationRepository.findByEntryId(entryId).join();
    }

    /**
     * Get the primary location for a lore entry.
     */
    public LoreLocation getPrimaryLocation(String entryId) {
        return locationRepository.findPrimaryByEntryId(entryId).join();
    }

    /**
     * Find lore locations near a point.
     */
    public List<LoreLocation> findNearbyLore(String world, double x, double z, double radius) {
        return locationRepository.findNearby(world, x, z, radius).join();
    }

    /**
     * Delete all locations for a lore entry.
     */
    public boolean deleteLoreLocations(String entryId) {
        if (!validateConnection()) return false;
        return locationRepository.deleteByEntryId(entryId).join();
    }

    /**
     * Get the LocationRepository for direct async access.
     */
    public LocationRepository getLocationRepository() {
        return locationRepository;
    }

    /**
     * Get the DiscoveryRepository for direct async access.
     */
    public DiscoveryRepository getDiscoveryRepository() {
        return discoveryRepository;
    }

    /**
     * Get the AchievementRepository for direct async access.
     */
    public AchievementRepository getAchievementRepository() {
        return achievementRepository;
    }

    /**
     * Get the CollectionRewardRepository for direct async access.
     */
    public CollectionRewardRepository getCollectionRewardRepository() {
        return collectionRewardRepository;
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
    private boolean validateConnection() {
        if (connectionValid && isConnected()) {
            return true;
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            logger.error("Maximum reconnection attempts reached. Database operations disabled.", null);
            return false;
        }

        logger.warning("Database connection invalid, attempting reconnect");
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

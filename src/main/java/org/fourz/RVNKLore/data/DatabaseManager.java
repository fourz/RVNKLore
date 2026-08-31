package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.data.FallbackTracker;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.data.model.LoreLocation;
import org.fourz.RVNKLore.data.repository.AchievementRepository;
import org.fourz.RVNKLore.data.repository.CollectionRewardRepository;
import org.fourz.RVNKLore.data.repository.DiscoveryRepository;
import org.fourz.RVNKLore.data.repository.LocationRepository;
import org.fourz.RVNKLore.data.repository.MapRepository;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    /**
     * Pool serving cluster-shared lore content (#1834). Null when clustering is off or this server is
     * the authoritative tier — in both cases the shared content is the local database and a second
     * pool would be waste.
     */
    private DatabaseConnection clusterConnection;
    private DatabaseHelper databaseHelper;
    private LoreEntryRepository loreRepository;
    private LocationRepository locationRepository;
    private DiscoveryRepository discoveryRepository;
    private AchievementRepository achievementRepository;
    private CollectionRewardRepository collectionRewardRepository;
    private MapRepository mapRepository;
    private DatabaseBackupService backupService;
    private volatile boolean connectionValid = false;
    private volatile boolean inFallbackMode = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private final int maxFailuresBeforeFallback;
    private final int recoveryTimeMinutes;
    private final FallbackWriteLog fallbackWriteLog;

    /**
     * Journal of writes made while on the SQLite fallback, replayed to the primary on recovery.
     *
     * @return the write log, never null
     */
    public FallbackWriteLog getFallbackWriteLog() {
        return fallbackWriteLog;
    }

    /**
     * Whether this server participates in the lore cluster.
     *
     * <p>Gates the #1833 fail-closed rule: refusing writes to shared tables only makes sense once
     * another tier is actually writing them. On a standalone server every table is effectively
     * per-server, so refusing anything would cost availability during an outage and buy no safety.
     * Defaults to false, which keeps behaviour identical to before #1833 until #1834 turns it on.</p>
     *
     * @return true when {@code cluster.enabled} is set in config.yml
     */
    public boolean isClusterEnabled() {
        return plugin.getConfig().getBoolean("cluster.enabled", false);
    }

    /**
     * @return {@code authoritative} when this server owns the shared lore content, else {@code member}
     */
    public String getClusterRole() {
        return plugin.getConfig().getString("cluster.role", "member");
    }

    /** @return true when this server owns the cluster database rather than reading someone else's. */
    public boolean isClusterAuthoritative() {
        return "authoritative".equalsIgnoreCase(getClusterRole());
    }

    /**
     * The connection serving cluster-shared lore content (#1834).
     *
     * <p>Falls back to the local connection whenever clustering is off or this server is the
     * authoritative tier — in both cases the shared content <em>is</em> the local database, so there
     * is no second pool. Callers can therefore use this unconditionally without branching.</p>
     *
     * @return the connection shared lore content should be read from and written to
     */
    public DatabaseConnection getClusterConnection() {
        return clusterConnection != null ? clusterConnection : connection;
    }

    /**
     * Route a statement to the pool that owns its table (#1834).
     *
     * <p>Per-statement rather than per-repository, because two repositories legitimately touch both
     * groups: {@code PlayerRepository} reads cluster lore while writing per-server discoveries, and
     * {@code LoreEntryRepository} deletes per-server satellite rows when removing a cluster entry
     * (#1839). No single statement spans both, which is what makes this safe.</p>
     *
     * @param sql the statement about to run
     * @return the connection that owns the statement's table
     */
    public DatabaseConnection connectionForStatement(String sql) {
        if (clusterConnection == null) {
            return connection;
        }
        String table = FallbackWriteLog.extractTable(sql);
        return (table != null && LoreTableScope.isShared(table)) ? clusterConnection : connection;
    }

    /**
     * Reload lore into memory after a reconcile pass so replayed rows are actually visible.
     *
     * <p>Without this the replay lands in the database but the in-memory cache still holds the
     * pre-outage view, so {@code /lore get} reports the entry as missing — indistinguishable from the
     * reconcile having failed. Caught on Dev while verifying #1833: the row was in MySQL and only a
     * manual {@code /lore reload} revealed it.</p>
     *
     * <p>Best-effort and never allowed to fail the recovery: the data is already durable at this
     * point, and a stale cache is recoverable with a reload.</p>
     */
    private void refreshLoreCacheAfterReconcile() {
        try {
            if (plugin.getLoreManager() != null) {
                plugin.getLoreManager().reloadLore();
                logger.info("Lore cache refreshed after reconcile - replayed entries are now visible");
            }
        } catch (Exception e) {
            logger.warning("Reconcile succeeded but the lore cache could not be refreshed; run "
                    + "/lore reload to see replayed entries: " + e.getMessage());
        }
    }

    /**
     * Resolve a fallback tuning value from config.
     *
     * <p>The shipped config.yml defines these under {@code storage.fallback.*}, which is the
     * documented path and wins. {@code database.fallback.*} is accepted as a legacy alias so an
     * operator who previously set it does not silently lose their override. Prior to #1835 only
     * the legacy path was read, so the documented keys had no effect at all.</p>
     *
     * @param key      the leaf key name under the fallback block
     * @param defaultValue value to use when neither path is present
     * @return the effective value
     */
    private int resolveFallbackInt(String key, int defaultValue) {
        if (plugin.getConfig().isSet("storage.fallback." + key)) {
            return plugin.getConfig().getInt("storage.fallback." + key, defaultValue);
        }
        if (plugin.getConfig().isSet("database.fallback." + key)) {
            int legacy = plugin.getConfig().getInt("database.fallback." + key, defaultValue);
            logger.warning("Using legacy config path database.fallback." + key
                    + " - move this to storage.fallback." + key);
            return legacy;
        }
        return defaultValue;
    }

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
        this.maxFailuresBeforeFallback = resolveFallbackInt("maxFailuresBeforeFallback", 3);
        this.recoveryTimeMinutes = resolveFallbackInt("recoveryTimeMinutes", 5);
        this.fallbackTracker = new FallbackTracker(
                maxFailuresBeforeFallback,
                recoveryTimeMinutes * 60 * 1000L,
                LogManager.getInstance(plugin, "FallbackTracker"));
        logger.info("Fallback tuning: maxFailuresBeforeFallback=" + maxFailuresBeforeFallback
                + ", recoveryTimeMinutes=" + recoveryTimeMinutes);
        // Built before initializeDatabase() so a journal left by a previous outage is loaded and
        // ready to replay the moment the primary comes back (#1833).
        this.fallbackWriteLog = new FallbackWriteLog(plugin);
        // getDatabaseHelper() returned null for every caller until this was assigned: the field was
        // declared and exposed but never initialised, so the accessor was a guaranteed NPE and each
        // consumer quietly built its own `new DatabaseHelper(plugin)` instead. Safe to build here —
        // DatabaseHelper resolves the DatabaseManager at use time rather than capturing it.
        this.databaseHelper = new DatabaseHelper(plugin);
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

            // Dev reset flags — both are no-ops unless explicitly enabled in config.yml
            if (plugin.getConfigManager().isPurgeSchema()) {
                logger.warning("=== DEV purgeSchema ENABLED - dropping and recreating all lore tables ===");
                connection.dropAllTables();
                connection.createTables();
            } else if (plugin.getConfigManager().isPurgeData()) {
                logger.warning("=== DEV purgeData ENABLED - deleting all lore table rows ===");
                connection.purgeAllData();
            }

            initializeClusterConnection();
            wireRepositories(connection);

            connectionValid = true;
            inFallbackMode = false;
            reconnectAttempts = 0;
            fallbackTracker.recordSuccess();
            logger.info("Database initialized successfully");

            // An outage that spanned a restart leaves a durable journal but produces no recovery
            // transition — the server simply boots onto a healthy primary. Without this the pending
            // writes would sit on disk forever, which is the failure the journal exists to prevent
            // (#1833). Off the main thread: this is blocking DB I/O during enable.
            if (fallbackWriteLog != null && !fallbackWriteLog.isEmpty()) {
                final int carried = fallbackWriteLog.pendingCount();
                logger.warning("Found " + carried + " un-reconciled write(s) from a previous outage -"
                        + " replaying to the primary database now");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try (Connection primaryHandle = connection.getConnection()) {
                        fallbackWriteLog.replayTo(primaryHandle);
                        refreshLoreCacheAfterReconcile();
                    } catch (Exception e) {
                        logger.error("Startup reconcile failed; the journal is retained and will be "
                                + "retried on the next recovery", e);
                    }
                });
            }
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
     * Open the cluster pool for shared lore content, if this server is a member (#1834).
     *
     * <p>No-op in three cases, each for a different reason:</p>
     * <ul>
     *   <li>clustering off — nothing is shared, everything is local</li>
     *   <li>authoritative — the cluster database <em>is</em> this database, so a second pool to the
     *       same schema would double the connections for no benefit</li>
     *   <li>fallback mode — the primary is already unreachable; the cluster almost certainly is too,
     *       and #1833 refuses shared writes while in fallback anyway</li>
     * </ul>
     *
     * <p>A member that cannot reach the cluster does <b>not</b> silently fall back to serving shared
     * content from its local tables. That would present a stale private copy of the canon as if it
     * were the real thing — the divergence this whole split exists to prevent. It fails loudly and
     * leaves shared lore unavailable instead.</p>
     */
    private void initializeClusterConnection() {
        clusterConnection = null;
        if (!isClusterEnabled()) {
            return;
        }
        if (isClusterAuthoritative()) {
            logger.info("Cluster: role=authoritative - shared lore content is served from this "
                    + "server's own database; no second pool opened");
            return;
        }

        try {
            DatabaseConnection cluster = connectionFactory.createClusterConnection();
            if (cluster == null) {
                logger.error("Cluster: role=member but no usable cluster.mysql configuration - shared "
                        + "lore content is UNAVAILABLE. Refusing to serve it from local tables, which "
                        + "would present a stale copy of the canon as authoritative.", null);
                return;
            }
            cluster.initialize();
            cluster.createTables();
            clusterConnection = cluster;
            logger.info("Cluster: role=member - shared lore content served from the cluster pool");
        } catch (Exception e) {
            logger.error("Cluster: failed to reach the cluster database - shared lore content is "
                    + "UNAVAILABLE. Per-server lore (discoveries, locations, maps) is unaffected.", e);
            clusterConnection = null;
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

            wireRepositories(connection);

            connectionValid = true;
            inFallbackMode = true;
            reconnectAttempts = 0;

            logger.warning("=== RUNNING IN FALLBACK MODE ===");
            logger.warning("SQLite fallback connection established successfully");
            logger.warning("Data will be stored locally until MySQL connection is restored");
            logger.warning("Recovery will be attempted in " + recoveryTimeMinutes + " minutes");
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
        boolean saved = loreRepository.addLoreEntry(entry).join();
        if (saved) {
            syncPrimaryLocation(entry);
        }
        return saved;
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
        boolean updated = loreRepository.updateLoreEntry(entry).join();
        if (updated) {
            syncPrimaryLocation(entry);
        }
        return updated;
    }

    /**
     * Mirror an entry's coordinates into the relational lore_location table (#1900).
     *
     * <p>Coordinates have always been persisted inside the lore_submission content JSON, but
     * nothing ever wrote lore_location, so proximity discovery, per-world location counts and
     * collection markers all read a permanently empty table. This is the write side.</p>
     *
     * <p>Deliberately outside the entry transaction: lore_entry is cluster-shared while
     * lore_location is per-server (it carries a world name), so the two live in different
     * databases and cannot share a transaction. A failure here is logged and swallowed — an
     * entry that saved should not be reported as failed because its spatial index lagged.</p>
     *
     * <p>Only writes when the entry actually carries a location. A null location means "no
     * coordinates supplied", never "delete the coordinates on record", so existing rows are
     * left alone rather than clobbered by an unrelated edit.</p>
     */
    private void syncPrimaryLocation(LoreEntry entry) {
        org.bukkit.Location loc = entry.getLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        try {
            // Replace only the PRIMARY row, so WAYPOINT/BOUNDARY rows survive an entry edit.
            LoreLocation existing = locationRepository.findPrimaryByEntryId(entry.getId()).join();
            if (existing != null) {
                locationRepository.deleteById(existing.getId()).join();
            }

            LoreLocation location = LoreLocation.builder()
                    .entryId(entry.getId())
                    .world(loc.getWorld().getName())
                    .x(loc.getX())
                    .y(loc.getY())
                    .z(loc.getZ())
                    .locationType("PRIMARY")
                    .label(entry.getName())
                    .build();

            if (locationRepository.save(location).join() == null) {
                logger.warning("Lore entry saved but its location row did not: " + entry.getId());
            }
        } catch (Exception e) {
            logger.error("Failed to sync lore_location for entry: " + entry.getId(), e);
        }
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
        return loreRepository.deleteLoreEntry(id).join();
    }

    public boolean updateLoreEntryInPlace(LoreEntry entry) {
        if (!validateConnection()) {
            logger.warning("Database connection invalid, cannot update lore entry in-place");
            return false;
        }
        return loreRepository.updateLoreEntryInPlace(entry).join();
    }

    public boolean softDeleteLoreEntry(UUID id) {
        if (!validateConnection()) {
            logger.warning("Database connection invalid, cannot soft-delete lore entry");
            return false;
        }
        return loreRepository.softDeleteEntry(id).join();
    }

    public List<LoreEntry> getArchivedLoreEntries() {
        if (!validateConnection()) {
            logger.warning("Database connection invalid, cannot retrieve archived entries");
            return new java.util.ArrayList<>();
        }
        return loreRepository.getAllLoreEntriesIncludingArchived().join();
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

    public boolean rejectLoreEntry(String entryId) {
        return rejectLoreEntry(entryId, null);
    }

    public boolean rejectLoreEntry(String entryId, String reason) {
        if (!validateConnection()) {
            logger.warning("Database connection invalid, cannot reject lore entry");
            return false;
        }
        return loreRepository.rejectLoreEntry(entryId, reason).join();
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

            // Replay outage-era writes BEFORE the fallback handle is closed (#1833). The primary is
            // live and validated at this point, and the fallback connection is still open, so a
            // failure here leaves both the journal and the fallback store intact for the next
            // attempt rather than stranding the data.
            if (fallbackWriteLog != null && !fallbackWriteLog.isEmpty()) {
                try (java.sql.Connection primaryHandle = primaryConnection.getConnection()) {
                    fallbackWriteLog.replayTo(primaryHandle);
                    refreshLoreCacheAfterReconcile();
                } catch (Exception replayError) {
                    // Recovery still proceeds — the journal is durable and retries on the next pass.
                    logger.error("Reconcile pass failed; outage-era writes remain journalled and will "
                            + "be retried", replayError);
                }
            }

            // If successful, switch from fallback to primary
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {}
            }

            connection = primaryConnection;
            wireRepositories(connection);

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

    /**
     * Wire all repository fields from the given database connection.
     * Called after every successful connection (primary, fallback, and reconnect).
     *
     * @param conn The active DatabaseConnection to bind repositories to
     */
    private void wireRepositories(DatabaseConnection conn) {
        // #1834: repositories are bound by the pool their READS use. Writes route per statement in
        // DatabaseHelper, because two repositories legitimately touch both groups — but no single
        // statement does, and every repository's reads are single-pool (verified against the JOIN
        // graph: nothing joins cluster content to per-server world-bearing tables).
        //
        // getClusterConnection() returns the local connection whenever clustering is off or this
        // tier is authoritative, so this is identical to the old wiring in those cases.
        DatabaseConnection cluster = getClusterConnection();

        // Cluster-shared content
        loreRepository = new LoreEntryRepository(plugin, cluster);
        achievementRepository = new AchievementRepository(plugin, cluster);
        collectionRewardRepository = new CollectionRewardRepository(plugin, cluster);

        // Per-server, world-bearing — these carry a world name and must never be shared
        locationRepository = new LocationRepository(plugin, conn);
        discoveryRepository = new DiscoveryRepository(plugin, conn);
        mapRepository = new MapRepository(plugin, conn);

        // Backups run against the local database; the authoritative tier owns backing up the canon.
        backupService = new DatabaseBackupService(plugin, conn);
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
     * Most recent lore locations, newest first; {@code world} null = every world (#2053).
     */
    public List<LoreLocation> findRecentLore(String world, int limit) {
        return locationRepository.findRecent(world, limit).join();
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
     * Get the MapRepository for lore_map cross-server map storage.
     */
    public MapRepository getMapRepository() {
        return mapRepository;
    }

    /**
     * Close the database connection
     */
    public void close() {
        if (connection != null) {
            connection.close();
        }
        // #1834: the cluster pool is a separate HikariCP pool on a member tier and would otherwise
        // leak its connections across a reload. Null on an authoritative tier or with clustering off,
        // where it is the same object as `connection` and must not be closed twice.
        if (clusterConnection != null) {
            clusterConnection.close();
            clusterConnection = null;
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

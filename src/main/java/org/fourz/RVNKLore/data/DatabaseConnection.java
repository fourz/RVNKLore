package org.fourz.RVNKLore.data;

import com.zaxxer.hikari.HikariDataSource;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.dialect.SQLDialect;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Abstract base class for database connections using HikariCP connection pooling.
 *
 * <p>Each connection holds a reference to its SQL dialect, which provides
 * database-specific SQL generation for cross-platform compatibility.
 *
 * <p>Uses HikariCP connection pool instead of a single shared connection to prevent
 * "database connection closed" errors during concurrent async operations.
 */
public abstract class DatabaseConnection {
    protected final RVNKLore plugin;
    protected final LogManager logger;
    protected final SQLDialect dialect;
    protected HikariDataSource connectionPool;
    protected String lastConnectionError = null;
    protected String tablePrefix = "";

    // Table name constants
    public static final String TABLE_LORE_ENTRY = "lore_entry";
    public static final String TABLE_LORE_SUBMISSION = "lore_submission";
    public static final String TABLE_LORE_ITEM = "lore_item";
    public static final String TABLE_LORE_METADATA = "lore_metadata";
    public static final String TABLE_COLLECTION = "collection";
    public static final String TABLE_PLAYER_COLLECTION_PROGRESS = "player_collection_progress";
    public static final String TABLE_COLLECTION_REWARD = "collection_reward";
    public static final String TABLE_COLLECTION_ITEM = "collection_item";
    public static final String TABLE_PLAYER_COLLECTION_ITEMS = "player_collection_items";
    public static final String TABLE_LORE_LOCATION = "lore_location";
    public static final String TABLE_LORE_DISCOVERY = "lore_discovery";
    public static final String TABLE_PLAYER_ACHIEVEMENT = "player_achievement";
    public static final String TABLE_PLAYER_REWARD_CLAIM = "player_reward_claim";

    public DatabaseConnection(RVNKLore plugin, SQLDialect dialect) {
        this.plugin = plugin;
        this.dialect = dialect;
        this.logger = LogManager.getInstance(plugin, "DatabaseConnection");

        // Load table prefix from config
        String storageType = plugin.getConfig().getString("storage.type", "sqlite");
        this.tablePrefix = plugin.getConfig().getString("storage." + storageType + ".tablePrefix", "");
        if (tablePrefix != null && !tablePrefix.isEmpty()) {
            logger.info("Using table prefix: " + tablePrefix);
        }
    }

    /**
     * Get the table name with prefix applied.
     * @param baseName The base table name (e.g., "lore_entry")
     * @return The prefixed table name (e.g., "rvnklore_lore_entry")
     */
    public String table(String baseName) {
        if (tablePrefix == null || tablePrefix.isEmpty()) {
            return baseName;
        }
        return tablePrefix + baseName;
    }

    /**
     * Get the configured table prefix.
     * @return The table prefix, or empty string if none
     */
    public String getTablePrefix() {
        return tablePrefix != null ? tablePrefix : "";
    }

    /**
     * Get the SQL dialect for this connection.
     * @return The SQLDialect instance
     */
    public SQLDialect getDialect() {
        return dialect;
    }

    /**
     * Initialize the database connection pool
     */
    public abstract void initialize() throws SQLException, ClassNotFoundException;

    /**
     * Create necessary database tables.
     * Uses dialect-aware DDL for MySQL/SQLite compatibility.
     */
    public void createTables() throws SQLException {
        logger.debug("Creating database tables using " + dialect.getName() + " dialect...");

        // Helper variables for dialect-specific types
        String autoIncPK = dialect.getAutoIncrementPK();
        String boolType = dialect.getBooleanType();
        String timestampDefault = dialect.getTimestampType(true);
        String timestampNullable = dialect.getTimestampType(false);

        // --- Core Lore Schema Tables (with prefix support) ---
        String loreEntry = table(TABLE_LORE_ENTRY);
        String loreSubmission = table(TABLE_LORE_SUBMISSION);
        String loreItem = table(TABLE_LORE_ITEM);
        String loreMetadata = table(TABLE_LORE_METADATA);
        String collection = table(TABLE_COLLECTION);
        String playerProgress = table(TABLE_PLAYER_COLLECTION_PROGRESS);
        String collectionReward = table(TABLE_COLLECTION_REWARD);
        String collectionItem = table(TABLE_COLLECTION_ITEM);

        String createLoreEntryTable = "CREATE TABLE IF NOT EXISTS " + loreEntry + " (" +
                "id CHAR(36) PRIMARY KEY, " +
                "entry_type VARCHAR(50) NOT NULL, " +
                "name VARCHAR(100) NOT NULL, " +
                "CONSTRAINT uq_" + tablePrefix + "lore_entry_name_type UNIQUE (name, entry_type)" +
                ")";

        String createLoreSubmissionTable = "CREATE TABLE IF NOT EXISTS " + loreSubmission + " (" +
                "id " + autoIncPK + ", " +
                "entry_id CHAR(36) NOT NULL, " +
                "slug VARCHAR(150) NOT NULL, " +
                "visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', " +
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', " +
                "submitter_uuid CHAR(36) NOT NULL, " +
                "submission_date " + timestampDefault + ", " +
                "approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', " +
                "approved_by CHAR(36), " +
                "approved_at " + timestampNullable + ", " +
                "view_count INTEGER NOT NULL DEFAULT 0, " +
                "last_viewed_at " + timestampNullable + ", " +
                "created_at " + timestampDefault + ", " +
                "updated_at " + timestampNullable + ", " +
                "content_version INTEGER NOT NULL DEFAULT 1, " +
                "is_current_version " + boolType + " NOT NULL DEFAULT FALSE, " +
                "content TEXT, " +
                "CONSTRAINT uq_" + tablePrefix + "lore_submission_entry_version UNIQUE (entry_id, content_version), " +
                "CONSTRAINT uq_" + tablePrefix + "lore_submission_slug UNIQUE (slug), " +
                "CONSTRAINT ck_" + tablePrefix + "lore_submission_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DRAFT', 'PENDING_APPROVAL')), " +
                "CONSTRAINT ck_" + tablePrefix + "lore_submission_visibility CHECK (visibility IN ('PUBLIC', 'STAFF_ONLY', 'HIDDEN')), " +
                "FOREIGN KEY (entry_id) REFERENCES " + loreEntry + "(id) ON DELETE CASCADE" +
                ")";
        String createLoreItemTable = "CREATE TABLE IF NOT EXISTS " + loreItem + " (" +
                "id " + autoIncPK + ", " +
                "name VARCHAR(64) NOT NULL, " +
                "short_uuid VARCHAR(12), " +
                "lore_entry_id CHAR(36) NOT NULL, " +
                "material VARCHAR(50) NOT NULL, " +
                "item_type VARCHAR(50) NOT NULL, " +
                "rarity VARCHAR(20) NOT NULL, " +
                "is_obtainable " + boolType + " DEFAULT 1, " +
                "custom_model_data INTEGER, " +
                "season_id INTEGER, " +
                "is_vote_reward " + boolType + " NOT NULL DEFAULT FALSE, " +
                "item_properties TEXT, " +
                "drop_settings TEXT, " +
                "created_by VARCHAR(64), " +
                "nbt_data TEXT, " +
                "created_at " + timestampDefault + ", " +
                "updated_at " + timestampDefault + ", " +
                "CONSTRAINT uq_" + tablePrefix + "lore_item_entry UNIQUE (lore_entry_id), " +
                "FOREIGN KEY (lore_entry_id) REFERENCES " + loreEntry + "(id) ON DELETE CASCADE" +
                ")";

        String createMetadataTable = "CREATE TABLE IF NOT EXISTS " + loreMetadata + " (" +
                "lore_id VARCHAR(36) NOT NULL, " +
                "meta_key VARCHAR(64) NOT NULL, " +
                "meta_value TEXT, " +
                "PRIMARY KEY (lore_id, meta_key), " +
                "FOREIGN KEY (lore_id) REFERENCES " + loreEntry + "(id) ON DELETE CASCADE" +
                ")";

        // Create index for fast lookups
        String createLoreSubmissionEntryIndex =
                "CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "lore_submission_entry_id ON " + loreSubmission + "(entry_id)";

        String createLoreItemEntryIndex =
                "CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "lore_item_entry_id ON " + loreItem + "(lore_entry_id)";

        try (Connection conn = connectionPool.getConnection();
             Statement stmt = conn.createStatement()) {
            // Create new schema tables
            stmt.execute(createLoreEntryTable);
            stmt.execute(createLoreSubmissionTable);
            stmt.execute(createLoreItemTable);
            stmt.execute(createLoreSubmissionEntryIndex);
            stmt.execute(createLoreItemEntryIndex);

            stmt.execute(createMetadataTable);

            // --- Collection System Tables ---
            // Use dialect-aware constraint syntax (MySQL uses length specifiers, SQLite doesn't)
            boolean isMySQL = "MySQL".equals(dialect.getName());
            String collectionIdConstraint = isMySQL
                ? "CONSTRAINT uq_" + tablePrefix + "collection_id UNIQUE (collection_id(255))"
                : "CONSTRAINT uq_" + tablePrefix + "collection_id UNIQUE (collection_id)";
            String playerCollectionConstraint = isMySQL
                ? "CONSTRAINT uq_" + tablePrefix + "player_collection UNIQUE (player_id(36), collection_id(255))"
                : "CONSTRAINT uq_" + tablePrefix + "player_collection UNIQUE (player_id, collection_id)";

            String createCollectionTable = "CREATE TABLE IF NOT EXISTS " + collection + " (" +
                "id " + autoIncPK + ", " +
                "collection_id TEXT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "description TEXT, " +
                "theme_id TEXT, " +
                "is_active " + boolType + " DEFAULT 1, " +
                "created_at INTEGER NOT NULL, " +
                collectionIdConstraint +
            ")";
            String createPlayerCollectionProgressTable = "CREATE TABLE IF NOT EXISTS " + playerProgress + " (" +
                "id " + autoIncPK + ", " +
                "player_id TEXT NOT NULL, " +
                "collection_id TEXT NOT NULL, " +
                "progress REAL DEFAULT 0.0, " +
                "completed_at INTEGER, " +
                "last_updated INTEGER NOT NULL, " +
                playerCollectionConstraint +
            ")";
            String createCollectionRewardTable = "CREATE TABLE IF NOT EXISTS " + collectionReward + " (" +
                "id " + autoIncPK + ", " +
                "collection_id TEXT NOT NULL, " +
                "reward_type TEXT NOT NULL, " +
                "reward_data TEXT, " +
                "is_claimed " + boolType + " DEFAULT 0" +
            ")";

            // Collection-item relationship table for managing item sequences in collections
            String createCollectionItemTable = "CREATE TABLE IF NOT EXISTS " + collectionItem + " (" +
                "collection_id INTEGER NOT NULL, " +
                "item_id INTEGER NOT NULL, " +
                "sequence_number INTEGER DEFAULT 0, " +
                "item_config TEXT, " +
                "PRIMARY KEY (collection_id, item_id), " +
                "FOREIGN KEY (collection_id) REFERENCES " + collection + "(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (item_id) REFERENCES " + loreItem + "(id) ON DELETE CASCADE" +
            ")";

            // Player collection items table for tracking individual item discoveries per player
            String playerCollectionItems = table(TABLE_PLAYER_COLLECTION_ITEMS);
            String createPlayerCollectionItemsTable = "CREATE TABLE IF NOT EXISTS " + playerCollectionItems + " (" +
                "id " + autoIncPK + ", " +
                "player_uuid CHAR(36) NOT NULL, " +
                "collection_id INTEGER NOT NULL, " +
                "item_id INTEGER NOT NULL, " +
                "discovered_at " + timestampDefault + ", " +
                "CONSTRAINT uq_" + tablePrefix + "player_collection_item UNIQUE (player_uuid, collection_id, item_id), " +
                "FOREIGN KEY (collection_id) REFERENCES " + collection + "(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (item_id) REFERENCES " + loreItem + "(id) ON DELETE CASCADE" +
            ")";

            stmt.execute(createCollectionTable);
            stmt.execute(createPlayerCollectionProgressTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "player_collection_progress_player ON " + playerProgress + "(player_id)");
            stmt.execute(createCollectionRewardTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "collection_reward_collection ON " + collectionReward + "(collection_id)");
            stmt.execute(createCollectionItemTable);
            stmt.execute(createPlayerCollectionItemsTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "player_collection_items_player ON " + playerCollectionItems + "(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "player_collection_items_collection ON " + playerCollectionItems + "(collection_id)");

            // --- Lore Location Table (spatial data for lore entries) ---
            String loreLocation = table(TABLE_LORE_LOCATION);
            String createLoreLocationTable = "CREATE TABLE IF NOT EXISTS " + loreLocation + " (" +
                "id " + autoIncPK + ", " +
                "entry_id CHAR(36) NOT NULL, " +
                "world VARCHAR(64) NOT NULL, " +
                "x DOUBLE NOT NULL, " +
                "y DOUBLE NOT NULL, " +
                "z DOUBLE NOT NULL, " +
                "location_type VARCHAR(30) DEFAULT 'PRIMARY', " +
                "label VARCHAR(100), " +
                "created_at " + timestampDefault + ", " +
                "FOREIGN KEY (entry_id) REFERENCES " + loreEntry + "(id) ON DELETE CASCADE" +
            ")";
            stmt.execute(createLoreLocationTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "lore_location_entry ON " + loreLocation + "(entry_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "lore_location_world ON " + loreLocation + "(world, x, z)");

            // --- Lore Discovery Table (enriched discovery tracking) ---
            String loreDiscovery = table(TABLE_LORE_DISCOVERY);
            String createLoreDiscoveryTable = "CREATE TABLE IF NOT EXISTS " + loreDiscovery + " (" +
                "id " + autoIncPK + ", " +
                "player_uuid CHAR(36) NOT NULL, " +
                "entry_id CHAR(36) NOT NULL, " +
                "trigger_type VARCHAR(30) NOT NULL, " +
                "world VARCHAR(64), " +
                "x DOUBLE, " +
                "y DOUBLE, " +
                "z DOUBLE, " +
                "is_first_discovery " + boolType + " NOT NULL DEFAULT FALSE, " +
                "discovered_at " + timestampDefault + ", " +
                "CONSTRAINT uq_" + tablePrefix + "lore_discovery_player_entry UNIQUE (player_uuid, entry_id), " +
                "FOREIGN KEY (entry_id) REFERENCES " + loreEntry + "(id) ON DELETE CASCADE" +
            ")";
            stmt.execute(createLoreDiscoveryTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "lore_discovery_player ON " + loreDiscovery + "(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "lore_discovery_entry ON " + loreDiscovery + "(entry_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "lore_discovery_first ON " + loreDiscovery + "(is_first_discovery)");

            // --- Player Achievement Table (achievement progress persistence) ---
            String playerAchievement = table(TABLE_PLAYER_ACHIEVEMENT);
            String createPlayerAchievementTable = "CREATE TABLE IF NOT EXISTS " + playerAchievement + " (" +
                "player_uuid CHAR(36) NOT NULL, " +
                "achievement_id VARCHAR(50) NOT NULL, " +
                "current_progress INTEGER NOT NULL DEFAULT 0, " +
                "target_progress INTEGER NOT NULL DEFAULT 1, " +
                "completed " + boolType + " NOT NULL DEFAULT FALSE, " +
                "rewards_claimed " + boolType + " NOT NULL DEFAULT FALSE, " +
                "started_at BIGINT NOT NULL, " +
                "completed_at BIGINT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (player_uuid, achievement_id)" +
            ")";
            stmt.execute(createPlayerAchievementTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "player_achievement_player ON " + playerAchievement + "(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "player_achievement_completed ON " + playerAchievement + "(completed)");

            // --- Player Reward Claim Table (per-player reward claim tracking) ---
            String playerRewardClaim = table(TABLE_PLAYER_REWARD_CLAIM);
            String createPlayerRewardClaimTable = "CREATE TABLE IF NOT EXISTS " + playerRewardClaim + " (" +
                "id " + autoIncPK + ", " +
                "reward_id INTEGER NOT NULL, " +
                "player_uuid CHAR(36) NOT NULL, " +
                "claimed_at BIGINT NOT NULL, " +
                "FOREIGN KEY (reward_id) REFERENCES " + collectionReward + "(id) ON DELETE CASCADE" +
            ")";
            stmt.execute(createPlayerRewardClaimTable);
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_" + tablePrefix + "reward_claim_unique ON " + playerRewardClaim + "(reward_id, player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "reward_claim_player ON " + playerRewardClaim + "(player_uuid)");

            logger.debug("Database tables created/verified");
        }
    }

    /**
     * Close the database connection pool
     */
    public void close() {
        if (connectionPool != null && !connectionPool.isClosed()) {
            try {
                connectionPool.close();
                logger.debug("Database connection pool closed");
            } catch (Exception e) {
                logger.error("Failed to close database connection pool", e);
            }
        }
    }

    /**
     * Check if the database connection pool is active and valid.
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        if (connectionPool == null || connectionPool.isClosed()) {
            return false;
        }

        // Test the pool with a quick connection check
        try (Connection conn = connectionPool.getConnection()) {
            return conn != null && conn.isValid(2);
        } catch (SQLException e) {
            logger.debug("Database connection check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Reconnect to the database by reinitializing the connection pool.
     * HikariCP handles connection recovery automatically, but this allows
     * manual pool recreation if needed.
     * @return true if reconnection was successful
     */
    public boolean reconnect() {
        logger.warning("Attempting to reconnect to database...");

        try {
            lastConnectionError = null;

            // Close existing pool if present
            if (connectionPool != null && !connectionPool.isClosed()) {
                connectionPool.close();
            }

            // Reinitialize
            initialize();
            createTables();

            if (isConnected()) {
                logger.info("Successfully reconnected to database");
                return true;
            } else {
                logger.warning("Failed to reconnect to database");
                return false;
            }
        } catch (Exception e) {
            lastConnectionError = e.getMessage();
            logger.error("Failed to reconnect to database", e);
            return false;
        }
    }

    /**
     * Get a connection from the pool.
     * Each call returns a fresh connection that MUST be closed after use
     * (preferably via try-with-resources).
     *
     * @return A database connection from the pool
     * @throws IllegalStateException if the pool is not available
     */
    public Connection getConnection() {
        if (connectionPool == null || connectionPool.isClosed()) {
            throw new IllegalStateException("Database connection pool is not available");
        }

        try {
            return connectionPool.getConnection();
        } catch (SQLException e) {
            lastConnectionError = e.getMessage();
            throw new IllegalStateException("Failed to get connection from pool: " + e.getMessage(), e);
        }
    }

    /**
     * Get the HikariCP connection pool for direct access.
     * @return The HikariDataSource, or null if not initialized
     */
    public HikariDataSource getConnectionPool() {
        return connectionPool;
    }

    /**
     * Get information about the connected database
     */
    public abstract String getDatabaseInfo();

    /**
     * Check if the database is in read-only mode
     */
    public abstract boolean isReadOnly();

    /**
     * Get the database type (sqlite or mysql)
     */
    public abstract String getDatabaseType();
    
    /**
     * Get the last connection error message
     */
    public String getLastConnectionError() {
        return lastConnectionError;
    }
}

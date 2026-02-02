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
    public static final String TABLE_LORE_ENTRIES = "lore_entries";
    public static final String TABLE_LORE_METADATA = "lore_metadata";
    public static final String TABLE_COLLECTION = "collection";
    public static final String TABLE_PLAYER_COLLECTION_PROGRESS = "player_collection_progress";
    public static final String TABLE_COLLECTION_REWARD = "collection_reward";
    public static final String TABLE_COLLECTION_ITEM = "collection_item";

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
        String loreEntries = table(TABLE_LORE_ENTRIES);
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

        // Keep existing metadata table for backward compatibility during transition
        String createMetadataTable = "CREATE TABLE IF NOT EXISTS " + loreMetadata + " (" +
                "lore_id VARCHAR(36) NOT NULL, " +
                "meta_key VARCHAR(64) NOT NULL, " +
                "meta_value TEXT, " +
                "PRIMARY KEY (lore_id, meta_key), " +
                "FOREIGN KEY (lore_id) REFERENCES " + loreEntries + "(id) ON DELETE CASCADE" +
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

            // Create legacy table for backward compatibility
            stmt.execute("CREATE TABLE IF NOT EXISTS " + loreEntries + " (" +
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
                "approved " + boolType + " DEFAULT 0, " +
                "created_at " + timestampDefault +
                ")");
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

            stmt.execute(createCollectionTable);
            stmt.execute(createPlayerCollectionProgressTable);
            stmt.execute(createCollectionRewardTable);
            stmt.execute(createCollectionItemTable);
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

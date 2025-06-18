package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.config.ConfigManager;
import org.fourz.RVNKLore.config.dto.DatabaseSettingsDTO;
import org.fourz.RVNKLore.config.dto.MySQLSettingsDTO;
import org.fourz.RVNKLore.config.dto.SQLiteSettingsDTO;
import org.fourz.RVNKLore.data.connection.ConnectionProvider;
import org.fourz.RVNKLore.data.connection.MySQLConnectionProvider;
import org.fourz.RVNKLore.data.connection.SQLiteConnectionProvider;
import org.fourz.RVNKLore.data.dto.ItemPropertiesDTO;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.data.dto.LoreSubmissionDTO;
import org.fourz.RVNKLore.data.query.DefaultQueryExecutor;
import org.fourz.RVNKLore.data.query.MySQLQueryBuilder;
import org.fourz.RVNKLore.data.query.QueryBuilder;
import org.fourz.RVNKLore.data.query.QueryExecutor;
import org.fourz.RVNKLore.data.query.SQLiteQueryBuilder;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Central hub for all database operations.
 * This class handles connection management, query execution, and transaction management.
 */
public class DatabaseManager {
    @SuppressWarnings("unused")
    private final RVNKLore plugin;
    private final LogManager logger;
    @SuppressWarnings("unused")
    private final ConfigManager configManager; 
    private final ConnectionProvider connectionProvider;
    private final QueryExecutor queryExecutor;
    private final QueryBuilder queryBuilder;
    private final DatabaseType databaseType;
    private final ScheduledExecutorService healthCheckExecutor;
    private boolean connectionValid = false;
    private String lastConnectionError;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    
    /**
     * Database types supported by the plugin.
     */
    public enum DatabaseType {
        MYSQL, SQLITE
    }    /**
     * Create a new DatabaseManager instance.
     *
     * @param plugin The RVNKLore plugin instance
     * @param configManager The configuration manager
     */
    @SuppressWarnings("unchecked")
    public DatabaseManager(RVNKLore plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DatabaseManager");
        this.configManager = configManager;
        
        // Load database settings from config
        Map<String, Object> dbSettings = configManager.getDatabaseSettings();
        String dbTypeStr = (String)dbSettings.get("type");
        this.databaseType = "mysql".equalsIgnoreCase(dbTypeStr) ? DatabaseType.MYSQL : DatabaseType.SQLITE;
        
        // Create the appropriate connection provider
        if (databaseType == DatabaseType.MYSQL) {
            MySQLSettingsDTO mysqlSettings = configManager.getMySQLSettings();
            this.connectionProvider = new MySQLConnectionProvider(plugin, mysqlSettings);
            this.queryBuilder = new MySQLQueryBuilder();
        } else {
            SQLiteSettingsDTO sqliteSettings = configManager.getSQLiteSettings();
            this.connectionProvider = new SQLiteConnectionProvider(plugin, sqliteSettings);
            this.queryBuilder = new SQLiteQueryBuilder();
        }
        
        // Create the query executor
        this.queryExecutor = new DefaultQueryExecutor(plugin, connectionProvider);
        
        // Start the connection health check service
        this.healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
        this.healthCheckExecutor.scheduleAtFixedRate(
            this::performHealthCheck, 
            30, // Initial delay
            30, // Period
            TimeUnit.SECONDS
        );
        
        // Create database schema
        createDatabaseSchema();
    }

    /**
     * Create the database schema if it doesn't exist.
     */
    private void createDatabaseSchema() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = connectionProvider.getConnection()) {
                logger.info("Creating database schema...");
                
                // Create core tables
                createLoreEntryTable(conn);
                createLoreSubmissionTable(conn);
                createLoreItemTable(conn);
                createLoreLocationTable(conn);
                
                logger.info("Database schema created successfully");
                connectionValid = true;
                reconnectAttempts = 0;
                
            } catch (SQLException e) {
                connectionValid = false;
                lastConnectionError = e.getMessage();
                logger.error("Failed to create database schema", e);
            }
        }).exceptionally(ex -> {
            logger.error("Error in database schema creation", ex);
            return null;
        });
    }
    
    /**
     * Create the lore_entry table.
     *
     * @param conn The database connection
     * @throws SQLException If a database error occurs
     */
    private void createLoreEntryTable(Connection conn) throws SQLException {
        String sql;
        
        if (databaseType == DatabaseType.MYSQL) {
            sql = "CREATE TABLE IF NOT EXISTS lore_entry (" +
                  "id INT AUTO_INCREMENT PRIMARY KEY," +
                  "entry_type VARCHAR(50) NOT NULL," +
                  "name VARCHAR(100) NOT NULL," +
                  "description TEXT," +
                  "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                  "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                  "INDEX idx_entry_type (entry_type)" +
                  ") ENGINE=InnoDB";
        } else {
            sql = "CREATE TABLE IF NOT EXISTS lore_entry (" +
                  "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                  "entry_type TEXT NOT NULL," +
                  "name TEXT NOT NULL," +
                  "description TEXT," +
                  "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                  "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                  ")";
            
            // Create index in separate statement for SQLite
            String indexSql = "CREATE INDEX IF NOT EXISTS idx_entry_type ON lore_entry (entry_type)";
            try (var stmt = conn.createStatement()) {
                stmt.execute(indexSql);
            }
        }
        
        try (var stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    /**
     * Create the lore_submission table.
     *
     * @param conn The database connection
     * @throws SQLException If a database error occurs
     */
    private void createLoreSubmissionTable(Connection conn) throws SQLException {
        String sql;
        
        if (databaseType == DatabaseType.MYSQL) {
            sql = "CREATE TABLE IF NOT EXISTS lore_submission (" +
                  "id INT AUTO_INCREMENT PRIMARY KEY," +
                  "entry_id INT NOT NULL," +
                  "slug VARCHAR(150) NOT NULL," +
                  "visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC'," +
                  "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'," +
                  "submitter_uuid CHAR(36) NOT NULL," +
                  "created_by VARCHAR(36) NOT NULL," +
                  "submission_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                  "approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'," +
                  "approved_by VARCHAR(36)," +
                  "approved_at TIMESTAMP NULL," +
                  "view_count INT NOT NULL DEFAULT 0," +
                  "last_viewed_at TIMESTAMP NULL," +
                  "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                  "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                  "content_version INT NOT NULL DEFAULT 1," +
                  "is_current_version BOOLEAN NOT NULL DEFAULT FALSE," +
                  "content JSON," +
                  "UNIQUE KEY uq_lore_submission_entry_version (entry_id, content_version)," +
                  "UNIQUE KEY uq_lore_submission_slug (slug)," +
                  "FOREIGN KEY (entry_id) REFERENCES lore_entry(id) ON DELETE CASCADE," +
                  "INDEX idx_entry_id (entry_id)," +
                  "INDEX idx_current_version (is_current_version)" +
                  ") ENGINE=InnoDB";
        } else {
            sql = "CREATE TABLE IF NOT EXISTS lore_submission (" +
                  "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                  "entry_id INTEGER NOT NULL," +
                  "slug TEXT NOT NULL," +
                  "visibility TEXT NOT NULL DEFAULT 'PUBLIC'," +
                  "status TEXT NOT NULL DEFAULT 'ACTIVE'," +
                  "submitter_uuid TEXT NOT NULL," +
                  "created_by TEXT NOT NULL," +
                  "submission_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                  "approval_status TEXT NOT NULL DEFAULT 'PENDING'," +
                  "approved_by TEXT," +
                  "approved_at TIMESTAMP," +
                  "view_count INTEGER NOT NULL DEFAULT 0," +
                  "last_viewed_at TIMESTAMP," +
                  "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                  "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                  "content_version INTEGER NOT NULL DEFAULT 1," +
                  "is_current_version INTEGER NOT NULL DEFAULT 0," +
                  "content TEXT," +
                  "FOREIGN KEY (entry_id) REFERENCES lore_entry(id) ON DELETE CASCADE," +
                  "UNIQUE (entry_id, content_version)," +
                  "UNIQUE (slug)" +
                  ")";
            
            // Create indices in separate statements for SQLite
            String[] indexSqls = {
                "CREATE INDEX IF NOT EXISTS idx_submission_entry_id ON lore_submission (entry_id)",
                "CREATE INDEX IF NOT EXISTS idx_submission_current_version ON lore_submission (is_current_version)"
            };
            
            for (String indexSql : indexSqls) {
                try (var stmt = conn.createStatement()) {
                    stmt.execute(indexSql);
                }
            }
        }
        
        try (var stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    /**
     * Create the lore_item table.
     *
     * @param conn The database connection
     * @throws SQLException If a database error occurs
     */
    private void createLoreItemTable(Connection conn) throws SQLException {
        String sql;
        
        if (databaseType == DatabaseType.MYSQL) {
            sql = "CREATE TABLE IF NOT EXISTS lore_item (" +
                  "id INT AUTO_INCREMENT PRIMARY KEY," +
                  "lore_entry_id INT NOT NULL," +
                  "item_type VARCHAR(50) NOT NULL," +
                  "material VARCHAR(50) NOT NULL," +
                  "display_name VARCHAR(100) NOT NULL," +
                  "lore TEXT," +
                  "custom_model_data INT," +
                  "rarity VARCHAR(20) NOT NULL DEFAULT 'COMMON'," +
                  "is_obtainable BOOLEAN NOT NULL DEFAULT TRUE," +
                  "glow BOOLEAN NOT NULL DEFAULT FALSE," +
                  "skull_texture TEXT," +
                  "texture_data TEXT," +
                  "owner_name VARCHAR(100)," +
                  "collection_id VARCHAR(50)," +
                  "theme_id VARCHAR(50)," +
                  "rarity_level VARCHAR(20)," +
                  "collection_sequence INT," +
                  "nbt_data TEXT," +
                  "created_by VARCHAR(36)," +
                  "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                  "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                  "custom_properties JSON," +
                  "metadata JSON," +
                  "FOREIGN KEY (lore_entry_id) REFERENCES lore_entry(id) ON DELETE CASCADE," +
                  "INDEX idx_lore_entry_id (lore_entry_id)," +
                  "INDEX idx_item_type (item_type)," +
                  "INDEX idx_collection_id (collection_id)" +
                  ") ENGINE=InnoDB";
        } else {
            sql = "CREATE TABLE IF NOT EXISTS lore_item (" +
                  "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                  "lore_entry_id INTEGER NOT NULL," +
                  "item_type TEXT NOT NULL," +
                  "material TEXT NOT NULL," +
                  "display_name TEXT NOT NULL," +
                  "lore TEXT," +
                  "custom_model_data INTEGER," +
                  "rarity TEXT NOT NULL DEFAULT 'COMMON'," +
                  "is_obtainable INTEGER NOT NULL DEFAULT 1," +
                  "glow INTEGER NOT NULL DEFAULT 0," +
                  "skull_texture TEXT," +
                  "texture_data TEXT," +
                  "owner_name TEXT," +
                  "collection_id TEXT," +
                  "theme_id TEXT," +
                  "rarity_level TEXT," +
                  "collection_sequence INTEGER," +
                  "nbt_data TEXT," +
                  "created_by TEXT," +
                  "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                  "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                  "custom_properties TEXT," +
                  "metadata TEXT," +
                  "FOREIGN KEY (lore_entry_id) REFERENCES lore_entry(id) ON DELETE CASCADE" +
                  ")";
            
            // Create indices in separate statements for SQLite
            String[] indexSqls = {
                "CREATE INDEX IF NOT EXISTS idx_item_lore_entry_id ON lore_item (lore_entry_id)",
                "CREATE INDEX IF NOT EXISTS idx_item_type ON lore_item (item_type)",
                "CREATE INDEX IF NOT EXISTS idx_item_collection_id ON lore_item (collection_id)"
            };
            
            for (String indexSql : indexSqls) {
                try (var stmt = conn.createStatement()) {
                    stmt.execute(indexSql);
                }
            }
        }
        
        try (var stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    /**
     * Create the lore_location table.
     *
     * @param conn The database connection
     * @throws SQLException If a database error occurs
     */
    private void createLoreLocationTable(Connection conn) throws SQLException {
        String sql;
        
        if (databaseType == DatabaseType.MYSQL) {
            sql = "CREATE TABLE IF NOT EXISTS lore_location (" +
                  "id INT AUTO_INCREMENT PRIMARY KEY," +
                  "lore_entry_id INT NOT NULL," +
                  "world VARCHAR(100) NOT NULL," +
                  "x DOUBLE NOT NULL," +
                  "y DOUBLE NOT NULL," +
                  "z DOUBLE NOT NULL," +
                  "yaw FLOAT," +
                  "pitch FLOAT," +
                  "location_type VARCHAR(50) NOT NULL," +
                  "discovery_message TEXT," +
                  "discoverable BOOLEAN NOT NULL DEFAULT TRUE," +
                  "discovery_radius DOUBLE NOT NULL DEFAULT 10.0," +
                  "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                  "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                  "FOREIGN KEY (lore_entry_id) REFERENCES lore_entry(id) ON DELETE CASCADE," +
                  "INDEX idx_lore_location_entry_id (lore_entry_id)," +
                  "INDEX idx_lore_location_world (world)" +
                  ") ENGINE=InnoDB";
        } else {
            sql = "CREATE TABLE IF NOT EXISTS lore_location (" +
                  "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                  "lore_entry_id INTEGER NOT NULL," +
                  "world TEXT NOT NULL," +
                  "x REAL NOT NULL," +
                  "y REAL NOT NULL," +
                  "z REAL NOT NULL," +
                  "yaw REAL," +
                  "pitch REAL," +
                  "location_type TEXT NOT NULL," +
                  "discovery_message TEXT," +
                  "discoverable INTEGER NOT NULL DEFAULT 1," +
                  "discovery_radius REAL NOT NULL DEFAULT 10.0," +
                  "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                  "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                  "FOREIGN KEY (lore_entry_id) REFERENCES lore_entry(id) ON DELETE CASCADE" +
                  ")";
            
            // Create indices in separate statements for SQLite
            String[] indexSqls = {
                "CREATE INDEX IF NOT EXISTS idx_location_lore_entry_id ON lore_location (lore_entry_id)",
                "CREATE INDEX IF NOT EXISTS idx_location_world ON lore_location (world)"
            };
            
            for (String indexSql : indexSqls) {
                try (var stmt = conn.createStatement()) {
                    stmt.execute(indexSql);
                }
            }
        }
        
        try (var stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    // LORE ENTRY OPERATIONS
    
    /**
     * Get a lore entry by ID.
     *
     * @param id The ID of the lore entry
     * @return A future containing the lore entry DTO, or null if not found
     */
    public CompletableFuture<LoreEntryDTO> getLoreEntry(int id) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_entry")
                                        .where("id = ?", id);
        
        return queryExecutor.executeQuery(query, LoreEntryDTO.class);
    }
    
    /**
     * Get all lore entries.
     *
     * @return A future containing a list of lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> getAllLoreEntries() {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_entry")
                                        .orderBy("created_at", false);
        
        return queryExecutor.executeQueryList(query, LoreEntryDTO.class);
    }
    
    /**
     * Get lore entries by type.
     *
     * @param type The type of lore entries to retrieve
     * @return A future containing a list of lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> getLoreEntriesByType(String type) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_entry")
                                        .where("entry_type = ?", type)
                                        .orderBy("created_at", false);
        
        return queryExecutor.executeQueryList(query, LoreEntryDTO.class);
    }
    
    /**
     * Save a lore entry.
     *
     * @param dto The lore entry DTO to save
     * @return A future containing the saved lore entry ID
     */
    public CompletableFuture<Integer> saveLoreEntry(LoreEntryDTO dto) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        if (dto.getId() > 0) {
            // Update existing entry
            QueryBuilder query = queryBuilder.update("lore_entry")
                                            .set("entry_type", dto.getEntryType())
                                            .set("name", dto.getName())
                                            .set("description", dto.getDescription())
                                            .where("id = ?", dto.getId());
            
            return queryExecutor.executeUpdate(query)
                              .thenApply(rowsAffected -> dto.getId());
        } else {
            // Insert new entry
            QueryBuilder query = queryBuilder.insertInto("lore_entry")
                                            .columns("entry_type", "name", "description")
                                            .values(dto.getEntryType(), dto.getName(), dto.getDescription());
            
            return queryExecutor.executeInsert(query);
        }
    }
    
    /**
     * Delete a lore entry.
     *
     * @param id The ID of the lore entry to delete
     * @return A future containing true if the entry was deleted, false otherwise
     */
    public CompletableFuture<Boolean> deleteLoreEntry(int id) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.deleteFrom("lore_entry")
                                        .where("id = ?", id);
        
        return queryExecutor.executeUpdate(query)
                          .thenApply(rowsAffected -> rowsAffected > 0);
    }
    
    /**
     * Search lore entries by keyword.
     *
     * @param keyword The keyword to search for
     * @return A future containing a list of matching lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> searchLoreEntries(String keyword) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        // Create a search term with wildcards
        String searchTerm = "%" + keyword + "%";
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_entry")
                                        .where("name LIKE ? OR description LIKE ?", searchTerm, searchTerm)
                                        .orderBy("created_at", false);
        
        return queryExecutor.executeQueryList(query, LoreEntryDTO.class);
    }
    
    // LORE SUBMISSION OPERATIONS
    
    /**
     * Get a lore submission by ID.
     *
     * @param id The ID of the lore submission
     * @return A future containing the lore submission DTO, or null if not found
     */
    public CompletableFuture<LoreSubmissionDTO> getLoreSubmission(int id) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_submission")
                                        .where("id = ?", id);
        
        return queryExecutor.executeQuery(query, LoreSubmissionDTO.class);
    }
    
    /**
     * Get the current version of a lore submission for an entry.
     *
     * @param entryId The ID of the lore entry
     * @return A future containing the lore submission DTO, or null if not found
     */
    public CompletableFuture<LoreSubmissionDTO> getCurrentSubmission(int entryId) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_submission")
                                        .where("entry_id = ? AND is_current_version = ?", entryId, true);
        
        return queryExecutor.executeQuery(query, LoreSubmissionDTO.class);
    }
    
    /**
     * Get all submissions for a lore entry.
     *
     * @param entryId The ID of the lore entry
     * @return A future containing a list of lore submission DTOs
     */
    public CompletableFuture<List<LoreSubmissionDTO>> getSubmissionsForEntry(int entryId) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_submission")
                                        .where("entry_id = ?", entryId)
                                        .orderBy("content_version", false);
        
        return queryExecutor.executeQueryList(query, LoreSubmissionDTO.class);
    }
    
    /**
     * Save a lore submission.
     *
     * @param dto The lore submission DTO to save
     * @return A future containing the saved lore submission ID
     */
    public CompletableFuture<Integer> saveLoreSubmission(LoreSubmissionDTO dto) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        if (dto.getId() > 0) {
            // Update existing submission
            QueryBuilder query = queryBuilder.update("lore_submission")
                                            .set("entry_id", dto.getEntryId())
                                            .set("slug", dto.getSlug())
                                            .set("visibility", dto.getVisibility())
                                            .set("status", dto.getStatus())
                                            .set("submitter_uuid", dto.getSubmitterUuid())
                                            .set("created_by", dto.getCreatedBy())
                                            .set("submission_date", dto.getSubmissionDate())
                                            .set("approval_status", dto.getApprovalStatus())
                                            .set("approved_by", dto.getApprovedBy())
                                            .set("approved_at", dto.getApprovedAt())
                                            .set("view_count", dto.getViewCount())
                                            .set("last_viewed_at", dto.getLastViewedAt())
                                            .set("content_version", dto.getContentVersion())
                                            .set("is_current_version", dto.isCurrentVersion())
                                            .set("content", dto.getContent())
                                            .where("id = ?", dto.getId());
            
            return queryExecutor.executeUpdate(query)
                              .thenApply(rowsAffected -> dto.getId());
        } else {
            // Insert new submission
            QueryBuilder query = queryBuilder.insertInto("lore_submission")
                                            .columns("entry_id", "slug", "visibility", "status", 
                                                    "submitter_uuid", "created_by", "submission_date", 
                                                    "approval_status", "content_version", 
                                                    "is_current_version", "content")
                                            .values(dto.getEntryId(), dto.getSlug(), dto.getVisibility(), 
                                                   dto.getStatus(), dto.getSubmitterUuid(), dto.getCreatedBy(), 
                                                   dto.getSubmissionDate(), dto.getApprovalStatus(), 
                                                   dto.getContentVersion(), dto.isCurrentVersion(), 
                                                   dto.getContent());
            
            return queryExecutor.executeInsert(query);
        }
    }
    
    /**
     * Approve a lore submission.
     *
     * @param submissionId The ID of the submission to approve
     * @param approverUuid The UUID of the staff member approving the submission
     * @return A future containing true if the submission was approved, false otherwise
     */
    public CompletableFuture<Boolean> approveSubmission(int submissionId, String approverUuid) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }

        return queryExecutor.executeTransaction(conn -> {
            // 1. Get the submission
            QueryBuilder query = queryBuilder.select("*")
                                          .from("lore_submission")
                                          .where("id = ?", submissionId);

            LoreSubmissionDTO submission = queryExecutor.executeQuery(query, LoreSubmissionDTO.class).join();
            if (submission == null) {
                logger.error("Failed to approve submission", 
                    new IllegalStateException("Submission not found for ID: " + submissionId));
                return false;
            }

            // 2. Check if it's already approved
            if ("APPROVED".equals(submission.getApprovalStatus())) {
                logger.warning("Submission " + submissionId + " is already approved");
                return true;
            }

            // 3. Mark any existing current version as not current
            QueryBuilder resetQuery = queryBuilder.update("lore_submission")
                                               .set("is_current_version", false)
                                               .where("entry_id = ? AND id != ? AND is_current_version = ?", 
                                                    submission.getEntryId(), submissionId, true);

            queryExecutor.executeUpdate(resetQuery).join();

            // 4. Update the submission being approved
            QueryBuilder updateQuery = queryBuilder.update("lore_submission")
                                                .set("approval_status", "APPROVED")
                                                .set("approved_by", approverUuid)
                                                .set("approved_at", new Timestamp(System.currentTimeMillis()))
                                                .set("status", "ACTIVE")
                                                .set("is_current_version", true)
                                                .where("id = ?", submissionId);

            int rowsAffected = queryExecutor.executeUpdate(updateQuery).join();
            if (rowsAffected != 1) {
                throw new SQLException("Failed to update submission " + submissionId);
            }

            return true;
        });
    }
    
    // ITEM OPERATIONS
    
    /**
     * Get an item by ID.
     *
     * @param id The ID of the item
     * @return A future containing the item properties DTO, or null if not found
     */
    public CompletableFuture<ItemPropertiesDTO> getItem(int id) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_item")
                                        .where("id = ?", id);
        
        return queryExecutor.executeQuery(query, ItemPropertiesDTO.class);
    }
    
    /**
     * Get an item by lore entry ID.
     *
     * @param loreEntryId The lore entry ID
     * @return A future containing the item properties DTO, or null if not found
     */
    public CompletableFuture<ItemPropertiesDTO> getItemByLoreEntry(int loreEntryId) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_item")
                                        .where("lore_entry_id = ?", loreEntryId);
        
        return queryExecutor.executeQuery(query, ItemPropertiesDTO.class);
    }
    
    /**
     * Get items by type.
     *
     * @param type The item type
     * @return A future containing a list of item properties DTOs
     */
    public CompletableFuture<List<ItemPropertiesDTO>> getItemsByType(String type) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.select("*")
                                        .from("lore_item")
                                        .where("item_type = ?", type)
                                        .orderBy("created_at", false);
        
        return queryExecutor.executeQueryList(query, ItemPropertiesDTO.class);
    }
    
    /**
     * Save an item.
     *
     * @param dto The item properties DTO to save
     * @return A future containing the saved item ID
     */
    public CompletableFuture<Integer> saveItem(ItemPropertiesDTO dto) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        // Use transaction to ensure consistency
        return queryExecutor.executeTransaction(conn -> {
            int itemId;
            
            if (dto.getId() > 0) {
                // Update existing item
                QueryBuilder query = queryBuilder.update("lore_item")
                                                .set("lore_entry_id", dto.getLoreEntryId())
                                                .set("item_type", dto.getItemType() != null ? dto.getItemType().name() : null)
                                                .set("material", dto.getMaterial())
                                                .set("display_name", dto.getDisplayName())
                                                .set("lore", dto.getLore() != null ? String.join("\n", dto.getLore()) : null)
                                                .set("custom_model_data", dto.getCustomModelData())
                                                .set("rarity", dto.getRarity())
                                                .set("is_obtainable", dto.isObtainable())
                                                .set("glow", dto.isGlow())
                                                .set("skull_texture", dto.getSkullTexture())
                                                .set("texture_data", dto.getTextureData())
                                                .set("owner_name", dto.getOwnerName())
                                                .set("collection_id", dto.getCollectionId())
                                                .set("theme_id", dto.getThemeId())
                                                .set("rarity_level", dto.getRarityLevel())
                                                .set("collection_sequence", dto.getCollectionSequence())
                                                .set("nbt_data", dto.getNbtData())
                                                .set("created_by", dto.getCreatedBy())
                                                .where("id = ?", dto.getId());
                
                int rowsAffected = 0;
                try (var stmt = conn.prepareStatement(query.build())) {
                    for (int i = 0; i < query.getParameters().length; i++) {
                        stmt.setObject(i + 1, query.getParameters()[i]);
                    }
                    rowsAffected = stmt.executeUpdate();
                }
                
                if (rowsAffected <= 0) {
                    throw new SQLException("Failed to update item");
                }
                
                itemId = dto.getId();
            } else {
                // Insert new item
                QueryBuilder query = queryBuilder.insertInto("lore_item")
                                                .columns("lore_entry_id", "item_type", "material", "display_name", "lore", "custom_model_data", "rarity", "is_obtainable", "glow", "skull_texture", "texture_data", "owner_name", "collection_id", "theme_id", "rarity_level", "collection_sequence", "nbt_data", "created_by")
                                                .values(dto.getLoreEntryId(), dto.getItemType() != null ? dto.getItemType().name() : null, dto.getMaterial(), dto.getDisplayName(), dto.getLore() != null ? String.join("\n", dto.getLore()) : null, dto.getCustomModelData(), dto.getRarity(), dto.isObtainable(), dto.isGlow(), dto.getSkullTexture(), dto.getTextureData(), dto.getOwnerName(), dto.getCollectionId(), dto.getThemeId(), dto.getRarityLevel(), dto.getCollectionSequence(), dto.getNbtData(), dto.getCreatedBy());
                
                try (var stmt = conn.prepareStatement(query.build(), java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    for (int i = 0; i < query.getParameters().length; i++) {
                        stmt.setObject(i + 1, query.getParameters()[i]);
                    }
                    stmt.executeUpdate();
                    
                    try (var rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            itemId = rs.getInt(1);
                        } else {
                            throw new SQLException("Failed to get generated item ID");
                        }
                    }
                }
            }
            
            return itemId;
        });
    }
    
    /**
     * Delete an item (async).
     */
    public CompletableFuture<Boolean> deleteItem(int id) {
        QueryBuilder query = queryBuilder.deleteFrom("lore_item")
            .where("id = ?", id);
        return queryExecutor.executeUpdate(query)
            .thenApply(rowsAffected -> rowsAffected > 0);
    }
    
    // UTILITY METHODS
    
    /**
     * Convert a legacy LoreEntry to a new DTO-based entry with submission.
     *
     * @param legacyEntry The legacy LoreEntry to convert
     * @return A future containing true if the conversion was successful, false otherwise
     */
    public CompletableFuture<Boolean> convertLegacyEntry(LoreEntry legacyEntry) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        return queryExecutor.executeTransaction(conn -> {
            // 1. Create the lore entry
            LoreEntryDTO entryDto = new LoreEntryDTO();
            entryDto.setEntryType(legacyEntry.getType().name());
            entryDto.setName(legacyEntry.getName());
            entryDto.setDescription(legacyEntry.getDescription());
            entryDto.setCreatedAt(legacyEntry.getCreatedAt());
            entryDto.setUpdatedAt(legacyEntry.getCreatedAt());
            
            // Insert the entry
            QueryBuilder entryQuery = queryBuilder.insertInto("lore_entry")
                                                .columns("entry_type", "name", "description", "created_at")
                                                .values(entryDto.getEntryType(), entryDto.getName(), 
                                                       entryDto.getDescription(), entryDto.getCreatedAt());
            
            int entryId;
            try (var stmt = conn.prepareStatement(entryQuery.build(), java.sql.Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < entryQuery.getParameters().length; i++) {
                    stmt.setObject(i + 1, entryQuery.getParameters()[i]);
                }
                stmt.executeUpdate();
                
                try (var rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        entryId = rs.getInt(1);
                    } else {
                        throw new SQLException("Failed to get generated entry ID");
                    }
                }
            }
            
            // 2. Create the submission
            LoreSubmissionDTO submissionDto = new LoreSubmissionDTO();
            submissionDto.setEntryId(entryId);
            submissionDto.setSlug("lore-" + entryId + "-" + System.currentTimeMillis());
            submissionDto.setVisibility("PUBLIC");
            submissionDto.setStatus("ACTIVE");
            submissionDto.setSubmitterUuid(legacyEntry.getSubmittedBy());
            submissionDto.setCreatedBy(legacyEntry.getSubmittedBy());
            submissionDto.setSubmissionDate(legacyEntry.getCreatedAt());
            submissionDto.setApprovalStatus(legacyEntry.isApproved() ? "APPROVED" : "PENDING");
            if (legacyEntry.isApproved()) {
                submissionDto.setApprovedAt(legacyEntry.getCreatedAt());
                submissionDto.setApprovedBy("SYSTEM");
            }
            submissionDto.setCreatedAt(legacyEntry.getCreatedAt());
            submissionDto.setUpdatedAt(legacyEntry.getCreatedAt());
            submissionDto.setContentVersion(1);
            submissionDto.setCurrentVersion(true);
            submissionDto.setContent(legacyEntry.getDescription());
            
            // Insert the submission
            QueryBuilder submissionQuery = queryBuilder.insertInto("lore_submission")
                                                    .columns("entry_id", "slug", "visibility", "status", 
                                                           "submitter_uuid", "created_by", "submission_date", 
                                                           "approval_status", "approved_by", "approved_at", 
                                                           "created_at", "updated_at", "content_version", 
                                                           "is_current_version", "content")
                                                    .values(submissionDto.getEntryId(), submissionDto.getSlug(), 
                                                          submissionDto.getVisibility(), submissionDto.getStatus(), 
                                                          submissionDto.getSubmitterUuid(), submissionDto.getCreatedBy(), 
                                                          submissionDto.getSubmissionDate(), submissionDto.getApprovalStatus(), 
                                                          submissionDto.getApprovedBy(), submissionDto.getApprovedAt(), 
                                                          submissionDto.getCreatedAt(), submissionDto.getUpdatedAt(), 
                                                          submissionDto.getContentVersion(), submissionDto.isCurrentVersion(), 
                                                          submissionDto.getContent());
            
            try (var stmt = conn.prepareStatement(submissionQuery.build())) {
                for (int i = 0; i < submissionQuery.getParameters().length; i++) {
                    stmt.setObject(i + 1, submissionQuery.getParameters()[i]);
                }
                stmt.executeUpdate();
            }
            
            // 3. If this is an ITEM type, create the item record
            if (legacyEntry.getType() == LoreType.ITEM) {
                // In a real implementation, we would extract the item properties from the legacy entry
                // and create an ItemPropertiesDTO
                
                // This is just a stub, as we don't have full item data in the legacy entry
                ItemPropertiesDTO itemDto = new ItemPropertiesDTO();
                itemDto.setLoreEntryId(String.valueOf(entryId));
                // Set other properties...
                
                // Insert the item record would go here
            }
            
            return true;
        });
    }
    
    /**
     * Check database health and reconnect if necessary.
     */
    private void performHealthCheck() {
        try {
            boolean isHealthy = connectionProvider.isHealthy();
            
            if (isHealthy) {
                if (!connectionValid) {
                    logger.info("Database connection restored");
                }
                connectionValid = true;
                reconnectAttempts = 0;
            } else {
                connectionValid = false;
                
                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++;
                    logger.warning("Database connection unhealthy, attempting reconnect (attempt " + reconnectAttempts + " of " + MAX_RECONNECT_ATTEMPTS + ")");
                    // The next health check will determine if reconnection was successful
                } else {
                    logger.error("Maximum reconnection attempts reached. Database operations disabled.", null);
                }
            }
        } catch (Exception e) {
            connectionValid = false;
            logger.error("Error during database health check", e);
        }
    }
    
    /**
     * Validate the database connection.
     *
     * @return True if the connection is valid, false otherwise
     */
    private boolean validateConnection() {
        return connectionValid || reconnectAttempts < MAX_RECONNECT_ATTEMPTS;
    }
    
    /**
     * Get the database type.
     *
     * @return The database type
     */
    public DatabaseType getDatabaseType() {
        return databaseType;
    }
    
    /**
     * Get the database connection provider.
     *
     * @return The connection provider
     */
    public ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }
    
    /**
     * Get the query executor.
     *
     * @return The query executor
     */
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }
    
    /**
     * Get the query builder.
     *
     * @return The query builder
     */
    public QueryBuilder getQueryBuilder() {
        return queryBuilder;
    }
    
    /**
     * Check if the database connection is valid.
     *
     * @return True if the connection is valid, false otherwise
     */
    public boolean isConnectionValid() {
        return connectionValid;
    }
    
    /**
     * Get the last connection error.
     *
     * @return The last connection error
     */
    public String getLastConnectionError() {
        return lastConnectionError;
    }
    
    /**
     * Close the database connection and clean up resources.
     */
    public void close() {
        if (healthCheckExecutor != null) {
            healthCheckExecutor.shutdown();
            try {
                if (!healthCheckExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    healthCheckExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                healthCheckExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (connectionProvider != null) {
            try {
                connectionProvider.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.error("Error closing database connection", e);
            }
        }
    }
    
    /**
     * Convert legacy LoreEntry objects to DTOs.
     *
     * @param legacyEntries The legacy LoreEntry objects
     * @return A list of LoreEntryDTO objects
     */
    public List<LoreEntryDTO> convertLegacyEntries(List<LoreEntry> legacyEntries) {
        return legacyEntries.stream()
                          .map(LoreEntryDTO::fromLoreEntry)
                          .collect(Collectors.toList());
    }
}

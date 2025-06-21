package org.fourz.RVNKLore.data;

import org.bukkit.Location;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.config.ConfigManager;
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
import org.fourz.RVNKLore.data.query.SQLiteQueryBuilder;
import org.fourz.RVNKLore.data.query.MySQLSchemaQueryBuilder;
import org.fourz.RVNKLore.data.query.SQLiteSchemaQueryBuilder;
import org.fourz.RVNKLore.data.query.SchemaQueryBuilder;
import org.fourz.RVNKLore.data.service.DatabaseHealthService;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.dto.PlayerDTO;
import org.fourz.RVNKLore.data.dto.NameChangeRecordDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Central hub for all database operations.
 * Handles connection management, query execution, and transaction management.
 *
 * COPILOT INSTRUCTIONS EXCEPTIONS NOTE: The ConfigManager class is the exception to the DTO configuration pattern.
 * Database settings are accessed directly from ConfigManager, and the ConnectionProvider
 * implementations retrieve their configuration settings directly from ConfigManager.
 */
public class DatabaseManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private ConnectionProvider connectionProvider;
    private final DatabaseType databaseType;
    private QueryBuilder queryBuilder;
    private DefaultQueryExecutor queryExecutor;
    private final ConfigManager configManager;
    private final ExecutorService executor;
    private DatabaseHealthService healthService;
    private String storageType;
    private DatabaseSetup databaseSetup;
    private SchemaQueryBuilder schemaQueryBuilder;
    
    /**
     * Database types supported by the plugin.
     */
    public enum DatabaseType {
        MYSQL, SQLITE
    }
    
    /**
     * Create a new DatabaseManager instance.
     * 
     * IMPLEMENTATION NOTE: This class is an exception to the DTO configuration pattern.
     * Database settings are accessed directly from ConfigManager, and the ConnectionProvider
     * implementations retrieve their configuration settings directly from ConfigManager
     * instead of using DTOs passed from outside.
     *
     * @param plugin The RVNKLore plugin instance
     */
    public DatabaseManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DatabaseManager");
        this.executor = Executors.newCachedThreadPool();
        this.configManager = plugin.getConfigManager();
        
        // Get storage type directly from ConfigManager
        String storageType = configManager.getStorageType();
        this.databaseType = "mysql".equalsIgnoreCase(storageType) ? 
            DatabaseType.MYSQL : DatabaseType.SQLITE;
        
        // Initialize connection provider based on type - providers get settings directly from ConfigManager
        if (databaseType == DatabaseType.MYSQL) {
            this.connectionProvider = new MySQLConnectionProvider(plugin);
        } else {
            this.connectionProvider = new SQLiteConnectionProvider(plugin);
        }
        
        // Initialize query builder and executor
        if (databaseType == DatabaseType.MYSQL) {
            this.queryBuilder = new MySQLQueryBuilder();
            this.schemaQueryBuilder = new MySQLSchemaQueryBuilder();
        } else {
            this.queryBuilder = new SQLiteQueryBuilder();
            this.schemaQueryBuilder = new SQLiteSchemaQueryBuilder();
        }
        
        this.queryExecutor = new DefaultQueryExecutor(plugin, connectionProvider);
        
        // Initialize health service
        this.healthService = new DatabaseHealthService(this, plugin);
    }

    /**
     * Initialize the database connection and schema.
     * This method should be called during plugin startup.
     */
    public void initialize() {
        logger.info("Initializing database connection...");
        
        // Initialize connection provider based on storage type
        initializeConnectionProvider();
        
        // Create appropriate query builder based on storage type
        if (databaseType == DatabaseType.MYSQL) {
            queryBuilder = new MySQLQueryBuilder();
            schemaQueryBuilder = new MySQLSchemaQueryBuilder();
        } else {
            queryBuilder = new SQLiteQueryBuilder();
            schemaQueryBuilder = new SQLiteSchemaQueryBuilder();
        }
        
        // Initialize query executor
        queryExecutor = new DefaultQueryExecutor(plugin, connectionProvider);
        
        // Initialize database setup
        databaseSetup = new DatabaseSetup(plugin, connectionProvider, schemaQueryBuilder, queryExecutor);
        
        // Initialize database schema
        databaseSetup.initializeTables()
            .thenRun(() -> {
                logger.info("Database schema initialized successfully");
                validateTables();
            })
            .exceptionally(e -> {
                logger.error("Failed to initialize database schema", e);
                return null;
            });
            
        // Start health check service
        healthService = new DatabaseHealthService(this, plugin);
        //healthService.startHealthCheck();
        
        logger.info("Database initialized with " + databaseType + " storage");
    }

    /**
     * Create the database schema if it doesn't exist.
     */
    private void createDatabaseSchema() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = connectionProvider.getConnection()) {
                logger.info("Creating database schema...");
                
                // Create core tables
                // Removed: createLoreEntryTable(conn);
                // Removed: createLoreSubmissionTable(conn);
                // Removed: createLoreItemTable(conn);
                // Removed: createLoreLocationTable(conn);
                
                logger.info("Database schema created successfully");
                
            } catch (SQLException e) {
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
        String query = "SELECT * FROM lore_submissions WHERE id = ?";

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setInt(1, id);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return extractLoreSubmissionFromResultSet(rs);
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting lore submission", e);
                throw new RuntimeException(e);
            }
            return null;
        }, executor);
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
        String query = "INSERT INTO lore_submissions "
            + "(content, submitter_uuid, approval_status, submission_date) "
            + "VALUES (?, ?, ?, ?)";
        
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                
                stmt.setString(1, dto.getContent());
                stmt.setString(2, dto.getSubmitterUuid());
                stmt.setString(3, dto.getApprovalStatus());
                stmt.setTimestamp(4, dto.getSubmissionDate());

                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Creating lore submission failed, no rows affected.");
                }

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Creating lore submission failed, no ID obtained.");
                    }
                }
            } catch (SQLException e) {
                logger.error("Error saving lore submission", e);
                throw new RuntimeException(e);
            }
        }, executor);
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
                                                .set("approved_at", new java.sql.Timestamp(System.currentTimeMillis()))
                                                .set("status", "ACTIVE")
                                                .set("is_current_version", true)
                                                .where("id = ?", submissionId);

            int rowsAffected = queryExecutor.executeUpdate(updateQuery).join();
            if (rowsAffected != 1) {
                logger.error("Failed to approve submission, rows affected: " + rowsAffected, 
                    new SQLException("Update failed for submission ID: " + submissionId));
                return false;
            }

            return true;
        });
    }
    
    /**
     * Approves a lore submission and creates the associated lore entry.
     */
    public CompletableFuture<Boolean> approveLoreSubmission(int submissionId, String approverUuid) {
        return getLoreSubmission(submissionId)
            .thenCompose(submissionDto -> {
                if (submissionDto == null) {
                    return CompletableFuture.completedFuture(false);
                }
                
                // Update submission status
                submissionDto.setApprovalStatus("APPROVED");
                submissionDto.setApprovedBy(approverUuid);
                submissionDto.setApprovedAt(new Timestamp(System.currentTimeMillis()));
                
                // Create the lore entry
                LoreEntryDTO entryDto = new LoreEntryDTO();
                entryDto.setContent(submissionDto.getContent());
                entryDto.setSubmittedBy(submissionDto.getSubmitterUuid());
                entryDto.setSubmissionDate(submissionDto.getSubmissionDate());
                entryDto.setApproved(true);
                
                // Save both changes
                return saveLoreEntry(entryDto)
                    .thenCompose(loreId -> {
                        if (loreId != null && loreId > 0) {
                            return updateLoreSubmission(submissionDto);
                        }
                        return CompletableFuture.completedFuture(false);
                    });
            });
    }
    
    /**
     * Updates a lore submission's approval status and related fields.
     */
    public CompletableFuture<Boolean> updateLoreSubmission(LoreSubmissionDTO submission) {
        String query = "UPDATE lore_submissions SET "
            + "approval_status = ?, "
            + "approved_by = ?, "
            + "approved_at = ?, "
            + "updated_at = NOW() "
            + "WHERE id = ?";

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, submission.getApprovalStatus());
                stmt.setString(2, submission.getApprovedBy());
                stmt.setTimestamp(3, submission.getApprovedAt());
                stmt.setInt(4, submission.getId());
                int affectedRows = stmt.executeUpdate();
                return affectedRows > 0;
            } catch (SQLException e) {
                logger.error("Error updating lore submission", e);
                return false;
            }
        }, executor);
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
     * Save an item with robust error handling and logging.
     *
     * @param dto The item properties DTO to save
     * @return A future containing the saved item ID, or failed future on error
     */
    public CompletableFuture<Integer> saveItem(ItemPropertiesDTO dto) {
        if (!validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }

        return queryExecutor.executeTransaction(conn -> {
            int itemId;
            
            try {
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

                    try (var stmt = conn.prepareStatement(query.build())) {
                        for (int i = 0; i < query.getParameters().length; i++) {
                            stmt.setObject(i + 1, query.getParameters()[i]);
                        }
                        int rowsAffected = stmt.executeUpdate();
                        
                        if (rowsAffected <= 0) {
                            String error = "Failed to update item with ID: " + dto.getId();
                            logger.error(error, new SQLException(error));
                            throw new SQLException(error);
                        }
                        
                        itemId = dto.getId();
                    }
                } else {
                    // Insert new item
                    QueryBuilder query = queryBuilder.insertInto("lore_item")
                        .columns("lore_entry_id", "item_type", "material", "display_name", 
                                "lore", "custom_model_data", "rarity", "is_obtainable", 
                                "glow", "skull_texture", "texture_data", "owner_name", 
                                "collection_id", "theme_id", "rarity_level", 
                                "collection_sequence", "nbt_data", "created_by")
                        .values(dto.getLoreEntryId(), 
                               dto.getItemType() != null ? dto.getItemType().name() : null,
                               dto.getMaterial(),
                               dto.getDisplayName(),
                               dto.getLore() != null ? String.join("\n", dto.getLore()) : null,
                               dto.getCustomModelData(),
                               dto.getRarity(),
                               dto.isObtainable(),
                               dto.isGlow(),
                               dto.getSkullTexture(),
                               dto.getTextureData(),
                               dto.getOwnerName(),
                               dto.getCollectionId(),
                               dto.getThemeId(),
                               dto.getRarityLevel(),
                               dto.getCollectionSequence(),
                               dto.getNbtData(),
                               dto.getCreatedBy());

                    try (var stmt = conn.prepareStatement(query.build(), java.sql.Statement.RETURN_GENERATED_KEYS)) {
                        for (int i = 0; i < query.getParameters().length; i++) {
                            stmt.setObject(i + 1, query.getParameters()[i]);
                        }
                        
                        int rows = stmt.executeUpdate();
                        if (rows == 0) {
                            String error = "Failed to insert new item (no rows affected)";
                            logger.error(error, new SQLException(error));
                            throw new SQLException(error);
                        }
                        
                        try (var rs = stmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                itemId = rs.getInt(1);
                            } else {
                                String error = "Failed to get generated item ID after insert";
                                logger.error(error, new SQLException(error));
                                throw new SQLException(error);
                            }
                        }
                    }
                }
                
                return itemId;
            } catch (SQLException e) {
                logger.error("Error saving item", e);
                throw new RuntimeException(e);
            }
        }).exceptionally(ex -> {
            logger.error("Error saving item", ex);
            throw new CompletionException(ex);
        });
    }
    
    /**
     * Attempt to reconnect to the database.
     * 
     * @throws SQLException If reconnection fails
     */
    public void reconnect() throws SQLException {
        logger.info("Attempting to reconnect to database...");
        close();
        
        if (databaseType == DatabaseType.MYSQL) {
            MySQLSettingsDTO mysqlSettings = configManager.getDatabaseSettings().getMysqlSettings();
            ((MySQLConnectionProvider) connectionProvider).initializeConnectionPool();
        } else {
            SQLiteSettingsDTO sqliteSettings = configManager.getDatabaseSettings().getSqliteSettings();
            ((SQLiteConnectionProvider) connectionProvider).initializeConnection();
        }
        
        createDatabaseSchema();
    }

    /**
     * Validate the current database connection.
     * 
     * @return true if the connection is valid, false otherwise
     */
    public boolean validateConnection() {
        return connectionProvider != null && connectionProvider.validateConnection();
    }
    
    /**
     * Close the database connection and clean up resources.
     */
    public void close() {
        try {
            if (connectionProvider != null) {
                connectionProvider.close();
                logger.info("Database connection closed");
            }
        } catch (SQLException e) {
            logger.error("Error closing database connection", e);
        }
    }

    /**
     * Start the database health check service.
     */
    public void startHealthService() {
        if (healthService == null) {
            healthService = new DatabaseHealthService(this, plugin);
            healthService.start();
        }
    }

    /**
     * Stop the database health check service.
     */
    public void stopHealthService() {
        if (healthService != null) {
            healthService.stop();
            healthService = null;
        }
    }

    /**
     * Finds lore entries near a location within a specified radius.
     */
    public CompletableFuture<List<LoreEntryDTO>> findNearbyLoreEntries(Location location, double radius) {
        String query = "SELECT * FROM lore_entries WHERE "
            + "world = ? AND "
            + "x BETWEEN ? AND ? AND "
            + "y BETWEEN ? AND ? AND "
            + "z BETWEEN ? AND ?";

        double minX = location.getX() - radius;
        double maxX = location.getX() + radius;
        double minY = location.getY() - radius;
        double maxY = location.getY() + radius;
        double minZ = location.getZ() - radius;
        double maxZ = location.getZ() + radius;
        String world = location.getWorld().getName();

        return CompletableFuture.supplyAsync(() -> {
            List<LoreEntryDTO> results = new ArrayList<>();
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, world);
                stmt.setDouble(2, minX);
                stmt.setDouble(3, maxX);
                stmt.setDouble(4, minY);
                stmt.setDouble(5, maxY);
                stmt.setDouble(6, minZ);
                stmt.setDouble(7, maxZ);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        LoreEntryDTO dto = extractLoreEntryFromResultSet(rs);
                        results.add(dto);
                    }
                }
            } catch (SQLException e) {
                logger.error("Error finding nearby lore entries", e);
                throw new RuntimeException(e);
            }
            return results;
        }, executor);
    }

    private LoreEntryDTO extractLoreEntryFromResultSet(ResultSet rs) throws SQLException {
        LoreEntryDTO dto = new LoreEntryDTO();
        dto.setId(rs.getInt("id"));
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setEntryType(rs.getString("entry_type"));
        dto.setApproved(rs.getBoolean("is_approved"));
        dto.setSubmittedBy(rs.getString("submitted_by"));
        dto.setSubmissionDate(rs.getTimestamp("submission_date"));
        dto.setNbtData(rs.getString("nbt_data"));
        dto.setCreatedAt(rs.getTimestamp("created_at"));
        dto.setUpdatedAt(rs.getTimestamp("updated_at"));
        dto.setX(rs.getDouble("x"));
        dto.setY(rs.getDouble("y"));
        dto.setZ(rs.getDouble("z"));
        dto.setWorld(rs.getString("world"));
        dto.setContent(rs.getString("content"));
        return dto;
    }

    private LoreSubmissionDTO extractLoreSubmissionFromResultSet(ResultSet rs) throws SQLException {
        LoreSubmissionDTO dto = new LoreSubmissionDTO();
        dto.setId(rs.getInt("id"));
        dto.setContent(rs.getString("content"));
        dto.setSubmitterUuid(rs.getString("submitter_uuid"));
        dto.setSubmissionDate(rs.getTimestamp("submission_date"));
        dto.setApprovalStatus(rs.getString("approval_status"));
        dto.setApprovedBy(rs.getString("approved_by"));
        dto.setApprovedAt(rs.getTimestamp("approved_at"));
        dto.setCreatedAt(rs.getTimestamp("created_at"));
        dto.setUpdatedAt(rs.getTimestamp("updated_at"));
        return dto;
    }

    /**
     * Checks if a player exists in the lore system.
     */
    public CompletableFuture<Boolean> playerExists(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT COUNT(*) FROM lore_submission s " +
                "JOIN lore_entry e ON e.id = s.entry_id " +
                "WHERE e.entry_type = ? AND s.is_current_version = TRUE AND s.content LIKE ?";
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, "PLAYER");
                stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1) > 0;
                    }
                }
            } catch (SQLException e) {
                logger.error("Error checking if player exists: " + playerUuid, e);
            }
            return false;
        }, executor);
    }

    /**
     * Gets the current player name stored in the database for a given player UUID.
     */
    public CompletableFuture<String> getStoredPlayerName(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT s.content FROM lore_submission s " +
                "JOIN lore_entry e ON e.id = s.entry_id " +
                "WHERE e.entry_type = ? AND s.is_current_version = TRUE AND s.content LIKE ? AND s.content LIKE ? " +
                "ORDER BY s.created_at DESC LIMIT 1";
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, "PLAYER");
                stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
                stmt.setString(3, "%\"entry_type\":\"player_character\"%");
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String content = rs.getString("content");
                        // Extract player name from JSON content (assume JSON format)
                        return PlayerDTO.extractPlayerNameFromContent(content);
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting stored player name: " + playerUuid, e);
            }
            return null;
        }, executor);
    }

    /**
     * Gets all lore entries associated with a player.
     */
    public CompletableFuture<List<PlayerDTO>> getPlayerLoreEntries(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerDTO> results = new java.util.ArrayList<>();
            String query = "SELECT s.content, s.created_at, s.entry_id FROM lore_entry e " +
                "JOIN lore_submission s ON e.id = s.entry_id " +
                "WHERE e.entry_type = ? AND s.is_current_version = TRUE AND s.content LIKE ? " +
                "ORDER BY s.created_at DESC";
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, "PLAYER");
                stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        PlayerDTO dto = PlayerDTO.fromResultSet(rs);
                        results.add(dto);
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting player lore entries: " + playerUuid, e);
            }
            return results;
        }, executor);
    }

    /**
     * Gets player lore entries by type (e.g., FIRST_JOIN, PLAYER_CHARACTER, NAME_CHANGE).
     */
    public CompletableFuture<List<PlayerDTO>> getPlayerLoreEntriesByType(UUID playerUuid, String entryType) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerDTO> results = new java.util.ArrayList<>();
            String query = "SELECT s.content, s.created_at, s.entry_id FROM lore_entry e " +
                "JOIN lore_submission s ON e.id = s.entry_id " +
                "WHERE e.entry_type = ? AND s.is_current_version = TRUE AND s.content LIKE ? AND s.content LIKE ? " +
                "ORDER BY s.created_at DESC";
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, "PLAYER");
                stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
                stmt.setString(3, "%\"entry_type\":\"" + entryType + "\"%");
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        PlayerDTO dto = PlayerDTO.fromResultSet(rs);
                        results.add(dto);
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting player lore entries by type: " + playerUuid + ", " + entryType, e);
            }
            return results;
        }, executor);
    }

    /**
     * Saves a player entry (insert or update).
     */
    public CompletableFuture<String> savePlayer(PlayerDTO dto) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connectionProvider.getConnection()) {
                if (dto.getEntryId() != null) {
                    // Update existing entry
                    String query = "UPDATE lore_submission SET content = ? WHERE entry_id = ? AND is_current_version = TRUE";
                    try (PreparedStatement stmt = conn.prepareStatement(query)) {
                        stmt.setString(1, dto.getMetadataJson());
                        stmt.setString(2, dto.getEntryId());
                        int rows = stmt.executeUpdate();
                        return rows > 0 ? dto.getEntryId() : null;
                    }
                } else {
                    // Insert new entry
                    String entryQuery = "INSERT INTO lore_entry (entry_type, name, description) VALUES (?, ?, ?)";
                    try (PreparedStatement entryStmt = conn.prepareStatement(entryQuery, Statement.RETURN_GENERATED_KEYS)) {
                        entryStmt.setString(1, "PLAYER");
                        entryStmt.setString(2, "Player_" + dto.getPlayerUuid().toString());
                        entryStmt.setString(3, dto.getMetadata().get("description"));
                        entryStmt.executeUpdate();
                        try (ResultSet keys = entryStmt.getGeneratedKeys()) {
                            if (keys.next()) {
                                String entryId = String.valueOf(keys.getInt(1));
                                String submissionQuery = "INSERT INTO lore_submission (entry_id, content, is_current_version) VALUES (?, ?, TRUE)";
                                try (PreparedStatement subStmt = conn.prepareStatement(submissionQuery)) {
                                    subStmt.setString(1, entryId);
                                    subStmt.setString(2, dto.getMetadataJson());
                                    subStmt.executeUpdate();
                                    return entryId;
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Error saving player: " + dto.getPlayerUuid(), e);
            }
            return null;
        }, executor);
    }

    /**
     * Gets the history of name changes for a player.
     */
    public CompletableFuture<List<NameChangeRecordDTO>> getNameChangeHistory(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<NameChangeRecordDTO> results = new java.util.ArrayList<>();
            String query = "SELECT s.content, s.created_at, s.entry_id FROM lore_submission s " +
                "JOIN lore_entry e ON e.id = s.entry_id " +
                "WHERE e.entry_type = ? AND s.is_current_version = TRUE AND s.content LIKE ? AND s.content LIKE ? " +
                "ORDER BY s.created_at DESC";
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, "PLAYER");
                stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
                stmt.setString(3, "%\"entry_type\":\"name_change\"%");
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        NameChangeRecordDTO dto = NameChangeRecordDTO.fromResultSet(rs);
                        results.add(dto);
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting name change history: " + playerUuid, e);
            }
            return results;
        }, executor);
    }

    /**
     * Delete an item by ID (async).
     */
    public CompletableFuture<Boolean> deleteItem(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM lore_item WHERE id = ?";
            try (Connection conn = connectionProvider.getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                int rows = stmt.executeUpdate();
                return rows > 0;
            } catch (SQLException e) {
                logger.error("Error deleting item with ID: " + id, e);
                return false;
            }
        }, executor);
    }

    /**
     * Initialize the connection provider based on the storage type.
     */
    private void initializeConnectionProvider() {        // Get storage type from configuration
        storageType = plugin.getConfigManager().getConfig().getString("storage.type", "sqlite").toLowerCase();
        
        if (storageType.equalsIgnoreCase("mysql")) {
            // Get MySQL settings from configuration
            ConfigManager configManager = plugin.getConfigManager();
            Map<String, Object> mysqlSettings = configManager.getMySQLSettings();
            
            MySQLSettingsDTO settings = new MySQLSettingsDTO(
                (String) mysqlSettings.get("host"),
                (Integer) mysqlSettings.get("port"),
                (String) mysqlSettings.get("database"),
                (String) mysqlSettings.get("username"),
                (String) mysqlSettings.get("password"),
                (Boolean) mysqlSettings.get("useSSL"),
                (String) mysqlSettings.get("tablePrefix")
            );
            
            // Create MySQL connection provider
            connectionProvider = new MySQLConnectionProvider(plugin);
        } else {
            // Default to SQLite
            ConfigManager configManager = plugin.getConfigManager();
            Map<String, Object> sqliteSettings = configManager.getSQLiteSettings();
            
            String dbName = (String) sqliteSettings.getOrDefault("database", "data.db");
            boolean walMode = (Boolean) sqliteSettings.getOrDefault("walMode", true);
            int busyTimeout = (Integer) sqliteSettings.getOrDefault("busyTimeout", 30000);
            int cacheSize = (Integer) sqliteSettings.getOrDefault("cacheSize", 4000);
            
            // Create SQLite settings DTO
            SQLiteSettingsDTO settings = new SQLiteSettingsDTO(dbName, walMode, busyTimeout, cacheSize);
            
            // Create SQLite connection provider
            connectionProvider = new SQLiteConnectionProvider(plugin);
        }
    }

    /**
     * Validate that all required database tables exist.
     */
    private void validateTables() {
        logger.info("Validating database tables...");
        
        try {
            // Check if essential tables exist
            boolean playerTableExists = tableExists("player");
            boolean loreEntryTableExists = tableExists("lore_entry");
            boolean loreSubmissionTableExists = tableExists("lore_submission");
            boolean itemPropertiesTableExists = tableExists("item_properties");
            boolean loreCollectionTableExists = tableExists("lore_collection");
            
            // Log validation results
            logger.info("Table validation results:");
            logger.info("- player: " + (playerTableExists ? "exists" : "missing"));
            logger.info("- lore_entry: " + (loreEntryTableExists ? "exists" : "missing"));
            logger.info("- lore_submission: " + (loreSubmissionTableExists ? "exists" : "missing"));
            logger.info("- item_properties: " + (itemPropertiesTableExists ? "exists" : "missing"));
            logger.info("- lore_collection: " + (loreCollectionTableExists ? "exists" : "missing"));
            
            // If any essential table is missing, initialize tables
            if (!playerTableExists || !loreEntryTableExists || !loreSubmissionTableExists || 
                !itemPropertiesTableExists || !loreCollectionTableExists) {
                logger.warning("Some essential tables are missing. Initializing database tables...");
                databaseSetup.initializeTables()
                    .thenRun(() -> logger.info("Database tables initialized successfully after validation"))
                    .exceptionally(e -> {
                        logger.error("Failed to initialize database tables after validation", e);
                        return null;
                    });
            } else {
                logger.info("All essential database tables exist");
            }
        } catch (SQLException e) {
            logger.error("Failed to validate database tables", e);
        }
    }

    /**
     * Check if a table exists in the database.
     *
     * @param tableName The name of the table to check
     * @return True if the table exists, false otherwise
     * @throws SQLException if an SQL error occurs
     */
    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = connectionProvider.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Get table metadata
            try (ResultSet tables = metaData.getTables(
                    connection.getCatalog(), 
                    connection.getSchema(), 
                    tableName, 
                    new String[]{"TABLE"})) {
                
                return tables.next();
            }
        }
    }
}

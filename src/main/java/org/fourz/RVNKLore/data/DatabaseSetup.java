package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.connection.ConnectionProvider;
import org.fourz.RVNKLore.data.query.SchemaQueryBuilder;
import org.fourz.RVNKLore.data.query.QueryExecutor;
import org.fourz.RVNKLore.debug.LogManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;

/**
 * Handles database table creation and initialization operations.
 * Responsible for setting up the database schema during plugin startup.
 */
public class DatabaseSetup {    private final RVNKLore plugin;
    private final LogManager logger;
    private final ConnectionProvider connectionProvider;
    private final SchemaQueryBuilder schemaQueryBuilder;
    private final QueryExecutor queryExecutor;    /**
     * Creates a new DatabaseSetup instance.
     *
     * @param plugin The RVNKLore plugin instance
     * @param connectionProvider The connection provider to use
     * @param schemaQueryBuilder The schema query builder to use
     * @param queryExecutor The query executor to use
     */
    public DatabaseSetup(RVNKLore plugin, ConnectionProvider connectionProvider, 
                          SchemaQueryBuilder schemaQueryBuilder, QueryExecutor queryExecutor) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DatabaseSetup");
        this.connectionProvider = connectionProvider;
        this.schemaQueryBuilder = schemaQueryBuilder;
        this.queryExecutor = queryExecutor;
    }

    /**
     * Initializes all database tables.
     * Creates tables if they don't exist.
     *
     * @return A CompletableFuture that completes when all tables are created
     */
    public CompletableFuture<Void> initializeTables() {
        return CompletableFuture.runAsync(() -> {
            try {
                logger.info("Initializing database tables...");
                
                createPlayerTable();
                createNameChangeRecordTable();
                createLoreEntryTable();
                createLoreSubmissionTable();
                createItemPropertiesTable();
                createLoreCollectionTable();
                createLoreCollectionEntryTable();
                createLoreItemTable();
                createLoreLocationTable();
                
                logger.info("Database tables initialized successfully");
            } catch (SQLException e) {
                logger.error("Failed to initialize database tables", e);
                throw new RuntimeException("Failed to initialize database tables", e);
            }
        });
    }

    /**
     * Creates the player table if it doesn't exist.
     *
     * @throws SQLException if an SQL error occurs
     */
    private void createPlayerTable() throws SQLException {
        String createTable = schemaQueryBuilder.createTable("player")
                .column("id", "VARCHAR(36)", "PRIMARY KEY")
                .column("username", "VARCHAR(16)", "NOT NULL")
                .column("last_seen", "TIMESTAMP", "NOT NULL")
                .column("first_join", "TIMESTAMP", "NOT NULL")
                .column("is_banned", "BOOLEAN", "DEFAULT FALSE")
                .column("has_submitted_lore", "BOOLEAN", "DEFAULT FALSE")
                .column("lore_submission_count", "INT", "DEFAULT 0")
                .column("lore_approval_count", "INT", "DEFAULT 0")
                .build();

        executeStatement(createTable);
    }

    /**
     * Creates the name_change_record table if it doesn't exist.
     *
     * @throws SQLException if an SQL error occurs
     */
    private void createNameChangeRecordTable() throws SQLException {        String createTable = schemaQueryBuilder.createTable("name_change_record")
                .column("id", "INT", "PRIMARY KEY " + schemaQueryBuilder.getAutoIncrementSyntax())
                .column("player_id", "VARCHAR(36)", "NOT NULL")
                .column("old_username", "VARCHAR(16)", "NOT NULL")
                .column("new_username", "VARCHAR(16)", "NOT NULL")
                .column("change_date", "TIMESTAMP", "NOT NULL")
                .foreignKey("player_id", "player", "id")
                .build();

        executeStatement(createTable);
    }

    /**
     * Creates the lore_entry table if it doesn't exist.
     *
     * @throws SQLException if an SQL error occurs
     */
    private void createLoreEntryTable() throws SQLException {        String createTable = schemaQueryBuilder.createTable("lore_entry")
                .column("id", "INT", "PRIMARY KEY " + schemaQueryBuilder.getAutoIncrementSyntax())
                .column("title", "VARCHAR(100)", "NOT NULL")
                .column("content", "TEXT", "NOT NULL")
                .column("author_id", "VARCHAR(36)", "NOT NULL")
                .column("creation_date", "TIMESTAMP", "NOT NULL")
                .column("last_modified", "TIMESTAMP", "NOT NULL")
                .column("is_approved", "BOOLEAN", "DEFAULT FALSE")
                .column("approver_id", "VARCHAR(36)", "NULL")
                .column("approval_date", "TIMESTAMP", "NULL")
                .column("entity_type", "VARCHAR(50)", "NOT NULL")
                .column("entity_id", "VARCHAR(100)", "NOT NULL")
                .column("is_deleted", "BOOLEAN", "DEFAULT FALSE")
                .foreignKey("author_id", "player", "id")
                .foreignKey("approver_id", "player", "id")
                .index("entity_type_id_idx", "entity_type, entity_id")
                .build();

        executeStatement(createTable);
    }

    /**
     * Creates the lore_submission table if it doesn't exist.
     *
     * @throws SQLException if an SQL error occurs
     */
    private void createLoreSubmissionTable() throws SQLException {        String createTable = schemaQueryBuilder.createTable("lore_submission")
                .column("id", "INT", "PRIMARY KEY " + schemaQueryBuilder.getAutoIncrementSyntax())
                .column("title", "VARCHAR(100)", "NOT NULL")
                .column("content", "TEXT", "NOT NULL")
                .column("submitter_id", "VARCHAR(36)", "NOT NULL")
                .column("submission_date", "TIMESTAMP", "NOT NULL")
                .column("status", "VARCHAR(20)", "DEFAULT 'PENDING'")
                .column("reviewer_id", "VARCHAR(36)", "NULL")
                .column("review_date", "TIMESTAMP", "NULL")
                .column("review_notes", "TEXT", "NULL")
                .column("entity_type", "VARCHAR(50)", "NOT NULL")
                .column("entity_id", "VARCHAR(100)", "NOT NULL")
                .foreignKey("submitter_id", "player", "id")
                .foreignKey("reviewer_id", "player", "id")
                .build();

        executeStatement(createTable);
    }

    /**
     * Creates the item_properties table if it doesn't exist.
     *
     * @throws SQLException if an SQL error occurs
     */
    private void createItemPropertiesTable() throws SQLException {        String createTable = schemaQueryBuilder.createTable("item_properties")
                .column("id", "INT", "PRIMARY KEY " + schemaQueryBuilder.getAutoIncrementSyntax())
                .column("item_key", "VARCHAR(100)", "UNIQUE NOT NULL")
                .column("display_name", "VARCHAR(100)", "NOT NULL")
                .column("material", "VARCHAR(50)", "NOT NULL")
                .column("model_data", "INT", "DEFAULT 0")
                .column("lore_id", "INT", "NULL")
                .column("creator_id", "VARCHAR(36)", "NOT NULL")
                .column("creation_date", "TIMESTAMP", "NOT NULL")
                .column("is_enabled", "BOOLEAN", "DEFAULT TRUE")
                .column("properties", "TEXT", "NULL")
                .foreignKey("lore_id", "lore_entry", "id")
                .foreignKey("creator_id", "player", "id")
                .build();

        executeStatement(createTable);
    }

    /**
     * Creates the lore_collection table if it doesn't exist.
     *
     * @throws SQLException if an SQL error occurs
     */
    private void createLoreCollectionTable() throws SQLException {        String createTable = schemaQueryBuilder.createTable("lore_collection")
                .column("id", "INT", "PRIMARY KEY " + schemaQueryBuilder.getAutoIncrementSyntax())
                .column("name", "VARCHAR(100)", "UNIQUE NOT NULL")
                .column("description", "TEXT", "NOT NULL")
                .column("creator_id", "VARCHAR(36)", "NOT NULL")
                .column("creation_date", "TIMESTAMP", "NOT NULL")
                .column("is_active", "BOOLEAN", "DEFAULT TRUE")
                .foreignKey("creator_id", "player", "id")
                .build();

        executeStatement(createTable);
    }

    /**
     * Creates the lore_collection_entry table if it doesn't exist.
     *
     * @throws SQLException if an SQL error occurs
     */
    private void createLoreCollectionEntryTable() throws SQLException {
        String createTable = schemaQueryBuilder.createTable("lore_collection_entry")
                .column("collection_id", "INT", "NOT NULL")
                .column("lore_id", "INT", "NOT NULL")
                .column("added_date", "TIMESTAMP", "NOT NULL")
                .column("added_by", "VARCHAR(36)", "NOT NULL")
                .column("sort_order", "INT", "DEFAULT 0")
                .primaryKey("collection_id, lore_id")
                .foreignKey("collection_id", "lore_collection", "id")
                .foreignKey("lore_id", "lore_entry", "id")
                .foreignKey("added_by", "player", "id")
                .build();

        executeStatement(createTable);
    }

    /**
     * Creates the lore_item table if it doesn't exist.
     *
     * @throws SQLException if an SQL error occurs
     */
    private void createLoreItemTable() throws SQLException {
        // Use schemaQueryBuilder for dialect-agnostic SQL
        // If you need to support MySQL/SQLite differences, use schemaQueryBuilder or add logic here
        // For now, use a generic definition
        String createTable = schemaQueryBuilder.createTable("lore_item")
                .column("id", "INT", "PRIMARY KEY " + schemaQueryBuilder.getAutoIncrementSyntax())
                .column("lore_entry_id", "INT", "NOT NULL")
                .column("item_type", "VARCHAR(50)", "NOT NULL")
                .column("material", "VARCHAR(50)", "NOT NULL")
                .column("display_name", "VARCHAR(100)", "NOT NULL")
                .column("lore", "TEXT", "NULL")
                .column("custom_model_data", "INT", "NULL")
                .column("rarity", "VARCHAR(20)", "DEFAULT 'COMMON'")
                .column("is_obtainable", "BOOLEAN", "DEFAULT TRUE")
                .column("glow", "BOOLEAN", "DEFAULT FALSE")
                .column("skull_texture", "TEXT", "NULL")
                .column("texture_data", "TEXT", "NULL")
                .column("owner_name", "VARCHAR(100)", "NULL")
                .column("collection_id", "VARCHAR(50)", "NULL")
                .column("theme_id", "VARCHAR(50)", "NULL")
                .column("rarity_level", "VARCHAR(20)", "NULL")
                .column("collection_sequence", "INT", "NULL")
                .column("nbt_data", "TEXT", "NULL")
                .column("created_by", "VARCHAR(36)", "NULL")
                .column("created_at", "TIMESTAMP", "DEFAULT CURRENT_TIMESTAMP")
                .column("updated_at", "TIMESTAMP", "DEFAULT CURRENT_TIMESTAMP")
                .column("custom_properties", "TEXT", "NULL")
                .column("metadata", "TEXT", "NULL")
                .foreignKey("lore_entry_id", "lore_entry", "id")
                .index("lore_entry_id_idx", "lore_entry_id")
                .index("item_type_idx", "item_type")
                .index("collection_id_idx", "collection_id")
                .build();
        executeStatement(createTable);
    }

    /**
     * Creates the lore_location table if it doesn't exist.
     *
     * @throws SQLException if an SQL error occurs
     */
    private void createLoreLocationTable() throws SQLException {
        String createTable = schemaQueryBuilder.createTable("lore_location")
                .column("id", "INT", "PRIMARY KEY " + schemaQueryBuilder.getAutoIncrementSyntax())
                .column("lore_entry_id", "INT", "NOT NULL")
                .column("world", "VARCHAR(100)", "NOT NULL")
                .column("x", "DOUBLE", "NOT NULL")
                .column("y", "DOUBLE", "NOT NULL")
                .column("z", "DOUBLE", "NOT NULL")
                .column("yaw", "FLOAT", "NULL")
                .column("pitch", "FLOAT", "NULL")
                .column("location_type", "VARCHAR(50)", "NOT NULL")
                .column("discovery_message", "TEXT", "NULL")
                .column("discoverable", "BOOLEAN", "DEFAULT TRUE")
                .column("discovery_radius", "DOUBLE", "DEFAULT 10.0")
                .column("created_at", "TIMESTAMP", "DEFAULT CURRENT_TIMESTAMP")
                .column("updated_at", "TIMESTAMP", "DEFAULT CURRENT_TIMESTAMP")
                .foreignKey("lore_entry_id", "lore_entry", "id")
                .index("lore_entry_id_idx", "lore_entry_id")
                .index("world_idx", "world")
                .build();
        executeStatement(createTable);
    }

    /**
     * Executes a SQL statement.
     *
     * @param sql The SQL statement to execute
     * @throws SQLException if an SQL error occurs
     */
    private void executeStatement(String sql) throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            
            // Commit changes if auto-commit is disabled
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        }
    }
}

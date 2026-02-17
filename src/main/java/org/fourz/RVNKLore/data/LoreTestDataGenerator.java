package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.testing.TestDataGenerator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Test data generator for RVNKLore plugin.
 *
 * <p>Seeds 9 core tables with deterministic test data:
 * <ul>
 *   <li>lore_entry - Base entries (LANDMARK, CITY, ITEM, etc.)</li>
 *   <li>lore_submission - Content with versioning</li>
 *   <li>lore_item - Items with materials and rarity</li>
 *   <li>collection - Item collections</li>
 *   <li>collection_item - M2M relationships</li>
 *   <li>player_collection_progress - Progress tracking</li>
 *   <li>collection_reward - Rewards</li>
 *   <li>lore_metadata - Key-value pairs</li>
 * </ul>
 * </p>
 */
public class LoreTestDataGenerator extends TestDataGenerator {

    // Lore types for testing
    private static final String[] LORE_TYPES = {
        "LANDMARK", "CITY", "PLAYER", "ITEM", "EVENT", "FACTION", "QUEST", "HEAD"
    };

    // Item materials
    private static final String[] MATERIALS = {
        "DIAMOND_SWORD", "NETHERITE_HELMET", "BOW", "TRIDENT",
        "ENCHANTED_BOOK", "GOLDEN_APPLE", "DRAGON_EGG", "ELYTRA",
        "TOTEM_OF_UNDYING", "BEACON"
    };

    // Item types
    private static final String[] ITEM_TYPES = {
        "LEGENDARY", "ARTIFACT", "SEASONAL", "EVENT", "QUEST_REWARD",
        "UNIQUE", "RARE", "UNCOMMON"
    };

    // Item rarities
    private static final String[] RARITIES = {
        "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "UNIQUE", "ARTIFACT"
    };

    // Collection themes
    private static final String[] THEMES = {
        "MICKY_HATS", "LEGENDARY_WEAPONS", "QUEST_REWARDS", "SEASONAL_ITEMS",
        "RARE_HEADS", "ENCHANTED_GEAR"
    };

    // Approval statuses
    private static final String[] APPROVAL_STATUSES = {
        "PENDING", "APPROVED", "REJECTED", "NEEDS_REVISION"
    };

    // Visibility options
    private static final String[] VISIBILITIES = {"PUBLIC", "STAFF_ONLY", "HIDDEN"};

    private final DatabaseManager databaseManager;
    private final DatabaseConnection dbConnection;
    private final ExecutorService executor;
    private final String tablePrefix;

    /**
     * Create a new LoreTestDataGenerator.
     *
     * @param databaseManager the database manager instance
     */
    public LoreTestDataGenerator(DatabaseManager databaseManager) {
        super(
            Logger.getLogger("RVNKLore"),
            () -> {
                DatabaseConnection conn = databaseManager.getDatabaseConnection();
                return conn != null && "MySQL".equals(conn.getDialect().getName());
            },
            databaseManager::getConnection
        );
        this.databaseManager = databaseManager;
        this.dbConnection = databaseManager.getDatabaseConnection();
        this.executor = Executors.newSingleThreadExecutor();
        this.tablePrefix = dbConnection != null ? dbConnection.getTablePrefix() : "";
    }

    /**
     * Get prefixed table name.
     */
    private String table(String baseName) {
        if (dbConnection != null) {
            return dbConnection.table(baseName);
        }
        if (tablePrefix == null || tablePrefix.isEmpty()) {
            return baseName;
        }
        return tablePrefix + baseName;
    }

    @Override
    public String getGeneratorName() {
        return "LoreTestDataGenerator";
    }

    @Override
    public CompletableFuture<Integer> seed(DataCategory category) {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Seeding " + category.name() + " data...");
            int totalRecords = 0;

            try {
                Connection conn = getConnection();
                conn.setAutoCommit(false);

                try {
                    // 1. Seed lore_entry (base entries)
                    int entryCount = seedLoreEntries(conn, category.getBaseCount());
                    totalRecords += entryCount;

                    // 2. Seed lore_submission (content for entries)
                    totalRecords += seedLoreSubmissions(conn, entryCount);

                    // 3. Seed lore_item (items linked to entries)
                    totalRecords += seedLoreItems(conn, entryCount);

                    // 4. Seed collections
                    totalRecords += seedCollections(conn);

                    // 5. Seed collection_item (M2M) - use actual auto-increment IDs
                    int[] collectionIds = getGeneratedIds(conn, table("collection"),
                        "id", "collection_id LIKE 'test_collection_%'");
                    int[] itemIds = getGeneratedIds(conn, table("lore_item"),
                        "id", "name LIKE 'Test Item%'");
                    totalRecords += seedCollectionItems(conn, collectionIds, itemIds);

                    // 6. Seed player_collection_progress
                    totalRecords += seedPlayerCollectionProgress(conn, category.getBaseCount());

                    // 7. Seed collection_reward
                    totalRecords += seedCollectionRewards(conn);

                    // 8. Seed lore_metadata (references lore_entry UUIDs)
                    totalRecords += seedLoreMetadata(conn, category.getBaseCount());

                    conn.commit();
                    logInfo("Seed complete: " + totalRecords + " total records");

                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Seed failed, rolling back: " + e.getMessage());
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException e) {
                logSevere("Failed to seed data: " + e.getMessage());
                return 0;
            }

            return totalRecords;
        }, executor);
    }

    private int seedLoreEntries(Connection conn, int count) throws SQLException {
        String sql;
        if (isMySQL()) {
            sql = "INSERT INTO " + table("lore_entry") +
                " (id, entry_type, name) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE entry_type = VALUES(entry_type)";
        } else {
            sql = "INSERT OR REPLACE INTO " + table("lore_entry") +
                " (id, entry_type, name) VALUES (?, ?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < count; i++) {
                UUID id = testUUID(i);
                String entryType = LORE_TYPES[i % LORE_TYPES.length];
                String name = "Test " + entryType.toLowerCase() + " " + i;

                stmt.setString(1, id.toString());
                stmt.setString(2, entryType);
                stmt.setString(3, name);
                stmt.addBatch();
                inserted++;

                if (inserted % 100 == 0) {
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
        }
        logSeeded("lore_entry", inserted);
        return inserted;
    }

    private int seedLoreSubmissions(Connection conn, int entryCount) throws SQLException {
        String sql;
        if (isMySQL()) {
            sql = "INSERT INTO " + table("lore_submission") +
                " (entry_id, slug, visibility, status, submitter_uuid, approval_status, content_version, " +
                "is_current_version, content) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE content = VALUES(content), status = VALUES(status)";
        } else {
            sql = "INSERT OR REPLACE INTO " + table("lore_submission") +
                " (entry_id, slug, visibility, status, submitter_uuid, approval_status, content_version, " +
                "is_current_version, content) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < entryCount; i++) {
                UUID entryId = testUUID(i);
                String slug = "test-entry-" + i;
                String visibility = VISIBILITIES[i % VISIBILITIES.length];
                String status = (i % 4 == 3) ? "DRAFT" : "ACTIVE";
                UUID submitterUuid = testUUID(i + 1000);
                String approvalStatus = APPROVAL_STATUSES[i % APPROVAL_STATUSES.length];
                int contentVersion = 1;
                boolean isCurrent = true;
                // Content must be JSON matching LoreEntryRepository expectations
                String loreType = LORE_TYPES[i % LORE_TYPES.length];
                StringBuilder contentJson = new StringBuilder();
                contentJson.append("{\"description\":\"Test lore content for entry ").append(i)
                    .append(". Interesting lore about the world of Ravenkraft.\",\"nbt_data\":null");
                // Add location for location-capable types
                if ("LANDMARK".equals(loreType) || "CITY".equals(loreType) ||
                    "MONUMENT".equals(loreType) || "PATH".equals(loreType) ||
                    "EVENT".equals(loreType) || "FACTION".equals(loreType)) {
                    contentJson.append(",\"location\":{\"world\":\"world\",\"x\":")
                        .append(100 + i * 50).append(",\"y\":64,\"z\":")
                        .append(200 + i * 50).append("}");
                }
                contentJson.append("}");
                String content = contentJson.toString();

                stmt.setString(1, entryId.toString());
                stmt.setString(2, slug);
                stmt.setString(3, visibility);
                stmt.setString(4, status);
                stmt.setString(5, submitterUuid.toString());
                stmt.setString(6, approvalStatus);
                stmt.setInt(7, contentVersion);
                stmt.setInt(8, isCurrent ? 1 : 0);
                stmt.setString(9, content);
                stmt.addBatch();
                inserted++;

                if (inserted % 100 == 0) {
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
        }
        logSeeded("lore_submission", inserted);
        return inserted;
    }

    private int seedLoreItems(Connection conn, int entryCount) throws SQLException {
        String sql;
        if (isMySQL()) {
            sql = "INSERT INTO " + table("lore_item") +
                " (name, short_uuid, lore_entry_id, material, item_type, rarity, " +
                "is_obtainable, custom_model_data, item_properties) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE material = VALUES(material), item_properties = VALUES(item_properties)";
        } else {
            sql = "INSERT OR REPLACE INTO " + table("lore_item") +
                " (name, short_uuid, lore_entry_id, material, item_type, rarity, " +
                "is_obtainable, custom_model_data, item_properties) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Only 30% of entries are items
            int itemCount = entryCount * 3 / 10;
            for (int i = 0; i < itemCount; i++) {
                UUID entryId = testUUID(i);
                String name = "Test Item " + i;
                String shortUuid = entryId.toString().substring(0, 8);
                String material = MATERIALS[i % MATERIALS.length];
                String itemType = ITEM_TYPES[i % ITEM_TYPES.length];
                String rarity = RARITIES[i % RARITIES.length];
                boolean isObtainable = i % 5 != 0;  // 80% obtainable
                int customModelData = 10000 + i;
                String itemProperties = "{\"test\":true,\"damage\":" + (i * 5) + ",\"durability\":" + (100 + i * 10) + "}";

                stmt.setString(1, name);
                stmt.setString(2, shortUuid);
                stmt.setString(3, entryId.toString());
                stmt.setString(4, material);
                stmt.setString(5, itemType);
                stmt.setString(6, rarity);
                stmt.setInt(7, isObtainable ? 1 : 0);
                stmt.setInt(8, customModelData);
                stmt.setString(9, itemProperties);
                stmt.addBatch();
                inserted++;
            }
            stmt.executeBatch();
        }
        logSeeded("lore_item", inserted);
        return inserted;
    }

    private int seedCollections(Connection conn) throws SQLException {
        String sql;
        if (isMySQL()) {
            sql = "INSERT INTO " + table("collection") +
                " (collection_id, name, description, theme_id, is_active, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE name = VALUES(name)";
        } else {
            sql = "INSERT OR REPLACE INTO " + table("collection") +
                " (collection_id, name, description, theme_id, is_active, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < THEMES.length; i++) {
                String collectionId = "test_collection_" + i;
                String name = THEMES[i].replace("_", " ");
                String description = "Test collection for " + name;
                String themeId = THEMES[i].toLowerCase();
                boolean isActive = i % 3 != 2;  // 66% active
                long createdAt = testTimestamp(i * 7).getTime() / 1000;

                stmt.setString(1, collectionId);
                stmt.setString(2, name);
                stmt.setString(3, description);
                stmt.setString(4, themeId);
                stmt.setInt(5, isActive ? 1 : 0);
                stmt.setLong(6, createdAt);
                stmt.addBatch();
                inserted++;
            }
            stmt.executeBatch();
        }
        logSeeded("collection", inserted);
        return inserted;
    }

    /**
     * Query actual auto-increment IDs from a table matching a condition.
     */
    private int[] getGeneratedIds(Connection conn, String tableName, String idColumn,
                                   String whereClause) throws SQLException {
        String sql = "SELECT " + idColumn + " FROM " + tableName +
            " WHERE " + whereClause + " ORDER BY " + idColumn + " ASC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            java.util.List<Integer> ids = new java.util.ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
            return ids.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    private int seedCollectionItems(Connection conn, int[] collectionIds, int[] itemIds) throws SQLException {
        if (collectionIds.length == 0 || itemIds.length == 0) {
            logInfo("Skipping collection_item seed: no collections or items to link");
            return 0;
        }

        String sql;
        if (isMySQL()) {
            sql = "INSERT INTO " + table("collection_item") +
                " (collection_id, item_id) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE collection_id = VALUES(collection_id)";
        } else {
            sql = "INSERT OR IGNORE INTO " + table("collection_item") +
                " (collection_id, item_id) VALUES (?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Each item in 1-2 collections
            for (int i = 0; i < itemIds.length; i++) {
                int numCollections = 1 + (i % 2);
                for (int c = 0; c < numCollections; c++) {
                    int collectionId = collectionIds[(i + c) % collectionIds.length];
                    int itemId = itemIds[i];

                    stmt.setInt(1, collectionId);
                    stmt.setInt(2, itemId);
                    stmt.addBatch();
                    inserted++;
                }
            }
            stmt.executeBatch();
        }
        logSeeded("collection_item", inserted);
        return inserted;
    }

    private int seedPlayerCollectionProgress(Connection conn, int playerCount) throws SQLException {
        String sql;
        if (isMySQL()) {
            sql = "INSERT INTO " + table("player_collection_progress") +
                " (player_id, collection_id, progress, completed_at, last_updated) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE progress = VALUES(progress)";
        } else {
            sql = "INSERT OR REPLACE INTO " + table("player_collection_progress") +
                " (player_id, collection_id, progress, completed_at, last_updated) " +
                "VALUES (?, ?, ?, ?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Each player has progress in 2-3 collections
            for (int i = 0; i < playerCount; i++) {
                UUID playerId = testUUID(i);
                int numCollections = 2 + (i % 2);

                for (int c = 0; c < numCollections; c++) {
                    String collectionId = "test_collection_" + ((i + c) % THEMES.length);
                    double progress = randomDouble(0.0, 100.0);
                    Long completedAt = progress >= 100.0 ? testTimestamp(0).getTime() / 1000 : null;
                    long lastUpdated = testTimestamp(i % 30).getTime() / 1000;

                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, collectionId);
                    stmt.setDouble(3, progress);
                    if (completedAt != null) {
                        stmt.setLong(4, completedAt);
                    } else {
                        stmt.setNull(4, java.sql.Types.BIGINT);
                    }
                    stmt.setLong(5, lastUpdated);
                    stmt.addBatch();
                    inserted++;

                    if (inserted % 100 == 0) {
                        stmt.executeBatch();
                    }
                }
            }
            stmt.executeBatch();
        }
        logSeeded("player_collection_progress", inserted);
        return inserted;
    }

    private int seedCollectionRewards(Connection conn) throws SQLException {
        String sql;
        if (isMySQL()) {
            sql = "INSERT INTO " + table("collection_reward") +
                " (collection_id, reward_type, reward_data, is_claimed) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE reward_data = VALUES(reward_data)";
        } else {
            sql = "INSERT OR REPLACE INTO " + table("collection_reward") +
                " (collection_id, reward_type, reward_data, is_claimed) VALUES (?, ?, ?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Each collection has 2 rewards
            for (int i = 0; i < THEMES.length; i++) {
                String collectionId = "test_collection_" + i;

                // Reward 1: Item reward
                stmt.setString(1, collectionId);
                stmt.setString(2, "ITEM");
                stmt.setString(3, "{\"material\":\"DIAMOND\",\"amount\":5}");
                stmt.setInt(4, 0);
                stmt.addBatch();
                inserted++;

                // Reward 2: XP reward
                stmt.setString(1, collectionId);
                stmt.setString(2, "EXPERIENCE");
                stmt.setString(3, "{\"amount\":" + (100 + i * 50) + "}");
                stmt.setInt(4, 0);
                stmt.addBatch();
                inserted++;
            }
            stmt.executeBatch();
        }
        logSeeded("collection_reward", inserted);
        return inserted;
    }

    private int seedLoreMetadata(Connection conn, int count) throws SQLException {
        // lore_metadata.lore_id references lore_entry(id) - use same UUID range as seedLoreEntries
        String sql;
        if (isMySQL()) {
            sql = "INSERT INTO " + table("lore_metadata") +
                " (lore_id, meta_key, meta_value) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)";
        } else {
            sql = "INSERT OR REPLACE INTO " + table("lore_metadata") +
                " (lore_id, meta_key, meta_value) VALUES (?, ?, ?)";
        }

        int inserted = 0;
        int metaCount = count / 5;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // 2-3 metadata entries per lore_entry record
            for (int i = 0; i < metaCount; i++) {
                UUID loreId = testUUID(i);  // Matches lore_entry IDs from seedLoreEntries
                int numMeta = 2 + (i % 2);

                String[] keys = {"author", "creation_date", "revision", "tags", "category"};
                for (int m = 0; m < numMeta && m < keys.length; m++) {
                    String value = switch (keys[m]) {
                        case "author" -> testPlayerName(i);
                        case "creation_date" -> testTimestamp(i * 5).toString();
                        case "revision" -> String.valueOf(1 + (i % 5));
                        case "tags" -> "test,generated,seed-" + i;
                        case "category" -> LORE_TYPES[i % LORE_TYPES.length].toLowerCase();
                        default -> "test_value";
                    };

                    stmt.setString(1, loreId.toString());
                    stmt.setString(2, keys[m]);
                    stmt.setString(3, value);
                    stmt.addBatch();
                    inserted++;
                }
            }
            stmt.executeBatch();
        }
        logSeeded("lore_metadata", inserted);
        return inserted;
    }

    @Override
    public CompletableFuture<Boolean> cleanup() {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Cleaning up all test data...");

            try {
                Connection conn = getConnection();
                conn.setAutoCommit(false);

                try {
                    // Disable FK checks for cleanup
                    try (PreparedStatement stmt = conn.prepareStatement(disableForeignKeyChecks())) {
                        stmt.execute();
                    }

                    // Delete in reverse FK order
                    // collection_item uses INTEGER FK to collection.id, so match via subquery
                    String collectionItemCondition = "collection_id IN (SELECT id FROM " +
                        table("collection") + " WHERE collection_id LIKE 'test_collection_%')";
                    String[][] tablesToClean = {
                        {"player_collection_progress", "collection_id LIKE 'test_collection_%'"},
                        {"collection_reward", "collection_id LIKE 'test_collection_%'"},
                        {"collection_item", collectionItemCondition},
                        {"collection", "collection_id LIKE 'test_collection_%'"},
                        {"lore_metadata", "meta_value LIKE '%test%' OR meta_key = 'tags'"},
                        {"lore_item", "name LIKE 'Test Item%'"},
                        {"lore_submission", "slug LIKE 'test-entry-%'"},
                        {"lore_entry", "name LIKE 'Test %'"}
                    };

                    for (String[] tableInfo : tablesToClean) {
                        String tableName = tableInfo[0];
                        String condition = tableInfo[1];
                        String sql = "DELETE FROM " + table(tableName) + " WHERE " + condition;
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            int deleted = stmt.executeUpdate();
                            logInfo("Deleted " + deleted + " records from " + tableName);
                        }
                    }

                    // Re-enable FK checks
                    try (PreparedStatement stmt = conn.prepareStatement(enableForeignKeyChecks())) {
                        stmt.execute();
                    }

                    conn.commit();
                    logInfo("Cleanup complete");
                    return true;

                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Cleanup failed: " + e.getMessage());
                    return false;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException e) {
                logSevere("Failed to cleanup: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> cleanupByPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Cleaning up data for player: " + playerUuid);
            int totalDeleted = 0;

            try {
                Connection conn = getConnection();
                conn.setAutoCommit(false);

                try {
                    // Delete player collection progress
                    String progressSql = "DELETE FROM " + table("player_collection_progress") +
                        " WHERE player_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(progressSql)) {
                        stmt.setString(1, playerUuid.toString());
                        totalDeleted += stmt.executeUpdate();
                    }

                    // Delete submissions by this player
                    String submissionSql = "DELETE FROM " + table("lore_submission") +
                        " WHERE submitter_uuid = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(submissionSql)) {
                        stmt.setString(1, playerUuid.toString());
                        totalDeleted += stmt.executeUpdate();
                    }

                    // Delete lore_entry records by this player (via submission)
                    String entrySql = "DELETE FROM " + table("lore_entry") +
                        " WHERE id IN (SELECT entry_id FROM " + table("lore_submission") +
                        " WHERE submitter_uuid = ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(entrySql)) {
                        stmt.setString(1, playerUuid.toString());
                        totalDeleted += stmt.executeUpdate();
                    }

                    conn.commit();
                    logInfo("Player cleanup complete: " + totalDeleted + " records");

                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Player cleanup failed: " + e.getMessage());
                    return 0;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException e) {
                logSevere("Failed to cleanup player data: " + e.getMessage());
                return 0;
            }

            return totalDeleted;
        }, executor);
    }

    /**
     * Cleanup the executor when done.
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}

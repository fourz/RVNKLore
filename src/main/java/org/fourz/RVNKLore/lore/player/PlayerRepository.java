package org.fourz.RVNKLore.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseConnection;
import org.fourz.RVNKLore.data.FallbackTracker;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.lore.LoreType;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Repository for player-related database operations
 *
 * This repository manages player lore entries, name changes, and provides
 * utilities for checking player existence and history in the lore system.
 *
 * All methods return CompletableFuture<T> for async operations per RVNKCore standard.
 */
public class PlayerRepository implements IPlayerRepository {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseConnection dbConnection;
    private final FallbackTracker fallbackTracker;
    private final JSONParser jsonParser;

    public PlayerRepository(RVNKLore plugin, DatabaseConnection dbConnection) {
        this.plugin = plugin;
        this.dbConnection = dbConnection;
        this.logger = LogManager.getInstance(plugin, "PlayerRepository");
        this.fallbackTracker = new FallbackTracker(plugin);
        this.jsonParser = new JSONParser();
    }

    /** Helper to get prefixed table name */
    private String t(String baseName) {
        return dbConnection.table(baseName);
    }

    /**
     * Check if a player already has a lore entry in the database
     *
     * @param playerUuid The UUID of the player to check
     * @return CompletableFuture that completes with true if the player has a lore entry, false otherwise
     */
    @Override
    public CompletableFuture<Boolean> playerExists(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + t("lore_submission") + " s " +
                         "JOIN " + t("lore_entry") + " e ON e.id = s.entry_id " +
                         "WHERE e.entry_type = ? " +
                         "AND s.is_current_version = TRUE " +
                         "AND s.content LIKE ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, LoreType.PLAYER.name());
                stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            } catch (SQLException e) {
                logger.error("Error checking if player exists: " + playerUuid, e);
                fallbackTracker.recordFailure();
            } catch (IllegalStateException e) {
                logger.error("Database unavailable checking player exists: " + playerUuid, e);
                fallbackTracker.recordFailure();
            }

            return false;
        });
    }

    /**
     * Get the current player name stored in the database for a given player UUID
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture that completes with Optional containing the stored player name, or empty if not found
     */
    @Override
    public CompletableFuture<Optional<String>> getStoredPlayerName(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT s.content FROM " + t("lore_submission") + " s " +
                         "JOIN " + t("lore_entry") + " e ON e.id = s.entry_id " +
                         "WHERE e.entry_type = ? " +
                         "AND s.is_current_version = TRUE " +
                         "AND s.content LIKE ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, LoreType.PLAYER.name());
                stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String content = rs.getString("content");
                        String playerName = extractJsonValue(content, "player_name");
                        return Optional.ofNullable(playerName);
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting stored player name: " + playerUuid, e);
                fallbackTracker.recordFailure();
            } catch (IllegalStateException e) {
                logger.error("Database unavailable getting stored player name: " + playerUuid, e);
                fallbackTracker.recordFailure();
            }

            return Optional.empty();
        });
    }

    /**
     * Get all lore entries associated with a player
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture that completes with a list of lore entry IDs
     */
    @Override
    public CompletableFuture<List<String>> getPlayerLoreEntryIds(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> entryIds = new ArrayList<>();
            String sql = "SELECT e.id FROM " + t("lore_entry") + " e " +
                         "JOIN " + t("lore_submission") + " s ON e.id = s.entry_id " +
                         "WHERE e.entry_type = ? " +
                         "AND s.is_current_version = TRUE " +
                         "AND s.content LIKE ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, LoreType.PLAYER.name());
                stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        entryIds.add(rs.getString("id"));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting player lore entries: " + playerUuid, e);
                fallbackTracker.recordFailure();
            } catch (IllegalStateException e) {
                logger.error("Database unavailable getting player lore entries: " + playerUuid, e);
                fallbackTracker.recordFailure();
            }

            return entryIds;
        });
    }

    /**
     * Get player lore entries by type (FIRST_JOIN, PLAYER_CHARACTER, NAME_CHANGE)
     *
     * @param playerUuid The UUID of the player
     * @param entryType The type of entry to filter by
     * @return CompletableFuture that completes with a list of entry IDs matching the type
     */
    @Override
    public CompletableFuture<List<String>> getPlayerLoreEntriesByType(UUID playerUuid, String entryType) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> entryIds = new ArrayList<>();
            String sql = "SELECT e.id FROM " + t("lore_entry") + " e " +
                         "JOIN " + t("lore_submission") + " s ON e.id = s.entry_id " +
                         "WHERE e.entry_type = ? " +
                         "AND s.is_current_version = TRUE " +
                         "AND s.content LIKE ? " +
                         "AND s.content LIKE ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, LoreType.PLAYER.name());
                stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
                stmt.setString(3, "%\"entry_type\":\"" + entryType + "\"%");

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        entryIds.add(rs.getString("id"));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting player lore entries by type: " + playerUuid + ", " + entryType, e);
                fallbackTracker.recordFailure();
            } catch (IllegalStateException e) {
                logger.error("Database unavailable getting player lore entries by type: " + playerUuid, e);
                fallbackTracker.recordFailure();
            }

            return entryIds;
        });
    }

    /**
     * Check if a player has had a name change recorded
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture that completes with true if the player has a name change entry, false otherwise
     */
    @Override
    public CompletableFuture<Boolean> hasNameChangeRecords(UUID playerUuid) {
        return getPlayerLoreEntriesByType(playerUuid, "name_change")
            .thenApply(entryIds -> !entryIds.isEmpty());
    }

    /**
     * Get the history of name changes for a player
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture that completes with a list of previous names, from oldest to newest
     */
    @Override
    public CompletableFuture<List<NameChangeRecord>> getNameChangeHistory(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<NameChangeRecord> nameChanges = new ArrayList<>();
            String sql = "SELECT s.content, s.created_at FROM " + t("lore_submission") + " s " +
                         "JOIN " + t("lore_entry") + " e ON e.id = s.entry_id " +
                         "WHERE e.entry_type = ? " +
                         "AND s.is_current_version = TRUE " +
                         "AND s.content LIKE ? " +
                         "AND s.content LIKE ? " +
                         "ORDER BY s.created_at ASC";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, LoreType.PLAYER.name());
                stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
                stmt.setString(3, "%\"entry_type\":\"name_change\"%");

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String content = rs.getString("content");

                        // Extract previous_name and player_name from the JSON content
                        String previousName = extractJsonValue(content, "previous_name");
                        String newName = extractJsonValue(content, "player_name");
                        long timestamp = rs.getTimestamp("created_at").getTime();

                        if (previousName != null && newName != null) {
                            nameChanges.add(new NameChangeRecord(previousName, newName, timestamp));
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting name change history: " + playerUuid, e);
                fallbackTracker.recordFailure();
            } catch (IllegalStateException e) {
                logger.error("Database unavailable getting name change history: " + playerUuid, e);
                fallbackTracker.recordFailure();
            }

            return nameChanges;
        });
    }

    /**
     * Record that a player has discovered a lore entry.
     * Creates an entry in the player_discoveries table.
     *
     * @param playerUuid The UUID of the player
     * @param entryId The ID of the lore entry discovered
     * @return CompletableFuture that completes with true if recorded successfully
     */
    @Override
    public CompletableFuture<Boolean> recordLoreDiscovery(UUID playerUuid, String entryId) {
        return CompletableFuture.supplyAsync(() -> {
            // First check if already discovered to avoid duplicates
            String checkSql = "SELECT COUNT(*) FROM " + t("player_discoveries") + " WHERE player_uuid = ? AND entry_id = ?";
            String insertSql = "INSERT INTO " + t("player_discoveries") + " (player_uuid, entry_id, discovered_at) VALUES (?, ?, ?)";

            try (Connection conn = dbConnection.getConnection()) {
                // Check if already exists
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, playerUuid.toString());
                    checkStmt.setString(2, entryId);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            // Already discovered
                            logger.debug("Player " + playerUuid + " already discovered entry " + entryId);
                            return false;
                        }
                    }
                }

                // Insert new discovery
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, playerUuid.toString());
                    insertStmt.setString(2, entryId);
                    insertStmt.setTimestamp(3, new java.sql.Timestamp(System.currentTimeMillis()));

                    int rows = insertStmt.executeUpdate();
                    if (rows > 0) {
                        logger.debug("Recorded discovery: player=" + playerUuid + ", entry=" + entryId);
                        return true;
                    }
                }
            } catch (SQLException e) {
                // Table might not exist - try to create it
                if (e.getMessage().contains("player_discoveries") || e.getMessage().contains("no such table")) {
                    logger.warning("player_discoveries table may not exist, attempting to create...");
                    if (createDiscoveriesTable()) {
                        // Retry the insert
                        return recordLoreDiscoveryDirect(playerUuid, entryId);
                    }
                }
                logger.error("Error recording lore discovery: " + playerUuid + ", " + entryId, e);
                fallbackTracker.recordFailure();
            } catch (IllegalStateException e) {
                logger.error("Database unavailable recording discovery: " + playerUuid, e);
                fallbackTracker.recordFailure();
            }

            return false;
        });
    }

    /**
     * Helper to create the player_discoveries table if it doesn't exist.
     */
    private boolean createDiscoveriesTable() {
        String createSql = "CREATE TABLE IF NOT EXISTS " + t("player_discoveries") + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "player_uuid VARCHAR(36) NOT NULL, " +
            "entry_id VARCHAR(36) NOT NULL, " +
            "discovered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "UNIQUE(player_uuid, entry_id))";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(createSql)) {
            stmt.executeUpdate();
            logger.info("Created player_discoveries table");
            return true;
        } catch (SQLException e) {
            logger.error("Failed to create player_discoveries table", e);
            return false;
        }
    }

    /**
     * Direct insert without checking (used after table creation).
     */
    private boolean recordLoreDiscoveryDirect(UUID playerUuid, String entryId) {
        String insertSql = "INSERT OR IGNORE INTO " + t("player_discoveries") + " (player_uuid, entry_id, discovered_at) VALUES (?, ?, ?)";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, entryId);
            stmt.setTimestamp(3, new java.sql.Timestamp(System.currentTimeMillis()));
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to record discovery after table creation", e);
            return false;
        }
    }

    /**
     * Check if the repository is operating in fallback mode.
     * Delegates to the FallbackTracker which manages failure counting and recovery.
     *
     * @return true if in fallback mode due to database connectivity issues
     */
    @Override
    public boolean isInFallbackMode() {
        return fallbackTracker.isInFallbackMode();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Extract a string value from a JSON string using JSONParser.
     *
     * @param json The JSON string to parse
     * @param key The key to extract
     * @return The string value, or null if not found or parse error
     */
    private String extractJsonValue(String json, String key) {
        if (json == null || json.isEmpty() || key == null) {
            return null;
        }
        try {
            JSONObject jsonObj = (JSONObject) jsonParser.parse(json);
            Object value = jsonObj.get(key);
            return value != null ? value.toString() : null;
        } catch (ParseException e) {
            logger.warning("Failed to parse JSON for key '" + key + "': " + e.getMessage());
            return null;
        }
    }
}

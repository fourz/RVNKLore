package org.fourz.RVNKLore.lore.player;

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

    /**
     * Check if a player already has a lore entry in the database
     *
     * @param playerUuid The UUID of the player to check
     * @return CompletableFuture that completes with true if the player has a lore entry, false otherwise
     */
    @Override
    public CompletableFuture<Boolean> playerExists(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM lore_submission s " +
                         "JOIN lore_entry e ON e.id = s.entry_id " +
                         "WHERE e.entry_type = ? " +
                         "AND s.is_current_version = TRUE " +
                         "AND s.content LIKE ?";

            try {
                Connection conn = dbConnection.getConnection();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, LoreType.PLAYER.name());
                    stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt(1) > 0;
                        }
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
            String sql = "SELECT s.content FROM lore_submission s " +
                         "JOIN lore_entry e ON e.id = s.entry_id " +
                         "WHERE e.entry_type = ? " +
                         "AND s.is_current_version = TRUE " +
                         "AND s.content LIKE ?";

            try {
                Connection conn = dbConnection.getConnection();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, LoreType.PLAYER.name());
                    stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String content = rs.getString("content");
                            String playerName = extractJsonValue(content, "player_name");
                            return Optional.ofNullable(playerName);
                        }
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
            String sql = "SELECT e.id FROM lore_entry e " +
                         "JOIN lore_submission s ON e.id = s.entry_id " +
                         "WHERE e.entry_type = ? " +
                         "AND s.is_current_version = TRUE " +
                         "AND s.content LIKE ?";

            try {
                Connection conn = dbConnection.getConnection();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, LoreType.PLAYER.name());
                    stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            entryIds.add(rs.getString("id"));
                        }
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
            String sql = "SELECT e.id FROM lore_entry e " +
                         "JOIN lore_submission s ON e.id = s.entry_id " +
                         "WHERE e.entry_type = ? " +
                         "AND s.is_current_version = TRUE " +
                         "AND s.content LIKE ? " +
                         "AND s.content LIKE ?";

            try {
                Connection conn = dbConnection.getConnection();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, LoreType.PLAYER.name());
                    stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
                    stmt.setString(3, "%\"entry_type\":\"" + entryType + "\"%");

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            entryIds.add(rs.getString("id"));
                        }
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
            String sql = "SELECT s.content, s.created_at FROM lore_submission s " +
                         "JOIN lore_entry e ON e.id = s.entry_id " +
                         "WHERE e.entry_type = ? " +
                         "AND s.is_current_version = TRUE " +
                         "AND s.content LIKE ? " +
                         "AND s.content LIKE ? " +
                         "ORDER BY s.created_at ASC";

            try {
                Connection conn = dbConnection.getConnection();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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

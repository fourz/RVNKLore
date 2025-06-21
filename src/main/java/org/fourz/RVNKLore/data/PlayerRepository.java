package org.fourz.RVNKLore.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.dto.NameChangeRecordDTO;
import org.fourz.RVNKLore.data.dto.PlayerDTO;
import org.fourz.RVNKLore.debug.LogManager;

/**
 * Service for player-related operations using the new database architecture.
 * Delegates all DB operations to DatabaseManager and uses DTOs for data transfer.
 */
public class PlayerRepository {
    private final LogManager logger;
    private final DatabaseManager databaseManager;

    public PlayerRepository(RVNKLore plugin, DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.logger = LogManager.getInstance(plugin, "PlayerRepository");
    }

    /**
     * Get a player by UUID.
     * 
     * @param uuid The UUID of the player to find
     * @return A future containing the player DTO, or null if not found
     */
    public CompletableFuture<PlayerDTO> getPlayerByUuid(UUID uuid) {
        if (!databaseManager.validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT s.entry_id, s.content FROM lore_submission s " +
                "JOIN lore_entry e ON e.id = s.entry_id " +
                "WHERE e.entry_type = ? AND s.is_current_version = TRUE AND s.content LIKE ? " +
                "LIMIT 1";
            
            try (Connection conn = databaseManager.getConnectionProvider().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, "PLAYER");
                stmt.setString(2, "%\"player_uuid\":\"" + uuid.toString() + "\"%");
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return PlayerDTO.fromResultSet(rs);
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting player by UUID: " + uuid, e);
            }
            
            return null;
        });
    }

    /**
     * Get players by name.
     * 
     * @param name The name of the player to find
     * @return A future containing a list of matching player DTOs
     */
    public CompletableFuture<List<PlayerDTO>> getPlayersByName(String name) {
        if (!databaseManager.validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerDTO> results = new java.util.ArrayList<>();
            String query = "SELECT s.entry_id, s.content FROM lore_submission s " +
                "JOIN lore_entry e ON e.id = s.entry_id " +
                "WHERE e.entry_type = ? AND s.is_current_version = TRUE AND s.content LIKE ?";
            
            try (Connection conn = databaseManager.getConnectionProvider().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, "PLAYER");
                stmt.setString(2, "%\"player_name\":\"" + name + "\"%");
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(PlayerDTO.fromResultSet(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting players by name: " + name, e);
            }
            
            return results;
        });
    }

    /**
     * Save a player entry (insert or update).
     * 
     * @param dto The player DTO to save
     * @return A future containing the player UUID
     */
    public CompletableFuture<UUID> savePlayer(PlayerDTO dto) {
        if (!databaseManager.validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (dto.getPlayerUuid() == null) {
                    throw new SQLException("Cannot save player with null UUID");
                }
                
                // Ensure metadata map exists
                if (dto.getMetadata() == null) {
                    dto.setMetadata(new HashMap<>());
                }
                
                // Add player UUID and name to metadata
                Map<String, String> metadata = dto.getMetadata();
                metadata.put("player_uuid", dto.getPlayerUuid().toString());
                if (dto.getPlayerName() != null) {
                    metadata.put("player_name", dto.getPlayerName());
                }
                
                // Check if this is an update or insert
                if (dto.getEntryId() == null) {
                    // Create a new player entry
                    databaseManager.savePlayerMetadata(dto).join();
                } else {
                    // Update existing player entry
                    databaseManager.savePlayerMetadata(dto).join();
                }
                
                return dto.getPlayerUuid();
            } catch (Exception e) {
                logger.error("Error saving player: " + (dto.getPlayerUuid() != null ? dto.getPlayerUuid() : "unknown"), e);
                return null;
            }
        });
    }

    /**
     * Delete a player by UUID.
     * 
     * @param uuid The UUID of the player to delete
     * @return A future containing true if successful, false otherwise
     */
    public CompletableFuture<Boolean> deletePlayer(UUID uuid) {
        if (!databaseManager.validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        return CompletableFuture.supplyAsync(() -> {
            String query = "DELETE e FROM lore_entry e " +
                "JOIN lore_submission s ON e.id = s.entry_id " +
                "WHERE e.entry_type = ? AND s.content LIKE ?";
            
            try (Connection conn = databaseManager.getConnectionProvider().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                
                stmt.setString(1, "PLAYER");
                stmt.setString(2, "%\"player_uuid\":\"" + uuid.toString() + "\"%");
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                logger.error("Error deleting player: " + uuid, e);
                return false;
            }
        });
    }

    /**
     * Checks if a player exists in the lore system.
     */
    public CompletableFuture<Boolean> playerExists(UUID playerUuid) {
        return databaseManager.playerExists(playerUuid)
            .exceptionally(e -> {
                logger.error("Error checking if player exists: " + playerUuid, e);
                return false;
            });
    }

    /**
     * Gets the current player name stored in the database for a given player UUID.
     */
    public CompletableFuture<String> getStoredPlayerName(UUID playerUuid) {
        return databaseManager.getStoredPlayerName(playerUuid)
            .exceptionally(e -> {
                logger.error("Error getting stored player name: " + playerUuid, e);
                return null;
            });
    }

    /**
     * Gets all lore entries associated with a player.
     */
    public CompletableFuture<List<PlayerDTO>> getPlayerLoreEntries(UUID playerUuid) {
        return databaseManager.getPlayerLoreEntries(playerUuid)
            .exceptionally(e -> {
                logger.error("Error getting player lore entries: " + playerUuid, e);
                return List.of();
            });
    }

    /**
     * Gets player lore entries by type (e.g., FIRST_JOIN, PLAYER_CHARACTER, NAME_CHANGE).
     */
    public CompletableFuture<List<PlayerDTO>> getPlayerLoreEntriesByType(UUID playerUuid, String entryType) {
        return databaseManager.getPlayerLoreEntriesByType(playerUuid, entryType)
            .exceptionally(e -> {
                logger.error("Error getting player lore entries by type: " + playerUuid + ", " + entryType, e);
                return List.of();
            });
    }

    /**
     * Gets the history of name changes for a player.
     */
    public CompletableFuture<List<NameChangeRecordDTO>> getNameChangeHistory(UUID playerUuid) {
        return databaseManager.getNameChangeHistory(playerUuid)
            .exceptionally(e -> {
                logger.error("Error getting name change history: " + playerUuid, e);
                return List.of();
            });
    }
}

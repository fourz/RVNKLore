package org.fourz.RVNKLore.data;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.dto.NameChangeRecordDTO;
import org.fourz.RVNKLore.data.dto.PlayerDTO;
import org.fourz.RVNKLore.data.query.DefaultQueryExecutor;
import org.fourz.RVNKLore.data.query.QueryBuilder;
import org.fourz.RVNKLore.debug.LogManager;

/**
 * Repository for player-related database operations.
 * Delegates complex operations to DatabaseManager and uses DTOs for data transfer.
 * All methods are async and use the new QueryBuilder pattern for SQL operations.
 */
public class PlayerRepository {
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private final DefaultQueryExecutor queryExecutor;
    private final QueryBuilder queryBuilder;

    public PlayerRepository(RVNKLore plugin, DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.logger = LogManager.getInstance(plugin, "PlayerRepository");
        this.queryExecutor = databaseManager.getQueryExecutor();
        this.queryBuilder = databaseManager.getQueryBuilder();
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
        
        QueryBuilder query = queryBuilder.select("s.entry_id", "s.content")
            .from("lore_submission s")
            .join("lore_entry e", "e.id = s.entry_id")
            .where("e.entry_type = ? AND s.is_current_version = TRUE AND s.content LIKE ?",
                  "PLAYER", "%\"player_uuid\":\"" + uuid.toString() + "\"%")
            .limit(1);
        
        return queryExecutor.executeQuery(query, PlayerDTO.class)
            .exceptionally(e -> {
                logger.error("Error getting player by UUID: " + uuid, e);
                throw new CompletionException(e);
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
        
        QueryBuilder query = queryBuilder.select("s.entry_id", "s.content")
            .from("lore_submission s")
            .join("lore_entry e", "e.id = s.entry_id")
            .where("e.entry_type = ? AND s.is_current_version = TRUE AND s.content LIKE ?",
                  "PLAYER", "%\"player_name\":\"" + name + "\"%");
        
        return queryExecutor.executeQueryList(query, PlayerDTO.class)
            .exceptionally(e -> {
                logger.error("Error getting players by name: " + name, e);
                return List.of();
            });
    }

    /**
     * Save a player entry (insert or update).
     * Uses DatabaseManager for complex metadata operations.
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
        
        if (dto.getPlayerUuid() == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Cannot save player with null UUID")
            );
        }

        // Ensure metadata exists and contains required fields
        if (dto.getMetadata() == null) {
            dto.setMetadata(new HashMap<>());
        }
        
        Map<String, String> metadata = dto.getMetadata();
        metadata.put("player_uuid", dto.getPlayerUuid().toString());
        if (dto.getPlayerName() != null) {
            metadata.put("player_name", dto.getPlayerName());
        }

        return databaseManager.savePlayerMetadata(dto)
            .thenApply(success -> dto.getPlayerUuid())
            .exceptionally(e -> {
                logger.error("Error saving player: " + dto.getPlayerUuid(), e);
                throw new CompletionException(e);
            });
    }

    /**
     * Delete a player by UUID.
     * 
     * @param uuid The UUID of the player to delete
     * @return A future containing true if successful
     */
    public CompletableFuture<Boolean> deletePlayer(UUID uuid) {
        if (!databaseManager.validateConnection()) {
            return CompletableFuture.failedFuture(
                new SQLException("Database connection is not valid")
            );
        }
        
        QueryBuilder query = queryBuilder.raw(
            "DELETE e FROM lore_entry e " +
            "JOIN lore_submission s ON e.id = s.entry_id " +
            "WHERE e.entry_type = ? AND s.content LIKE ?",
            "PLAYER", "%\"player_uuid\":\"" + uuid.toString() + "\"%"
        );
        
        return queryExecutor.executeUpdate(query)
            .thenApply(rowsAffected -> rowsAffected > 0)
            .exceptionally(e -> {
                logger.error("Error deleting player: " + uuid, e);
                return false;
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
     * Gets the current player name stored in the database.
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
     * Gets player lore entries by type.
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

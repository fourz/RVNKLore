package org.fourz.RVNKLore.data;

import java.util.List;
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
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;

    public PlayerRepository(RVNKLore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.logger = LogManager.getInstance(plugin, "PlayerRepository");
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
     * Saves a player entry (insert or update).
     */
    public CompletableFuture<String> savePlayer(PlayerDTO dto) {
        return databaseManager.savePlayer(dto)
            .exceptionally(e -> {
                logger.error("Error saving player: " + dto.getPlayerUuid(), e);
                return null;
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

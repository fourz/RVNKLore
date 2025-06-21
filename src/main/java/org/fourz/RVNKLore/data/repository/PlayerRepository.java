package org.fourz.RVNKLore.data.repository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseManager;
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
public class PlayerRepository {    private final LogManager logger;
    private final QueryBuilder queryBuilder;
    private final DefaultQueryExecutor queryExecutor;

    public PlayerRepository(RVNKLore plugin, DatabaseManager databaseManager) {
        this.logger = LogManager.getInstance(plugin, "PlayerRepository");
        this.queryBuilder = databaseManager.getQueryBuilder();
        this.queryExecutor = databaseManager.getQueryExecutor();
    }

    /**
     * Get a player by UUID.
     *
     * @param uuid The UUID of the player
     * @return A future containing the PlayerDTO, or null if not found
     */
    public CompletableFuture<PlayerDTO> getPlayerByUuid(UUID uuid) {
        QueryBuilder query = queryBuilder.select("*")
            .from("player")
            .where("uuid = ?", uuid.toString());
        
        return queryExecutor.executeQuery(query, PlayerDTO.class);
    }

    /**
     * Get players by name.
     *
     * @param name The name of the player
     * @return A future containing a list of PlayerDTOs
     */
    public CompletableFuture<List<PlayerDTO>> getPlayersByName(String name) {
        QueryBuilder query = queryBuilder.select("*")
            .from("player")
            .where("name = ?", name);
        
        return queryExecutor.executeQueryList(query, PlayerDTO.class);
    }

    /**
     * Save a player.
     *
     * @param dto The PlayerDTO to save
     * @return A future containing the saved player UUID
     */
    public CompletableFuture<UUID> savePlayer(PlayerDTO dto) {
        QueryBuilder query = queryBuilder.insert("player", false)
            .set("uuid", dto.getPlayerUuid().toString())
            .set("name", dto.getPlayerName())
            .set("metadata", dto.getMetadata());
        
        return queryExecutor.executeUpdate(query)
            .thenApply(rows -> dto.getPlayerUuid());
    }

    /**
     * Delete a player by UUID.
     *
     * @param uuid The UUID of the player to delete
     * @return A future containing true if the player was deleted, false otherwise
     */
    public CompletableFuture<Boolean> deletePlayer(UUID uuid) {
        QueryBuilder query = queryBuilder.deleteFrom("player")
            .where("uuid = ?", uuid.toString());
        
        return queryExecutor.executeUpdate(query)
            .thenApply(rows -> rows > 0);
    }

    /**
     * Save player metadata (insert or update player entry in lore_entry table).
     *
     * @param dto The PlayerDTO containing player data
     * @return CompletableFuture<Boolean> indicating success
     */
    public CompletableFuture<Boolean> savePlayerMetadata(PlayerDTO dto) {
        QueryBuilder query = queryBuilder.insert("lore_entry", false)
            .set("entry_type", "PLAYER")
            .set("name", dto.getPlayerName())
            .set("metadata", dto.getMetadata())
            .set("is_approved", true)
            .set("uuid", dto.getPlayerUuid().toString());
            
        return queryExecutor.executeUpdate(query)
            .thenApply(rows -> rows > 0);
    }

    /**
     * Check if a player exists by UUID.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture<Boolean> indicating existence
     */
    public CompletableFuture<Boolean> playerExists(UUID playerUuid) {
        QueryBuilder query = queryBuilder.select("COUNT(*) as count")
            .from("lore_entry")
            .where("entry_type = ? AND uuid = ?", "PLAYER", playerUuid.toString());
            
        return queryExecutor.executeQuery(query, PlayerDTO.class)
            .thenApply(dto -> dto != null);
    }

    /**
     * Get the stored player name for a UUID.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture<String> with the player name or null
     */
    public CompletableFuture<String> getStoredPlayerName(UUID playerUuid) {
        QueryBuilder query = queryBuilder.select("name")
            .from("lore_entry")
            .where("entry_type = ? AND uuid = ?", "PLAYER", playerUuid.toString());
            
        return queryExecutor.executeQuery(query, PlayerDTO.class)
            .thenApply(dto -> dto != null ? dto.getPlayerName() : null);
    }

    /**
     * Get all lore entries for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture<List<PlayerDTO>>
     */
    public CompletableFuture<List<PlayerDTO>> getPlayerLoreEntries(UUID playerUuid) {
        QueryBuilder query = queryBuilder.select("*")
            .from("lore_entry")
            .where("entry_type = ? AND uuid = ?", "PLAYER", playerUuid.toString());
            
        return queryExecutor.executeQueryList(query, PlayerDTO.class);
    }

    /**
     * Get player lore entries by type.
     *
     * @param playerUuid The player's UUID
     * @param entryType The entry type
     * @return CompletableFuture<List<PlayerDTO>>
     */
    public CompletableFuture<List<PlayerDTO>> getPlayerLoreEntriesByType(UUID playerUuid, String entryType) {
        QueryBuilder query = queryBuilder.select("*")
            .from("lore_entry")
            .where("entry_type = ? AND uuid = ? AND entry_type = ?", "PLAYER", playerUuid.toString(), entryType);
            
        return queryExecutor.executeQueryList(query, PlayerDTO.class);
    }

    /**
     * Get name change history for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture<List<NameChangeRecordDTO>>
     */
    public CompletableFuture<List<NameChangeRecordDTO>> getNameChangeHistory(UUID playerUuid) {
        QueryBuilder query = queryBuilder.select("*")
            .from("name_change_record")
            .where("player_uuid = ?", playerUuid.toString())
            .orderBy("changed_at", false);
            
        return queryExecutor.executeQueryList(query, NameChangeRecordDTO.class);
    }
}

package org.fourz.RVNKLore.lore.player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.NameChangeRecordDTO;
import org.fourz.RVNKLore.data.dto.PlayerDTO;
import org.fourz.RVNKLore.data.query.QueryBuilder;
import org.fourz.RVNKLore.debug.LogManager;

/**
 * Repository for player-related database operations
 * 
 * This repository manages player lore entries, name changes, and provides
 * utilities for checking player existence and history in the lore system.
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
     * Check if a player already has a lore entry in the database
     * 
     * @param playerUuid The UUID of the player to check
     * @return Future containing true if the player exists, false otherwise
     */
    public CompletableFuture<Boolean> playerExists(UUID playerUuid) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("COUNT(*)")
            .from("lore_submission s")
            .join("lore_entry e", "e.id = s.entry_id")
            .where("e.entry_type = ?", "PLAYER")
            .and("s.is_current_version = TRUE")
            .and("s.content LIKE ?", "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
            
        return databaseManager.getQueryExecutor()
            .executeQuery(query, Long.class)
            .thenApply(count -> count != null && count > 0)
            .exceptionally(e -> {
                logger.error("Error checking if player exists: " + playerUuid, e);
                return false;
            });
    }
    
    /**
     * Get the current player name stored in the database for a given player UUID
     * 
     * @param playerUuid The UUID of the player
     * @return Future containing the stored name, or null if not found
     */
    public CompletableFuture<String> getStoredPlayerName(UUID playerUuid) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("s.content", "s.created_at", "s.entry_id")
            .from("lore_submission s")
            .join("lore_entry e", "e.id = s.entry_id")
            .where("e.entry_type = ?", "PLAYER")
            .and("s.is_current_version = TRUE")
            .and("s.content LIKE ?", "%\"player_uuid\":\"" + playerUuid.toString() + "\"%")
            .and("s.content LIKE ?", "%\"entry_type\":\"player_character\"%")
            .orderBy("s.created_at", false)
            .limit(1);
            
        return databaseManager.getQueryExecutor()
            .executeQuery(query, PlayerDTO.class)
            .thenApply(dto -> dto != null ? dto.getPlayerName() : null)
            .exceptionally(e -> {
                logger.error("Error getting stored player name: " + playerUuid, e);
                return null;
            });
    }
    
    /**
     * Get all lore entries associated with a player
     * 
     * @param playerUuid The UUID of the player
     * @return Future containing list of player DTOs
     */
    public CompletableFuture<List<PlayerDTO>> getPlayerLoreEntries(UUID playerUuid) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("s.content", "s.created_at", "s.entry_id")
            .from("lore_entry e")
            .join("lore_submission s", "e.id = s.entry_id")
            .where("e.entry_type = ?", "PLAYER")
            .and("s.is_current_version = TRUE")
            .and("s.content LIKE ?", "%\"player_uuid\":\"" + playerUuid.toString() + "\"%")
            .orderBy("s.created_at", false);
            
        return databaseManager.getQueryExecutor()
            .executeQueryList(query, PlayerDTO.class)
            .exceptionally(e -> {
                logger.error("Error getting player lore entries: " + playerUuid, e);
                return new ArrayList<>();
            });
    }
    
    /**
     * Get player lore entries by type (FIRST_JOIN, PLAYER_CHARACTER, NAME_CHANGE)
     * 
     * @param playerUuid The UUID of the player
     * @param entryType The type of entry to filter by
     * @return Future containing list of player DTOs
     */
    public CompletableFuture<List<PlayerDTO>> getPlayerLoreEntriesByType(UUID playerUuid, String entryType) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("s.content", "s.created_at", "s.entry_id")
            .from("lore_entry e")
            .join("lore_submission s", "e.id = s.entry_id")
            .where("e.entry_type = ?", "PLAYER")
            .and("s.is_current_version = TRUE")
            .and("s.content LIKE ?", "%\"player_uuid\":\"" + playerUuid.toString() + "\"%")
            .and("s.content LIKE ?", "%\"entry_type\":\"" + entryType + "\"%")
            .orderBy("s.created_at", false);
            
        return databaseManager.getQueryExecutor()
            .executeQueryList(query, PlayerDTO.class)
            .exceptionally(e -> {
                logger.error("Error getting player lore entries by type: " + playerUuid + ", " + entryType, e);
                return new ArrayList<>();
            });
    }
    
    /**
     * Save a player entry
     * 
     * @param dto The player DTO to save
     * @return Future containing saved player ID
     */
    public CompletableFuture<String> savePlayer(PlayerDTO dto) {
        if (dto.getEntryId() != null) {
            // Update existing entry
            QueryBuilder query = databaseManager.getQueryBuilder()
                .update("lore_submission")
                .set("content", dto.getMetadata().toString()) // Convert metadata map to JSON
                .where("entry_id = ?", dto.getEntryId())
                .and("is_current_version = TRUE");
                
            return databaseManager.getQueryExecutor()
                .executeUpdate(query)
                .thenApply(rowsAffected -> dto.getEntryId())
                .exceptionally(e -> {
                    logger.error("Error updating player: " + dto.getPlayerUuid(), e);
                    return null;
                });
        } else {
            // Insert new entry using transaction
            return databaseManager.getQueryExecutor()
                .executeTransaction(conn -> {
                    // First create the lore entry
                    QueryBuilder entryQuery = databaseManager.getQueryBuilder()
                        .insertInto("lore_entry")
                        .columns("entry_type", "name", "description")
                        .values("PLAYER", 
                               "Player_" + dto.getPlayerUuid().toString(),
                               dto.getMetadata().get("description"));
                
                    String entryId = databaseManager.getQueryExecutor()
                        .executeInsert(entryQuery)
                        .thenApply(String::valueOf)
                        .join();
                        
                    // Then create the submission
                    QueryBuilder submissionQuery = databaseManager.getQueryBuilder()
                        .insertInto("lore_submission")
                        .columns("entry_id", "content", "is_current_version")
                        .values(entryId, 
                               dto.getMetadata().toString(), 
                               true);
                               
                    return databaseManager.getQueryExecutor()
                        .executeInsert(submissionQuery)
                        .thenApply(id -> entryId)
                        .join();
                })
                .exceptionally(e -> {
                    logger.error("Error creating new player entry: " + dto.getPlayerUuid(), e);
                    return null;
                });
        }
    }
    
    /**
     * Get the history of name changes for a player
     * 
     * @param playerUuid The UUID of the player
     * @return Future containing list of name change records
     */
    public CompletableFuture<List<NameChangeRecordDTO>> getNameChangeHistory(UUID playerUuid) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("s.content", "s.created_at", "s.entry_id")
            .from("lore_submission s")
            .join("lore_entry e", "e.id = s.entry_id")
            .where("e.entry_type = ?", "PLAYER")
            .and("s.is_current_version = TRUE")
            .and("s.content LIKE ?", "%\"player_uuid\":\"" + playerUuid.toString() + "\"%")
            .and("s.content LIKE ?", "%\"entry_type\":\"name_change\"%")
            .orderBy("s.created_at", false);
            
        return databaseManager.getQueryExecutor()
            .executeQueryList(query, NameChangeRecordDTO.class)
            .exceptionally(e -> {
                logger.error("Error getting name change history: " + playerUuid, e);
                return new ArrayList<>();
            });
    }
}

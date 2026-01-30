package org.fourz.RVNKLore.lore.player;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for player-related database operations.
 * Provides abstraction for player lore entries and name change history.
 */
public interface IPlayerRepository {

    /**
     * Check if a player already has a lore entry in the database.
     *
     * @param playerUuid The UUID of the player to check
     * @return true if the player has a lore entry, false otherwise
     */
    boolean playerExists(UUID playerUuid);

    /**
     * Get the current player name stored in the database for a given player UUID.
     *
     * @param playerUuid The UUID of the player
     * @return The stored player name, or null if not found
     */
    String getStoredPlayerName(UUID playerUuid);

    /**
     * Get all lore entries associated with a player.
     *
     * @param playerUuid The UUID of the player
     * @return List of lore entry IDs
     */
    List<String> getPlayerLoreEntryIds(UUID playerUuid);

    /**
     * Get player lore entries by type (FIRST_JOIN, PLAYER_CHARACTER, NAME_CHANGE).
     *
     * @param playerUuid The UUID of the player
     * @param entryType The type of entry to filter by
     * @return List of entry IDs matching the type
     */
    List<String> getPlayerLoreEntriesByType(UUID playerUuid, String entryType);

    /**
     * Check if a player has had a name change recorded.
     *
     * @param playerUuid The UUID of the player
     * @return true if the player has a name change entry, false otherwise
     */
    boolean hasNameChangeRecords(UUID playerUuid);

    /**
     * Get the history of name changes for a player.
     *
     * @param playerUuid The UUID of the player
     * @return List of previous names, from oldest to newest
     */
    List<NameChangeRecord> getNameChangeHistory(UUID playerUuid);
}

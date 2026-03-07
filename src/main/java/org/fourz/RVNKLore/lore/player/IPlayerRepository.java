package org.fourz.RVNKLore.lore.player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for player-related database operations.
 * Provides abstraction for player lore entries and name change history.
 *
 * All methods return CompletableFuture<T> for async operations per RVNKCore standard.
 */
public interface IPlayerRepository {

    /**
     * Check if a player already has a lore entry in the database.
     *
     * @param playerUuid The UUID of the player to check
     * @return CompletableFuture that completes with true if the player has a lore entry, false otherwise
     */
    CompletableFuture<Boolean> playerExists(UUID playerUuid);

    /**
     * Get the current player name stored in the database for a given player UUID.
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture that completes with Optional containing the stored player name, or empty if not found
     */
    CompletableFuture<Optional<String>> getStoredPlayerName(UUID playerUuid);

    /**
     * Get all lore entries associated with a player.
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture that completes with a list of lore entry IDs
     */
    CompletableFuture<List<String>> getPlayerLoreEntryIds(UUID playerUuid);

    /**
     * Get player lore entries by type (FIRST_JOIN, PLAYER_CHARACTER, NAME_CHANGE).
     *
     * @param playerUuid The UUID of the player
     * @param entryType The type of entry to filter by
     * @return CompletableFuture that completes with a list of entry IDs matching the type
     */
    CompletableFuture<List<String>> getPlayerLoreEntriesByType(UUID playerUuid, String entryType);

    /**
     * Check if a player has had a name change recorded.
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture that completes with true if the player has a name change entry, false otherwise
     */
    CompletableFuture<Boolean> hasNameChangeRecords(UUID playerUuid);

    /**
     * Get the history of name changes for a player.
     *
     * @param playerUuid The UUID of the player
     * @return CompletableFuture that completes with a list of previous names, from oldest to newest
     */
    CompletableFuture<List<NameChangeRecord>> getNameChangeHistory(UUID playerUuid);

    /**
     * Record that a player has discovered a lore entry.
     *
     * @param playerUuid The UUID of the player
     * @param entryId The ID of the lore entry discovered
     * @return CompletableFuture that completes with true if recorded successfully
     */
    CompletableFuture<Boolean> recordLoreDiscovery(UUID playerUuid, String entryId);

    /**
     * Check if the repository is operating in fallback mode.
     * Fallback mode indicates degraded operation due to database connectivity issues.
     *
     * @return true if in fallback mode, false otherwise
     */
    boolean isInFallbackMode();
}

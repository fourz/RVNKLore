package org.fourz.RVNKLore.service;

import org.fourz.RVNKLore.lore.player.NameChangeRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for player-related lore operations.
 * Exposes player lore functionality for cross-plugin access via RVNKCore ServiceRegistry.
 *
 * <p>This service provides access to player lore entries, name change history,
 * and player existence checks without requiring direct database access.</p>
 *
 * @since RVNKCore Integration
 */
public interface IPlayerService {

    /**
     * Check if a player already has a lore entry in the system.
     *
     * @param playerId The UUID of the player to check
     * @return Future containing true if the player has a lore entry, false otherwise
     */
    CompletableFuture<Boolean> hasPlayer(UUID playerId);

    /**
     * Get the current player name stored in the lore system.
     *
     * @param playerId The UUID of the player
     * @return Future containing Optional with the stored player name, or empty if not found
     */
    CompletableFuture<Optional<String>> getPlayerName(UUID playerId);

    /**
     * Get the history of name changes for a player.
     *
     * @param playerId The UUID of the player
     * @return Future containing list of name change records, from oldest to newest
     */
    CompletableFuture<List<NameChangeRecord>> getNameChangeHistory(UUID playerId);

    /**
     * Get all lore entry IDs associated with a player.
     *
     * @param playerId The UUID of the player
     * @return Future containing list of lore entry IDs
     */
    CompletableFuture<List<String>> getPlayerLoreEntryIds(UUID playerId);

    /**
     * Get player lore entries filtered by type.
     *
     * @param playerId The UUID of the player
     * @param entryType The type of entry to filter by (e.g., "first_join", "name_change", "player_character")
     * @return Future containing list of entry IDs matching the type
     */
    CompletableFuture<List<String>> getPlayerLoreEntriesByType(UUID playerId, String entryType);

    /**
     * Check if the service is in fallback mode due to database connectivity issues.
     * Fallback mode indicates degraded operation.
     *
     * @return true if operating in degraded mode, false otherwise
     */
    boolean isInFallbackMode();
}

package org.fourz.RVNKLore.service;

import org.bukkit.Location;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for lore entry operations.
 * Exposes core lore functionality for cross-plugin access via RVNKCore ServiceRegistry.
 */
public interface ILoreService {

    /**
     * Get a lore entry by its unique ID.
     *
     * @param id The UUID of the lore entry
     * @return Future containing the lore entry, or empty if not found
     */
    CompletableFuture<Optional<LoreEntry>> getLoreEntry(UUID id);

    /**
     * Get a lore entry by its name.
     *
     * @param name The name of the lore entry
     * @return Future containing the lore entry, or empty if not found
     */
    CompletableFuture<Optional<LoreEntry>> getLoreEntryByName(String name);

    /**
     * Get all lore entries of a specific type.
     *
     * @param type The lore type to filter by
     * @return Future containing list of matching lore entries
     */
    CompletableFuture<List<LoreEntry>> getLoreEntriesByType(LoreType type);

    /**
     * Get all approved lore entries.
     *
     * @return Future containing list of approved lore entries
     */
    CompletableFuture<List<LoreEntry>> getApprovedLoreEntries();

    /**
     * Get all lore entries.
     *
     * @return Future containing list of all lore entries
     */
    CompletableFuture<List<LoreEntry>> getAllLoreEntries();

    /**
     * Add a new lore entry.
     *
     * @param entry The lore entry to add
     * @return Future containing true if successful
     */
    CompletableFuture<Boolean> addLoreEntry(LoreEntry entry);

    /**
     * Approve a lore entry.
     *
     * @param id The UUID of the lore entry to approve
     * @return Future containing true if successful
     */
    CompletableFuture<Boolean> approveLoreEntry(UUID id);

    /**
     * Find lore entries near a location.
     *
     * @param location The center location
     * @param radius The search radius in blocks
     * @return Future containing list of nearby lore entries
     */
    CompletableFuture<List<LoreEntry>> findNearbyLoreEntries(Location location, double radius);

    /**
     * Search for lore entries by name fragment.
     *
     * @param nameFragment The partial name to search for
     * @return Future containing list of matching lore entries
     */
    CompletableFuture<List<LoreEntry>> findLoreEntries(String nameFragment);

    /**
     * Check if the service is in fallback mode due to errors.
     *
     * @return true if operating in degraded mode
     */
    boolean isInFallbackMode();
}

package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for Lore Entry database operations.
 * Provides abstraction for CRUD operations on lore entries.
 *
 * All methods return CompletableFuture<T> for async operations per RVNKCore standard.
 */
public interface ILoreEntryRepository {

    /**
     * Add a new lore entry to the database.
     *
     * @param entry The lore entry to add
     * @return CompletableFuture that completes with true if successful, false otherwise
     */
    CompletableFuture<Boolean> addLoreEntry(LoreEntry entry);

    /**
     * Update an existing lore entry.
     *
     * @param entry The lore entry to update
     * @return CompletableFuture that completes with true if successful, false otherwise
     */
    CompletableFuture<Boolean> updateLoreEntry(LoreEntry entry);

    /**
     * Delete a lore entry by ID.
     *
     * @param id The UUID of the entry to delete
     * @return CompletableFuture that completes with true if successful, false otherwise
     */
    CompletableFuture<Boolean> deleteLoreEntry(UUID id);

    /**
     * Get a lore entry by its ID.
     *
     * @param id The ID of the lore entry to retrieve
     * @return CompletableFuture that completes with Optional containing the lore entry, or empty if not found
     */
    CompletableFuture<Optional<LoreEntry>> getLoreEntryById(String id);

    /**
     * Get all lore entries.
     *
     * @return CompletableFuture that completes with a list of all lore entries
     */
    CompletableFuture<List<LoreEntry>> getAllLoreEntries();

    /**
     * Get lore entries by type.
     *
     * @param type The type of lore entries to retrieve
     * @return CompletableFuture that completes with a list of matching lore entries
     */
    CompletableFuture<List<LoreEntry>> getLoreEntriesByType(LoreType type);

    /**
     * Search lore entries by keyword.
     *
     * @param keyword The keyword to search for
     * @return CompletableFuture that completes with a list of matching lore entries
     */
    CompletableFuture<List<LoreEntry>> searchLoreEntries(String keyword);

    /**
     * Get the total count of lore entries.
     *
     * @return CompletableFuture that completes with the count of entries
     */
    CompletableFuture<Integer> getEntryCount();

    /**
     * Get all submissions for a lore entry.
     *
     * @param entryId The ID of the lore entry
     * @return CompletableFuture that completes with a list of submission metadata
     */
    CompletableFuture<List<Map<String, Object>>> getLoreSubmissions(String entryId);

    /**
     * Approve a lore entry.
     *
     * @param entryId The ID of the entry to approve
     * @param approvedBy The UUID of the approver
     * @return CompletableFuture that completes with true if successful, false otherwise
     */
    CompletableFuture<Boolean> approveLoreEntry(String entryId, String approvedBy);

    /**
     * Reject a lore entry (sets approval_status = 'REJECTED').
     *
     * @param entryId The ID of the entry to reject
     * @return CompletableFuture that completes with true if successful, false otherwise
     */
    CompletableFuture<Boolean> rejectLoreEntry(String entryId);

    /**
     * Check if the repository is operating in fallback mode.
     * Fallback mode indicates degraded operation due to database connectivity issues.
     *
     * @return true if in fallback mode, false otherwise
     */
    boolean isInFallbackMode();
}

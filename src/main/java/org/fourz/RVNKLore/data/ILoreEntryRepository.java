package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repository interface for Lore Entry database operations.
 * Provides abstraction for CRUD operations on lore entries.
 */
public interface ILoreEntryRepository {

    /**
     * Add a new lore entry to the database.
     *
     * @param entry The lore entry to add
     * @return true if successful, false otherwise
     */
    boolean addLoreEntry(LoreEntry entry);

    /**
     * Update an existing lore entry.
     *
     * @param entry The lore entry to update
     * @return true if successful, false otherwise
     */
    boolean updateLoreEntry(LoreEntry entry);

    /**
     * Delete a lore entry by ID.
     *
     * @param id The UUID of the entry to delete
     * @return true if successful, false otherwise
     */
    boolean deleteLoreEntry(UUID id);

    /**
     * Get a lore entry by its ID.
     *
     * @param id The ID of the lore entry to retrieve
     * @return The lore entry, or null if not found
     */
    LoreEntry getLoreEntryById(String id);

    /**
     * Get all lore entries.
     *
     * @return A list of all lore entries
     */
    List<LoreEntry> getAllLoreEntries();

    /**
     * Get lore entries by type.
     *
     * @param type The type of lore entries to retrieve
     * @return A list of matching lore entries
     */
    List<LoreEntry> getLoreEntriesByType(LoreType type);

    /**
     * Search lore entries by keyword.
     *
     * @param keyword The keyword to search for
     * @return A list of matching lore entries
     */
    List<LoreEntry> searchLoreEntries(String keyword);

    /**
     * Get the total count of lore entries.
     *
     * @return The count of entries
     */
    int getEntryCount();

    /**
     * Get all submissions for a lore entry.
     *
     * @param entryId The ID of the lore entry
     * @return A list of submission metadata
     */
    List<Map<String, Object>> getLoreSubmissions(String entryId);

    /**
     * Approve a lore entry.
     *
     * @param entryId The ID of the entry to approve
     * @param approvedBy The UUID of the approver
     * @return true if successful, false otherwise
     */
    boolean approveLoreEntry(String entryId, String approvedBy);

    /**
     * Check if the repository is operating in fallback mode.
     * Fallback mode indicates degraded operation due to database connectivity issues.
     *
     * @return true if in fallback mode, false otherwise
     */
    boolean isInFallbackMode();
}

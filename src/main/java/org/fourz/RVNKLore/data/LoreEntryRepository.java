package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.data.dto.LoreSubmissionDTO;
import org.fourz.RVNKLore.lore.LoreEntry;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import java.sql.Timestamp;
import org.json.simple.JSONObject;
import org.bukkit.Location;

/**
 * Repository for Lore Entry database operations
 * 
 * This repository manages the creation, retrieval, and updating of lore entries
 * using the lore_entry, lore_submission, and specialized tables (e.g., lore_item).
 * All operations are asynchronous using CompletableFuture.
 */
public class LoreEntryRepository {
    protected final RVNKLore plugin; // Used by subclasses
    private final LogManager logger;
    private final DatabaseManager databaseManager;

    public LoreEntryRepository(RVNKLore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreEntryRepository");
        this.databaseManager = databaseManager;
    }

    /**
     * Add a new lore entry to the database asynchronously with submission and specialized records.
     *
     * @param entryDto The lore entry DTO to add
     * @return CompletableFuture of true if successful, false otherwise
     */
    public CompletableFuture<Boolean> addLoreEntry(LoreEntryDTO entryDto) {
        if (entryDto == null) {
            logger.error("Cannot add null LoreEntryDTO", null);
            return CompletableFuture.completedFuture(false);
        }
        return databaseManager.saveLoreEntry(entryDto)
            .thenApply(id -> id > 0)
            .exceptionally(e -> {
                logger.error("Error saving lore entry DTO", e);
                return false;
            });
    }

    /**
     * Update a lore entry asynchronously.
     *
     * @param entryDto The lore entry DTO to update
     * @return CompletableFuture of true if successful, false otherwise
     */
    public CompletableFuture<Boolean> updateLoreEntry(LoreEntryDTO entryDto) {
        if (entryDto == null) {
            logger.error("Cannot update null LoreEntryDTO", null);
            return CompletableFuture.completedFuture(false);
        }
        return databaseManager.saveLoreEntry(entryDto)
            .thenApply(id -> id > 0)
            .exceptionally(e -> {
                logger.error("Error updating lore entry DTO", e);
                return false;
            });
    }

    /**
     * Get a lore entry by database ID (async).
     *
     * @param id The database ID
     * @return CompletableFuture of LoreEntryDTO or null
     */
    public CompletableFuture<LoreEntryDTO> getLoreEntryById(int id) {
        return databaseManager.getLoreEntry(id);
    }

    /**
     * Get all lore entries (async).
     *
     * @return CompletableFuture with a list of LoreEntryDTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> getAllLoreEntries() {
        return databaseManager.getAllLoreEntries();
    }

    /**
     * Search lore entries by keyword in name or description asynchronously
     *
     * @param keyword The keyword to search for
     * @return CompletableFuture with a list of matching lore entries as DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> searchLoreEntries(String keyword) {
        return databaseManager.searchLoreEntries(keyword);
    }

    /**
     * Delete a lore entry by database ID (async).
     *
     * @param id The database ID
     * @return CompletableFuture of true if deleted, false otherwise
     */
    public CompletableFuture<Boolean> deleteLoreEntry(int id) {
        return databaseManager.deleteLoreEntry(id);
    }

    /**
     * Get the total count of lore entries asynchronously
     *
     * @return CompletableFuture containing the count of all lore entries
     */
    public CompletableFuture<Integer> getEntryCount() {
        return getAllLoreEntries().thenApply(entries -> entries != null ? entries.size() : 0);
    }
}

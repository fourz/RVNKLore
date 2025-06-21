package org.fourz.RVNKLore.data.repository;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.data.dto.LoreSubmissionDTO;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.bukkit.Location;
import org.fourz.RVNKLore.data.query.QueryBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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
        return saveLoreEntry(entryDto)
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
        return saveLoreEntry(entryDto)
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
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from("lore_entry")
            .where("id = ?", id);
        
        return databaseManager.getQueryExecutor().executeQuery(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error retrieving lore entry with ID: " + id, e);
                return null;
            });
    }

    /**
     * Get all lore entries (async).
     *
     * @return CompletableFuture with a list of LoreEntryDTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> getAllLoreEntries() {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from("lore_entry")
            .orderBy("created_at", false);
        
        return databaseManager.getQueryExecutor().executeQueryList(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error retrieving all lore entries", e);
                return new ArrayList<>();
            });
    }

    /**
     * Get lore entries by type (async).
     *
     * @param type The type of lore entries to retrieve
     * @return CompletableFuture with a list of LoreEntryDTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> getLoreEntriesByType(String type) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from("lore_entry")
            .where("entry_type = ?", type)
            .orderBy("created_at", false);
        
        return databaseManager.getQueryExecutor().executeQueryList(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error retrieving lore entries of type: " + type, e);
                return new ArrayList<>();
            });
    }

    /**
     * Save a lore entry (insert or update).
     *
     * @param dto The lore entry DTO to save
     * @return CompletableFuture of saved lore entry ID
     */
    public CompletableFuture<Integer> saveLoreEntry(LoreEntryDTO dto) {
        if (dto == null) {
            logger.error("Cannot save null LoreEntryDTO", null);
            return CompletableFuture.completedFuture(-1);
        }
        
        // Handle update case
        if (dto.getId() > 0) {
            QueryBuilder query = databaseManager.getQueryBuilder()
                .update("lore_entry")
                .set("entry_type", dto.getEntryType())
                .set("name", dto.getName())
                .set("description", dto.getDescription())
                .where("id = ?", dto.getId());
            
            return databaseManager.getQueryExecutor().executeUpdate(query)
                .thenApply(rowsAffected -> rowsAffected > 0 ? dto.getId() : -1)
                .exceptionally(e -> {
                    logger.error("Error updating lore entry with ID: " + dto.getId(), e);
                    return -1;
                });
        }
        
        // Handle insert case
        QueryBuilder query = databaseManager.getQueryBuilder()
            .insertInto("lore_entry")
            .columns("entry_type", "name", "description")
            .values(dto.getEntryType(), dto.getName(), dto.getDescription());
        
        return databaseManager.getQueryExecutor().executeInsert(query)
            .exceptionally(e -> {
                logger.error("Error inserting new lore entry", e);
                return -1;
            });
    }

    /**
     * Search lore entries by keyword in name or description asynchronously
     *
     * @param keyword The keyword to search for
     * @return CompletableFuture with a list of matching lore entries as DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> searchLoreEntries(String keyword) {
        String searchPattern = "%" + keyword + "%";
        
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from("lore_entry")
            .where("name LIKE ? OR description LIKE ?", searchPattern, searchPattern)
            .orderBy("created_at", false);
        
        return databaseManager.getQueryExecutor().executeQueryList(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error searching lore entries with keyword: " + keyword, e);
                return new ArrayList<>();
            });
    }

    /**
     * Delete a lore entry by database ID (async).
     *
     * @param id The database ID
     * @return CompletableFuture of true if deleted, false otherwise
     */
    public CompletableFuture<Boolean> deleteLoreEntry(int id) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .deleteFrom("lore_entry")
            .where("id = ?", id);
        
        return databaseManager.getQueryExecutor().executeUpdate(query)
            .thenApply(rowsAffected -> rowsAffected > 0)
            .exceptionally(e -> {
                logger.error("Error deleting lore entry with ID: " + id, e);
                return false;
            });
    }

    /**
     * Find nearby lore entries within a radius of a location.
     *
     * @param location The base location to search from
     * @param radius The radius in blocks to search within
     * @return A future containing a list of nearby lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> findNearbyLoreEntries(Location location, double radius) {
        if (location == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }            QueryBuilder query = databaseManager.getQueryBuilder()
            .select("e.*")
            .from("lore_entry e")
            .join("lore_location", "l", "e.id = l.entry_id")
            .where("l.world = ?", location.getWorld().getName())
            .where(
                "(POWER(l.x - ?, 2) + POWER(l.y - ?, 2) + POWER(l.z - ?, 2)) <= POWER(?, 2)",
                location.getX(), location.getY(), location.getZ(), radius
            );
        
        return databaseManager.getQueryExecutor().executeQueryList(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error finding nearby lore entries", e);
                return new ArrayList<>();
            });
    }

    /**
     * Find lore entries in a specific world.
     *
     * @param worldName The name of the world
     * @return A future containing a list of lore entry DTOs in the world
     */
    public CompletableFuture<List<LoreEntryDTO>> findLoreEntriesInWorld(String worldName) {        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("e.*")
            .from("lore_entry e")
            .join("lore_location", "l", "e.id = l.entry_id")
            .where("l.world = ?", worldName);
        
        return databaseManager.getQueryExecutor().executeQueryList(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error finding lore entries in world: " + worldName, e);
                return new ArrayList<>();
            });
    }

    /**
     * Find pending (unapproved) lore entries.
     *
     * @return A future containing a list of pending lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> findPendingLoreEntries() {        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("e.*")
            .from("lore_entry e")
            .join("lore_submission", "s", "e.id = s.entry_id")
            .where("s.approval_status = ?", "PENDING")
            .orderBy("s.submission_date", false);
        
        return databaseManager.getQueryExecutor().executeQueryList(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error finding pending lore entries", e);
                return new ArrayList<>();
            });
    }

    /**
     * Find recent lore entries, sorted by creation date.
     *
     * @param count The maximum number of entries to return
     * @return A future containing a list of recent lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> findRecentLoreEntries(int count) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from("lore_entry")
            .orderBy("created_at", false)
            .limit(count);
        
        return databaseManager.getQueryExecutor().executeQueryList(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error finding recent lore entries", e);
                return new ArrayList<>();
            });
    }

    /**
     * Get the total count of lore entries asynchronously
     *
     * @return CompletableFuture containing the count of all lore entries
     */    public CompletableFuture<Integer> getEntryCount() {
        // Use the getAllLoreEntries method and count the results
        return databaseManager.getAllLoreEntries()
            .thenApply(entries -> entries != null ? entries.size() : 0)
            .exceptionally(e -> {
                logger.error("Error getting entry count", e);
                return 0;
            });
    }

    /**
     * Find lore entries by submitter name.
     *
     * @param submitter The name of the submitter
     * @return A future containing a list of lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> findLoreEntriesBySubmitter(String submitter) {        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("e.*")
            .from("lore_entry e")
            .join("lore_submission", "s", "e.id = s.entry_id")
            .where("s.created_by = ?", submitter)
            .orderBy("s.submission_date", false);
        
        return databaseManager.getQueryExecutor().executeQueryList(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error finding lore entries by submitter: " + submitter, e);
                return new ArrayList<>();
            });
    }

    /**
     * Get a lore entry by UUID.
     *
     * @param uuid The UUID of the lore entry
     * @return A future containing the lore entry DTO, or null if not found
     */
    public CompletableFuture<LoreEntryDTO> getLoreEntryById(UUID uuid) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from("lore_entry")
            .where("uuid = ?", uuid.toString());
        
        return databaseManager.getQueryExecutor().executeQuery(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error finding lore entry with UUID: " + uuid, e);
                return null;
            });
    }
}

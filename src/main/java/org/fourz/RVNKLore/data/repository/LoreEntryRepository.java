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
    private final QueryBuilder queryBuilder;
    private final org.fourz.RVNKLore.data.query.DefaultQueryExecutor queryExecutor;

    public LoreEntryRepository(RVNKLore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreEntryRepository");
        this.databaseManager = databaseManager;
        this.queryBuilder = databaseManager.getQueryBuilder();
        this.queryExecutor = databaseManager.getQueryExecutor();
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
     * Get all lore entries by type and approval status.
     *
     * @param type The type of lore entries to retrieve
     * @param approved Whether to retrieve only approved entries
     * @return A CompletableFuture containing a list of matching lore entries
     */
    public CompletableFuture<List<LoreEntryDTO>> getLoreEntriesByTypeAndApproved(String type, boolean approved) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from("lore_entry")
            .where("entry_type = ? AND approved = ?", type, approved)
            .orderBy("created_at", false);
        return databaseManager.getQueryExecutor().executeQueryList(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error retrieving lore entries by type and approved: " + type + ", " + approved, e);
                return new ArrayList<>();
            });
    }

    /**
     * Search lore entries by text in their name or description.
     *
     * @param searchText The text to search for
     * @return A future containing a list of matching lore entry DTOs
     */
    public CompletableFuture<List<LoreEntryDTO>> searchLoreEntries(String searchText) {
        String pattern = "%" + searchText + "%";
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from("lore_entry")
            .where("name LIKE ? OR description LIKE ?", pattern, pattern)
            .orderBy("created_at", false);
        return databaseManager.getQueryExecutor().executeQueryList(query, LoreEntryDTO.class)
            .exceptionally(e -> {
                logger.error("Error searching lore entries: " + searchText, e);
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
        }
        QueryBuilder query = databaseManager.getQueryBuilder()
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
    public CompletableFuture<List<LoreEntryDTO>> findLoreEntriesInWorld(String worldName) {
        QueryBuilder query = databaseManager.getQueryBuilder()
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
    public CompletableFuture<List<LoreEntryDTO>> findPendingLoreEntries() {
        QueryBuilder query = databaseManager.getQueryBuilder()
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
    public CompletableFuture<List<LoreEntryDTO>> findLoreEntriesBySubmitter(String submitter) {
        QueryBuilder query = databaseManager.getQueryBuilder()
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

    /**
     * Save a lore entry to the database.
     *
     * @param dto The LoreEntryDTO to save
     * @return A CompletableFuture containing the saved lore entry ID
     */
    public CompletableFuture<Integer> saveLoreEntry(LoreEntryDTO dto) {
        // Use QueryBuilder insert for upsert/insert logic
        QueryBuilder query = queryBuilder.insert("lore_entry", true)
            .set("uuid", dto.getUuid() != null ? dto.getUuid().toString() : null)
            .set("entry_type", dto.getEntryType())
            .set("name", dto.getName())
            .set("description", dto.getDescription())
            .set("metadata", dto.getMetadata())
            .set("is_approved", dto.isApproved())
            .set("submitted_by", dto.getSubmittedBy())
            .set("world", dto.getWorld())
            .set("x", dto.getX())
            .set("y", dto.getY())
            .set("z", dto.getZ())
            .set("created_at", dto.getCreatedAt())
            .set("updated_at", dto.getUpdatedAt());

        return queryExecutor.executeInsert(query)
            .thenApply(id -> id != null ? id : -1)
            .exceptionally(e -> {
                logger.error("Error saving lore entry", e);
                return -1;
            });
    }
}

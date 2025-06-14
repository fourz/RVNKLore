package org.fourz.RVNKLore.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.sql.*;
import java.util.*;

/**
 * Repository for Lore Entry database operations
 * 
 * This repository manages the creation, retrieval, and updating of lore entries
 * using the lore_entry, lore_submission, and specialized tables (e.g., lore_item).
 */
public class LoreEntryRepository {
    @SuppressWarnings("unused")
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseConnection dbConnection;
    private final JSONParser jsonParser;
    
    public LoreEntryRepository(RVNKLore plugin, DatabaseConnection dbConnection) {
        this.plugin = plugin;
        this.dbConnection = dbConnection;
        this.logger = LogManager.getInstance(plugin, "LoreEntryRepository");
        this.jsonParser = new JSONParser();
    }
    
    /**
     * Add a new lore entry to the database with submission and specialized records.
     * 
     * This method inserts data in three steps within a single transaction:
     * 1. Inserts the base lore_entry record
     * 2. Inserts specialized type-specific record (e.g., lore_item for ITEM type)
     * 3. Creates the initial lore_submission version record
     *
     * @param entry The lore entry to add
     * @return true if successful, false otherwise
     */
    public boolean addLoreEntry(LoreEntry entry) {
        try {
            Connection conn = dbConnection.getConnection();
            conn.setAutoCommit(false);
            
            try {
                // Step 1: Insert base lore_entry record
                String entryId = insertLoreEntry(entry, conn);
                if (entryId == null) {
                    throw new SQLException("Failed to insert base lore entry record");
                }
                
                // Step 2: Insert specialized type-specific record if applicable
                if (entry.getType() == LoreType.ITEM) {
                    boolean itemInserted = insertLoreItem(entryId, entry, conn);
                    if (!itemInserted) {
                        throw new SQLException("Failed to insert lore item record");
                    }
                }
                // TODO: Add other specialized type handlers here
                
                // Step 3: Create initial submission version
                boolean submissionCreated = insertLoreSubmission(entryId, entry, conn);
                if (!submissionCreated) {
                    throw new SQLException("Failed to create initial submission record");
                }
                
                // All operations succeeded, commit transaction
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                logger.error("Failed to add lore entry to database", e);
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Transaction error when adding lore entry", e);
            return false;
        }
    }
    
    /**
     * Insert a base lore entry record
     *
     * @param entry The lore entry to insert
     * @param conn  The database connection
     * @return The generated entry ID string, or null if insert failed
     * @throws SQLException If a database error occurs
     */
    private String insertLoreEntry(LoreEntry entry, Connection conn) throws SQLException {
        // Use provided UUID string as primary key
        String sql = "INSERT INTO lore_entry (id, entry_type, name) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entry.getId());
            stmt.setString(2, entry.getType().name());
            stmt.setString(3, entry.getName());
            int affected = stmt.executeUpdate();
            return affected > 0 ? entry.getId() : null;
        }
    }
    
    /**
     * Insert a lore item record for entries of type ITEM
     * 
     * @param entryId The parent lore entry UUID
     * @param entry   The lore entry containing item data
     * @param conn    The database connection
     * @return true if successful, false otherwise
     * @throws SQLException If a database error occurs
     */
    private boolean insertLoreItem(String entryId, LoreEntry entry, Connection conn) throws SQLException {
        // Require material metadata for lore items
        String material = entry.getMetadata("material");
        if (material == null || material.trim().isEmpty()) {
            logger.warning("Material is required for lore item entry: " + entry.getName());
            throw new SQLException("Material is required for lore item entry");
        }
        String sql = "INSERT INTO lore_item (lore_entry_id, name, material, item_type, rarity, is_obtainable, nbt_data) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entryId);
            stmt.setString(2, entry.getName());
            stmt.setString(3, material);
            stmt.setString(4, entry.getMetadata("item_type") != null ? entry.getMetadata("item_type") : "STANDARD");
            stmt.setString(5, entry.getMetadata("rarity") != null ? entry.getMetadata("rarity") : "COMMON");
            stmt.setBoolean(6, entry.getMetadata("is_obtainable") != null ? Boolean.parseBoolean(entry.getMetadata("is_obtainable")) : true);
            stmt.setString(7, entry.getNbtData());
            // Attempt to extract more detailed properties from metadata could be added here
            return stmt.executeUpdate() > 0;
        }
    }
    
    /**
     * Insert the initial submission record for a lore entry
     * 
     * @param entryId The parent lore entry ID
     * @param entry The lore entry
     * @param conn The database connection
     * @return true if successful, false otherwise
     * @throws SQLException If a database error occurs
     */
    @SuppressWarnings("unchecked")
    private boolean insertLoreSubmission(String entryId, LoreEntry entry, Connection conn) throws SQLException {
        String sql = "INSERT INTO lore_submission (entry_id, submitter_uuid, content, slug, is_current_version) VALUES (?, ?, ?, ?, TRUE)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entryId);
            // Defensive: use "Server" if submittedBy is null or empty
            String submitter = entry.getSubmittedBy();
            if (submitter == null || submitter.trim().isEmpty()) {
                submitter = "Server";
            }
            stmt.setString(2, submitter);
            // Build content JSON manually
            JSONObject content = new JSONObject();
            content.put("description", entry.getDescription());
            content.put("nbt_data", entry.getNbtData());
            // Include location if available
            Location loc = entry.getLocation();
            if (loc != null) {
                JSONObject locJson = new JSONObject();
                locJson.put("world", loc.getWorld().getName());
                locJson.put("x", loc.getX());
                locJson.put("y", loc.getY());
                locJson.put("z", loc.getZ());
                content.put("location", locJson);
            }
            // Include metadata
            Map<String, String> metadata = entry.getAllMetadata();
            if (metadata != null) {
                for (Map.Entry<String, String> meta : metadata.entrySet()) {
                    content.put(meta.getKey(), meta.getValue());
                }
            }
            stmt.setString(3, content.toJSONString());
            // Generate and set slug
            String slug = generateSlug(entry.getName());
            stmt.setString(4, slug);
            return stmt.executeUpdate() > 0;
        }
    }
    
    // Utility to generate a URL-friendly slug from a name
    private String generateSlug(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase()
            .replaceAll("[^a-z0-9\s-]", "") // Remove invalid chars
            .replaceAll("\s+", "-")         // Replace whitespace with hyphens
            .replaceAll("-+", "-")           // Remove multiple hyphens
            .replaceAll("^-|-$", "");        // Trim leading/trailing hyphens
    }

    /**
     * Update an entry and its current submission
     * 
     * @param entry The lore entry to update
     * @return true if successful, false otherwise
     */
    public boolean updateLoreEntry(LoreEntry entry) {
        try {
            Connection conn = dbConnection.getConnection();
            conn.setAutoCommit(false);
            
            try {
                // Step 1: Update base lore_entry record
                String updateEntrySql = "UPDATE lore_entry SET name = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateEntrySql)) {
                    stmt.setString(1, entry.getName());
                    stmt.setString(2, entry.getId());
                    int rowsAffected = stmt.executeUpdate();
                    
                    if (rowsAffected == 0) {
                        throw new SQLException("Failed to update lore entry record");
                    }
                }
                
                // Step 2: Update specialized record if applicable
                if (entry.getType() == LoreType.ITEM) {
                    String updateItemSql = "UPDATE lore_item SET name = ?, nbt_data = ? WHERE lore_entry_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(updateItemSql)) {
                        stmt.setString(1, entry.getName());
                        stmt.setString(2, entry.getNbtData());
                        stmt.setString(3, entry.getId());
                        stmt.executeUpdate();
                    }
                }
                
                // Step 3: Mark existing current version as not current
                String updateCurrentSql = "UPDATE lore_submission SET is_current_version = FALSE " +
                                          "WHERE entry_id = ? AND is_current_version = TRUE";
                try (PreparedStatement stmt = conn.prepareStatement(updateCurrentSql)) {
                    stmt.setString(1, entry.getId());
                    stmt.executeUpdate();
                }
                
                // Step 4: Create new submission version
                boolean submissionCreated = insertLoreSubmission(entry.getId(), entry, conn);
                if (!submissionCreated) {
                    throw new SQLException("Failed to create new submission version");
                }
                
                // All operations succeeded, commit transaction
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                logger.error("Failed to update lore entry in database", e);
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Transaction error when updating lore entry", e);
            return false;
        }
    }
    
    /**
     * Get all lore entries from the database
     * 
     * @return A list of all lore entries
     */
    public List<LoreEntry> getAllLoreEntries() {
        List<LoreEntry> entries = new ArrayList<>();
        
        String sql = "SELECT e.id, e.entry_type, e.name, s.content, s.submitter_uuid, " +
                     "s.approval_status, s.created_at " +
                     "FROM lore_entry e " +
                     "JOIN lore_submission s ON e.id = s.entry_id " +
                     "WHERE s.is_current_version = TRUE";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                entries.add(resultSetToLoreEntry(rs, conn));
            }
        } catch (SQLException e) {
            logger.error("Error retrieving all lore entries", e);
        }
        
        return entries;
    }
    
    /**
     * Get lore entries by type
     * 
     * @param type The type of lore entries to retrieve
     * @return A list of matching lore entries
     */
    public List<LoreEntry> getLoreEntriesByType(LoreType type) {
        List<LoreEntry> entries = new ArrayList<>();
        
        String sql = "SELECT e.id, e.entry_type, e.name, s.content, s.submitter_uuid, " +
                     "s.approval_status, s.created_at " +
                     "FROM lore_entry e " +
                     "JOIN lore_submission s ON e.id = s.entry_id " +
                     "WHERE e.entry_type = ? AND s.is_current_version = TRUE";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, type.name());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(resultSetToLoreEntry(rs, conn));
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving lore entries by type: " + type, e);
        }
        
        return entries;
    }
    
    /**
     * Convert a database result set to a LoreEntry object
     * 
     * @param rs The result set containing lore entry data
     * @param conn The database connection
     * @return The populated LoreEntry object
     * @throws SQLException If a database error occurs
     */
    private LoreEntry resultSetToLoreEntry(ResultSet rs, Connection conn) throws SQLException {
        String id = rs.getString("id");
        String typeName = rs.getString("entry_type");
        String name = rs.getString("name");
        String contentJson = rs.getString("content");
        String submittedBy = rs.getString("submitter_uuid");
        boolean approved = "APPROVED".equalsIgnoreCase(rs.getString("approval_status"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        
        LoreType type;        try {
            type = LoreType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            // Default to an existing type if the enum value is not found
            type = LoreType.GENERIC;
            logger.warning("Unknown lore type: " + typeName + ", defaulting to GENERIC");
        }
        
        String description = null;
        String nbtData = null;
        Location location = null;
        
        // Parse the content JSON
        try {
            JSONObject content = (JSONObject) jsonParser.parse(contentJson);
            description = (String) content.get("description");
            nbtData = (String) content.get("nbt_data");
            
            // Extract location if available
            if (content.containsKey("location")) {
                JSONObject locJson = (JSONObject) content.get("location");
                String worldName = (String) locJson.get("world");
                World world = Bukkit.getWorld(worldName);
                
                if (world != null) {
                    double x = ((Number) locJson.get("x")).doubleValue();
                    double y = ((Number) locJson.get("y")).doubleValue();
                    double z = ((Number) locJson.get("z")).doubleValue();
                    
                    location = new Location(world, x, y, z);
                }
            }
        } catch (ParseException e) {
            logger.error("Error parsing lore entry content JSON for ID: " + id, e);
        }
        
        // Create the LoreEntry object
        LoreEntry entry = new LoreEntry(
            id,
            type,
            name,
            description,
            nbtData,
            location,
            submittedBy,
            approved,
            createdAt != null ? createdAt.toString() : null
        );
        
        return entry;
    }
    
    // Additional methods would go here
      /**
     * Delete a lore entry by ID
     * 
     * @param id The UUID of the entry to delete
     * @return true if successful, false otherwise
     */
    public boolean deleteLoreEntry(UUID id) {
        try {
            Connection conn = dbConnection.getConnection();
            conn.setAutoCommit(false);
            
            try {
                // Delete from lore_entry will cascade to lore_submission and lore_item
                // due to foreign key constraints with ON DELETE CASCADE
                String sql = "DELETE FROM lore_entry WHERE id = ?";
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, id.toString());
                    int rowsAffected = stmt.executeUpdate();
                    
                    if (rowsAffected == 0) {
                        logger.warning("No lore entry found with ID: " + id);
                        conn.rollback();
                        return false;
                    }
                    
                    conn.commit();
                    return true;
                }
            } catch (SQLException e) {
                conn.rollback();
                logger.error("Failed to delete lore entry: " + id, e);
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Transaction error when deleting lore entry: " + id, e);
            return false;
        }
    }
      /**
     * Search lore entries by keyword in name or description
     * 
     * @param keyword The keyword to search for
     * @return A list of matching lore entries
     */
    public List<LoreEntry> searchLoreEntries(String keyword) {
        List<LoreEntry> entries = new ArrayList<>();
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return entries;
        }
        
        String searchPattern = "%" + keyword.trim() + "%";
        
        String sql = "SELECT e.id, e.entry_type, e.name, s.content, s.submitter_uuid, " +
                     "s.approval_status, s.created_at " +
                     "FROM lore_entry e " +
                     "JOIN lore_submission s ON e.id = s.entry_id " +
                     "WHERE s.is_current_version = TRUE " +
                     "AND (e.name LIKE ? OR s.content LIKE ?)";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(resultSetToLoreEntry(rs, conn));
                }
            }
        } catch (SQLException e) {
            logger.error("Error searching lore entries for: " + keyword, e);
        }
        
        return entries;
    }

    public int getEntryCount() {
        int count = 0;
        String sql = "SELECT COUNT(*) FROM lore_entry";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Error counting lore entries", e);
        }
        
        return count;
    }
    
    /**
     * Get a lore entry by its ID
     * 
     * @param id The ID of the lore entry to retrieve
     * @return The lore entry, or null if not found
     */
    public LoreEntry getLoreEntryById(String id) {
        String sql = "SELECT e.id, e.entry_type, e.name, s.content, s.submitter_uuid, " +
                     "s.approval_status, s.created_at " +
                     "FROM lore_entry e " +
                     "JOIN lore_submission s ON e.id = s.entry_id " +
                     "WHERE e.id = ? AND s.is_current_version = TRUE";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return resultSetToLoreEntry(rs, conn);
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving lore entry by ID: " + id, e);
        }
        
        return null;
    }
    
    /**
     * Get all submissions for a lore entry
     * 
     * @param entryId The ID of the lore entry
     * @return A list of submission metadata (version, date, author, etc.)
     */
    public List<Map<String, Object>> getLoreSubmissions(String entryId) {
        List<Map<String, Object>> submissions = new ArrayList<>();
        
        String sql = "SELECT id, content_version, submitter_uuid, created_at, " +
                     "approval_status, is_current_version " +
                     "FROM lore_submission " +
                     "WHERE entry_id = ? " +
                     "ORDER BY content_version DESC";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, entryId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> submission = new HashMap<>();
                    submission.put("id", rs.getInt("id"));
                    submission.put("version", rs.getInt("content_version"));
                    submission.put("submitter", rs.getString("submitter_uuid"));
                    submission.put("date", rs.getTimestamp("created_at"));
                    submission.put("status", rs.getString("approval_status"));
                    submission.put("isCurrent", rs.getBoolean("is_current_version"));
                    
                    submissions.add(submission);
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving lore submissions for entry: " + entryId, e);
        }
        
        return submissions;
    }
    
    /**
     * Approve a lore entry
     * 
     * @param entryId The ID of the entry to approve
     * @param approvedBy The UUID of the player who approved the entry
     * @return true if successful, false otherwise
     */
    public boolean approveLoreEntry(String entryId, String approvedBy) {
        try {
            Connection conn = dbConnection.getConnection();
            conn.setAutoCommit(false);
            
            try {
                String sql = "UPDATE lore_submission " +
                             "SET approval_status = 'APPROVED', approved_by = ?, approved_at = CURRENT_TIMESTAMP " +
                             "WHERE entry_id = ? AND is_current_version = TRUE";
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, approvedBy);
                    stmt.setString(2, entryId);
                    
                    int rowsAffected = stmt.executeUpdate();
                    if (rowsAffected == 0) {
                        throw new SQLException("No current submission found for entry: " + entryId);
                    }
                    
                    conn.commit();
                    return true;
                }
            } catch (SQLException e) {
                conn.rollback();
                logger.error("Failed to approve lore entry: " + entryId, e);
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Transaction error when approving lore entry: " + entryId, e);
            return false;
        }
    }
}

package org.fourz.RVNKLore.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.Debug;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Repository for Lore Entry database operations
 */
public class LoreEntryRepository {
    private final RVNKLore plugin;
    private final Debug debug;
    private final DatabaseConnection dbConnection;
    
    public LoreEntryRepository(RVNKLore plugin, DatabaseConnection dbConnection) {
        this.plugin = plugin;
        this.dbConnection = dbConnection;
        this.debug = Debug.createDebugger(plugin, "LoreEntryRepository", Level.FINE);
    }
    
    /**
     * Add a new lore entry to the database with metadata
     */
    public boolean addLoreEntry(LoreEntry entry) {
        String sql = "INSERT INTO lore_entries (id, type, name, description, nbt_data, world, x, y, z, submitted_by, approved) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try {
            Connection conn = dbConnection.getConnection();
            conn.setAutoCommit(false);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, entry.getId().toString());
                stmt.setString(2, entry.getType().name());
                stmt.setString(3, entry.getName());
                stmt.setString(4, entry.getDescription());
                stmt.setString(5, entry.getNbtData());
                
                Location loc = entry.getLocation();
                if (loc != null) {
                    stmt.setString(6, loc.getWorld().getName());
                    stmt.setDouble(7, loc.getX());
                    stmt.setDouble(8, loc.getY());
                    stmt.setDouble(9, loc.getZ());
                } else {
                    stmt.setNull(6, Types.VARCHAR);
                    stmt.setNull(7, Types.DOUBLE);
                    stmt.setNull(8, Types.DOUBLE);
                    stmt.setNull(9, Types.DOUBLE);
                }
                
                stmt.setString(10, entry.getSubmittedBy());
                stmt.setBoolean(11, entry.isApproved());
                
                stmt.executeUpdate();
                
                // Now add any metadata
                addMetadataForEntry(entry, conn);
                
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                debug.error("Failed to add lore entry to database", e);
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            debug.error("Transaction error when adding lore entry", e);
            return false;
        }
    }
    
    /**
     * Update an entry and its metadata
     */
    public boolean updateLoreEntry(LoreEntry entry) {
        String sql = "UPDATE lore_entries SET name = ?, description = ?, nbt_data = ?, " +
                "world = ?, x = ?, y = ?, z = ?, approved = ? WHERE id = ?";
        
        try {
            Connection conn = dbConnection.getConnection();
            conn.setAutoCommit(false);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, entry.getName());
                stmt.setString(2, entry.getDescription());
                stmt.setString(3, entry.getNbtData());
                
                Location loc = entry.getLocation();
                if (loc != null) {
                    stmt.setString(4, loc.getWorld().getName());
                    stmt.setDouble(5, loc.getX());
                    stmt.setDouble(6, loc.getY());
                    stmt.setDouble(7, loc.getZ());
                } else {
                    stmt.setNull(4, Types.VARCHAR);
                    stmt.setNull(5, Types.DOUBLE);
                    stmt.setNull(6, Types.DOUBLE);
                    stmt.setNull(7, Types.DOUBLE);
                }
                
                stmt.setBoolean(8, entry.isApproved());
                stmt.setString(9, entry.getId().toString());
                
                stmt.executeUpdate();
                
                // Update metadata: first delete existing, then add new
                deleteMetadataForEntry(entry.getId().toString(), conn);
                addMetadataForEntry(entry, conn);
                
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                debug.error("Failed to update lore entry in database", e);
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            debug.error("Transaction error when updating lore entry", e);
            return false;
        }
    }
    
    /**
     * Get all lore entries from the database
     */
    public List<LoreEntry> getAllLoreEntries() {
        List<LoreEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM lore_entries";
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                entries.add(resultSetToLoreEntry(rs, conn));
            }
        } catch (SQLException e) {
            debug.error("Failed to get lore entries from database", e);
        }
        
        return entries;
    }
    
    /**
     * Get lore entries by type
     */
    public List<LoreEntry> getLoreEntriesByType(LoreType type) {
        List<LoreEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM lore_entries WHERE type = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, type.name());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(resultSetToLoreEntry(rs, conn));
                }
            }
        } catch (SQLException e) {
            debug.error("Failed to get lore entries by type: " + type, e);
        }
        
        return entries;
    }
    
    /**
     * Delete a lore entry by ID
     */
    public boolean deleteLoreEntry(UUID id) {
        String sql = "DELETE FROM lore_entries WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, id.toString());
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            debug.error("Failed to delete lore entry: " + id, e);
            return false;
        }
    }
    
    /**
     * Search lore entries by keyword in name or description
     */
    public List<LoreEntry> searchLoreEntries(String keyword) {
        List<LoreEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM lore_entries WHERE name LIKE ? OR description LIKE ?";
        String searchTerm = "%" + keyword + "%";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, searchTerm);
            stmt.setString(2, searchTerm);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(resultSetToLoreEntry(rs, conn));
                }
            }
        } catch (SQLException e) {
            debug.error("Failed to search lore entries for: " + keyword, e);
        }
        
        return entries;
    }
    
    /**
     * Get the number of entries in the database
     */
    public int getEntryCount() {
        String sql = "SELECT COUNT(*) FROM lore_entries";
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            debug.error("Failed to get entry count", e);
            return -1;
        }
    }
    
    /**
     * Helper method to add metadata for an entry
     */
    private void addMetadataForEntry(LoreEntry entry, Connection conn) throws SQLException {
        Map<String, String> metadata = entry.getAllMetadata();
        if (metadata.isEmpty()) {
            return;
        }
        
        String sql = "INSERT INTO lore_metadata (lore_id, meta_key, meta_value) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Map.Entry<String, String> meta : metadata.entrySet()) {
                stmt.setString(1, entry.getId().toString());
                stmt.setString(2, meta.getKey());
                stmt.setString(3, meta.getValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
    
    /**
     * Delete metadata for an entry
     */
    private void deleteMetadataForEntry(String id, Connection conn) throws SQLException {
        String sql = "DELETE FROM lore_metadata WHERE lore_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Convert a database result set to a LoreEntry object with metadata
     */
    private LoreEntry resultSetToLoreEntry(ResultSet rs, Connection conn) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        LoreType type = LoreType.valueOf(rs.getString("type"));
        String name = rs.getString("name");
        String description = rs.getString("description");
        String nbtData = rs.getString("nbt_data");
        
        // Handle location
        Location location = null;
        String worldName = rs.getString("world");
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                location = new Location(world, x, y, z);
            }
        }
        
        String submittedBy = rs.getString("submitted_by");
        boolean approved = rs.getBoolean("approved");
        Timestamp createdAt = rs.getTimestamp("created_at");
        
        LoreEntry entry = new LoreEntry(id, type, name, description, nbtData, location, submittedBy, approved, createdAt);
        
        // Load metadata
        loadMetadataForEntry(entry, conn);
        
        return entry;
    }
    
    /**
     * Load metadata for a lore entry
     */
    private void loadMetadataForEntry(LoreEntry entry, Connection conn) throws SQLException {
        String sql = "SELECT meta_key, meta_value FROM lore_metadata WHERE lore_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entry.getId().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("meta_key");
                    String value = rs.getString("meta_value");
                    entry.addMetadata(key, value);
                }
            }
        }
    }
}

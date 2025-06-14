package org.fourz.RVNKLore.lore.player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseConnection;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

/**
 * Repository for player-related database operations
 * 
 * This repository manages player lore entries, name changes, and provides
 * utilities for checking player existence and history in the lore system.
 */
public class PlayerRepository {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseConnection dbConnection;
    
    public PlayerRepository(RVNKLore plugin, DatabaseConnection dbConnection) {
        this.plugin = plugin;
        this.dbConnection = dbConnection;
        this.logger = LogManager.getInstance(plugin, "PlayerRepository");
    }
    
    /**
     * Check if a player already has a lore entry in the database
     * 
     * @param playerUuid The UUID of the player to check
     * @return true if the player has a lore entry, false otherwise
     */
    public boolean playerExists(UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM lore_submission s " +
                     "JOIN lore_entry e ON e.id = s.entry_id " +
                     "WHERE e.entry_type = ? " +
                     "AND s.is_current_version = TRUE " +
                     "AND s.content LIKE ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, LoreType.PLAYER.name());
            stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking if player exists: " + playerUuid, e);
        }
        
        return false;
    }
    
    /**
     * Get the current player name stored in the database for a given player UUID
     * 
     * @param playerUuid The UUID of the player
     * @return The stored player name, or null if not found
     */
    public String getStoredPlayerName(UUID playerUuid) {
        String sql = "SELECT s.content FROM lore_submission s " +
                     "JOIN lore_entry e ON e.id = s.entry_id " +
                     "WHERE e.entry_type = ? " +
                     "AND s.is_current_version = TRUE " +
                     "AND s.content LIKE ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, LoreType.PLAYER.name());
            stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String content = rs.getString("content");
                    
                    // Extract player_name from the JSON content
                    // This is a simple extraction method for demonstration
                    // In a real implementation, use a JSON parser
                    int nameIndex = content.indexOf("\"player_name\":\"");
                    if (nameIndex != -1) {
                        nameIndex += 15; // Length of "player_name":"
                        int endIndex = content.indexOf("\"", nameIndex);
                        if (endIndex != -1) {
                            return content.substring(nameIndex, endIndex);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting stored player name: " + playerUuid, e);
        }
        
        return null;
    }
    
    /**
     * Get all lore entries associated with a player
     * 
     * @param playerUuid The UUID of the player
     * @return List of lore entry IDs
     */
    public List<String> getPlayerLoreEntryIds(UUID playerUuid) {
        List<String> entryIds = new ArrayList<>();
        String sql = "SELECT e.id FROM lore_entry e " +
                     "JOIN lore_submission s ON e.id = s.entry_id " +
                     "WHERE e.entry_type = ? " +
                     "AND s.is_current_version = TRUE " +
                     "AND s.content LIKE ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, LoreType.PLAYER.name());
            stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entryIds.add(rs.getString("id"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player lore entries: " + playerUuid, e);
        }
        
        return entryIds;
    }
    
    /**
     * Get player lore entries by type (FIRST_JOIN, PLAYER_CHARACTER, NAME_CHANGE)
     * 
     * @param playerUuid The UUID of the player
     * @param entryType The type of entry to filter by
     * @return List of entry IDs matching the type
     */
    public List<String> getPlayerLoreEntriesByType(UUID playerUuid, String entryType) {
        List<String> entryIds = new ArrayList<>();
        String sql = "SELECT e.id FROM lore_entry e " +
                     "JOIN lore_submission s ON e.id = s.entry_id " +
                     "WHERE e.entry_type = ? " +
                     "AND s.is_current_version = TRUE " +
                     "AND s.content LIKE ? " +
                     "AND s.content LIKE ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, LoreType.PLAYER.name());
            stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
            stmt.setString(3, "%\"entry_type\":\"" + entryType + "\"%");
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entryIds.add(rs.getString("id"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting player lore entries by type: " + playerUuid + ", " + entryType, e);
        }
        
        return entryIds;
    }
    
    /**
     * Check if a player has had a name change recorded
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has a name change entry, false otherwise
     */
    public boolean hasNameChangeRecords(UUID playerUuid) {
        return !getPlayerLoreEntriesByType(playerUuid, "name_change").isEmpty();
    }
    
    /**
     * Get the history of name changes for a player
     * 
     * @param playerUuid The UUID of the player
     * @return List of previous names, from oldest to newest
     */
    public List<NameChangeRecord> getNameChangeHistory(UUID playerUuid) {
        List<NameChangeRecord> nameChanges = new ArrayList<>();
        String sql = "SELECT s.content, s.created_at FROM lore_submission s " +
                     "JOIN lore_entry e ON e.id = s.entry_id " +
                     "WHERE e.entry_type = ? " +
                     "AND s.is_current_version = TRUE " +
                     "AND s.content LIKE ? " +
                     "AND s.content LIKE ? " +
                     "ORDER BY s.created_at ASC";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, LoreType.PLAYER.name());
            stmt.setString(2, "%\"player_uuid\":\"" + playerUuid.toString() + "\"%");
            stmt.setString(3, "%\"entry_type\":\"name_change\"%");
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String content = rs.getString("content");
                    
                    // Extract previous_name and player_name from the JSON content
                    // In a real implementation, use a JSON parser
                    String previousName = extractJsonValue(content, "previous_name");
                    String newName = extractJsonValue(content, "player_name");
                    long timestamp = rs.getTimestamp("created_at").getTime();
                    
                    if (previousName != null && newName != null) {
                        nameChanges.add(new NameChangeRecord(previousName, newName, timestamp));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting name change history: " + playerUuid, e);
        }
        
        return nameChanges;
    }
    
    /**
     * Simple helper to extract a value from a JSON string
     * In a real implementation, use a proper JSON parser
     */
    private String extractJsonValue(String json, String key) {
        int keyIndex = json.indexOf("\"" + key + "\":\"");
        if (keyIndex != -1) {
            keyIndex += key.length() + 4; // Length of "key":"
            int endIndex = json.indexOf("\"", keyIndex);
            if (endIndex != -1) {
                return json.substring(keyIndex, endIndex);
            }
        }
        return null;
    }
}

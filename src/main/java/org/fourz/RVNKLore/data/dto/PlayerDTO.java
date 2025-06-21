package org.fourz.RVNKLore.data.dto;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * DTO for player data
 */
public class PlayerDTO {
    private int id;
    private UUID playerUuid;
    private String playerName;
    private Date firstJoinDate;
    private String location;
    private Map<String, String> metadata;
    private boolean isApproved;
    private String entryId;

    // Getters
    public int getId() { return id; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public Date getFirstJoinDate() { return firstJoinDate; }
    public String getLocation() { return location; }
    public Map<String, String> getMetadata() { return metadata; }
    public boolean isApproved() { return isApproved; }
    public String getEntryId() { return entryId; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setFirstJoinDate(Date firstJoinDate) { this.firstJoinDate = firstJoinDate; }
    public void setLocation(String location) { this.location = location; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    public void setApproved(boolean approved) { isApproved = approved; }
    public void setEntryId(String entryId) { this.entryId = entryId; }

    /**
     * Create a PlayerDTO from a ResultSet row.
     */
    public static PlayerDTO fromResultSet(ResultSet rs) throws SQLException {
        PlayerDTO dto = new PlayerDTO();
        dto.setEntryId(rs.getString("entry_id"));
        String content = rs.getString("content");
        // Parse JSON content for playerUuid, playerName, etc.
        Map<String, String> meta = parseMetadata(content);
        dto.setMetadata(meta);
        if (meta.containsKey("player_uuid")) {
            try { dto.setPlayerUuid(UUID.fromString(meta.get("player_uuid"))); } catch (Exception ignored) {}
        }
        if (meta.containsKey("player_name")) {
            dto.setPlayerName(meta.get("player_name"));
        }
        return dto;
    }

    /**
     * Extract player name from JSON content string.
     */
    public static String extractPlayerNameFromContent(String content) {
        Map<String, String> meta = parseMetadata(content);
        return meta.getOrDefault("player_name", null);
    }

    /**
     * Convert metadata map to JSON string.
     */
    public String getMetadataJson() {
        return new Gson().toJson(metadata != null ? metadata : new HashMap<>());
    }

    private static Map<String, String> parseMetadata(String json) {
        if (json == null) return new HashMap<>();
        try {
            return new Gson().fromJson(json, new TypeToken<Map<String, String>>(){}.getType());
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}

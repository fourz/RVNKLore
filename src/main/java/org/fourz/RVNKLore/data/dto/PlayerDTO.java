package org.fourz.RVNKLore.data.dto;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.annotations.SerializedName;

/**
 * DTO for player data. Uses Gson for JSON serialization.
 */
public class PlayerDTO {
    @SerializedName("id")
    private int id;
    
    @SerializedName("player_uuid")
    private UUID playerUuid;
    
    @SerializedName("player_name")
    private String playerName;
    
    @SerializedName("first_join_date")
    private Date firstJoinDate;
    
    @SerializedName("location")
    private String location;
    
    @SerializedName("metadata")
    private Map<String, String> metadata;
    
    @SerializedName("is_approved")
    private boolean isApproved;
    
    @SerializedName("entry_id")
    private Integer entryId;

    // Getters
    public int getId() { return id; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public Date getFirstJoinDate() { return firstJoinDate; }
    public String getLocation() { return location; }
    public Map<String, String> getMetadata() { return metadata; }
    public boolean isApproved() { return isApproved; }
    public Integer getEntryId() { return entryId; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setFirstJoinDate(Date firstJoinDate) { this.firstJoinDate = firstJoinDate; }
    public void setLocation(String location) { this.location = location; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    public void setApproved(boolean approved) { isApproved = approved; }
    public void setEntryId(Integer entryId) { this.entryId = entryId; }

    /**
     * Create a PlayerDTO from a ResultSet row.
     */
    public static PlayerDTO fromResultSet(ResultSet rs) throws SQLException {
        PlayerDTO dto = new PlayerDTO();
        dto.setEntryId(rs.getInt("entry_id"));
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
     * Convert this DTO to a JSON string.
     */
    public String toJson() {
        return new Gson().toJson(this);
    }

    /**
     * Convert metadata map to JSON string.
     */
    public String getMetadataJson() {
        return new Gson().toJson(metadata != null ? metadata : new HashMap<>());
    }

    /**
     * Parse metadata map from JSON string.
     * Package private for DTO class access only.
     */
    private static Map<String, String> parseMetadata(String json) {
        if (json == null) return new HashMap<>();
        try {
            return new Gson().fromJson(json, new TypeToken<Map<String, String>>(){}.getType());
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}

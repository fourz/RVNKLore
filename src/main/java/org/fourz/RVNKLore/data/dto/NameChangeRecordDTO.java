package org.fourz.RVNKLore.data.dto;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

/**
 * DTO for player name change history
 */
public class NameChangeRecordDTO {
    @SerializedName("id")
    private int id;
    
    @SerializedName("player_uuid")
    private UUID playerUuid;
    
    @SerializedName("previous_name")
    private String previousName;
    
    @SerializedName("new_name")
    private String newName;
    
    @SerializedName("change_date")
    private Date changeDate;
    
    @SerializedName("entry_id")
    private Integer entryId;

    public NameChangeRecordDTO() {}

    /**
     * Create a NameChangeRecordDTO from a ResultSet.
     */
    public NameChangeRecordDTO(ResultSet rs) throws SQLException {
        this.entryId = rs.getInt("entry_id");
        String content = rs.getString("content");
        this.changeDate = rs.getTimestamp("submitted_at");
        
        // Parse JSON content for playerUuid, previousName, newName, etc.
        Map<String, String> meta = parseMetadata(content);
        if (meta.containsKey("player_uuid")) {
            try { this.playerUuid = UUID.fromString(meta.get("player_uuid")); } catch (Exception ignored) {}
        }
        if (meta.containsKey("previous_name")) {
            this.previousName = meta.get("previous_name");
        }
        if (meta.containsKey("new_name")) {
            this.newName = meta.get("new_name");
        }
    }

    // Getters
    public int getId() { return id; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPreviousName() { return previousName; }
    public String getNewName() { return newName; }
    public Date getChangeDate() { return changeDate; }
    public Integer getEntryId() { return entryId; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }
    public void setPreviousName(String previousName) { this.previousName = previousName; }
    public void setNewName(String newName) { this.newName = newName; }
    public void setChangeDate(Date changeDate) { this.changeDate = changeDate; }
    public void setEntryId(Integer entryId) { this.entryId = entryId; }

    /**
     * Create a NameChangeRecordDTO from a ResultSet row.
     */
    public static NameChangeRecordDTO fromResultSet(ResultSet rs) throws SQLException {
        return new NameChangeRecordDTO(rs);
    }

    /**
     * Convert this DTO to a JSON string.
     */
    public String toJson() {
        return new Gson().toJson(this);
    }

    /**
     * Parse metadata map from JSON string.
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

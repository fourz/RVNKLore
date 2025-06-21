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
 * DTO for player name change history
 */
public class NameChangeRecordDTO {
    private int id;
    private UUID playerUuid;
    private String previousName;
    private String newName;
    private Date changeDate;
    private String entryId;

    // Getters
    public int getId() { return id; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPreviousName() { return previousName; }
    public String getNewName() { return newName; }
    public Date getChangeDate() { return changeDate; }
    public String getEntryId() { return entryId; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }
    public void setPreviousName(String previousName) { this.previousName = previousName; }
    public void setNewName(String newName) { this.newName = newName; }
    public void setChangeDate(Date changeDate) { this.changeDate = changeDate; }
    public void setEntryId(String entryId) { this.entryId = entryId; }

    /**
     * Create a NameChangeRecordDTO from a ResultSet row.
     */
    public static NameChangeRecordDTO fromResultSet(ResultSet rs) throws SQLException {
        NameChangeRecordDTO dto = new NameChangeRecordDTO();
        dto.setEntryId(rs.getString("entry_id"));
        String content = rs.getString("content");
        // Parse JSON content for playerUuid, previousName, newName, etc.
        Map<String, String> meta = parseMetadata(content);
        if (meta.containsKey("player_uuid")) {
            try { dto.setPlayerUuid(UUID.fromString(meta.get("player_uuid"))); } catch (Exception ignored) {}
        }
        if (meta.containsKey("previous_name")) {
            dto.setPreviousName(meta.get("previous_name"));
        }
        if (meta.containsKey("new_name")) {
            dto.setNewName(meta.get("new_name"));
        }
        // Optionally parse changeDate if present
        return dto;
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

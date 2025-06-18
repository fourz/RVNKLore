package org.fourz.RVNKLore.data.dto;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

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
}

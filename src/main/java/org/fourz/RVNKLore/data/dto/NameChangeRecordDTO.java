package org.fourz.RVNKLore.data.dto;

import java.util.Date;
import java.util.UUID;

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
}

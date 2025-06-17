package org.fourz.RVNKLore.data.dto;

import java.sql.Timestamp;
import org.fourz.RVNKLore.lore.LoreEntry;

/**
 * Data Transfer Object for LoreEntry.
 * Used to transfer lore entry data between database and domain layers.
 */
public class LoreEntryDTO {
    private int id;
    private String entryType;
    private String name;
    private String description;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public LoreEntryDTO() {}

    public LoreEntryDTO(int id, String entryType, String name, String description, 
                       Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.entryType = entryType;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }    /**
     * Converts a LoreEntry domain object to a DTO.
     */
    public static LoreEntryDTO fromLoreEntry(LoreEntry entry) {
        if (entry == null) return null;
        return new LoreEntryDTO(
            entry.getId(),
            entry.getEntryType(),
            entry.getName(),
            entry.getDescription(),
            entry.getCreatedAt(),
            entry.getUpdatedAt()
        );
    }

    /**
     * Converts this DTO to a LoreEntry domain object.
     */
    public LoreEntry toLoreEntry() {
        return new LoreEntry(this);
    }
}

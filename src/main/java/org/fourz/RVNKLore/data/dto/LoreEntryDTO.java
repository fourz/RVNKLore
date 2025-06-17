package org.fourz.RVNKLore.data.dto;

import java.sql.Timestamp;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

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
        
        LoreEntryDTO dto = new LoreEntryDTO();
        
        // Use String.valueOf to convert UUID to string, then parse to int where possible
        try {
            dto.setId(Integer.parseInt(entry.getId()));
        } catch (NumberFormatException e) {
            // If the ID is not a number (UUID), we'll need another way to handle this
            // For now, just set a placeholder ID
            dto.setId(-1);
        }
        
        dto.setEntryType(entry.getType() != null ? entry.getType().name() : null);
        dto.setName(entry.getName());
        dto.setDescription(entry.getDescription());
        dto.setCreatedAt(entry.getCreatedAt());
        // Note: LoreEntry doesn't have updatedAt field yet, setting to createdAt for now
        dto.setUpdatedAt(entry.getCreatedAt());
        
        return dto;
    }

    /**
     * Converts this DTO to a LoreEntry domain object.
     * Note: This is a partial conversion as LoreEntry has more fields than this DTO.
     */
    public LoreEntry toLoreEntry() {
        // Convert the entryType string to a LoreType enum
        LoreType type = null;
        try {
            if (entryType != null) {
                type = LoreType.valueOf(entryType);
            }
        } catch (IllegalArgumentException e) {
            // Handle invalid type strings
            type = LoreType.GENERIC;
        }
        
        // Use the string version of the ID for now
        String idStr = String.valueOf(id);
        
        // Create a new LoreEntry with the basic fields
        LoreEntry entry = new LoreEntry(idStr, name, description, type);
        
        // Set other fields if needed
        if (createdAt != null) {
            entry.setCreatedAt(createdAt);
        }
        
        return entry;
    }
}

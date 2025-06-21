package org.fourz.RVNKLore.lore;

import org.bukkit.Location;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a lore entry in the system
 */
public class LoreEntry {
    private int numericId; // Database ID
    private String id;     // Legacy string ID
    private String name;
    private LoreType type;
    private String description;
    private Location location;
    private boolean approved;
    private String submittedBy;
    private Timestamp submissionDate;
    private String nbtData;
    private Map<String, String> metadata;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public LoreEntry() {
        this.metadata = new HashMap<>();
        this.id = UUID.randomUUID().toString();
    }    // Convert from DTO
    public static LoreEntry fromDTO(LoreEntryDTO dto) {
        if (dto == null) return null;
        
        LoreEntry entry = new LoreEntry();
        entry.numericId = dto.getId();
        entry.id = dto.getUuidString();
        entry.name = dto.getName();
        
        try {
            if (dto.getEntryType() != null) {
                entry.type = LoreType.valueOf(dto.getEntryType());
            } else {
                entry.type = LoreType.GENERIC;
            }
        } catch (IllegalArgumentException e) {
            entry.type = LoreType.GENERIC;
        }
        
        entry.description = dto.getDescription();
        entry.approved = dto.isApproved();
        entry.submittedBy = dto.getSubmittedBy();
        entry.submissionDate = dto.getSubmissionDate();
        entry.nbtData = dto.getNbtData();
        entry.createdAt = dto.getCreatedAt();
        entry.updatedAt = dto.getUpdatedAt();
        entry.metadata = dto.getMetadata() != null ? new HashMap<>(dto.getMetadata()) : new HashMap<>();
        entry.location = dto.getLocation();
        
        return entry;
    }// Convert to DTO
    public LoreEntryDTO toDTO() {
        LoreEntryDTO dto = new LoreEntryDTO();
        dto.setId(this.numericId);
        dto.setUuid(this.id);
        dto.setEntryType(this.type != null ? this.type.name() : null);
        dto.setName(this.name);
        dto.setDescription(this.description);
        dto.setApproved(this.approved);
        dto.setSubmittedBy(this.submittedBy);
        dto.setSubmissionDate(this.submissionDate);
        dto.setNbtData(this.nbtData);
        dto.setCreatedAt(this.createdAt);
        dto.setUpdatedAt(this.updatedAt);
        dto.setMetadata(this.metadata);
        if (this.location != null) {
            dto.setLocation(this.location);
        }
        return dto;
    }

    // Standard getters and setters
    public int getNumericId() { return numericId; }
    public void setNumericId(int id) { this.numericId = id; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LoreType getType() { return type; }
    public void setType(LoreType type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public Timestamp getSubmissionDate() { return submissionDate; }
    public void setSubmissionDate(Timestamp submissionDate) { this.submissionDate = submissionDate; }
    public String getNbtData() { return nbtData; }
    public void setNbtData(String nbtData) { this.nbtData = nbtData; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    // For legacy compatibility
    public Map<String, Object> toJson() {
        Map<String, Object> json = new HashMap<>();
        json.put("id", getId());
        json.put("type", getType().name());
        json.put("name", getName());
        json.put("description", getDescription());
        
        if (getNbtData() != null) {
            json.put("nbtData", getNbtData());
        }
        
        if (getLocation() != null) {
            Map<String, Object> locationJson = new HashMap<>();
            locationJson.put("world", getLocation().getWorld().getName());
            locationJson.put("x", getLocation().getX());
            locationJson.put("y", getLocation().getY());
            locationJson.put("z", getLocation().getZ());
            json.put("location", locationJson);
        }
        
        json.put("submittedBy", getSubmittedBy());
        json.put("approved", isApproved());
        json.put("submissionDate", getSubmissionDate().toString());
        json.put("createdAt", getCreatedAt().toString());
        json.put("updatedAt", getUpdatedAt().toString());
        
        if (metadata != null && !metadata.isEmpty()) {
            json.put("metadata", new HashMap<>(metadata));
        }
        
        return json;
    }

    /**
     * Checks if this entry has any metadata
     * 
     * @return true if the entry has metadata
     */
    public boolean hasMetadata() {
        return metadata != null && !metadata.isEmpty();
    }
    
    /**
     * Gets all metadata for this entry
     * 
     * @return A map of all metadata key-value pairs
     */
    public Map<String, String> getAllMetadata() {
        return metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    /**
     * Gets a specific metadata value by key
     * 
     * @param key The metadata key to retrieve
     * @return The metadata value, or null if not found
     */
    public String getMetadata(String key) {
        if (metadata == null || key == null) return null;
        return metadata.get(key);
    }

    /**
     * Sets a metadata value for the specified key
     * 
     * @param key The metadata key to set
     * @param value The value to store
     */
    public void setMetadata(String key, String value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        if (key != null) {
            if (value != null) {
                metadata.put(key, value);
            } else {
                metadata.remove(key);
            }
        }
    }
}

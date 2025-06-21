package org.fourz.RVNKLore.data.dto;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

/**
 * Data Transfer Object for LoreEntry.
 * Used to transfer lore entry data between database and domain layers.
 * Includes all fields from the lore_entry table and related metadata.
 */
public class LoreEntryDTO {
    // Core DB fields
    private int id; // DB primary key
    private String uuid; // Domain UUID (CHAR(36))
    private String entryType;
    private String name;
    private String description;
    private boolean isApproved;
    private String submittedBy;
    private Timestamp submissionDate;
    private String nbtData;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Map<String, String> metadata = new HashMap<>();

    // Location fields
    private double x;
    private double y;
    private double z;
    private String world;
    private String content;

    public LoreEntryDTO() {}

    public LoreEntryDTO(int id, String uuid, String entryType, String name, String description, boolean isApproved, String submittedBy, Timestamp submissionDate, String nbtData, Timestamp createdAt, Timestamp updatedAt, Map<String, String> metadata) {
        this.id = id;
        this.uuid = uuid;
        this.entryType = entryType;
        this.name = name;
        this.description = description;
        this.isApproved = isApproved;
        this.submittedBy = submittedBy;
        this.submissionDate = submissionDate;
        this.nbtData = nbtData;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    // Getters and setters for all fields
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isApproved() { return isApproved; }
    public void setApproved(boolean approved) { isApproved = approved; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public Timestamp getSubmissionDate() { return submissionDate; }
    public void setSubmissionDate(Timestamp submissionDate) { this.submissionDate = submissionDate; }
    public String getNbtData() { return nbtData; }
    public void setNbtData(String nbtData) { this.nbtData = nbtData; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata != null ? metadata : new HashMap<>(); }

    // Location getters and setters
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }
    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public void setLocation(org.bukkit.Location location) {
        if (location != null) {
            this.x = location.getX();
            this.y = location.getY();
            this.z = location.getZ();
            this.world = location.getWorld() != null ? location.getWorld().getName() : null;
        }
    }

    public org.bukkit.Location getLocation() {
        if (world == null) return null;
        org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(world);
        if (bukkitWorld == null) return null;
        return new org.bukkit.Location(bukkitWorld, x, y, z);
    }

    /**
     * Converts a LoreEntry domain object to a DTO.
     * @param entry The domain object
     * @return LoreEntryDTO with all fields mapped
     */
    public static LoreEntryDTO fromLoreEntry(LoreEntry entry) {
        if (entry == null) return null;
        LoreEntryDTO dto = new LoreEntryDTO();
        dto.setId(entry.getNumericId());
        dto.setUuid(entry.getId());
        dto.setEntryType(entry.getType() != null ? entry.getType().name() : null);
        dto.setName(entry.getName());
        dto.setDescription(entry.getDescription());
        dto.setApproved(entry.isApproved());
        dto.setSubmittedBy(entry.getSubmittedBy());
        dto.setSubmissionDate(entry.getSubmissionDate());
        dto.setNbtData(entry.getNbtData());
        dto.setCreatedAt(entry.getCreatedAt());
        dto.setUpdatedAt(entry.getUpdatedAt());
        dto.setMetadata(entry.getMetadata());
        return dto;
    }

    /**
     * Converts this DTO to a LoreEntry domain object.
     * @return LoreEntry with all fields mapped
     */
    public LoreEntry toLoreEntry() {
        LoreEntry entry = new LoreEntry();
        entry.setNumericId(this.id);
        entry.setId(this.uuid);
        entry.setType(this.entryType != null ? LoreType.valueOf(this.entryType) : LoreType.GENERIC);
        entry.setName(this.name);
        entry.setDescription(this.description);
        entry.setApproved(this.isApproved);
        entry.setSubmittedBy(this.submittedBy);
        entry.setSubmissionDate(this.submissionDate);
        entry.setNbtData(this.nbtData);
        entry.setCreatedAt(this.createdAt);
        entry.setUpdatedAt(this.updatedAt);
        entry.setMetadata(this.metadata != null ? new HashMap<>(this.metadata) : new HashMap<>());
        return entry;
    }
}

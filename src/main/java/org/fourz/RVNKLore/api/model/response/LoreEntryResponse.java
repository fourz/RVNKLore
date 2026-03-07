package org.fourz.RVNKLore.api.model.response;

import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST API response DTO for lore entries.
 * Designed for JSON serialization with web dashboard integration.
 */
public class LoreEntryResponse {
    private String id;
    private String name;
    private String description;
    private String type;
    private String submittedBy;
    private boolean approved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Map<String, String> metadata;

    // Private constructor for builder
    private LoreEntryResponse() {}

    public static LoreEntryResponse from(LoreEntry entry) {
        if (entry == null) return null;

        LoreEntryResponse response = new LoreEntryResponse();
        response.id = entry.getId();
        response.name = entry.getName();
        response.description = entry.getDescription();
        response.type = entry.getType() != null ? entry.getType().name() : LoreType.GENERIC.name();
        response.submittedBy = entry.getSubmittedBy();
        response.approved = entry.isApproved();
        response.createdAt = entry.getCreatedAt() != null ? entry.getCreatedAt().toLocalDateTime() : null;
        response.updatedAt = response.createdAt; // Use createdAt as fallback
        response.metadata = entry.getAllMetadata();
        return response;
    }

    public static LoreEntryResponse from(LoreEntryDTO dto) {
        if (dto == null) return null;

        LoreEntryResponse response = new LoreEntryResponse();
        response.id = dto.entryId();
        response.name = dto.name();
        response.description = dto.description();
        response.type = dto.type() != null ? dto.type().name() : LoreType.GENERIC.name();
        response.submittedBy = dto.submittedBy();
        response.approved = dto.approved();
        response.createdAt = dto.createdAt() != null ? dto.createdAt().toLocalDateTime() : null;
        response.updatedAt = dto.updatedAt() != null ? dto.updatedAt().toLocalDateTime() : null;
        response.metadata = dto.metadata();
        return response;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public String getSubmittedBy() { return submittedBy; }
    public boolean isApproved() { return approved; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Map<String, String> getMetadata() { return metadata; }

    /**
     * Builder for LoreEntryResponse.
     */
    public static class Builder {
        private final LoreEntryResponse response = new LoreEntryResponse();

        public Builder id(String id) { response.id = id; return this; }
        public Builder name(String name) { response.name = name; return this; }
        public Builder description(String description) { response.description = description; return this; }
        public Builder type(String type) { response.type = type; return this; }
        public Builder submittedBy(String submittedBy) { response.submittedBy = submittedBy; return this; }
        public Builder approved(boolean approved) { response.approved = approved; return this; }
        public Builder createdAt(LocalDateTime createdAt) { response.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { response.updatedAt = updatedAt; return this; }
        public Builder metadata(Map<String, String> metadata) { response.metadata = metadata; return this; }

        public LoreEntryResponse build() {
            return response;
        }
    }
}

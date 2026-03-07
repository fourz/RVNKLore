package org.fourz.RVNKLore.data.dto;

import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Objects;

/**
 * Data Transfer Object for LoreEntry using Java Record.
 * Immutable and thread-safe for cross-plugin data transfer via RVNKCore.
 */
public record LoreEntryDTO(
    String entryId,
    LoreType type,
    String name,
    String description,
    String nbtData,
    String submittedBy,
    boolean approved,
    Timestamp createdAt,
    Timestamp updatedAt,
    Map<String, String> metadata
) {
    /**
     * Compact constructor with validation and defensive copies.
     */
    public LoreEntryDTO {
        Objects.requireNonNull(entryId, "entryId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        // Defensive copy for mutable collection
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Factory method from LoreEntry domain object.
     *
     * @param entry The domain entity to convert
     * @return A new LoreEntryDTO, or null if entry is null
     */
    public static LoreEntryDTO from(LoreEntry entry) {
        if (entry == null) return null;

        return new LoreEntryDTO(
            entry.getId(),
            entry.getType() != null ? entry.getType() : LoreType.GENERIC,
            entry.getName(),
            entry.getDescription(),
            entry.getNbtData(),
            entry.getSubmittedBy(),
            entry.isApproved(),
            entry.getCreatedAt(),
            entry.getCreatedAt(), // updatedAt defaults to createdAt
            entry.getAllMetadata()
        );
    }

    /**
     * Converts this DTO to a LoreEntry domain object.
     *
     * @return A new LoreEntry populated with DTO values
     */
    public LoreEntry toEntity() {
        LoreEntry entry = new LoreEntry(entryId, name, description, type);

        if (nbtData != null) {
            entry.setNbtData(nbtData);
        }
        if (submittedBy != null) {
            entry.setSubmittedBy(submittedBy);
        }
        entry.setApproved(approved);
        if (createdAt != null) {
            entry.setCreatedAt(createdAt);
        }

        // Copy metadata
        if (metadata != null) {
            for (Map.Entry<String, String> meta : metadata.entrySet()) {
                entry.addMetadata(meta.getKey(), meta.getValue());
            }
        }

        return entry;
    }

    /**
     * Builder for constructing LoreEntryDTO with optional fields.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for LoreEntryDTO.
     */
    public static class Builder {
        private String entryId;
        private LoreType type = LoreType.GENERIC;
        private String name;
        private String description;
        private String nbtData;
        private String submittedBy;
        private boolean approved = false;
        private Timestamp createdAt = new Timestamp(System.currentTimeMillis());
        private Timestamp updatedAt = new Timestamp(System.currentTimeMillis());
        private Map<String, String> metadata = Map.of();

        public Builder entryId(String entryId) {
            this.entryId = entryId;
            return this;
        }

        public Builder type(LoreType type) {
            this.type = type;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder nbtData(String nbtData) {
            this.nbtData = nbtData;
            return this;
        }

        public Builder submittedBy(String submittedBy) {
            this.submittedBy = submittedBy;
            return this;
        }

        public Builder approved(boolean approved) {
            this.approved = approved;
            return this;
        }

        public Builder createdAt(Timestamp createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Timestamp updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public LoreEntryDTO build() {
            return new LoreEntryDTO(
                entryId, type, name, description, nbtData,
                submittedBy, approved, createdAt, updatedAt, metadata
            );
        }
    }
}
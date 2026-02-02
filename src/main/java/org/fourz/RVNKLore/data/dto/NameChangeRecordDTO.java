package org.fourz.RVNKLore.data.dto;

import org.fourz.RVNKLore.lore.player.NameChangeRecord;

import java.util.Objects;
import java.util.UUID;

/**
 * Data Transfer Object for player name change records using Java Record.
 * Immutable and thread-safe for cross-plugin data transfer via RVNKCore.
 *
 * This DTO extends the base NameChangeRecord entity with additional fields
 * (id, playerId) needed for complete cross-plugin identification and transfer.
 */
public record NameChangeRecordDTO(
    UUID id,
    UUID playerId,
    String previousName,
    String newName,
    long timestamp
) {
    /**
     * Compact constructor with validation.
     */
    public NameChangeRecordDTO {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(previousName, "previousName cannot be null");
        Objects.requireNonNull(newName, "newName cannot be null");
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp cannot be negative");
        }
        // id can be null for new records not yet persisted
    }

    /**
     * Factory method from NameChangeRecord domain object.
     * Creates a DTO with a new generated ID since the entity doesn't have one.
     *
     * @param record The domain entity to convert
     * @param playerId The UUID of the player this record belongs to
     * @return A new NameChangeRecordDTO, or null if record is null
     */
    public static NameChangeRecordDTO from(NameChangeRecord record, UUID playerId) {
        if (record == null || playerId == null) return null;

        return new NameChangeRecordDTO(
            UUID.randomUUID(), // Generate new ID for cross-plugin transfer
            playerId,
            record.previousName(),
            record.newName(),
            record.timestamp()
        );
    }

    /**
     * Factory method with explicit ID for records loaded from database.
     *
     * @param id The unique record ID
     * @param playerId The UUID of the player
     * @param previousName The player's previous name
     * @param newName The player's new name
     * @param timestamp When the name change occurred
     * @return A new NameChangeRecordDTO
     */
    public static NameChangeRecordDTO of(UUID id, UUID playerId, String previousName, String newName, long timestamp) {
        return new NameChangeRecordDTO(id, playerId, previousName, newName, timestamp);
    }

    /**
     * Converts this DTO to a NameChangeRecord domain object.
     * Note: The id and playerId are not preserved as they're not part of the entity.
     *
     * @return A new NameChangeRecord populated with DTO values
     */
    public NameChangeRecord toEntity() {
        return new NameChangeRecord(previousName, newName, timestamp);
    }

    /**
     * Returns a display-friendly representation of the name change.
     *
     * @return String in format "playerId: oldName -> newName"
     */
    @Override
    public String toString() {
        return playerId.toString().substring(0, 8) + ": " + previousName + " -> " + newName;
    }

    /**
     * Builder for constructing NameChangeRecordDTO with optional fields.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for NameChangeRecordDTO.
     */
    public static class Builder {
        private UUID id;
        private UUID playerId;
        private String previousName;
        private String newName;
        private long timestamp = System.currentTimeMillis();

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder playerId(UUID playerId) {
            this.playerId = playerId;
            return this;
        }

        public Builder previousName(String previousName) {
            this.previousName = previousName;
            return this;
        }

        public Builder newName(String newName) {
            this.newName = newName;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public NameChangeRecordDTO build() {
            if (id == null) {
                id = UUID.randomUUID();
            }
            return new NameChangeRecordDTO(id, playerId, previousName, newName, timestamp);
        }
    }
}

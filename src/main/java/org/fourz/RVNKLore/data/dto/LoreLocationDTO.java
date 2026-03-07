package org.fourz.RVNKLore.data.dto;

import org.fourz.RVNKLore.data.model.LoreLocation;

import java.util.Objects;

/**
 * Data Transfer Object for LoreLocation using Java Record.
 * Immutable and thread-safe for cross-plugin data transfer via RVNKCore.
 */
public record LoreLocationDTO(
    int id,
    String entryId,
    String world,
    double x,
    double y,
    double z,
    String locationType,
    String label
) {
    public LoreLocationDTO {
        Objects.requireNonNull(entryId, "entryId cannot be null");
        Objects.requireNonNull(world, "world cannot be null");
        if (locationType == null) {
            locationType = "PRIMARY";
        }
    }

    public static LoreLocationDTO from(LoreLocation location) {
        if (location == null) return null;
        return new LoreLocationDTO(
            location.getId(),
            location.getEntryId(),
            location.getWorld(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getLocationType(),
            location.getLabel()
        );
    }

    public LoreLocation toEntity() {
        return LoreLocation.builder()
            .id(id)
            .entryId(entryId)
            .world(world)
            .x(x)
            .y(y)
            .z(z)
            .locationType(locationType)
            .label(label)
            .build();
    }
}

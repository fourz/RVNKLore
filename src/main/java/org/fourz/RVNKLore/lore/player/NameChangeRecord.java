package org.fourz.RVNKLore.lore.player;

import java.util.Objects;

/**
 * Immutable record for storing player name change information.
 * Tracks the transition from a previous username to a new username with timestamp.
 *
 * @param previousName The player's previous username (before the change)
 * @param newName The player's new username (after the change)
 * @param timestamp Unix timestamp when the name change occurred
 */
public record NameChangeRecord(
    String previousName,
    String newName,
    long timestamp
) {
    /**
     * Compact constructor with validation.
     */
    public NameChangeRecord {
        Objects.requireNonNull(previousName, "previousName cannot be null");
        Objects.requireNonNull(newName, "newName cannot be null");
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp cannot be negative");
        }
    }

    /**
     * Returns a display-friendly representation of the name change.
     *
     * @return String in format "oldName → newName"
     */
    @Override
    public String toString() {
        return previousName + " → " + newName;
    }
}

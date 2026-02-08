package org.fourz.RVNKLore.data.model;

import java.time.Instant;

/**
 * Model representing a spatial location associated with a lore entry.
 * Supports multiple location types per entry (PRIMARY, WAYPOINT, BOUNDARY).
 */
public class LoreLocation {
    private int id;
    private String entryId;
    private String world;
    private double x;
    private double y;
    private double z;
    private String locationType;
    private String label;
    private Instant createdAt;

    public LoreLocation() {
        this.locationType = "PRIMARY";
    }

    public LoreLocation(String entryId, String world, double x, double y, double z) {
        this.entryId = entryId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.locationType = "PRIMARY";
    }

    // Getters and setters

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public String getLocationType() { return locationType; }
    public void setLocationType(String locationType) { this.locationType = locationType; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    // Builder

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final LoreLocation loc = new LoreLocation();

        public Builder id(int id) { loc.id = id; return this; }
        public Builder entryId(String entryId) { loc.entryId = entryId; return this; }
        public Builder world(String world) { loc.world = world; return this; }
        public Builder x(double x) { loc.x = x; return this; }
        public Builder y(double y) { loc.y = y; return this; }
        public Builder z(double z) { loc.z = z; return this; }
        public Builder locationType(String locationType) { loc.locationType = locationType; return this; }
        public Builder label(String label) { loc.label = label; return this; }
        public Builder createdAt(Instant createdAt) { loc.createdAt = createdAt; return this; }

        public LoreLocation build() { return loc; }
    }
}

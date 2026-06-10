package org.fourz.RVNKLore.lore.map;

import java.time.Instant;

/**
 * Represents a stored lore map record in lore_map.
 *
 * Maps are linked to a lore entry (nullable for PIXEL subtype) and carry
 * coordinate metadata plus optional base64 pixel data for full cross-server
 * reconstruction without needing world data on the receiving server.
 */
public class LoreMap {
    private int id;
    private String loreEntryId;
    private MapSubtype subtype;
    private int centerX;
    private int centerZ;
    private byte scale;
    private String worldName;
    private String dimension;
    private String pixelData;
    private String createdBy;
    private Instant createdAt;

    public LoreMap() {}

    public LoreMap(String loreEntryId, MapSubtype subtype, int centerX, int centerZ,
                   byte scale, String worldName, String dimension, String createdBy) {
        this.loreEntryId = loreEntryId;
        this.subtype = subtype;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.scale = scale;
        this.worldName = worldName;
        this.dimension = dimension;
        this.createdBy = createdBy;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getLoreEntryId() { return loreEntryId; }
    public void setLoreEntryId(String loreEntryId) { this.loreEntryId = loreEntryId; }

    public MapSubtype getSubtype() { return subtype; }
    public void setSubtype(MapSubtype subtype) { this.subtype = subtype; }

    public int getCenterX() { return centerX; }
    public void setCenterX(int centerX) { this.centerX = centerX; }

    public int getCenterZ() { return centerZ; }
    public void setCenterZ(int centerZ) { this.centerZ = centerZ; }

    public byte getScale() { return scale; }
    public void setScale(byte scale) { this.scale = scale; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public String getPixelData() { return pixelData; }
    public void setPixelData(String pixelData) { this.pixelData = pixelData; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

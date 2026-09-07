package org.fourz.RVNKLore.api.model.response;

import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST API response DTO for lore entries.
 * Designed for JSON serialization with web dashboard integration.
 *
 * FIXED issue-899: Submitter UUID resolution
 * Returns both submitterUuid (the UUID string stored in database) and submitterName
 * (resolved player display name or "Unknown" if offline player name cannot be resolved).
 */
public class LoreEntryResponse {
    private String id;
    private String name;
    private String description;
    private String type;
    private String submittedBy;
    private String submitterUuid;
    private String submitterName;
    private boolean approved;
    private String approvalStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Map<String, String> metadata;
    private LocationData location;

    public static class LocationData {
        public final String world;
        public final double x;
        public final double y;
        public final double z;

        public LocationData(String world, double x, double y, double z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    // Private constructor for builder
    private LoreEntryResponse() {}

    /**
     * FIXED issue-899: Create response from LoreEntry with UUID storage
     * Expects entry.getSubmittedBy() to return either:
     * - A UUID string (new entries)
     * - "Server" (system-generated entries)
     * - A player name (legacy entries from before issue-899 fix)
     *
     * @param entry The lore entry to convert
     * @return Response with submitterUuid and submitterName fields
     */
    public static LoreEntryResponse from(LoreEntry entry) {
        if (entry == null) return null;

        LoreEntryResponse response = new LoreEntryResponse();
        response.id = entry.getId();
        response.name = entry.getName();
        response.description = entry.getDescription();
        response.type = entry.getType() != null ? entry.getType().name() : LoreType.GENERIC.name();

        // FIXED issue-899: Handle submitter field
        String submitter = entry.getSubmittedBy();
        response.submittedBy = submitter; // Keep for backward compatibility
        response.submitterUuid = submitter;
        // Try to resolve UUID to player name, fallback to "Unknown"
        response.submitterName = resolveSubmitterName(submitter);

        response.approved = entry.isApproved();
        response.approvalStatus = entry.getApprovalStatus();
        response.createdAt = entry.getCreatedAt() != null ? entry.getCreatedAt().toLocalDateTime() : null;
        response.updatedAt = response.createdAt; // Use createdAt as fallback
        response.metadata = entry.getAllMetadata();
        // Stored form, so an entry whose world is not loaded still reports its coordinates
        // (#1366). LocationData already holds the world as a String, so the live World handle
        // this used to demand was never needed to build the response - and demanding it made the
        // API answer "no location" for rows that plainly exist in lore_location.
        LoreEntry.StoredLocation loc = entry.getStoredLocation();
        if (loc != null) {
            response.location = new LocationData(loc.world(), loc.x(), loc.y(), loc.z());
        }
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
        response.submitterUuid = dto.submittedBy();
        response.submitterName = resolveSubmitterName(dto.submittedBy());
        response.approved = dto.approved();
        response.createdAt = dto.createdAt() != null ? dto.createdAt().toLocalDateTime() : null;
        response.updatedAt = dto.updatedAt() != null ? dto.updatedAt().toLocalDateTime() : null;
        response.metadata = dto.metadata();
        return response;
    }

    /**
     * Resolve a submitter UUID or name string to a display name.
     * FIXED issue-899: Handles UUID strings by looking up offline player names.
     *
     * @param submitter Either a UUID string, "Server", or a legacy player name
     * @return The player's display name, "Server", or "Unknown" if lookup fails
     */
    private static String resolveSubmitterName(String submitter) {
        if (submitter == null || submitter.isEmpty()) {
            return "Unknown";
        }

        if ("Server".equals(submitter)) {
            return "Server";
        }

        // Try to parse as UUID and look up offline player
        try {
            java.util.UUID uuid = java.util.UUID.fromString(submitter);
            org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(uuid);
            String name = offlinePlayer.getName();
            return name != null ? name : "Unknown";
        } catch (IllegalArgumentException e) {
            // Not a UUID — assume it's a legacy player name (from before issue-899 fix)
            return submitter;
        }
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
    public String getSubmitterUuid() { return submitterUuid; }
    public String getSubmitterName() { return submitterName; }
    public boolean isApproved() { return approved; }
    public String getApprovalStatus() { return approvalStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Map<String, String> getMetadata() { return metadata; }
    public LocationData getLocation() { return location; }

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
        public Builder submitterUuid(String submitterUuid) { response.submitterUuid = submitterUuid; return this; }
        public Builder submitterName(String submitterName) { response.submitterName = submitterName; return this; }
        public Builder approved(boolean approved) { response.approved = approved; return this; }
        public Builder createdAt(LocalDateTime createdAt) { response.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { response.updatedAt = updatedAt; return this; }
        public Builder metadata(Map<String, String> metadata) { response.metadata = metadata; return this; }
        public Builder location(LocationData location) { response.location = location; return this; }

        public LoreEntryResponse build() {
            return response;
        }
    }
}

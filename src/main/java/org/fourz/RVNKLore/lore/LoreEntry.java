package org.fourz.RVNKLore.lore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.json.simple.JSONObject;

import org.bukkit.entity.Player;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single lore entry with its type and content
 */
public class LoreEntry {
    private final String id;
    private String name;
    private String description;
    private LoreType type;
    private String nbtData;
    private Location location;
    // Coordinates whose world was not loaded at parse time; resolved lazily by getLocation() (#1953).
    private String deferredWorldName;
    private double deferredX;
    private double deferredY;
    private double deferredZ;
    private String submittedBy;
    private String approvalStatus = "PENDING";
    private Timestamp createdAt;
    private String status = "ACTIVE";
    private String visibility = "PUBLIC";
    // Missing metadata field
    private Map<String, String> metadata;

    /**
     * Canonical private constructor used by simple public constructors.
     * Centralises the common field defaults so each constructor delegates here
     * rather than duplicating initialisation.
     *
     * @param id          pre-generated entry ID (UUID string)
     * @param submittedBy attribution string; use {@code "Server"} for system-generated
     *                    entries, {@code null} only when attribution is unknown at
     *                    construction time (field-by-field builders)
     */
    private LoreEntry(String id, String submittedBy) {
        this.id = id;
        this.submittedBy = submittedBy;
        this.metadata = new HashMap<>();
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    /**
     * Default constructor for new entries built up field-by-field.
     * {@code submittedBy} is left {@code null}; callers must set it explicitly
     * before persisting the entry.
     */
    public LoreEntry() {
        this(UUID.randomUUID().toString(), null);
    }

    /**
     * Constructor for creating a new server-generated lore entry with NBT data.
     */
    public LoreEntry(String name, String description, LoreType type, String nbtData) {
        this(UUID.randomUUID().toString(), "Server");
        this.name = name;
        this.description = description;
        this.nbtData = nbtData;
        this.type = type;
    }
    /**
     * Constructor for creating a new lore entry from a Player (stores UUID, not name)
     *
     * FIXED issue-899: Now stores player UUID string instead of player name.
     * Player renames no longer corrupt lore attribution.
     */

    public LoreEntry(String name, String description, LoreType type, Player contributor) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.nbtData = "";
        // FIXED issue-899: Store UUID string instead of player name to prevent corruption on renames
        this.submittedBy = contributor != null ? contributor.getUniqueId().toString() : "Server";
        this.type = type;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }


    /**
     * Constructor with predefined ID for server-generated entries.
     */
    public LoreEntry(String id, String name, String description, LoreType type) {
        this(id, "Server");
        this.name = name;
        this.description = description;
        this.type = type;
    }

    /**
     * Full constructor
     */
    public LoreEntry(UUID id, LoreType type, String name, String description, String nbtData,
                    Location location, String submittedBy, boolean approved, Timestamp createdAt) {
        this.id = id.toString();
        this.type = type;
        this.name = name;
        this.description = description;
        this.nbtData = nbtData;
        this.location = location;
        this.submittedBy = submittedBy != null ? submittedBy : "Server";
        this.approvalStatus = approved ? "APPROVED" : "PENDING";
        this.createdAt = createdAt;
    }

    /**
     * Constructor for loading from database with string parameters
     */
    public LoreEntry(String id, LoreType type, String name, String description, String nbtData,
                   Location location, String submittedBy, boolean approved, String createdAtStr) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.description = description;
        this.nbtData = nbtData;
        this.location = location;
        this.submittedBy = submittedBy != null ? submittedBy : "Server";
        this.approvalStatus = approved ? "APPROVED" : "PENDING";

        try {
            this.createdAt = Timestamp.valueOf(createdAtStr);
        } catch (IllegalArgumentException e) {
            this.createdAt = new Timestamp(System.currentTimeMillis());
        }
    }

    public String getId() {
        return id;
    }

    public UUID getUUID() {
        return UUID.fromString(id);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LoreType getType() {
        return type;
    }

    public void setType(LoreType type) {
        this.type = type;
    }

    public String getNbtData() {
        return nbtData;
    }

    public void setNbtData(String nbtData) {
        this.nbtData = nbtData;
    }

    /**
     * The entry's location, resolving a deferred world on first successful lookup (#1953).
     *
     * <p>An entry parsed before its world was loaded used to lose its coordinates outright: the
     * mapper only built a {@link Location} when {@code Bukkit.getWorld(name)} returned non-null,
     * and dropped the coordinates silently otherwise. RVNKLore parses its entries during enable,
     * so every entry in a world that RVNKWorlds had not activated yet loaded with no location at
     * all — permanently, because the parsed object is cached. That killed proximity discovery for
     * those worlds with no error anywhere.</p>
     *
     * <p>The coordinates are now retained (see {@link #setDeferredLocation}) and resolved here the
     * first time the world exists. Enable order therefore stops being load-bearing.</p>
     */
    public Location getLocation() {
        if (location == null && deferredWorldName != null) {
            World world = Bukkit.getWorld(deferredWorldName);
            if (world != null) {
                location = new Location(world, deferredX, deferredY, deferredZ);
                deferredWorldName = null;
            }
        }
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
        if (location != null) {
            this.deferredWorldName = null;
        }
    }

    /**
     * Records coordinates whose world is not loaded yet, to be resolved by {@link #getLocation()}.
     *
     * @param worldName world name as stored; never resolved here
     * @param x         block/exact X as stored
     * @param y         block/exact Y as stored
     * @param z         block/exact Z as stored
     */
    public void setDeferredLocation(String worldName, double x, double y, double z) {
        this.deferredWorldName = worldName;
        this.deferredX = x;
        this.deferredY = y;
        this.deferredZ = z;
    }

    /**
     * @return true when coordinates are held but their world has not been loaded yet — the entry
     *         has a location on paper but cannot produce one, which callers may want to report
     *         rather than treat as "no location".
     */
    public boolean hasUnresolvedLocation() {
        return location == null && deferredWorldName != null;
    }

    /** @return the world name awaiting resolution, or null when nothing is deferred. */
    public String getDeferredWorldName() {
        return deferredWorldName;
    }

    /**
     * Coordinates in the form the database actually stores them: a world <b>name</b> plus x/y/z.
     *
     * <p>Both {@code lore_location.world} and the {@code content} JSON hold a varchar world name,
     * so neither needs a live {@link World} handle. Persisting through {@link #getLocation()}
     * therefore imposed a requirement the storage layer never had, and entries in an unloaded
     * world lost their coordinates on every write (#1366).</p>
     *
     * @return the stored form, resolved or deferred, or {@code null} when there are no coordinates
     */
    public StoredLocation getStoredLocation() {
        Location loc = getLocation();
        if (loc != null && loc.getWorld() != null) {
            return new StoredLocation(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
        }
        if (deferredWorldName != null) {
            return new StoredLocation(deferredWorldName, deferredX, deferredY, deferredZ);
        }
        return null;
    }

    /**
     * Attach coordinates by world <b>name</b>, resolving the world only if it happens to be up.
     *
     * <p>This is the single place that decides loaded-vs-deferred, so callers do not each repeat
     * a {@code Bukkit.getWorld() == null} check and then disagree about what to do with the
     * answer. It also stamps {@link LoreMetadataKeys#WORLD_STATUS}, which records the state
     * <i>at write time</i> — once the world loads, {@link #getLocation()} resolves and the tag
     * becomes a historical note rather than current truth. That is intentional and matches the
     * key's documented meaning.</p>
     *
     * @param worldName world name as supplied; stored verbatim, never validated against the server
     */
    public void applyLocationByWorldName(String worldName, double x, double y, double z) {
        World world = (worldName != null) ? Bukkit.getWorld(worldName) : null;
        if (world != null) {
            setLocation(new Location(world, x, y, z));
            addMetadata(LoreMetadataKeys.WORLD_STATUS, "loaded");
        } else {
            setDeferredLocation(worldName, x, y, z);
            addMetadata(LoreMetadataKeys.WORLD_STATUS, "unloaded");
        }
    }

    /**
     * A location as the database stores it — world name, not world handle.
     *
     * @param world world name, never null when this record exists
     */
    public record StoredLocation(String world, double x, double y, double z) {
    }

    /**
     * Get the submitter UUID string.
     * Returns the UUID of the player who submitted this lore entry,
     * or "Server" for system-generated entries.
     *
     * FIXED issue-899: Now returns UUID string instead of player name.
     *
     * @return UUID string of submitter, or "Server"
     */
    public String getSubmittedBy() {
        return submittedBy;
    }

    /**
     * Set the submitter UUID string.
     * Typically set to player.getUniqueId().toString() or "Server".
     *
     * @param submittedBy UUID string or "Server"
     */
    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(approvalStatus);
    }

    /** Maps true→APPROVED, false→PENDING. Use setApprovalStatus("REJECTED") for explicit rejection. */
    public void setApproved(boolean approved) {
        this.approvalStatus = approved ? "APPROVED" : "PENDING";
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public boolean isArchived() {
        return "ARCHIVED".equals(status);
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Get a human-readable display name for this entry.
     * For PLAYER-type entries, returns the player_name metadata instead of the
     * internal UUID-based name. Falls back to getName() for all other types.
     *
     * @return The display-friendly name
     */
    public String getDisplayName() {
        if (type == LoreType.PLAYER && hasMetadata("player_name")) {
            return getMetadata("player_name");
        }
        return name;
    }

    /**
     * Add metadata to this lore entry
     *
     * @param key The metadata key
     * @param value The metadata value
     */
    public void addMetadata(String key, String value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }

    /**
     * Get metadata from this lore entry
     *
     * @param key The metadata key
     * @return The metadata value, or null if not found
     */
    public String getMetadata(String key) {
        if (metadata == null) {
            return null;
        }
        return metadata.get(key);
    }

    /**
     * Check if this entry has metadata with the given key
     *
     * @param key The metadata key
     * @return True if metadata exists, false otherwise
     */
    public boolean hasMetadata(String key) {
        return metadata != null && metadata.containsKey(key);
    }

        /**
     * Check if this entry has metadata with the given key
     *
     * @param key The metadata key
     * @return True if metadata exists, false otherwise
     */
    public boolean hasMetadata() {
        return metadata != null;
    }

    /**
     * Get all metadata for this entry
     *
     * @return A map of all metadata
     */
    public Map<String, String> getAllMetadata() {
        return metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    /**
     * Convert the lore entry to a JSON object
     */
    @SuppressWarnings("unchecked") // JSONObject from json-simple doesn't support generics
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("type", type.name());
        json.put("name", name);
        json.put("description", description);

        if (nbtData != null) {
            json.put("nbtData", nbtData);
        }

        if (location != null) {
            JSONObject locationJson = new JSONObject();
            locationJson.put("world", location.getWorld().getName());
            locationJson.put("x", location.getX());
            locationJson.put("y", location.getY());
            locationJson.put("z", location.getZ());
            json.put("location", locationJson);
        }

        json.put("submittedBy", submittedBy);
        json.put("approved", isApproved());
        json.put("approvalStatus", approvalStatus);
        json.put("createdAt", createdAt.toString());

        // Add metadata to JSON
        if (metadata != null && !metadata.isEmpty()) {
            JSONObject metadataJson = new JSONObject();
            metadataJson.putAll(metadata);
            json.put("metadata", metadataJson);
        }

        return json;
    }

    @Override
    public String toString() {
        return "LoreEntry{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                '}';
    }

    /**
     * Creates a location-based lore entry (LANDMARK, CITY, PATH)
     *
     * @param name The name of the lore entry
     * @param description The description of the lore entry
     * @param type The type of lore (should be LANDMARK, CITY, or PATH)
     * @param location The location associated with this lore
     * @param player The player who submitted this lore
     * @return A new LoreEntry with location data
     */
    public static LoreEntry createLocationLore(String name, String description, LoreType type, Location location, Player player) {
        LoreEntry entry = new LoreEntry(name, description, type, player);
        entry.setLocation(location);
        return entry;
    }

    /**
     * Creates a head/hat based lore entry (PLAYER_HEAD, MOB_HEAD, HEAD, HAT)
     *
     * @param name The name of the lore entry
     * @param description The description of the lore entry
     * @param type The type of lore (should be a head/hat type)
     * @param nbtData The NBT data for the head item
     * @param player The player who submitted this lore
     * @return A new LoreEntry with NBT data
     */
    public static LoreEntry createHeadLore(String name, String description, LoreType type, String nbtData, Player player) {
        LoreEntry entry = new LoreEntry(name, description, type, player);
        entry.setNbtData(nbtData);
        return entry;
    }

    /**
     * Creates a character-related lore entry (PLAYER, FACTION)
     *
     * @param name The name of the lore entry
     * @param description The description of the lore entry
     * @param type The type of lore (should be PLAYER or FACTION)
     * @param player The player who submitted this lore
     * @return A new LoreEntry for character lore
     */
    public static LoreEntry createCharacterLore(String name, String description, LoreType type, Player player) {
        return new LoreEntry(name, description, type, player);
    }

    /**
     * Creates a gameplay-related lore entry (ENCHANTMENT, ITEM, QUEST)
     *
     * @param name The name of the lore entry
     * @param description The description of the lore entry
     * @param type The type of lore (should be ENCHANTMENT, ITEM, or QUEST)
     * @param player The player who submitted this lore
     * @return A new LoreEntry for gameplay lore
     */
    public static LoreEntry createGameplayLore(String name, String description, LoreType type, Player player) {
        return new LoreEntry(name, description, type, player);
    }

    /**
     * Validates if this lore entry has all required fields based on its type
     *
     * @return true if the entry is valid, false otherwise
     */
    public boolean isValid() {
        if (name == null || description == null || type == null) {
            return false;
        }

        // Location-based lore requires a location
        if (type != null && type.isLocationCapable() && location == null) {
            return false;
        }

        // Head/hat lore typically requires NBT data
        if ((type == LoreType.HEAD ) && (nbtData == null || nbtData.isEmpty())) {
            return false;
        }

        return true;
    }
}

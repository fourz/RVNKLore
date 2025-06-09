package org.fourz.RVNKLore.lore;

import org.bukkit.Location;
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
    private String submittedBy;
    private boolean approved;
    private Timestamp createdAt;
    // Missing metadata field
    private Map<String, String> metadata;
    
    /**
     * Default constructor for new entries
     */
    public LoreEntry() {
        this.id = UUID.randomUUID().toString();
        this.metadata = new HashMap<>();
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }
    
    /**
     * Constructor for creating a new lore entry
     */
    public LoreEntry(String name, String description, LoreType type, String nbtData) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.nbtData = nbtData;
        this.type = type;
        this.approved = false;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }
    /**
     * Constructor for creating a new lore entry
     */    

    public LoreEntry(String name, String description, LoreType type, Player contributor) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.nbtData = ""; // Initialize with empty string instead of using undefined variable
        this.submittedBy = contributor.getName(); // Uncomment this line to set the submitter's name
        this.type = type;
        this.approved = false;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }
    
    
    /**
     * Constructor with predefined ID
     */
    public LoreEntry(String id, String name, String description, LoreType type) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.approved = false;
        this.createdAt = new Timestamp(System.currentTimeMillis());
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
        this.submittedBy = submittedBy;
        this.approved = approved;
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
        this.submittedBy = submittedBy;
        this.approved = approved;
        
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
    
    public Location getLocation() {
        return location;
    }
    
    public void setLocation(Location location) {
        this.location = location;
    }
    
    public String getSubmittedBy() {
        return submittedBy;
    }
    
    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }
    
    public boolean isApproved() {
        return approved;
    }
    
    public void setApproved(boolean approved) {
        this.approved = approved;
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
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
        json.put("approved", approved);
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
        if ((type == LoreType.LANDMARK || type == LoreType.CITY || type == LoreType.PATH) && location == null) {
            return false;
        }
        
        // Head/hat lore typically requires NBT data
        if ((type == LoreType.HEAD ) && (nbtData == null || nbtData.isEmpty())) {
            return false;
        }
        
        return true;
    }

    public String getSubmitter() {
        return submittedBy;
    }
}

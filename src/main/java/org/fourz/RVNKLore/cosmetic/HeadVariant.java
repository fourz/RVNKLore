package org.fourz.RVNKLore.cosmetic;

import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a specific variant of a head with unique properties and metadata.
 * Head variants can be player heads, mob heads, or custom textured heads with additional properties.
 */
public class HeadVariant {
    private final String id;
    private final String name;
    private final String description;
    private final HeadType type;
    private final String textureData;
    private final String ownerName;
    private final EntityType mobType;
    private final Integer customModelData;
    private final Map<String, String> properties;
    private final HeadRarity rarity;
    private final boolean animated;
    private final String[] animationFrames;
    
    /**
     * Constructor for player head variants.
     *
     * @param id Unique identifier for this variant
     * @param name Display name of the head
     * @param description Description of the head's origin or purpose
     * @param ownerName Username of the player whose head this represents
     * @param rarity Rarity classification of this head
     */
    public HeadVariant(String id, String name, String description, String ownerName, HeadRarity rarity) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = HeadType.PLAYER;
        this.textureData = null;
        this.ownerName = ownerName;
        this.mobType = null;
        this.customModelData = null;
        this.properties = new HashMap<>();
        this.rarity = rarity;
        this.animated = false;
        this.animationFrames = null;
    }
    
    /**
     * Constructor for mob head variants.
     *
     * @param id Unique identifier for this variant
     * @param name Display name of the head
     * @param description Description of the head's origin or purpose
     * @param mobType Type of mob this head represents
     * @param rarity Rarity classification of this head
     */
    public HeadVariant(String id, String name, String description, EntityType mobType, HeadRarity rarity) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = HeadType.MOB;
        this.textureData = null;
        this.ownerName = null;
        this.mobType = mobType;
        this.customModelData = null;
        this.properties = new HashMap<>();
        this.rarity = rarity;
        this.animated = false;
        this.animationFrames = null;
    }
    
    /**
     * Constructor for custom textured head variants.
     *
     * @param id Unique identifier for this variant
     * @param name Display name of the head
     * @param description Description of the head's origin or purpose
     * @param textureData Base64 encoded texture data
     * @param customModelData Custom model data for resource pack integration
     * @param rarity Rarity classification of this head
     */
    public HeadVariant(String id, String name, String description, String textureData, 
                      Integer customModelData, HeadRarity rarity) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = HeadType.CUSTOM;
        this.textureData = textureData;
        this.ownerName = null;
        this.mobType = null;
        this.customModelData = customModelData;
        this.properties = new HashMap<>();
        this.rarity = rarity;
        this.animated = false;
        this.animationFrames = null;
    }
    
    /**
     * Constructor for animated custom head variants.
     *
     * @param id Unique identifier for this variant
     * @param name Display name of the head
     * @param description Description of the head's origin or purpose
     * @param animationFrames Array of base64 texture data for each animation frame
     * @param customModelData Custom model data for resource pack integration
     * @param rarity Rarity classification of this head
     */
    public HeadVariant(String id, String name, String description, String[] animationFrames, 
                      Integer customModelData, HeadRarity rarity) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = HeadType.ANIMATED;
        this.textureData = animationFrames != null && animationFrames.length > 0 ? animationFrames[0] : null;
        this.ownerName = null;
        this.mobType = null;
        this.customModelData = customModelData;
        this.properties = new HashMap<>();
        this.rarity = rarity;
        this.animated = true;
        this.animationFrames = animationFrames != null ? animationFrames.clone() : null;
    }
    
    /**
     * Add a custom property to this head variant.
     *
     * @param key Property key
     * @param value Property value
     */
    public void addProperty(String key, String value) {
        properties.put(key, value);
    }
    
    /**
     * Get a custom property value.
     *
     * @param key Property key
     * @return Property value or null if not found
     */
    public String getProperty(String key) {
        return properties.get(key);
    }
    
    /**
     * Get the texture data for a specific animation frame.
     *
     * @param frameIndex Index of the animation frame
     * @return Texture data for the frame or null if invalid index
     */
    public String getAnimationFrame(int frameIndex) {
        if (animationFrames != null && frameIndex >= 0 && frameIndex < animationFrames.length) {
            return animationFrames[frameIndex];
        }
        return null;
    }
    
    /**
     * Get the number of animation frames.
     *
     * @return Number of frames, or 0 if not animated
     */
    public int getAnimationFrameCount() {
        return animationFrames != null ? animationFrames.length : 0;
    }
    
    /**
     * Check if this head variant requires special permissions to obtain.
     *
     * @return True if special permissions are required
     */
    public boolean requiresPermission() {
        return rarity == HeadRarity.LEGENDARY || rarity == HeadRarity.MYTHIC || 
               properties.containsKey("permission_required");
    }
    
    /**
     * Get the permission node required for this head variant.
     *
     * @return Permission node or null if no special permission required
     */
    public String getRequiredPermission() {
        if (properties.containsKey("permission_required")) {
            return properties.get("permission_required");
        }
        
        // Default permission patterns based on rarity
        switch (rarity) {
            case LEGENDARY:
                return "rvnklore.heads.legendary." + id;
            case MYTHIC:
                return "rvnklore.heads.mythic." + id;
            default:
                return null;
        }
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public HeadType getType() { return type; }
    public String getTextureData() { return textureData; }
    public String getOwnerName() { return ownerName; }
    public EntityType getMobType() { return mobType; }
    public Integer getCustomModelData() { return customModelData; }
    public Map<String, String> getProperties() { return new HashMap<>(properties); }
    public HeadRarity getRarity() { return rarity; }
    public boolean isAnimated() { return animated; }
    public String[] getAnimationFrames() { 
        return animationFrames != null ? animationFrames.clone() : null; 
    }
}

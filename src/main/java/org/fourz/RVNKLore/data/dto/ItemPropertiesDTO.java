package org.fourz.RVNKLore.data.dto;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.ItemType;
import org.fourz.RVNKLore.lore.item.cosmetic.HeadVariant;


/**
 * Data Transfer Object for item properties.
 * Used to transfer item property data between database and domain layers.
 */
public class ItemPropertiesDTO {
    // Core properties
    private int id;
    private ItemType itemType;
    private String material;
    private String displayName;
    private List<String> lore;
    private Integer customModelData;
    private String rarity;
    private boolean obtainable;
    
    // Linking properties
    private String loreEntryId;
    private String nbtData;
    
    // Cosmetic properties
    private boolean glow;
    private String skullTexture;
    private String textureData;
    private String ownerName;
    
    // Collection properties
    private String collectionId;
    private String themeId;
    private String rarityLevel;
    private Integer collectionSequence;
    
    // Meta properties
    private String createdBy;
    private Long createdAt;
    private Map<String, Object> customProperties;
    private Map<String, String> metadata;

    public ItemPropertiesDTO() {}

    public ItemPropertiesDTO(int id, ItemType itemType, String material, 
                           String displayName, List<String> lore,
                           Integer customModelData, String rarity,
                           boolean obtainable, String loreEntryId) {
        this.id = id;
        this.itemType = itemType;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
        this.customModelData = customModelData;
        this.rarity = rarity;
        this.obtainable = obtainable;
        this.loreEntryId = loreEntryId;
    }

    // Core property getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public ItemType getItemType() { return itemType; }
    public void setItemType(ItemType itemType) { this.itemType = itemType; }
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public List<String> getLore() { return lore; }
    public void setLore(List<String> lore) { this.lore = lore; }
    public Integer getCustomModelData() { return customModelData; }
    public void setCustomModelData(Integer customModelData) { this.customModelData = customModelData; }
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
    public boolean isObtainable() { return obtainable; }
    public void setObtainable(boolean obtainable) { this.obtainable = obtainable; }

    // Linking properties getters and setters
    public String getLoreEntryId() { return loreEntryId; }
    public void setLoreEntryId(String loreEntryId) { this.loreEntryId = loreEntryId; }
    public String getNbtData() { return nbtData; }
    public void setNbtData(String nbtData) { this.nbtData = nbtData; }

    // Cosmetic properties getters and setters
    public boolean isGlow() { return glow; }
    public void setGlow(boolean glow) { this.glow = glow; }
    public String getSkullTexture() { return skullTexture; }
    public void setSkullTexture(String skullTexture) { this.skullTexture = skullTexture; }
    public String getTextureData() { return textureData; }
    public void setTextureData(String textureData) { this.textureData = textureData; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    // Collection properties getters and setters
    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getThemeId() { return themeId; }
    public void setThemeId(String themeId) { this.themeId = themeId; }
    public String getRarityLevel() { return rarityLevel; }
    public void setRarityLevel(String rarityLevel) { this.rarityLevel = rarityLevel; }
    public Integer getCollectionSequence() { return collectionSequence; }
    public void setCollectionSequence(Integer collectionSequence) { this.collectionSequence = collectionSequence; }

    // Meta properties getters and setters
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Map<String, Object> getCustomProperties() { return customProperties; }
    public void setCustomProperties(Map<String, Object> customProperties) { this.customProperties = customProperties; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    /**
     * Converts an ItemProperties domain object to a DTO
     */
    public static ItemPropertiesDTO fromItemProperties(ItemProperties properties) {
        if (properties == null) return null;

        ItemPropertiesDTO dto = new ItemPropertiesDTO(
            properties.getDatabaseId(),
            properties.getItemType(),
            properties.getMaterial().name(),
            properties.getDisplayName(),
            properties.getLore(),
            properties.getCustomModelData(),
            properties.getRarity(),
            properties.isObtainable(),
            properties.getLoreEntryId()
        );

        // Set optional properties
        dto.setNbtData(properties.getNbtData());
        dto.setGlow(properties.isGlow());
        dto.setSkullTexture(properties.getSkullTexture());
        dto.setCreatedBy(properties.getCreatedBy());
        dto.setCreatedAt(properties.getCreatedAt());
        
        // Set collection properties
        dto.setCollectionId(properties.getCollectionId());
        dto.setThemeId(properties.getThemeId());
        dto.setRarityLevel(properties.getRarityLevel());
        dto.setCollectionSequence(properties.getCollectionSequence());

        // Copy custom properties and metadata
        if (properties.hasCustomProperties()) {
            dto.setCustomProperties(properties.getAllCustomProperties());
        }
        dto.setMetadata(properties.getAllMetadata());

        // Copy head variant properties if exists
        HeadVariant headVariant = properties.getHeadVariant();
        if (headVariant != null) {
            dto.setTextureData(properties.getTextureData());
            dto.setOwnerName(properties.getOwnerName());
        }

        return dto;
    }

    /**
     * Converts this DTO to an ItemProperties domain object
     */
    public ItemProperties toItemProperties() {
        ItemProperties properties = new ItemProperties(Material.valueOf(material), displayName);
        
        // Set core properties
        properties.setDatabaseId(id);
        properties.setItemType(itemType);
        properties.setLore(lore);
        if (customModelData != null) {
            properties.setCustomModelData(customModelData);
        }
        properties.setRarity(rarity);
        properties.setObtainable(obtainable);
        properties.setLoreEntryId(loreEntryId);
        
        // Set optional properties
        if (nbtData != null) properties.setNbtData(nbtData);
        properties.setGlow(glow);
        if (skullTexture != null) properties.setSkullTexture(skullTexture);
        if (createdBy != null) properties.setCreatedBy(createdBy);
        if (createdAt != null) properties.setCreatedAt(createdAt);
        
        // Set collection properties
        if (collectionId != null) properties.setCollectionId(collectionId);
        if (themeId != null) properties.setThemeId(themeId);
        if (rarityLevel != null) properties.setRarityLevel(rarityLevel);
        if (collectionSequence != null) properties.setCollectionSequence(collectionSequence);

        // Set custom properties and metadata
        if (customProperties != null) {
            for (Map.Entry<String, Object> entry : customProperties.entrySet()) {
                properties.setCustomProperty(entry.getKey(), entry.getValue());
            }
        }
        if (metadata != null) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                properties.setMetadata(entry.getKey(), entry.getValue());
            }
        }

        // Set head variant properties if exists
        if (textureData != null || ownerName != null) {
            HeadVariant headVariant = new HeadVariant();
            if (textureData != null) properties.setTextureData(textureData);
            if (ownerName != null) properties.setOwnerName(ownerName);
            properties.setHeadVariant(headVariant);
        }

        return properties;
    }
}

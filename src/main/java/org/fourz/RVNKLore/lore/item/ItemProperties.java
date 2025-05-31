package org.fourz.RVNKLore.lore.item;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.fourz.RVNKLore.lore.item.cosmetic.HeadVariant;
import org.fourz.RVNKLore.lore.item.enchant.EnchantmentTier;

import java.util.List;
import java.util.Map;

/**
 * Container class for item properties used across different item management systems.
 * Provides a flexible way to pass configuration data to item creation methods.
 */
public class ItemProperties {
    // Basic item properties
    private Material material;
    private String displayName;
    private List<String> lore;
    private Integer customModelData;
    
    // Enchantment properties
    private Map<Enchantment, Integer> enchantments;
    private EnchantmentTier enchantmentTier;
    
    // Cosmetic properties
    private HeadVariant headVariant;
    private String textureData;
    private String ownerName;
    
    // Collection properties
    private String collectionId;
    private String themeId;
    private String rarityLevel;
    
    // Generic metadata
    private Map<String, String> metadata;
    
    /**
     * Create new item properties with basic configuration.
     * 
     * @param material The item material
     * @param displayName The display name
     */
    public ItemProperties(Material material, String displayName) {
        this.material = material;
        this.displayName = displayName;
    }
    
    // Getters and setters for all properties
    
    public Material getMaterial() {
        return material;
    }
    
    public ItemProperties setMaterial(Material material) {
        this.material = material;
        return this;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public ItemProperties setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }
    
    public List<String> getLore() {
        return lore;
    }
    
    public ItemProperties setLore(List<String> lore) {
        this.lore = lore;
        return this;
    }
    
    public Integer getCustomModelData() {
        return customModelData;
    }
    
    public ItemProperties setCustomModelData(Integer customModelData) {
        this.customModelData = customModelData;
        return this;
    }
    
    public Map<Enchantment, Integer> getEnchantments() {
        return enchantments;
    }
    
    public ItemProperties setEnchantments(Map<Enchantment, Integer> enchantments) {
        this.enchantments = enchantments;
        return this;
    }
    
    public EnchantmentTier getEnchantmentTier() {
        return enchantmentTier;
    }
    
    public ItemProperties setEnchantmentTier(EnchantmentTier enchantmentTier) {
        this.enchantmentTier = enchantmentTier;
        return this;
    }
    
    public HeadVariant getHeadVariant() {
        return headVariant;
    }
    
    public ItemProperties setHeadVariant(HeadVariant headVariant) {
        this.headVariant = headVariant;
        return this;
    }
    
    public String getTextureData() {
        return textureData;
    }
    
    public ItemProperties setTextureData(String textureData) {
        this.textureData = textureData;
        return this;
    }
    
    public String getOwnerName() {
        return ownerName;
    }
    
    public ItemProperties setOwnerName(String ownerName) {
        this.ownerName = ownerName;
        return this;
    }
    
    public String getCollectionId() {
        return collectionId;
    }
    
    public ItemProperties setCollectionId(String collectionId) {
        this.collectionId = collectionId;
        return this;
    }
    
    public String getThemeId() {
        return themeId;
    }
    
    public ItemProperties setThemeId(String themeId) {
        this.themeId = themeId;
        return this;
    }
    
    public String getRarityLevel() {
        return rarityLevel;
    }
    
    public ItemProperties setRarityLevel(String rarityLevel) {
        this.rarityLevel = rarityLevel;
        return this;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public ItemProperties setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
        return this;
    }
    
    /**
     * Add a metadata entry.
     * 
     * @param key The metadata key
     * @param value The metadata value
     * @return This ItemProperties instance for chaining
     */
    public ItemProperties addMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new java.util.HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }
    
    /**
     * Get a metadata value by key.
     * 
     * @param key The metadata key
     * @return The metadata value, or null if not found
     */
    public String getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
}

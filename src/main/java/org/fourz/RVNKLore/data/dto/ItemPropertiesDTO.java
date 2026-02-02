package org.fourz.RVNKLore.data.dto;

import org.bukkit.Material;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.ItemType;
import org.fourz.RVNKLore.lore.item.cosmetic.HeadRarity;
import org.fourz.RVNKLore.lore.item.cosmetic.HeadVariant;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data Transfer Object for ItemProperties using Java Record.
 * Immutable and thread-safe for cross-plugin data transfer via RVNKCore.
 */
public record ItemPropertiesDTO(
    // Core properties
    int id,
    ItemType itemType,
    String material,
    String displayName,
    List<String> lore,
    Integer customModelData,
    String rarity,
    boolean obtainable,

    // Linking properties
    String loreEntryId,
    String nbtData,

    // Cosmetic properties
    boolean glow,
    String skullTexture,
    String textureData,
    String ownerName,

    // Collection properties
    String collectionId,
    String themeId,
    String rarityLevel,
    Integer collectionSequence,

    // Meta properties
    String createdBy,
    Long createdAt,
    Map<String, Object> customProperties,
    Map<String, String> metadata
) {
    /**
     * Compact constructor with validation and defensive copies.
     */
    public ItemPropertiesDTO {
        Objects.requireNonNull(material, "material cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");

        // Defensive copies for mutable collections
        lore = lore == null ? List.of() : List.copyOf(lore);
        customProperties = customProperties == null ? Map.of() : Map.copyOf(customProperties);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Factory method from ItemProperties domain object.
     *
     * @param properties The domain entity to convert
     * @return A new ItemPropertiesDTO, or null if properties is null
     */
    public static ItemPropertiesDTO from(ItemProperties properties) {
        if (properties == null) return null;

        HeadVariant headVariant = properties.getHeadVariant();

        return new ItemPropertiesDTO(
            properties.getDatabaseId(),
            properties.getItemType(),
            properties.getMaterial().name(),
            properties.getDisplayName(),
            properties.getLore(),
            properties.getCustomModelData(),
            properties.getRarity(),
            properties.isObtainable(),
            properties.getLoreEntryId(),
            properties.getNbtData(),
            properties.isGlow(),
            properties.getSkullTexture(),
            headVariant != null ? properties.getTextureData() : null,
            headVariant != null ? properties.getOwnerName() : null,
            properties.getCollectionId(),
            properties.getThemeId(),
            properties.getRarityLevel(),
            properties.getCollectionSequence(),
            properties.getCreatedBy(),
            properties.getCreatedAt(),
            properties.hasCustomProperties() ? properties.getAllCustomProperties() : null,
            properties.getAllMetadata()
        );
    }

    /**
     * Converts this DTO to an ItemProperties domain object.
     *
     * @return A new ItemProperties populated with DTO values
     */
    public ItemProperties toEntity() {
        ItemProperties properties = new ItemProperties(Material.valueOf(material), displayName);

        // Set core properties
        properties.setDatabaseId(id);
        properties.setItemType(itemType);
        properties.setLore(lore.isEmpty() ? null : new java.util.ArrayList<>(lore));
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
        if (!customProperties.isEmpty()) {
            for (Map.Entry<String, Object> entry : customProperties.entrySet()) {
                properties.setCustomProperty(entry.getKey(), entry.getValue());
            }
        }
        if (!metadata.isEmpty()) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                properties.setMetadata(entry.getKey(), entry.getValue());
            }
        }

        // Set head variant properties if exists
        if (textureData != null || ownerName != null) {
            HeadVariant headVariant = new HeadVariant(
                "generated-" + System.currentTimeMillis(),
                "Generated Head",
                "Automatically generated head variant",
                ownerName != null ? ownerName : "",
                HeadRarity.COMMON
            );

            if (textureData != null) properties.setTextureData(textureData);
            if (ownerName != null) properties.setOwnerName(ownerName);
            properties.setHeadVariant(headVariant);
        }

        return properties;
    }

    /**
     * Builder for constructing ItemPropertiesDTO with optional fields.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for ItemPropertiesDTO.
     */
    public static class Builder {
        private int id;
        private ItemType itemType;
        private String material;
        private String displayName;
        private List<String> lore;
        private Integer customModelData;
        private String rarity;
        private boolean obtainable = true;
        private String loreEntryId;
        private String nbtData;
        private boolean glow;
        private String skullTexture;
        private String textureData;
        private String ownerName;
        private String collectionId;
        private String themeId;
        private String rarityLevel;
        private Integer collectionSequence;
        private String createdBy;
        private Long createdAt;
        private Map<String, Object> customProperties;
        private Map<String, String> metadata;

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder itemType(ItemType itemType) {
            this.itemType = itemType;
            return this;
        }

        public Builder material(String material) {
            this.material = material;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder lore(List<String> lore) {
            this.lore = lore;
            return this;
        }

        public Builder customModelData(Integer customModelData) {
            this.customModelData = customModelData;
            return this;
        }

        public Builder rarity(String rarity) {
            this.rarity = rarity;
            return this;
        }

        public Builder obtainable(boolean obtainable) {
            this.obtainable = obtainable;
            return this;
        }

        public Builder loreEntryId(String loreEntryId) {
            this.loreEntryId = loreEntryId;
            return this;
        }

        public Builder nbtData(String nbtData) {
            this.nbtData = nbtData;
            return this;
        }

        public Builder glow(boolean glow) {
            this.glow = glow;
            return this;
        }

        public Builder skullTexture(String skullTexture) {
            this.skullTexture = skullTexture;
            return this;
        }

        public Builder textureData(String textureData) {
            this.textureData = textureData;
            return this;
        }

        public Builder ownerName(String ownerName) {
            this.ownerName = ownerName;
            return this;
        }

        public Builder collectionId(String collectionId) {
            this.collectionId = collectionId;
            return this;
        }

        public Builder themeId(String themeId) {
            this.themeId = themeId;
            return this;
        }

        public Builder rarityLevel(String rarityLevel) {
            this.rarityLevel = rarityLevel;
            return this;
        }

        public Builder collectionSequence(Integer collectionSequence) {
            this.collectionSequence = collectionSequence;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder createdAt(Long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder customProperties(Map<String, Object> customProperties) {
            this.customProperties = customProperties;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ItemPropertiesDTO build() {
            return new ItemPropertiesDTO(
                id, itemType, material, displayName, lore, customModelData, rarity, obtainable,
                loreEntryId, nbtData, glow, skullTexture, textureData, ownerName,
                collectionId, themeId, rarityLevel, collectionSequence,
                createdBy, createdAt, customProperties, metadata
            );
        }
    }
}

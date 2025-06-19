package org.fourz.RVNKLore.data.dto;

import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;
import org.fourz.RVNKLore.lore.item.collection.CollectionTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for ItemCollection.
 * Used to transfer collection data between database and domain layers.
 */
public class ItemCollectionDTO {
    private String id;
    private String name;
    private String description;
    private String themeId;
    private boolean isActive;
    private long createdAt;
    private long updatedAt;
    private List<String> serializedItems; // Stored as serialized Base64 strings

    public ItemCollectionDTO() {
        this.serializedItems = new ArrayList<>();
    }

    /**
     * Create a new ItemCollectionDTO with the required fields
     *
     * @param id The collection identifier
     * @param name The display name
     * @param description The collection description
     */
    public ItemCollectionDTO(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isActive = true;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.serializedItems = new ArrayList<>();
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getThemeId() { return themeId; }
    public void setThemeId(String themeId) { this.themeId = themeId; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    
    public List<String> getSerializedItems() { return serializedItems; }
    public void setSerializedItems(List<String> serializedItems) { this.serializedItems = serializedItems; }

    /**
     * Convert from domain model to DTO
     *
     * @param collection The domain model object
     * @return A new DTO representing the collection
     */
    public static ItemCollectionDTO fromCollection(ItemCollection collection) {
        if (collection == null) {
            return null;
        }
        
        ItemCollectionDTO dto = new ItemCollectionDTO(
            collection.getId(),
            collection.getName(),
            collection.getDescription()
        );
        
        dto.setThemeId(collection.getThemeId());
        dto.setActive(collection.isActive());
        dto.setCreatedAt(collection.getCreatedAt());
        dto.setUpdatedAt(System.currentTimeMillis());
        
        // Serialize items if present (using utility to be defined in ItemSerializer)
        List<ItemStack> items = collection.getItems();
        if (items != null && !items.isEmpty()) {
            List<String> serialized = new ArrayList<>();
            for (ItemStack item : items) {
                // Note: Actual serialization will be handled by ItemSerializer utility
                // This is a placeholder - implementation in DatabaseManager or similar
                if (item != null) {
                    serialized.add("SERIALIZED_ITEM_PLACEHOLDER");
                }
            }
            dto.setSerializedItems(serialized);
        }
        
        return dto;
    }

    /**
     * Convert from DTO to domain model
     *
     * @return A new ItemCollection domain object
     */
    public ItemCollection toCollection() {
        ItemCollection collection = new ItemCollection(id, name, description);
        
        collection.setThemeId(themeId);
        collection.setActive(isActive);
        
        // Note: Item deserialization would happen here with the ItemSerializer
        // Not implemented in this initial version as it depends on utilities
        
        return collection;
    }
}

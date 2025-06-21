package org.fourz.RVNKLore.lore.item.collection;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a collection of items organized around a common theme or purpose.
 * Provides functionality for managing items within the collection and tracking metadata.
 */
public class ItemCollection {
    private final String id;
    private final String name;
    private final String description;
    private final List<ItemStack> items;
    private String themeId;
    private boolean isActive;
    private long createdAt;
    private CollectionRewards rewards;
    
    public ItemCollection(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.items = new CopyOnWriteArrayList<>();
        this.isActive = true;
        this.createdAt = System.currentTimeMillis();
    }
    
    /**
     * Get the collection identifier.
     * 
     * @return The collection ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * Get the collection display name.
     * 
     * @return The collection name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get the collection description.
     * 
     * @return The collection description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Get all items in the collection.
     * 
     * @return List of items
     */
    public List<ItemStack> getItems() {
        return new ArrayList<>(items);
    }
    
    /**
     * Add an item to the collection.
     * 
     * @param item The item to add
     */
    public void addItem(ItemStack item) {
        if (item != null) {
            items.add(item.clone());
        }
    }
    
    /**
     * Remove an item from the collection.
     * 
     * @param item The item to remove
     * @return True if the item was removed
     */
    public boolean removeItem(ItemStack item) {
        return items.removeIf(existing -> existing.isSimilar(item));
    }
    
    /**
     * Check if the collection contains a specific item.
     * 
     * @param item The item to check
     * @return True if the collection contains the item
     */
    public boolean containsItem(ItemStack item) {
        return items.stream().anyMatch(existing -> existing.isSimilar(item));
    }
    
    /**
     * Get the number of items in the collection.
     * 
     * @return The item count
     */
    public int getItemCount() {
        return items.size();
    }
    
    /**
     * Clear all items from the collection.
     */
    public void clearItems() {
        items.clear();
    }
    
    /**
     * Get the theme identifier for this collection.
     * 
     * @return The theme ID
     */
    public String getThemeId() {
        return themeId;
    }
    
    /**
     * Set the theme identifier for this collection.
     * 
     * @param themeId The theme ID
     */
    public void setThemeId(String themeId) {
        this.themeId = themeId;
    }
    
    /**
     * Check if the collection is active.
     * 
     * @return True if active
     */
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Set the active status of the collection.
     * 
     * @param active The active status
     */
    public void setActive(boolean active) {
        this.isActive = active;
    }
    
    /**
     * Get the creation timestamp.
     * 
     * @return The creation time in milliseconds
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Get the rewards for this collection.
     *
     * @return The CollectionRewards object, or null if none set
     */
    public CollectionRewards getRewards() {
        return rewards;
    }

    /**
     * Set the rewards for this collection.
     *
     * @param rewards The CollectionRewards to set
     */
    public void setRewards(CollectionRewards rewards) {
        this.rewards = rewards;
    }
    
    @Override
    public String toString() {
        return "ItemCollection{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", itemCount=" + items.size() +
                ", isActive=" + isActive +
                '}';
    }
}

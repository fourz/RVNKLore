package org.fourz.RVNKLore.lore.item.collection;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class LoreCollection {
    private final String id;
    private final String name;
    private final String description;
    private final List<ItemStack> items;
    private final List<UUID> requiredEntryIds;
    private String themeId;
    private boolean isActive;
    private long createdAt;
    private String rewardEntryId;
    private String rewardAchievementId;

    public LoreCollection(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.items = new CopyOnWriteArrayList<>();
        this.requiredEntryIds = new CopyOnWriteArrayList<>();
        this.isActive = true;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }

    public List<ItemStack> getItems() { return new ArrayList<>(items); }

    public void addItem(ItemStack item) {
        if (item != null) items.add(item.clone());
    }

    public boolean removeItem(ItemStack item) {
        return items.removeIf(existing -> existing.isSimilar(item));
    }

    public boolean containsItem(ItemStack item) {
        return items.stream().anyMatch(existing -> existing.isSimilar(item));
    }

    public int getItemCount() { return items.size(); }

    public void clearItems() { items.clear(); }

    public void addRequiredEntry(UUID entryId) {
        if (entryId != null && !requiredEntryIds.contains(entryId)) {
            requiredEntryIds.add(entryId);
        }
    }

    public boolean removeRequiredEntry(UUID entryId) {
        return entryId != null && requiredEntryIds.remove(entryId);
    }

    public List<UUID> getRequiredEntryIds() { return new ArrayList<>(requiredEntryIds); }

    public boolean hasRequiredEntry(UUID entryId) {
        return entryId != null && requiredEntryIds.contains(entryId);
    }

    public int getRequiredEntryCount() { return requiredEntryIds.size(); }

    public String getThemeId() { return themeId; }
    public void setThemeId(String themeId) { this.themeId = themeId; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { this.isActive = active; }

    public long getCreatedAt() { return createdAt; }

    /**
     * Restore the original creation timestamp when loading from storage.
     *
     * Without this the constructor's System.currentTimeMillis() survives the round trip, so every
     * collection read back from the database is stamped with load time and the "Newest First"
     * ordering in `/lore collection list` degrades to load order after any restart (#1956).
     *
     * @param createdAt Epoch millis; ignored when not positive, keeping the constructor default
     */
    public void setCreatedAt(long createdAt) {
        if (createdAt > 0) this.createdAt = createdAt;
    }

    public String getRewardEntryId() { return rewardEntryId; }
    public void setRewardEntryId(String rewardEntryId) { this.rewardEntryId = rewardEntryId; }

    public String getRewardAchievementId() { return rewardAchievementId; }
    public void setRewardAchievementId(String rewardAchievementId) { this.rewardAchievementId = rewardAchievementId; }

    @Override
    public String toString() {
        return "LoreCollection{id='" + id + "', name='" + name + "', itemCount=" + items.size() + ", isActive=" + isActive + '}';
    }
}

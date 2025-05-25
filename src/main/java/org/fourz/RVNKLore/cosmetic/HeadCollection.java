package org.fourz.RVNKLore.cosmetic;

import org.fourz.RVNKLore.lore.LoreEntry;

import java.util.*;

/**
 * Represents a thematic collection of heads with shared properties and grouping.
 * Collections provide organized access to related head items and track completion status.
 */
public class HeadCollection {
    private final String id;
    private final String name;
    private final String description;
    private final CollectionTheme theme;
    private final Map<String, HeadVariant> heads;
    private final List<String> requirements;
    private final CollectionRewards rewards;
    private final boolean seasonal;
    private final Date startDate;
    private final Date endDate;
    
    /**
     * Constructor for creating a new head collection.
     *
     * @param id Unique identifier for the collection
     * @param name Display name of the collection
     * @param description Description of the collection theme and purpose
     * @param theme Thematic categorization for the collection
     */
    public HeadCollection(String id, String name, String description, CollectionTheme theme) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.theme = theme;
        this.heads = new HashMap<>();
        this.requirements = new ArrayList<>();
        this.rewards = new CollectionRewards();
        this.seasonal = false;
        this.startDate = null;
        this.endDate = null;
    }
    
    /**
     * Constructor for creating a seasonal head collection.
     *
     * @param id Unique identifier for the collection
     * @param name Display name of the collection
     * @param description Description of the collection theme and purpose
     * @param theme Thematic categorization for the collection
     * @param startDate Start date for seasonal availability
     * @param endDate End date for seasonal availability
     */
    public HeadCollection(String id, String name, String description, CollectionTheme theme, 
                         Date startDate, Date endDate) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.theme = theme;
        this.heads = new HashMap<>();
        this.requirements = new ArrayList<>();
        this.rewards = new CollectionRewards();
        this.seasonal = true;
        this.startDate = startDate;
        this.endDate = endDate;
    }
    
    /**
     * Add a head variant to this collection.
     *
     * @param variant The head variant to add
     */
    public void addHead(HeadVariant variant) {
        heads.put(variant.getId(), variant);
    }
    
    /**
     * Remove a head variant from this collection.
     *
     * @param headId ID of the head variant to remove
     * @return True if the head was removed, false if not found
     */
    public boolean removeHead(String headId) {
        return heads.remove(headId) != null;
    }
    
    /**
     * Get a specific head variant by ID.
     *
     * @param headId ID of the head variant
     * @return The head variant or null if not found
     */
    public HeadVariant getHead(String headId) {
        return heads.get(headId);
    }
    
    /**
     * Get all head variants in this collection.
     *
     * @return Unmodifiable collection of head variants
     */
    public Collection<HeadVariant> getAllHeads() {
        return Collections.unmodifiableCollection(heads.values());
    }
    
    /**
     * Get the number of heads in this collection.
     *
     * @return Number of head variants
     */
    public int getHeadCount() {
        return heads.size();
    }
    
    /**
     * Add a requirement for completing this collection.
     *
     * @param requirement Description of the requirement
     */
    public void addRequirement(String requirement) {
        requirements.add(requirement);
    }
    
    /**
     * Check if this collection is currently available (considers seasonal dates).
     *
     * @return True if the collection is available now
     */
    public boolean isAvailable() {
        if (!seasonal) {
            return true;
        }
        
        Date now = new Date();
        return (startDate == null || now.after(startDate)) && 
               (endDate == null || now.before(endDate));
    }
    
    /**
     * Calculate completion percentage for a player.
     *
     * @param playerHeads Set of head IDs the player owns
     * @return Completion percentage (0.0 to 1.0)
     */
    public double getCompletionPercentage(Set<String> playerHeads) {
        if (heads.isEmpty()) {
            return 1.0;
        }
        
        long ownedCount = heads.keySet().stream()
            .mapToLong(headId -> playerHeads.contains(headId) ? 1 : 0)
            .sum();
            
        return (double) ownedCount / heads.size();
    }
    
    /**
     * Check if a player has completed this collection.
     *
     * @param playerHeads Set of head IDs the player owns
     * @return True if the collection is complete
     */
    public boolean isComplete(Set<String> playerHeads) {
        return heads.keySet().stream().allMatch(playerHeads::contains);
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CollectionTheme getTheme() { return theme; }
    public List<String> getRequirements() { return Collections.unmodifiableList(requirements); }
    public CollectionRewards getRewards() { return rewards; }
    public boolean isSeasonal() { return seasonal; }
    public Date getStartDate() { return startDate; }
    public Date getEndDate() { return endDate; }
}

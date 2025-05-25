package org.fourz.RVNKLore.lore;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Utility class for finding lore entries
 */
public class LoreFinder {
    private final RVNKLore plugin;
    private final LoreManager loreManager;
    private final Debug debug;
    
    public LoreFinder(RVNKLore plugin, LoreManager loreManager) {
        this.plugin = plugin;
        this.loreManager = loreManager;
        this.debug = Debug.createDebugger(plugin, "LoreFinder", Level.FINE);
    }
    
    /**
     * Find lore entries near a location
     * 
     * @param location The location to search near
     * @param radius The search radius
     * @return List of nearby lore entries
     */
    public List<LoreEntry> findNearbyLore(Location location, double radius) {
        debug.debug("Finding lore near " + location.getWorld().getName() + 
                   " at " + location.getX() + "," + location.getY() + "," + location.getZ() + 
                   " with radius " + radius);
                   
        return loreManager.getCachedEntries().stream()
                .filter(LoreEntry::isApproved)
                .filter(entry -> {
                    if (entry.getLocation() == null) return false;
                    if (!entry.getLocation().getWorld().equals(location.getWorld())) return false;
                    return entry.getLocation().distance(location) <= radius;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Find all lore entries in a specific world
     * 
     * @param worldName The name of the world
     * @return List of lore entries in the world
     */
    public List<LoreEntry> findLoreInWorld(String worldName) {
        debug.debug("Finding lore in world: " + worldName);
        
        return loreManager.getCachedEntries().stream()
                .filter(LoreEntry::isApproved)
                .filter(entry -> {
                    if (entry.getLocation() == null) return false;
                    return entry.getLocation().getWorld().getName().equals(worldName);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Find lore by player who submitted it
     * 
     * @param playerName The name of the player
     * @return List of lore entries submitted by the player
     */
    public List<LoreEntry> findLoreBySubmitter(String playerName) {
        debug.debug("Finding lore submitted by: " + playerName);
        
        return loreManager.getCachedEntries().stream()
                .filter(entry -> {
                    if (entry.getSubmittedBy() == null) return false;
                    return entry.getSubmittedBy().equalsIgnoreCase(playerName);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Get a lore entry by UUID
     * 
     * @param id The UUID of the lore entry
     * @return The lore entry or null if not found
     */
    public LoreEntry getLoreEntry(UUID id) {
        return loreManager.getCachedEntries().stream()
                .filter(entry -> entry.getUUID().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Find pending lore entries that need approval
     * 
     * @return List of unapproved lore entries
     */
    public List<LoreEntry> findPendingLoreEntries() {
        return loreManager.getCachedEntries().stream()
                .filter(entry -> !entry.isApproved())
                .collect(Collectors.toList());
    }
    
    /**
     * Find recent lore entries
     * 
     * @param count Number of recent entries to return
     * @return List of most recent lore entries
     */
    public List<LoreEntry> findRecentLoreEntries(int count) {
        return loreManager.getCachedEntries().stream()
                .filter(LoreEntry::isApproved)
                .sorted((e1, e2) -> {
                    // Sort by creation time if available, fallback to ID
                    if (e1.getMetadata("creation_time") != null && e2.getMetadata("creation_time") != null) {
                        return e2.getMetadata("creation_time").compareTo(e1.getMetadata("creation_time"));
                    }
                    return e2.getId().compareTo(e1.getId());
                })
                .limit(count)
                .collect(Collectors.toList());
    }
    
    /**
     * Search for lore entries containing a text fragment
     * 
     * @param searchText The text to search for
     * @return List of matching lore entries
     */
    public List<LoreEntry> searchLore(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return new ArrayList<>();
        }
        
        String lowerCaseSearch = searchText.toLowerCase();
        debug.debug("Searching for lore containing: " + lowerCaseSearch);
        
        return loreManager.getCachedEntries().stream()
                .filter(LoreEntry::isApproved)
                .filter(entry -> {
                    if (entry.getName() != null && entry.getName().toLowerCase().contains(lowerCaseSearch)) {
                        return true;
                    }
                    if (entry.getDescription() != null && entry.getDescription().toLowerCase().contains(lowerCaseSearch)) {
                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Find lore that might be relevant to a player's current activity
     * 
     * @param player The player
     * @return List of contextually relevant lore entries
     */
    public List<LoreEntry> findRelevantLore(Player player) {
        List<LoreEntry> relevantLore = new ArrayList<>();
        
        // Add nearby lore
        relevantLore.addAll(findNearbyLore(player.getLocation(), 100.0));
        
        // Add lore specific to this player
        relevantLore.addAll(findLoreBySubmitter(player.getName()));
        
        // Add any player-specific lore
        loreManager.getCachedEntries().stream()
                .filter(LoreEntry::isApproved)
                .filter(entry -> {
                    if (entry.getMetadata("player_uuid") != null) {
                        return entry.getMetadata("player_uuid").equals(player.getUniqueId().toString());
                    }
                    return false;
                })
                .forEach(relevantLore::add);
                
        return relevantLore.stream().distinct().collect(Collectors.toList());
    }
}

package org.fourz.RVNKLore.lore;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.util.Debug;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Utility class for finding lore entries by various criteria
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
     * Find lore entries near a player
     * 
     * @param player The player to search near
     * @param radius The search radius in blocks
     * @return List of nearby lore entries
     */
    public List<LoreEntry> findNearbyLore(Player player, double radius) {
        debug.debug("Finding lore entries near player: " + player.getName() + " within " + radius + " blocks");
        return findNearbyLore(player.getLocation(), radius);
    }
    
    /**
     * Find lore entries near a location
     * 
     * @param location The location to search near
     * @param radius The search radius in blocks
     * @return List of nearby lore entries
     */
    public List<LoreEntry> findNearbyLore(Location location, double radius) {
        debug.debug("Finding lore entries near location: " + 
                    location.getWorld().getName() + " at (" + 
                    location.getX() + ", " + 
                    location.getY() + ", " + 
                    location.getZ() + ") within " + radius + " blocks");
        
        return loreManager.getCachedEntries().stream()
                .filter(entry -> entry.isApproved() || !plugin.getConfigManager().requireApproval())
                .filter(entry -> {
                    Location entryLoc = entry.getLocation();
                    if (entryLoc == null) {
                        return false;
                    }
                    
                    if (!entryLoc.getWorld().equals(location.getWorld())) {
                        return false;
                    }
                    
                    return entryLoc.distance(location) <= radius;
                })
                .sorted(Comparator.comparingDouble(entry -> 
                    entry.getLocation().distance(location)))
                .collect(Collectors.toList());
    }
    
    /**
     * Get a lore entry by its UUID
     * 
     * @param id The UUID of the lore entry
     * @return The lore entry, or null if not found
     */
    public LoreEntry getLoreEntry(UUID id) {
        debug.debug("Finding lore entry by ID: " + id);
        return loreManager.getCachedEntries().stream()
                .filter(entry -> entry.getUUID().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Find lore entries by a keyword in their name or description
     * 
     * @param keyword The keyword to search for
     * @return List of matching lore entries
     */
    public List<LoreEntry> findLoreByKeyword(String keyword) {
        debug.debug("Finding lore entries by keyword: " + keyword);
        String searchTerm = keyword.toLowerCase();
        
        return loreManager.getCachedEntries().stream()
                .filter(entry -> entry.isApproved() || !plugin.getConfigManager().requireApproval())
                .filter(entry -> 
                    entry.getName().toLowerCase().contains(searchTerm) || 
                    entry.getDescription().toLowerCase().contains(searchTerm))
                .collect(Collectors.toList());
    }
    
    /**
     * Find lore entries submitted by a player
     * 
     * @param playerName The name of the player
     * @return List of lore entries submitted by the player
     */
    public List<LoreEntry> findLoreBySubmitter(String playerName) {
        debug.debug("Finding lore entries by submitter: " + playerName);
        
        return loreManager.getCachedEntries().stream()
                .filter(entry -> 
                    entry.getSubmittedBy() != null && 
                    entry.getSubmittedBy().equalsIgnoreCase(playerName))
                .collect(Collectors.toList());
    }
    
    /**
     * Get all lore entries in a specific world
     * 
     * @param worldName The name of the world
     * @return List of lore entries in the world
     */
    public List<LoreEntry> getLoreInWorld(String worldName) {
        debug.debug("Finding lore entries in world: " + worldName);
        
        return loreManager.getCachedEntries().stream()
                .filter(entry -> entry.isApproved() || !plugin.getConfigManager().requireApproval())
                .filter(entry -> {
                    Location loc = entry.getLocation();
                    return loc != null && loc.getWorld().getName().equalsIgnoreCase(worldName);
                })
                .collect(Collectors.toList());
    }
}

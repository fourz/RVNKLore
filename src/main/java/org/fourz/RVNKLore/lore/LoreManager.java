package org.fourz.RVNKLore.lore;

import org.bukkit.Location;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.util.Debug;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Manages lore entries and interactions with the database
 */
public class LoreManager {
    private final RVNKLore plugin;
    private final Debug debug;
    private final Map<LoreType, LoreHandler> handlers = new HashMap<>();
    private final Set<LoreEntry> cachedEntries = new HashSet<>();
    private final Map<LoreType, List<LoreEntry>> loreByType = new HashMap<>();
    private static LoreManager instance;

    public LoreManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "LoreManager", Level.FINE);
        // Initialize lists for all lore types
        for (LoreType type : LoreType.values()) {
            loreByType.put(type, new ArrayList<>());
        }
    }

    /**
     * Singleton instance getter
     */
    public static LoreManager getInstance(RVNKLore plugin) {
        if (instance == null) {
            instance = new LoreManager(plugin);
        }
        return instance;
    }

    /**
     * Initialize the lore system and load handlers
     */
    public void initializeLore() {
        debug.debug("Initializing lore system...");
        registerLoreHandlers();
        loadLoreEntries();
        debug.debug("Lore system initialized successfully");
    }

    /**
     * Register all lore type handlers
     */
    private void registerLoreHandlers() {
        debug.debug("Registering lore handlers...");
        handlers.put(LoreType.PLAYER_HEAD, new HeadLoreHandler(plugin));
        handlers.put(LoreType.MOB_HEAD, new HeadLoreHandler(plugin));
        handlers.put(LoreType.HEAD, new HeadLoreHandler(plugin));
        handlers.put(LoreType.HAT, new HeadLoreHandler(plugin));
        handlers.put(LoreType.LANDMARK, new LandmarkLoreHandler(plugin));
        handlers.put(LoreType.CITY, new LandmarkLoreHandler(plugin)); // Temporarily using LandmarkHandler
        handlers.put(LoreType.PATH, new LandmarkLoreHandler(plugin)); // Temporarily using LandmarkHandler
        
        // Add handlers for remaining types with a default handler if needed
        for (LoreType type : LoreType.values()) {
            if (!handlers.containsKey(type)) {
                handlers.put(type, new DefaultLoreHandler(plugin));
            }
        }
        
        debug.debug("Lore handlers registered successfully");
    }

    /**
     * Load all lore entries from the database
     */
    private void loadLoreEntries() {
        debug.debug("Loading lore entries from database...");
        cachedEntries.clear();
        List<LoreEntry> entries = plugin.getDatabaseManager().getAllLoreEntries();
        cachedEntries.addAll(entries);
        
        // Populate loreByType map
        for (LoreEntry entry : entries) {
            loreByType.get(entry.getType()).add(entry);
        }
        
        debug.debug("Loaded " + cachedEntries.size() + " lore entries");
    }

    /**
     * Add a new lore entry
     * 
     * @param entry The lore entry to add
     * @return True if successful, false otherwise
     */
    public boolean addLoreEntry(LoreEntry entry) {
        debug.debug("Adding lore entry: " + entry.getName() + " of type " + entry.getType());
        
        // Validate the entry using the appropriate handler
        LoreHandler handler = handlers.get(entry.getType());
        if (handler != null && !handler.validateEntry(entry)) {
            debug.warning("Lore entry validation failed for: " + entry.getName());
            return false;
        }
        
        // Add to database
        boolean success = plugin.getDatabaseManager().addLoreEntry(entry);
        if (success) {
            cachedEntries.add(entry);
            loreByType.get(entry.getType()).add(entry);
            debug.debug("Lore entry added successfully: " + entry.getId());
        } else {
            debug.warning("Failed to add lore entry to database: " + entry.getName());
        }
        
        return success;
    }

    /**
     * Get a lore entry by ID
     * 
     * @param id The UUID of the lore entry
     * @return The lore entry, or null if not found
     */
    public LoreEntry getLoreEntry(UUID id) {
        return cachedEntries.stream()
                .filter(entry -> entry.getUUID().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all lore entries of a specific type
     * 
     * @param type The type of lore entries to get
     * @return A list of matching lore entries
     */
    public List<LoreEntry> getLoreEntriesByType(LoreType type) {
        return new ArrayList<>(loreByType.get(type));
    }

    /**
     * Get all approved lore entries
     * 
     * @return A list of approved lore entries
     */
    public List<LoreEntry> getApprovedLoreEntries() {
        return cachedEntries.stream()
            .filter(LoreEntry::isApproved)
            .collect(Collectors.toList());
    }

    /**
     * Approve a lore entry
     * 
     * @param id The UUID of the lore entry to approve
     * @return True if successful, false otherwise
     */
    public boolean approveLoreEntry(UUID id) {
        LoreEntry entry = getLoreEntry(id);
        if (entry == null) {
            return false;
        }
        
        entry.setApproved(true);
        boolean success = plugin.getDatabaseManager().updateLoreEntry(entry);
        
        if (success) {
            debug.debug("Lore entry approved: " + id);
        } else {
            debug.warning("Failed to approve lore entry: " + id);
        }
        
        return success;
    }

    /**
     * Reload all lore entries from the database
     */
    public void reloadLore() {
        debug.debug("Reloading lore entries...");
        loadLoreEntries();
    }

    /**
     * Find lore entries near a location
     * 
     * @param location The location to search near
     * @param radius The search radius in blocks
     * @return A list of nearby lore entries
     */
    public List<LoreEntry> findNearbyLoreEntries(Location location, double radius) {
        return cachedEntries.stream()
                .filter(LoreEntry::isApproved)
                .filter(entry -> {
                    if (entry.getLocation() == null) return false;
                    if (!entry.getLocation().getWorld().equals(location.getWorld())) return false;
                    return entry.getLocation().distance(location) <= radius;
                })
                .collect(Collectors.toList());
    }

    /**
     * Export all lore entries to JSON
     * 
     * @return A string containing the JSON representation of all lore entries
     */
    public String exportToJson() {
        return plugin.getDatabaseManager().exportLoreEntriesToJson();
    }

    /**
     * Clean up resources when the plugin is disabled
     */
    public void cleanup() {
        debug.debug("Cleaning up lore manager...");
        cachedEntries.clear();
        loreByType.clear();
        handlers.clear();
        instance = null;
    }

    /**
     * Get a handler for a specific lore type
     * 
     * @param type The lore type
     * @return The handler, or null if not found
     */
    public LoreHandler getHandler(LoreType type) {
        return handlers.get(type);
    }

    /**
     * Get a lore entry by ID string
     */
    public Optional<LoreEntry> getLoreById(String id) {
        return cachedEntries.stream()
                .filter(entry -> entry.getId().equals(id))
                .findFirst();
    }

    /**
     * Get lore entries by name (partial match)
     */
    public List<LoreEntry> getLoreByName(String nameFragment) {
        return cachedEntries.stream()
                .filter(entry -> entry.getName().toLowerCase().contains(nameFragment.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Handle player head lore specifically
     */
    public List<LoreEntry> getPlayerHeadLore() {
        return getLoreEntriesByType(LoreType.PLAYER_HEAD);
    }

    /**
     * Handle mob head lore specifically
     */
    public List<LoreEntry> getMobHeadLore() {
        return getLoreEntriesByType(LoreType.MOB_HEAD);
    }

    /**
     * Get all lore related to wearable items
     */
    public List<LoreEntry> getWearableLore() {
        List<LoreEntry> wearables = new ArrayList<>();
        wearables.addAll(getLoreEntriesByType(LoreType.PLAYER_HEAD));
        wearables.addAll(getLoreEntriesByType(LoreType.MOB_HEAD));
        wearables.addAll(getLoreEntriesByType(LoreType.HEAD));
        wearables.addAll(getLoreEntriesByType(LoreType.HAT));
        return wearables;
    }

    /**
     * Get all location-based lore
     */
    public List<LoreEntry> getLocationLore() {
        List<LoreEntry> locations = new ArrayList<>();
        locations.addAll(getLoreEntriesByType(LoreType.LANDMARK));
        locations.addAll(getLoreEntriesByType(LoreType.CITY));
        locations.addAll(getLoreEntriesByType(LoreType.PATH));
        return locations;
    }

    /**
     * Get all faction and player-related lore
     */
    public List<LoreEntry> getCharacterLore() {
        List<LoreEntry> characters = new ArrayList<>();
        characters.addAll(getLoreEntriesByType(LoreType.PLAYER));
        characters.addAll(getLoreEntriesByType(LoreType.FACTION));
        return characters;
    }

    /**
     * Get all gameplay-related lore
     */
    public List<LoreEntry> getGameplayLore() {
        List<LoreEntry> gameplay = new ArrayList<>();
        gameplay.addAll(getLoreEntriesByType(LoreType.ENCHANTMENT));
        gameplay.addAll(getLoreEntriesByType(LoreType.ITEM));
        gameplay.addAll(getLoreEntriesByType(LoreType.QUEST));
        return gameplay;
    }

    /**
     * Clear all lore entries
     */
    public void clearAllLore() {
        cachedEntries.clear();
        for (List<LoreEntry> entries : loreByType.values()) {
            entries.clear();
        }
    }
}

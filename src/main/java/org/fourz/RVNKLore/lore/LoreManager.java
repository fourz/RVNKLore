package org.fourz.RVNKLore.lore;

import org.bukkit.Location;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.handler.LoreHandler;
import org.fourz.RVNKLore.lore.item.ItemManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages lore entries and interactions with the database
 */
public class LoreManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Set<LoreEntry> cachedEntries = new HashSet<>();
    private final Map<LoreType, List<LoreEntry>> loreByType = new HashMap<>();
    private static LoreManager instance;
    private LoreFinder loreFinder;
    private ItemManager itemManager;
    private boolean initializing = false;

    public LoreManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreManager");
        // Use the configured log level from ConfigManager instead of hardcoding Level.FINE
        //this.debug = Debug.createDebugger(plugin, "LoreManager", plugin.getConfigManager().getLogLevel());
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
     */    public void initializeLore() {
        if (initializing) {
            logger.info("Lore system initialization already in progress, skipping recursive call");
            return;
        }
        try {
            initializing = true;
            logger.info("Initializing lore system...");
            
            // Initialize the unified item management system
            this.itemManager = new ItemManager(plugin);
            logger.info("Item management system initialized");
            
            // First load entries from database (doesn't require handlers)
            loadLoreEntries();
            // Then create the LoreFinder (should be after entries are loaded)
            this.loreFinder = new LoreFinder(plugin, this);
            logger.info("Lore system initialized successfully");
        } catch (Exception e) {
            logger.error("Error initializing lore system", e);
        } finally {
            initializing = false;
        }
    }

    /**
     * Load all lore entries from the database
     */
    private void loadLoreEntries() {
        logger.info("Loading lore entries from database...");
        cachedEntries.clear();
        List<LoreEntry> entries = plugin.getDatabaseManager().getAllLoreEntries();
        cachedEntries.addAll(entries);
        // Populate loreByType map
        for (LoreEntry entry : entries) {
            loreByType.get(entry.getType()).add(entry);
        }
        logger.info("Loaded " + cachedEntries.size() + " lore entries");
    }    /**
     * Add a new lore entry
     * 
     * @param entry The lore entry to add
     * @return True if successful, false otherwise
     */
    public boolean addLoreEntry(LoreEntry entry) {
        if (entry == null) {
            logger.warning("Attempted to add null lore entry");
            return false;
        }
        if (entry.getType() == null) {
            logger.warning("Lore entry has null type: " + entry.getName());
            return false;
        }
        logger.info("Adding lore entry: " + entry.getName() + " of type " + entry.getType());
        // Validate the entry using the appropriate handler from HandlerFactory
        LoreHandler handler = plugin.getHandlerFactory().getHandler(entry.getType());
        if (handler == null) {
            logger.warning("No handler found for lore type: " + entry.getType());
            return false;
        }
        if (!handler.validateEntry(entry)) {
            logger.warning("Lore entry validation failed for: " + entry.getName());
            return false;
        }
        
        // Add to database
        boolean success = plugin.getDatabaseManager().addLoreEntry(entry);
        
        if (success) {
            cachedEntries.add(entry);
            loreByType.get(entry.getType()).add(entry);
            logger.info("Lore entry added successfully: " + entry.getId());
              // For ITEM type entries, register the item in the ItemManager
            if (entry.getType() == LoreType.ITEM && itemManager != null) {
                try {
                    // Create basic item properties
                    org.fourz.RVNKLore.lore.item.ItemProperties itemProps = 
                        new org.fourz.RVNKLore.lore.item.ItemProperties(
                            org.bukkit.Material.valueOf("DIAMOND_SWORD"), // Default, should be extracted from entry
                            entry.getName()
                        );
                    
                    // Set additional properties
                    itemProps.setLoreEntryId(entry.getId()); // Important: Link to lore entry ID
                    if (entry.getNbtData() != null) {
                        itemProps.setNbtData(entry.getNbtData());
                    }
                      // Register the item with reference to lore_entry.id
                    // Use the entry ID to link items to lore entries
                    java.util.UUID entryUUID = java.util.UUID.fromString(entry.getId());
                    itemManager.registerLoreItem(entryUUID, itemProps);
                    
                    logger.info("Registered item in ItemManager: " + entry.getName() + " with lore entry ID: " + entry.getId());
                } catch (Exception e) {
                    logger.warning("Failed to register item in ItemManager: " + e.getMessage());
                    // Continue even if item registration fails
                }
            }
        } else {
            logger.warning("Failed to add lore entry to database: " + entry.getName());
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
        LoreEntry entry = loreFinder.getLoreEntry(id);
        if (entry == null) {
            logger.warning("Attempted to approve non-existent lore entry: " + id);
            return false;
        }
        entry.setApproved(true);
        boolean success = plugin.getDatabaseManager().updateLoreEntry(entry);
        if (success) {
            logger.info("Lore entry approved: " + id);
        } else {
            logger.warning("Failed to approve lore entry: " + id);
        }
        return success;
    }

    /**
     * Reload all lore entries from the database
     */
    public void reloadLore() {
        logger.info("Reloading lore entries...");
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
        if (location == null) {
            logger.warning("Attempted to find nearby lore with null location");
            return new ArrayList<>();
        }
        
        return cachedEntries.stream()
                .filter(LoreEntry::isApproved)
                .filter(entry -> {
                    if (entry.getLocation() == null) return false;
                    if (entry.getLocation().getWorld() == null) return false;
                    if (location.getWorld() == null) return false;
                    try {
                        return entry.getLocation().getWorld().getName().equals(location.getWorld().getName()) 
                            && entry.getLocation().distance(location) <= radius;
                    } catch (IllegalArgumentException e) {
                        logger.debug("Error calculating distance for lore: " + entry.getId());
                        return false;
                    }
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
    }    /**
     * Clean up resources when the plugin is disabled
     */
    public void cleanup() {
        logger.info("Cleaning up lore manager...");
        
        // Cleanup item manager first
        if (itemManager != null) {
            itemManager.cleanup();
            itemManager = null;
        }
        
        cachedEntries.clear();
        loreByType.clear();
        instance = null;
    }

    /**
     * Get the item manager for handling all item-related operations.
     *
     * @return The ItemManager instance, or null if not initialized
     */
    public ItemManager getItemManager() {
        return itemManager;
    }

    /**
     * Get a handler for a specific lore type
     * 
     * @param type The lore type
     * @return The handler, or null if not found
     */
    public LoreHandler getHandler(LoreType type) {
        if (initializing) {
            logger.warning("Handler requested during LoreManager initialization - potential circular dependency");
        }
        return plugin.getHandlerFactory().getHandler(type);
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
     * Handle player head lore specifically (filter by metadata)
     */
    public List<LoreEntry> getPlayerHeadLore() {
        return getLoreEntriesByType(LoreType.HEAD).stream()
            .filter(entry -> "player".equals(entry.getMetadata("head_type")))
            .collect(Collectors.toList());
    }

    /**
     * Handle mob head lore specifically (filter by metadata)
     */
    public List<LoreEntry> getMobHeadLore() {
        return getLoreEntriesByType(LoreType.HEAD).stream()
            .filter(entry -> "mob".equals(entry.getMetadata("head_type")))
            .collect(Collectors.toList());
    }

    /**
     * Get all lore related to wearable items
     */
    public List<LoreEntry> getWearableLore() {
        // Now just return all HEAD type entries, as they include all head subtypes
        return getLoreEntriesByType(LoreType.HEAD);
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
    
    /**
     * Get the lore finder instance
     */
    public LoreFinder getLoreFinder() {
        return loreFinder;
    }
    
    /**
     * Package-private method to get cached entries for the LoreFinder
     */
    Set<LoreEntry> getCachedEntries() {
        return cachedEntries;
    }

    /**
     * Get a lore entry by its name
     * 
     * @param name The name of the lore entry
     * @return The lore entry, or null if not found
     */
    public LoreEntry getLoreEntryByName(String name) {
        logger.info("Looking up lore entry by name: " + name);
        
        return cachedEntries.stream()
            .filter(entry -> entry.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get all lore entries
     * 
     * @return A list of all lore entries
     */
    public List<LoreEntry> getAllLoreEntries() {
        return new ArrayList<>(cachedEntries);
    }

    /**
     * Find lore entries whose name or UUID starts with the given fragment (case-insensitive).
     *
     * @param startsWith The string fragment to match at the start of name or UUID
     * @return A list of matching lore entries
     */
    public List<LoreEntry> findLoreEntries(String startsWith) {
        String fragment = startsWith.toLowerCase();
        List<LoreEntry> result = new ArrayList<>();
        for (LoreEntry entry : cachedEntries) {
            if (entry.getId().toLowerCase().startsWith(fragment) ||
                entry.getName().toLowerCase().startsWith(fragment)) {
                result.add(entry);
            }
        }
        return result;
    }
}

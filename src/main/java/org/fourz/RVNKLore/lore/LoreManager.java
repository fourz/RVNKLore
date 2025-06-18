package org.fourz.RVNKLore.lore;

import org.bukkit.Location;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.data.dto.LoreSubmissionDTO;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.sql.Timestamp;

/**
 * Manages lore entries and interactions with the database
 * Handles asynchronous operations and caching for lore entries
 */
public class LoreManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private final Set<LoreEntry> cachedEntries = new HashSet<>();
    private final Map<LoreType, List<LoreEntry>> loreByType = new HashMap<>();
    private static LoreManager instance;
    private ItemManager itemManager;
    private boolean initializing = false;

    public LoreManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreManager");
        this.databaseManager = plugin.getDatabaseManager();
        
        // Initialize loreByType map for all LoreTypes
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
        for (List<LoreEntry> entries : loreByType.values()) {
            entries.clear();
        }

        // Load entries asynchronously but wait for completion during initialization
        databaseManager.getAllLoreEntries()
            .thenAccept(dtos -> {
                for (LoreEntryDTO dto : dtos) {
                    try {
                        // Get the current submission for this entry
                        databaseManager.getCurrentSubmission(dto.getId())
                            .thenAccept(submissionDto -> {
                                if (submissionDto != null) {
                                    // Create LoreEntry with both entry and submission data
                                    LoreEntry entry = convertDTOsToLoreEntry(dto, submissionDto);
                                    cachedEntries.add(entry);
                                    loreByType.get(entry.getType()).add(entry);
                                }
                            })
                            .exceptionally(e -> {
                                logger.error("Error loading submission for lore entry " + dto.getName(), e);
                                return null;
                            });
                    } catch (Exception e) {
                        logger.error("Error processing lore entry " + dto.getName(), e);
                    }
                }
                logger.info("Loaded " + cachedEntries.size() + " lore entries");
            })
            .exceptionally(e -> {
                logger.error("Error loading lore entries from database", e);
                return null;
            })
            .join(); // Wait for completion during initialization
    }

    /**
     * Convert DTOs to a LoreEntry domain object
     */
    private LoreEntry convertDTOsToLoreEntry(LoreEntryDTO entryDto, LoreSubmissionDTO submissionDto) {
        LoreEntry entry = LoreEntry.fromDTO(entryDto);
        entry.setDescription(submissionDto.getContent());
        entry.setApproved(submissionDto.getApprovalStatus().equals("APPROVED"));
        entry.setSubmittedBy(submissionDto.getSubmitterUuid());
        entry.setSubmissionDate(submissionDto.getSubmissionDate());
        return entry;
    }

    /**
     * Add a new lore entry
     * 
     * @param entry The lore entry to add
     * @return A CompletableFuture that will complete with true if successful
     */
    public CompletableFuture<Boolean> addLoreEntry(LoreEntry entry) {
        if (entry == null) {
            logger.warning("Attempted to add null lore entry");
            return CompletableFuture.completedFuture(false);
        }

        // Create a LoreEntryDTO from the entry
        LoreEntryDTO entryDto = entry.toDTO();
        
        return databaseManager.saveLoreEntry(entryDto)
            .thenCompose(entryId -> {
                // Create the submission
                LoreSubmissionDTO submissionDto = new LoreSubmissionDTO();
                submissionDto.setEntryId(entryId);
                submissionDto.setSlug("lore-" + entryId + "-" + System.currentTimeMillis());
                submissionDto.setVisibility("PUBLIC");
                submissionDto.setStatus("ACTIVE");
                submissionDto.setSubmitterUuid(entry.getSubmittedBy());
                submissionDto.setCreatedBy(entry.getSubmittedBy());
                submissionDto.setSubmissionDate(entry.getSubmissionDate());
                submissionDto.setApprovalStatus(entry.isApproved() ? "APPROVED" : "PENDING");
                submissionDto.setContentVersion(1);
                submissionDto.setCurrentVersion(true);
                submissionDto.setContent(entry.getDescription());
                
                return databaseManager.saveLoreSubmission(submissionDto)
                    .thenApply(submissionId -> {
                        // Add to cache
                        entry.setNumericId(entryId);
                        cachedEntries.add(entry);
                        loreByType.get(entry.getType()).add(entry);
                        
                        // For ITEM type entries, register with ItemManager
                        if (entry.getType() == LoreType.ITEM && itemManager != null) {
                            // TODO: Create item properties and register with ItemManager
                        }
                        
                        logger.info("Lore entry added successfully: " + entry.getName());
                        return true;
                    });
            })
            .exceptionally(e -> {
                logger.error("Error adding lore entry: " + entry.getName(), e);
                return false;
            });
    }
    
    /**
     * Approve a lore entry
     * 
     * @param id The numeric ID of the lore entry to approve
     * @param approvedBy The UUID of the player approving the entry
     * @return A CompletableFuture that will complete with true if successful
     */
    public CompletableFuture<Boolean> approveLoreEntry(int id, String approvedBy) {
        return databaseManager.getCurrentSubmission(id)
            .thenCompose(submissionDto -> {
                if (submissionDto == null) {
                    logger.warning("No submission found for entry ID: " + id);
                    return CompletableFuture.completedFuture(false);
                }
                
                // Update approval status
                submissionDto.setApprovalStatus("APPROVED");
                submissionDto.setApprovedBy(approvedBy);
                submissionDto.setApprovedAt(new Timestamp(System.currentTimeMillis()));
                
                return databaseManager.saveLoreSubmission(submissionDto)
                    .thenApply(submissionId -> {
                        // Update cache
                        Optional<LoreEntry> entry = cachedEntries.stream()
                            .filter(e -> e.getNumericId() == id)
                            .findFirst();
                            
                        entry.ifPresent(e -> {
                            e.setApproved(true);
                            e.setDescription(submissionDto.getContent());
                        });
                        
                        logger.info("Lore entry approved successfully: " + id);
                        return true;
                    });
            })
            .exceptionally(e -> {
                logger.error("Error approving lore entry: " + id, e);
                return false;
            });
    }
    
    /**
     * Get all lore entries
     * @return A list of all lore entries
     */
    public List<LoreEntry> getAllLoreEntries() {
        return new ArrayList<>(cachedEntries);
    }
    
    /**
     * Get a lore entry by its database ID
     * 
     * @param id The database ID of the lore entry
     * @return A CompletableFuture that will complete with the entry or null if not found
     */
    public CompletableFuture<LoreEntry> getLoreEntry(int id) {
        return databaseManager.getLoreEntry(id)
            .thenCompose(entryDto -> {
                if (entryDto == null) {
                    return CompletableFuture.completedFuture(null);
                }
                
                return databaseManager.getCurrentSubmission(id)
                    .thenApply(submissionDto -> {
                        if (submissionDto == null) {
                            return null;
                        }
                        return convertDTOsToLoreEntry(entryDto, submissionDto);
                    });
            })
            .exceptionally(e -> {
                logger.error("Error getting lore entry: " + id, e);
                return null;
            });
    }
    
    /**
     * Get all lore entries of a specific type
     * 
     * @param type The type of lore entries to get
     * @return A CompletableFuture that will complete with a list of matching entries
     */
    public List<LoreEntry> getLoreEntriesByType(LoreType type) {
        return new ArrayList<>(loreByType.getOrDefault(type, new ArrayList<>()));
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
            return new ArrayList<>();
        }

        // For now, filter the cache. In the future, this could be a database query with spatial index
        return cachedEntries.stream()
            .filter(entry -> entry.getLocation() != null && 
                           entry.getLocation().getWorld().equals(location.getWorld()) &&
                           entry.getLocation().distance(location) <= radius)
            .collect(Collectors.toList());
    }

    /**
     * Create a new version of a lore entry
     * 
     * @param entryId The ID of the entry to version
     * @param content The new content
     * @param submitter The UUID of the submitter
     * @return A CompletableFuture that will complete with true if successful
     */
    public CompletableFuture<Boolean> createNewVersion(int entryId, String content, String submitter) {
        return databaseManager.getCurrentSubmission(entryId)
            .thenCompose(currentSubmission -> {
                if (currentSubmission == null) {
                    logger.warning("No current submission found for entry: " + entryId);
                    return CompletableFuture.completedFuture(false);
                }

                // Set current version to false
                currentSubmission.setCurrentVersion(false);
                
                // Create new version
                LoreSubmissionDTO newSubmission = new LoreSubmissionDTO();
                newSubmission.setEntryId(entryId);
                newSubmission.setSlug("lore-" + entryId + "-" + System.currentTimeMillis());
                newSubmission.setVisibility(currentSubmission.getVisibility());
                newSubmission.setStatus("ACTIVE");
                newSubmission.setSubmitterUuid(submitter);
                newSubmission.setCreatedBy(submitter);
                newSubmission.setSubmissionDate(new Timestamp(System.currentTimeMillis()));
                newSubmission.setApprovalStatus("PENDING");
                newSubmission.setContentVersion(currentSubmission.getContentVersion() + 1);
                newSubmission.setCurrentVersion(true);
                newSubmission.setContent(content);
                
                // Save both changes in order
                return databaseManager.saveLoreSubmission(currentSubmission)
                    .thenCompose(oldId -> databaseManager.saveLoreSubmission(newSubmission))
                    .thenApply(newId -> {
                        // Update cache
                        Optional<LoreEntry> entry = cachedEntries.stream()
                            .filter(e -> e.getNumericId() == entryId)
                            .findFirst();
                            
                        entry.ifPresent(e -> {
                            e.setDescription(content);
                            e.setApproved(false);
                        });
                        
                        logger.info("Created new version for lore entry: " + entryId);
                        return true;
                    });
            })
            .exceptionally(e -> {
                logger.error("Error creating new version for entry: " + entryId, e);
                return false;
            });
    }

    /**
     * Clear all lore entries from cache and optionally the database
     * 
     * @param clearDatabase If true, also clears entries from the database
     * @return A CompletableFuture that will complete when the operation is done
     */
    public CompletableFuture<Void> clearAllLore(boolean clearDatabase) {
        cachedEntries.clear();
        for (List<LoreEntry> entries : loreByType.values()) {
            entries.clear();
        }
        
        if (clearDatabase) {
            // This would require implementing a clearAll method in DatabaseManager
            return CompletableFuture.runAsync(() -> {
                logger.warning("Database clearing not implemented yet");
            });
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Get a lore entry by name
     * 
     * @param name The name to search for
     * @return The matching entry, or null if not found
     */
    public LoreEntry getLoreEntryByName(String name) {
        if (name == null) return null;
        return cachedEntries.stream()
            .filter(entry -> name.equals(entry.getName()))
            .findFirst()
            .orElse(null);
    }
}

package org.fourz.RVNKLore.lore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.data.dto.LoreSubmissionDTO;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.sql.SQLException;
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
     * Convert DTOs to a LoreEntry domain object using the latest conversion methods
     */
    private LoreEntry convertDTOsToLoreEntry(LoreEntryDTO entryDto, LoreSubmissionDTO submissionDto) {
        LoreEntry entry = LoreEntry.fromDTO(entryDto);
        // Use all available fields from submissionDto
        if (submissionDto != null) {
            entry.setDescription(submissionDto.getContent());
            entry.setApproved("APPROVED".equalsIgnoreCase(submissionDto.getApprovalStatus()));
            entry.setSubmittedBy(submissionDto.getSubmitterUuid());
            entry.setSubmissionDate(submissionDto.getSubmissionDate());
            // Optionally set more fields if present in DTOs
        }
        return entry;
    }

    /**
     * Add a new lore entry (DTO-based, async)
     *
     * @param entry The lore entry to add
     * @return A CompletableFuture that will complete with true if successful
     */
    public CompletableFuture<Boolean> addLoreEntry(LoreEntry entry) {
        if (entry == null) {
            logger.warning("Attempted to add null lore entry");
            return CompletableFuture.completedFuture(false);
        }
        LoreEntryDTO entryDto = LoreEntryDTO.fromLoreEntry(entry);
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
                    .thenCompose(submissionId -> {
                        if (entry.getType() == LoreType.ITEM && itemManager != null) {
                            try {
                                Map<String, String> metadata = entry.getMetadata();
                                Material material = Material.valueOf(metadata.getOrDefault("material", "BOOK"));
                                String displayName = entry.getName();
                                ItemProperties itemProps = new ItemProperties(material, displayName);
                                itemProps.setLoreEntryId(String.valueOf(entryId));
                                itemProps.setCustomModelData(Integer.parseInt(metadata.getOrDefault("custom_model_data", "0")));
                                itemProps.setRarity(metadata.getOrDefault("rarity", "COMMON"));
                                itemProps.setNbtData(entry.getNbtData());
                                itemProps.setCreatedBy(entry.getSubmittedBy());
                                itemProps.setCreatedAt(entry.getSubmissionDate().getTime());
                                List<String> lore = Arrays.asList(entry.getDescription().split("\n"));
                                itemProps.setLore(lore);
                                for (Map.Entry<String, String> meta : metadata.entrySet()) {
                                    itemProps.setMetadata(meta.getKey(), meta.getValue());
                                }
                                if (material == Material.PLAYER_HEAD) {
                                    String headType = metadata.getOrDefault("head_type", "");
                                    if (headType.equals("player")) {
                                        itemProps.setOwnerName(metadata.get("player_name"));
                                    } else if (headType.equals("custom")) {
                                        itemProps.setTextureData(metadata.get("texture_data"));
                                    }
                                }
                                return itemManager.registerLoreItemAsync(UUID.fromString(entry.getId()), itemProps)
                                    .thenApply(itemSuccess -> {
                                        if (itemSuccess) {
                                            entry.setNumericId(entryId);
                                            cachedEntries.add(entry);
                                            loreByType.get(entry.getType()).add(entry);
                                            logger.info("Lore item registered successfully: " + entry.getName());
                                            return true;
                                        } else {
                                            logger.error("Failed to register lore item: " + entry.getName(), null);
                                            return false;
                                        }
                                    });
                            } catch (Exception e) {
                                logger.error("Error creating item properties for: " + entry.getName(), e);
                                return CompletableFuture.completedFuture(false);
                            }
                        } else {
                            entry.setNumericId(entryId);
                            cachedEntries.add(entry);
                            loreByType.get(entry.getType()).add(entry);
                            logger.info("Lore entry added successfully: " + entry.getName());
                            return CompletableFuture.completedFuture(true);
                        }
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
     * Find lore entries near a location, using database spatial indexing for efficiency
     * 
     * @param location The location to search near
     * @param radius The search radius in blocks
     * @return A future containing a list of nearby lore entries
     */
    public CompletableFuture<List<LoreEntry>> findNearbyLoreEntries(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return databaseManager.findNearbyLoreEntries(
                location.getWorld().getName(),
                location.getX(), 
                location.getY(), 
                location.getZ(),
                radius
            )
            .thenApply(dtos -> dtos.stream()
                .map(dto -> {
                    try {
                        return databaseManager.getCurrentSubmission(dto.getId())
                            .thenApply(submissionDto -> {
                                if (submissionDto != null) {
                                    return convertDTOsToLoreEntry(dto, submissionDto);
                                }
                                return null;
                            })
                            .get();  // Block on individual entry conversion
                    } catch (Exception e) {
                        logger.error("Error fetching submission for lore entry " + dto.getId(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
            );
    }
    
    /**
     * Update locations for multiple lore entries efficiently in a batch
     * 
     * @param locationUpdates Map of lore entry IDs to their new locations
     * @return A future that completes when the batch update is done
     */
    public CompletableFuture<Void> batchUpdateLocations(Map<Integer, Location> locationUpdates) {
        // Update both cache and database
        locationUpdates.forEach((id, location) -> {
            cachedEntries.stream()
                .filter(entry -> entry.getNumericId() == id)
                .forEach(entry -> entry.setLocation(location));
        });
        
        return databaseManager.batchUpdateLocations(locationUpdates)
            .exceptionally(e -> {
                logger.error("Failed to batch update locations", e);
                // Revert cache updates on failure
                locationUpdates.forEach((id, location) -> {
                    cachedEntries.stream()
                        .filter(entry -> entry.getNumericId() == id)
                        .forEach(entry -> entry.setLocation(null));
                });
                throw new CompletionException(e);
            });
    }
    
    /**
     * Clear all lore entries from database and cache
     * 
     * @param clearDatabase Whether to clear entries from the database as well
     * @return A future that completes when clearing is done
     */
    public CompletableFuture<Void> clearAllLore(boolean clearDatabase) {
        cachedEntries.clear();
        for (List<LoreEntry> entries : loreByType.values()) {
            entries.clear();
        }
        
        if (clearDatabase) {
            // Clear database in a transaction
            return databaseManager.beginTransaction()
                .thenCompose(tx -> {
                    // First clear submissions to maintain referential integrity
                    return databaseManager.clearAllSubmissions()
                        .thenCompose(v -> {
                            // Then clear entries
                            return databaseManager.clearAllEntries()
                                .thenCompose(v2 -> {
                                    // Commit transaction
                                    return databaseManager.commitTransaction(tx);
                                })
                                .exceptionally(e -> {
                                    logger.error("Failed to clear lore entries", e);
                                    databaseManager.rollbackTransaction(tx);
                                    throw new CompletionException(e);
                                });
                        });
                });
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Remove a specific lore entry
     * 
     * @param id The numeric ID of the entry to remove
     * @return A CompletableFuture that will complete with true if successful
     */
    public CompletableFuture<Boolean> removeLoreEntry(int id) {
        return databaseManager.getLoreEntry(id)
            .thenCompose(entryDto -> {
                if (entryDto == null) {
                    logger.warning("Attempted to remove non-existent lore entry: " + id);
                    return CompletableFuture.completedFuture(false);
                }
                return databaseManager.getCurrentSubmission(id)
                    .thenCompose(submissionDto -> {
                        if (submissionDto == null) {
                            logger.warning("No submission found for lore entry: " + id);
                            return CompletableFuture.completedFuture(false);
                        }
                        // For ITEM type, just delete the lore entry (unregisterLoreItem is not present)
                        return databaseManager.deleteLoreEntry(id)
                            .thenApply(success -> {
                                if (success) {
                                    cachedEntries.removeIf(entry -> entry.getNumericId() == id);
                                    for (List<LoreEntry> entries : loreByType.values()) {
                                        entries.removeIf(entry -> entry.getNumericId() == id);
                                    }
                                    logger.info("Lore entry removed successfully: " + id);
                                    return true;
                                }
                                return false;
                            });
                    });
            })
            .exceptionally(e -> {
                logger.error("Error removing lore entry: " + id, e);
                return false;
            });
    }
}

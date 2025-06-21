package org.fourz.RVNKLore.lore.item.collection;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.data.CollectionRepository;
import org.fourz.RVNKLore.data.dto.ItemCollectionDTO;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Manages item collections and thematic groupings within the lore system.
 * Handles organization, tracking, and distribution of items across different collections.
 * Uses async operations and DTOs for database interactions through CollectionRepository.
 * 
 * This class follows the singleton pattern and uses a thread-safe cache for collections.
 * All database operations are performed asynchronously and include retry logic for critical operations.
 */
public class CollectionManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Map<String, ItemCollection> collections = new ConcurrentHashMap<>();
    private final Map<String, CollectionTheme> themes = new ConcurrentHashMap<>();
    private final CollectionRepository collectionRepository;
    private final Object collectionLock = new Object();
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public CollectionManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CollectionManager");
        this.collectionRepository = new CollectionRepository(plugin, plugin.getDatabaseManager());
        
        initializeAsync();
    }

    /**
     * Initialize the manager asynchronously, creating default themes and collections if needed.
     * Uses retry logic for critical database operations.
     */
    private void initializeAsync() {
        createDefaultThemes();
        retryOperation(this::loadCollectionsFromDatabase)
            .thenRun(() -> {
                if (collections.isEmpty()) {
                    createDefaultCollections();
                }
                logger.info("&a✓ CollectionManager initialized with " + collections.size() + " collections");
            })
            .exceptionally(e -> {
                logger.error("&c✖ Fatal error during CollectionManager initialization", e);
                return null;
            });
    }

    /**
     * Helper method to retry critical operations with exponential backoff.
     * 
     * @param operation The operation to retry
     * @param <T> The return type of the operation
     * @return A CompletableFuture that will complete with the operation result
     */
    private <T> CompletableFuture<T> retryOperation(Supplier<CompletableFuture<T>> operation) {
        return retryOperation(operation, 1);
    }

    private <T> CompletableFuture<T> retryOperation(Supplier<CompletableFuture<T>> operation, int attempt) {
        return operation.get().exceptionally(e -> {
            if (attempt < MAX_RETRIES) {
                logger.warning("&e⚠ Operation failed, attempt " + attempt + "/" + MAX_RETRIES + ", retrying...");
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(ie);
                }
                return retryOperation(operation, attempt + 1).join();
            }
            throw new CompletionException(e);
        });
    }

    private void createDefaultThemes() {
        for (CollectionTheme theme : CollectionTheme.values()) {
            themes.put(theme.name().toLowerCase(), theme);
            logger.debug("Registered collection theme: " + theme.getDisplayName());
        }
    }

    private void createDefaultCollections() {
        CompletableFuture.allOf(
            createCollectionAsync("starter_items", "Starter Collection", "Basic items for new players"),
            createCollectionAsync("rare_finds", "Rare Discoveries", "Uncommon items found throughout the world"),
            createCollectionAsync("legendary_artifacts", "Legendary Artifacts", "Powerful items of great significance")
        ).thenRun(() -> logger.info("&a✓ Default collections initialized"));
    }

    /**
     * Creates a new collection and saves it to the database.
     * 
     * @param id The unique identifier for the collection
     * @param name The display name of the collection
     * @param description A brief description of the collection
     * @return A CompletableFuture that completes with the created ItemCollection, or null if creation fails
     */
    public CompletableFuture<ItemCollection> createCollectionAsync(String id, String name, String description) {
        if (!validateNewCollection(id, name, description)) {
            return CompletableFuture.completedFuture(null);
        }
        
        ItemCollection collection = new ItemCollection(id, name, description);
        return retryOperation(() -> collectionRepository.saveCollection(collection))
            .thenApply(saved -> {
                if (saved) {
                    synchronized (collectionLock) {
                        collections.put(id, collection);
                    }
                    logger.info("&a✓ Created collection: " + name + " (" + id + ")");
                    return collection;
                }
                logger.warning("&c✖ Failed to save new collection to database: " + id);
                return null;
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error creating collection: " + id, e);
                return null;
            });
    }

    private boolean validateNewCollection(String id, String name, String description) {
        if (id == null || id.trim().isEmpty()) {
            logger.warning("&e⚠ Collection validation failed: missing or empty ID");
            return false;
        }
        
        if (name == null || name.trim().isEmpty()) {
            logger.warning("&e⚠ Collection validation failed: missing or empty name");
            return false;
        }
        
        synchronized (collectionLock) {
            if (collections.containsKey(id)) {
                logger.warning("&e⚠ Collection validation failed: duplicate ID - " + id);
                return false;
            }
        }
        
        if (!id.matches("^[a-z0-9_]+$")) {
            logger.warning("&e⚠ Collection validation failed: invalid ID format - " + id);
            return false;
        }
        
        return true;
    }

    private boolean validateCollection(ItemCollection collection) {
        if (collection == null) {
            logger.warning("Collection validation failed: null collection");
            return false;
        }
        
        if (collection.getId() == null || collection.getId().trim().isEmpty()) {
            logger.warning("Collection validation failed: missing or empty ID");
            return false;
        }
        
        if (collection.getName() == null || collection.getName().trim().isEmpty()) {
            logger.warning("Collection validation failed: missing or empty name");
            return false;
        }
        
        return true;
    }

    /**
     * Gets a collection by ID, loading from database if not cached.
     * 
     * @param id The unique identifier of the collection
     * @return A CompletableFuture that completes with the requested ItemCollection, or null if not found
     */
    public CompletableFuture<ItemCollection> getCollectionAsync(String id) {
        if (id == null || id.trim().isEmpty()) {
            logger.warning("&e⚠ Cannot get collection: ID is null or empty");
            return CompletableFuture.completedFuture(null);
        }

        ItemCollection cached = collections.get(id);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return retryOperation(() -> collectionRepository.getCollectionById(id))
            .thenApply(dto -> {
                if (dto != null) {
                    ItemCollection collection = dto.toCollection();
                    synchronized (collectionLock) {
                        collections.put(id, collection);
                    }
                    return collection;
                }
                logger.warning("&e⚠ Collection not found: " + id);
                return null;
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error loading collection: " + id, e);
                return null;
            });
    }

    /**
     * Gets all collections, loading from database if needed.
     * 
     * @return A CompletableFuture that completes with a map of all collections
     */
    public CompletableFuture<Map<String, ItemCollection>> getAllCollectionsAsync() {
        return retryOperation(() -> collectionRepository.getAllCollections())
            .thenApply(dtos -> {
                Map<String, ItemCollection> result = new HashMap<>();
                synchronized (collectionLock) {
                    for (ItemCollectionDTO dto : dtos) {
                        ItemCollection collection = dto.toCollection();
                        collections.put(collection.getId(), collection);
                        result.put(collection.getId(), collection);
                    }
                }
                return result;
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error loading all collections", e);
                return new HashMap<>(collections);
            });
    }

    /**
     * Creates an ItemStack for a collection item with the given properties.
     * 
     * @param properties The properties to apply to the item
     * @return The created ItemStack
     * @throws IllegalArgumentException if properties is null
     */
    public ItemStack createCollectionItem(ItemProperties properties) {
        if (properties == null) {
            logger.debug("&c✖ Cannot create collection item: properties is null");
            throw new IllegalArgumentException("ItemProperties cannot be null");
        }

        Material material = properties.getMaterial() != null ? properties.getMaterial() : Material.PAPER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            if (properties.getDisplayName() != null) {
                meta.setDisplayName(properties.getDisplayName());
            }
            
            List<String> lore = properties.getLore() != null ? 
                new ArrayList<>(properties.getLore()) : new ArrayList<>();

            String collectionId = properties.getCollectionId();
            if (collectionId != null) {
                ItemCollection collection = collections.get(collectionId);
                if (collection != null) {
                    lore.add("§7Collection: §a" + collection.getName());
                }
            }
            
            if (properties.getRarityLevel() != null) {
                lore.add("§7Rarity: §e" + properties.getRarityLevel());
            }
            
            meta.setLore(lore);
            meta.setCustomModelData(properties.getCustomModelData());
            item.setItemMeta(meta);
        }
        
        logger.debug("Created collection item: " + properties.getDisplayName());
        return item;
    }

    /**
     * Adds an item to a collection and saves to database.
     * 
     * @param collectionId The ID of the collection to add the item to
     * @param item The item to add
     * @return A CompletableFuture that completes with true if the item was added successfully
     */
    public CompletableFuture<Boolean> addItemToCollectionAsync(String collectionId, ItemStack item) {
        return getCollectionAsync(collectionId)
            .thenCompose(collection -> {
                if (collection == null) {
                    logger.warning("&e⚠ Cannot add item to non-existent collection: " + collectionId);
                    return CompletableFuture.completedFuture(false);
                }
                
                collection.addItem(item);
                return retryOperation(() -> collectionRepository.saveCollection(collection));
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error adding item to collection: " + collectionId, e);
                return false;
            });
    }

    /**
     * Removes an item from a collection and updates database.
     */
    public CompletableFuture<Boolean> removeItemFromCollectionAsync(String collectionId, ItemStack item) {
        return getCollectionAsync(collectionId)
            .thenCompose(collection -> {
                if (collection == null) {
                    return CompletableFuture.completedFuture(false);
                }
                
                boolean removed = collection.removeItem(item);
                if (removed) {
                    return collectionRepository.saveCollection(collection);
                }
                return CompletableFuture.completedFuture(false);
            })
            .exceptionally(e -> {
                logger.error("Error removing item from collection: " + collectionId, e);
                return false;
            });
    }

    /**
     * Gets items from a collection, loading from database if needed.
     */
    public CompletableFuture<List<ItemStack>> getCollectionItemsAsync(String collectionId) {
        return getCollectionAsync(collectionId)
            .thenApply(collection -> 
                collection != null ? collection.getItems() : new ArrayList<>()
            );
    }

    public void registerTheme(CollectionTheme theme) {
        if (theme != null) {
            themes.put(theme.name().toLowerCase(), theme);
            logger.info("Registered collection theme: " + theme.getDisplayName());
        }
    }

    /**
     * Gets the theme with the given ID.
     * 
     * @param id The ID of the theme
     * @return The theme, or null if not found
     */
    public CollectionTheme getTheme(String id) {
        if (id == null) {
            logger.warning("&e⚠ Cannot get theme: ID is null");
            return null;
        }
        return themes.get(id.toLowerCase());
    }

    /**
     * Gets all registered themes.
     * 
     * @return A map of theme IDs to themes
     */
    public Map<String, CollectionTheme> getAllThemes() {
        return new HashMap<>(themes);
    }

    /**
     * Gets a player's collection progress.
     * 
     * @param playerId The UUID of the player
     * @param collectionId The ID of the collection
     * @return A CompletableFuture that completes with the player's progress (0.0 to 1.0)
     */
    public CompletableFuture<Double> getPlayerProgressAsync(UUID playerId, String collectionId) {
        if (playerId == null || collectionId == null) {
            logger.warning("&e⚠ Cannot get progress: player ID or collection ID is null");
            return CompletableFuture.completedFuture(0.0);
        }
        
        return retryOperation(() -> collectionRepository.getPlayerCollectionProgress(playerId, collectionId))
            .exceptionally(e -> {
                logger.error("&c✖ Error getting player progress", e);
                return 0.0;
            });
    }

    /**
     * Updates a player's collection progress and handles completion.
     * 
     * @param playerId The UUID of the player
     * @param collectionId The ID of the collection
     * @param progress The new progress value (0.0 to 1.0)
     * @return A CompletableFuture that completes with true if the update was successful
     */
    public CompletableFuture<Boolean> updatePlayerProgressAsync(UUID playerId, String collectionId, double progress) {
        if (playerId == null || collectionId == null) {
            logger.warning("&e⚠ Cannot update progress: player ID or collection ID is null");
            return CompletableFuture.completedFuture(false);
        }
        
        if (progress < 0.0 || progress > 1.0) {
            logger.warning("&e⚠ Invalid progress value: " + progress);
            return CompletableFuture.completedFuture(false);
        }
        
        return retryOperation(() -> collectionRepository.updatePlayerCollectionProgress(playerId, collectionId, progress))
            .thenCompose(updated -> {
                if (updated && progress >= 1.0) {
                    return handleCollectionCompletionAsync(playerId, collectionId);
                }
                return CompletableFuture.completedFuture(updated);
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error updating player progress", e);
                return false;
            });
    }

    private CompletableFuture<Boolean> handleCollectionCompletionAsync(UUID playerId, String collectionId) {
        return getCollectionAsync(collectionId)
            .thenCompose(collection -> {
                if (collection == null) {
                    logger.warning("&e⚠ Cannot handle completion for unknown collection: " + collectionId);
                    return CompletableFuture.completedFuture(false);
                }

                logger.info("&a✓ Player " + playerId + " completed collection: " + collection.getName());
                return retryOperation(() -> 
                    collectionRepository.markCollectionCompleted(playerId, collectionId, System.currentTimeMillis())
                );
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error handling collection completion", e);
                return false;
            });
    }

    /**
     * Reloads all collections from the database.
     * 
     * @return A CompletableFuture that completes with the number of collections loaded
     */
    public CompletableFuture<Integer> reloadCollectionsFromDatabase() {
        logger.info("&6⚙ Reloading collections from database...");
        synchronized (collectionLock) {
            collections.clear();
        }
        
        return loadCollectionsFromDatabase()
            .thenApply(ignored -> collections.size());
    }

    /**
     * Loads collections from the database into cache.
     * Uses retry logic for reliability.
     */
    private CompletableFuture<Void> loadCollectionsFromDatabase() {
        return retryOperation(() -> collectionRepository.getAllCollections())
            .thenAccept(dtos -> {
                synchronized (collectionLock) {
                    for (ItemCollectionDTO dto : dtos) {
                        ItemCollection collection = dto.toCollection();
                        collections.put(collection.getId(), collection);
                        logger.debug("Loaded collection: " + collection.getName());
                    }
                }
                logger.info("&a✓ Loaded " + dtos.size() + " collections from database");
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error loading collections from database", e);
                return null;
            });
    }

    /**
     * Cleans up resources and saves pending changes before shutdown.
     * 
     * @return A CompletableFuture that completes when shutdown is complete
     */
    public CompletableFuture<Void> shutdown() {
        logger.info("&6⚙ Shutting down CollectionManager...");
        List<CompletableFuture<Boolean>> saves = new ArrayList<>();
        
        synchronized (collectionLock) {
            saves = collections.values().stream()
                .map(collection -> retryOperation(() -> collectionRepository.saveCollection(collection)))
                .collect(Collectors.toList());
        }
        
        return CompletableFuture.allOf(saves.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                synchronized (collectionLock) {
                    collections.clear();
                    themes.clear();
                }
                logger.info("&a✓ CollectionManager shutdown complete");
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error during CollectionManager shutdown", e);
                return null;
            });
    }
}

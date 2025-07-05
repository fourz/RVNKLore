package org.fourz.RVNKLore.lore.item;

import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.enchant.EnchantManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.cosmetic.CosmeticsManager;
import org.fourz.RVNKLore.lore.item.custommodeldata.CustomModelDataManager;
import org.fourz.RVNKLore.data.dto.ItemPropertiesDTO;
import org.fourz.RVNKLore.data.repository.ItemRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Base manager class for all item-related functionality in the lore system.
 * Acts as a central orchestrator for enchantments, cosmetics, collections, and model data.
 * Uses async operations and DTOs for database interactions through ItemRepository.
 * 
 * This class follows the singleton pattern and uses a thread-safe cache for items.
 * All database operations are performed asynchronously and include retry logic for critical operations.
 */
public class ItemManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final Object cacheLock = new Object();
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    // Sub-managers for different item domains
    private final EnchantManager enchantManager;
    private final CosmeticsManager cosmeticItem;
    private final CollectionManager collectionManager;
    private final CustomModelDataManager modelDataManager;
    private final ItemRepository itemRepository;

    // Thread-safe caches for better performance
    private final Map<String, List<ItemPropertiesDTO>> itemNameCache = new ConcurrentHashMap<>();
    private final Map<String, ItemPropertiesDTO> loreEntryIdCache = new ConcurrentHashMap<>();
    private final Map<Integer, List<ItemPropertiesDTO>> collectionCache = new ConcurrentHashMap<>();
    private volatile boolean cacheInitialized = false;
    
    public ItemManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ItemManager");
        logger.info("&6⚙ Initializing ItemManager...");

        // Initialize database repository
        if (plugin.getDatabaseManager() != null) {
            this.itemRepository = new ItemRepository(plugin, plugin.getDatabaseManager());
            logger.info("&a✓ ItemRepository initialized");
        } else {
            logger.error("&c✖ DatabaseManager not available - some item features may be limited", null);
            this.itemRepository = null;
        }
        
        // Initialize sub-managers
        this.cosmeticItem = new CosmeticsManager(plugin);
        logger.info("&a✓ CosmeticItem initialized");
        
        this.enchantManager = new EnchantManager(plugin);
        logger.info("&a✓ EnchantManager initialized");
        
        this.modelDataManager = new CustomModelDataManager(plugin);
        logger.info("&a✓ ModelDataManager initialized");
        
        this.collectionManager = new CollectionManager(plugin);
        logger.info("&a✓ CollectionManager initialized");
        
        // Initial cache load in async task
        initializeAsync();
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

    /**
     * Initialize the cache asynchronously.
     */
    private void initializeAsync() {
        if (itemRepository == null) {
            logger.warning("&e⚠ Cannot initialize cache: ItemRepository is null");
            return;
        }

        logger.info("&6⚙ Initializing item cache from database...");
        retryOperation(() -> itemRepository.getItemsByType(null))
            .thenAccept(allItems -> {
                synchronized (cacheLock) {
                    itemNameCache.clear();
                    collectionCache.clear();
                    loreEntryIdCache.clear();

                    for (ItemPropertiesDTO dto : allItems) {
                        String key = dto.getDisplayName().toLowerCase();
                        itemNameCache.computeIfAbsent(key, k -> new ArrayList<>()).add(dto);
                        
                        if (dto.getLoreEntryId() != null) {
                            loreEntryIdCache.put(dto.getLoreEntryId(), dto);
                        }
                        
                        if (dto.getCollectionId() != null) {
                            try {
                                int colId = Integer.parseInt(dto.getCollectionId());
                                collectionCache.computeIfAbsent(colId, k -> new ArrayList<>()).add(dto);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    cacheInitialized = true;
                }
                logger.info("&a✓ Item cache initialized with " + itemNameCache.size() + " items and " + 
                    collectionCache.size() + " collections");
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error initializing item cache", e);
                return null;
            });
    }

    /**
     * Get the enchantment manager for enchanted item generation and management.
     * 
     * @return The EnchantManager instance
     */
    public EnchantManager getEnchantManager() {
        return enchantManager;
    }
    
    /**
     * Get the cosmetic manager for head collections and variants.
     * 
     * @return The CosmeticManager instance
     */
    public CosmeticsManager getCosmeticItem() {
        return cosmeticItem;
    }
    
    /**
     * Get the collection manager for item collections.
     * 
     * @return The CollectionManager instance
     */
    public CollectionManager getCollectionManager() {
        return collectionManager;
    }
    
    /**
     * Get the model data manager for custom model data allocation and tracking.
     * 
     * @return The ModelDataManager instance
     */
    public CustomModelDataManager getModelDataManager() {
        return modelDataManager;
    }
    
    /**
     * Give a lore item to a player asynchronously.
     *
     * @param itemName Name of the item to give
     * @param player Player to receive the item
     * @return CompletableFuture<Boolean> True if item was given successfully
     */
    public CompletableFuture<Boolean> giveItemToPlayerAsync(String itemName, org.bukkit.entity.Player player) {
        if (player == null) {
            logger.error("&c✖ Cannot give item - player is null", null);
            return CompletableFuture.completedFuture(false);
        }

        return retryOperation(() -> createLoreItemAsync(itemName))
            .thenApply(item -> {
                if (item == null) {
                    logger.warning("&e⚠ Item not found: " + itemName);
                    return false;
                }
                player.getInventory().addItem(item);
                logger.info("&a✓ Gave item " + itemName + " to player " + player.getName());
                return true;
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error giving item to player", e);
                return false;
            });
    }

    /**
     * Display detailed information about an item to a CommandSender asynchronously.
     *
     * @param itemName The name of the item to show information for
     * @param sender The CommandSender to show information to
     * @return CompletableFuture<Boolean> True if item was found and info displayed
     */
    public CompletableFuture<Boolean> displayItemInfoAsync(String itemName, org.bukkit.command.CommandSender sender) {
        if (sender == null) {
            logger.error("&c✖ Cannot display info - sender is null", null);
            return CompletableFuture.completedFuture(false);
        }

        return retryOperation(() -> createLoreItemAsync(itemName))
            .thenApply(item -> {
                if (item == null) {
                    sender.sendMessage(org.bukkit.ChatColor.RED + "Item not found: " + itemName);
                    return false;
                }

                sender.sendMessage(org.bukkit.ChatColor.GOLD + "===== Item Info: " + itemName + " =====");
                sender.sendMessage(org.bukkit.ChatColor.YELLOW + "Material: " + item.getType());
                
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    if (meta.hasDisplayName()) {
                        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "Display Name: " + meta.getDisplayName());
                    }
                    if (meta.hasLore()) {
                        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "Lore:");
                        for (String line : meta.getLore()) {
                            sender.sendMessage(org.bukkit.ChatColor.GRAY + "  " + line);
                        }
                    }
                    if (meta.hasCustomModelData()) {
                        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "Custom Model Data: " + meta.getCustomModelData());
                    }
                    if (meta.hasEnchants()) {
                        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "Enchantments:");
                        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                            String enchantName = entry.getKey().toString().replace("Enchantment[", "").replace("]", "");
                            sender.sendMessage(org.bukkit.ChatColor.GRAY + "  " + formatEnchantmentName(enchantName) + " " + entry.getValue());
                        }
                    }
                }
                return true;
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error displaying item info", e);
                sender.sendMessage(org.bukkit.ChatColor.RED + "An error occurred while retrieving item information");
                return false;
            });
    }

    /**
     * Display a list of all available items to a CommandSender asynchronously.
     *
     * @param sender The CommandSender to show available items to
     */
    public void displayAvailableItemsAsync(org.bukkit.command.CommandSender sender) {
        if (sender == null) {
            logger.error("&c✖ Cannot display items - sender is null", null);
            return;
        }

        retryOperation(() -> getAllItemNamesAsync())
            .thenAccept(allItems -> {
                sender.sendMessage(org.bukkit.ChatColor.GOLD + "Available Items:");
                if (allItems.isEmpty()) {
                    sender.sendMessage(org.bukkit.ChatColor.GRAY + "   No items available.");
                    return;
                }
                for (String name : allItems) {
                    sender.sendMessage(org.bukkit.ChatColor.YELLOW + " - " + name);
                }
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error displaying available items", e);
                sender.sendMessage(org.bukkit.ChatColor.RED + "An error occurred while retrieving item list");
                return null;
            });
    }
    
    /**
     * Format enchantment name for user-friendly display.
     * Converts snake_case to Title Case.
     * 
     * @param enchantName Raw enchantment name
     * @return Formatted enchantment name
     */
    private String formatEnchantmentName(String enchantName) {
        String[] parts = enchantName.split("_");
        StringBuilder formatted = new StringBuilder();
        
        for (String part : parts) {
            if (part.length() > 0) {
                formatted.append(part.substring(0, 1).toUpperCase())
                        .append(part.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        
        return formatted.toString().trim();
    }
    
    /**
     * Create a generic lore item with basic properties.
     *
     * @param type The type of item to create
     * @param name The name of the item
     * @param properties The properties to apply to the item
     * @return The created ItemStack
     */
    public ItemStack createLoreItem(ItemType type, String name, ItemProperties properties) {
        if (properties == null) {
            logger.error("&c✖ Cannot create item - properties is null", null);
            return null;
        }

        switch (type) {
            case ENCHANTED:
                return enchantManager.createEnchantedItem(properties);
            case COSMETIC:
                return cosmeticItem.createCosmeticItem(properties);
            case COLLECTION:
                return collectionManager.createCollectionItem(properties);
            case MODEL_DATA:
                if (modelDataManager != null) {
                    ItemStack item = new ItemStack(properties.getMaterial());
                    org.fourz.RVNKLore.lore.item.custommodeldata.CustomModelDataCategory category = 
                        org.fourz.RVNKLore.lore.item.custommodeldata.CustomModelDataCategory.COSMETIC;
                    try {
                        category = org.fourz.RVNKLore.lore.item.custommodeldata.CustomModelDataCategory.valueOf(type.name());
                    } catch (IllegalArgumentException ignored) {}
                    return modelDataManager.applyModelData(item, name, category);
                }
                return createBasicItem(properties);
            default:
                return createBasicItem(properties);
        }
    }

    /**
     * Create a basic item with the given properties.
     *
     * @param properties The properties to apply
     * @return The created ItemStack
     */
    private ItemStack createBasicItem(ItemProperties properties) {
        ItemStack item = new ItemStack(properties.getMaterial());
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (properties.getDisplayName() != null) {
                meta.setDisplayName(properties.getDisplayName());
            }
            if (properties.getLore() != null && !properties.getLore().isEmpty()) {
                meta.setLore(properties.getLore());
            }
            meta.setCustomModelData(properties.getCustomModelData());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Returns a list of all registered item names asynchronously.
     * Used for tab completion and lookup.
     *
     * @return CompletableFuture with a list of item names
     */
    public CompletableFuture<List<String>> getAllItemNamesAsync() {
        if (itemRepository != null && cacheInitialized) {
            synchronized (cacheLock) {
                return CompletableFuture.completedFuture(new ArrayList<>(itemNameCache.keySet()));
            }
        }

        if (itemRepository != null) {
            return retryOperation(() -> itemRepository.getItemsByType(null))
                .thenApply(list -> {
                    List<String> names = new ArrayList<>();
                    for (ItemPropertiesDTO dto : list) {
                        names.add(dto.getDisplayName());
                    }
                    return names;
                });
        }

        logger.warning("&e⚠ ItemRepository not available, returning empty item list");
        return CompletableFuture.completedFuture(new ArrayList<>());
    }
    
    /**
     * Synchronously get all items with properties.
     * This is a blocking call that waits for the async operation to complete.
     * 
     * @return List of ItemProperties objects
     */
    public List<ItemProperties> getAllItemsWithProperties() {
        try {
            return getAllItemsWithPropertiesAsync().get();
        } catch (Exception e) {
            logger.error("&c✖ Error getting all items with properties", e);
            return new ArrayList<>();
        }
    }

    /**
     * Get all items with properties asynchronously.
     * 
     * @return A CompletableFuture containing a list of ItemProperties objects
     */
    public CompletableFuture<List<ItemProperties>> getAllItemsWithPropertiesAsync() {
        if (itemRepository == null) {
            logger.warning("&e⚠ ItemRepository is not available, returning cached items");
            List<ItemProperties> result = new ArrayList<>();
            synchronized (cacheLock) {
                for (List<ItemPropertiesDTO> dtos : itemNameCache.values()) {
                    for (ItemPropertiesDTO dto : dtos) {
                        result.add(dto.toItemProperties());
                    }
                }
            }
            return CompletableFuture.completedFuture(result);
        }

        return plugin.getDatabaseManager().getItemRepository().getAllItems()
            .thenApply(dtos -> {
                List<ItemProperties> result = new ArrayList<>();
                for (ItemPropertiesDTO dto : dtos) {
                    result.add(dto.toItemProperties());
                }
                return result;
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error getting all items with properties", e);
                return new ArrayList<>();
            });
    }

    /**
     * Create a lore item by name asynchronously.
     * If multiple items exist with the same name, returns the first found.
     *
     * @param itemName The name of the item to create
     * @return CompletableFuture with the created ItemStack, or null if not found
     */
    public CompletableFuture<ItemStack> createLoreItemAsync(String itemName) {
        if (itemName == null || itemName.trim().isEmpty()) {
            logger.warning("&e⚠ Cannot create item - name is null or empty");
            return CompletableFuture.completedFuture(null);
        }

        String key = itemName.toLowerCase();
        if (itemRepository != null && cacheInitialized) {
            synchronized (cacheLock) {
                if (itemNameCache.containsKey(key)) {
                    ItemPropertiesDTO dto = itemNameCache.get(key).get(0);
                    return CompletableFuture.completedFuture(
                        createLoreItem(dto.toItemProperties().getItemType(), itemName, dto.toItemProperties())
                    );
                }
            }
        }

        if (itemRepository != null) {
            return retryOperation(() -> itemRepository.getItemsByType(null))
                .thenApply(list -> {
                    for (ItemPropertiesDTO dto : list) {
                        if (dto.getDisplayName().equalsIgnoreCase(itemName)) {
                            synchronized (cacheLock) {
                                itemNameCache.computeIfAbsent(key, k -> new ArrayList<>()).add(dto);
                            }
                            return createLoreItem(dto.toItemProperties().getItemType(), itemName, dto.toItemProperties());
                        }
                    }
                    return null;
                });
        }

        // Fallback to cosmetic/collection managers
        if (cosmeticItem != null) {
            var variant = cosmeticItem.getHeadVariant(itemName);
            if (variant != null) {
                logger.debug("Creating cosmetic head item: " + itemName);
                return CompletableFuture.completedFuture(cosmeticItem.createHeadItem(variant));
            }
        }

        if (collectionManager != null) {
            return collectionManager.getCollectionAsync(itemName)
                .thenApply(collection -> {
                    if (collection != null) {
                        logger.debug("Creating collection item: " + itemName);
                        ItemProperties props = new ItemProperties(org.bukkit.Material.PAPER, collection.getName());
                        props.setCollectionId(collection.getId());
                        return collectionManager.createCollectionItem(props);
                    }
                    return null;
                });
        }

        logger.warning("&e⚠ Item not found: " + itemName);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Refresh the item cache, reloading all data from the database.
     */
    public void refreshCache() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            logger.info("&6⚙ Refreshing item cache...");
            initializeAsync();
        });
    }
    
    /**
     * Shutdown all sub-managers and clean up resources.
     */
    public CompletableFuture<Void> shutdown() {
        logger.info("&6⚙ Shutting down ItemManager...");
        
        List<CompletableFuture<?>> shutdownTasks = new ArrayList<>();
        
        if (modelDataManager != null) {
            shutdownTasks.add(CompletableFuture.runAsync(() -> modelDataManager.shutdown()));
        }
        
        if (collectionManager != null) {
            shutdownTasks.add(collectionManager.shutdown());
        }
        
        if (cosmeticItem != null) {
            shutdownTasks.add(CompletableFuture.runAsync(() -> cosmeticItem.shutdown()));
        }
        
        if (enchantManager != null) {
            shutdownTasks.add(CompletableFuture.runAsync(() -> enchantManager.shutdown()));
        }
        
        return CompletableFuture.allOf(shutdownTasks.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                synchronized (cacheLock) {
                    itemNameCache.clear();
                    loreEntryIdCache.clear();
                    collectionCache.clear();
                }
                logger.info("&a✓ ItemManager shutdown complete");
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error during ItemManager shutdown", e);
                return null;
            });
    }
    
    /**
     * Clean up resources and shutdown (alias for shutdown).
     */
    public CompletableFuture<Void> cleanup() {
        return shutdown();
    }

    /**
     * Register a lore item with a reference to its lore entry ID asynchronously.
     *
     * @param loreEntryId The UUID of the lore entry in the lore_entry table
     * @param properties The properties of the item to register
     * @return CompletableFuture<Boolean> true if registration was successful
     */
    public CompletableFuture<Boolean> registerLoreItemAsync(UUID loreEntryId, ItemProperties properties) {
        if (loreEntryId == null || properties == null) {
            logger.warning("&e⚠ Cannot register lore item with null ID or properties");
            return CompletableFuture.completedFuture(false);
        }

        logger.info("&6⚙ Registering lore item: " + properties.getDisplayName() + " with lore entry ID: " + loreEntryId);
        properties.setLoreEntryId(loreEntryId.toString());
        ItemPropertiesDTO dto = ItemPropertiesDTO.fromItemProperties(properties);

        if (itemRepository == null) {
            logger.warning("&e⚠ ItemRepository is not available, item will not be persisted");
            synchronized (cacheLock) {
                String key = properties.getDisplayName().toLowerCase();
                itemNameCache.computeIfAbsent(key, k -> new ArrayList<>()).add(dto);
                loreEntryIdCache.put(loreEntryId.toString(), dto);
            }
            return CompletableFuture.completedFuture(true);
        }

        return retryOperation(() -> itemRepository.saveItem(dto))
            .thenApply(itemId -> {
                if (itemId > 0) {
                    synchronized (cacheLock) {
                        String key = properties.getDisplayName().toLowerCase();
                        itemNameCache.computeIfAbsent(key, k -> new ArrayList<>()).add(dto);
                        loreEntryIdCache.put(loreEntryId.toString(), dto);
                    }
                    logger.info("&a✓ Registered item in database with ID: " + itemId);
                    return true;
                } else {
                    logger.warning("&e⚠ Failed to insert item into database");
                    return false;
                }
            })
            .exceptionally(e -> {
                logger.error("&c✖ Error registering lore item", e);
                return false;
            });
    }
}

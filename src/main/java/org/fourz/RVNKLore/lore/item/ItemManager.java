package org.fourz.RVNKLore.lore.item;

import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.ItemRepository;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.enchant.EnchantManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.cosmetic.CosmeticsManager;
import org.fourz.RVNKLore.lore.item.custommodeldata.CustomModelDataManager;
import org.fourz.RVNKLore.data.dto.ItemPropertiesDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base manager class for all item-related functionality in the lore system.
 * Acts as a central orchestrator for enchantments, cosmetics, collections, and model data.
 */
public class ItemManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    
    // Sub-managers for different item domains
    private EnchantManager enchantManager;
    private CosmeticsManager cosmeticItem;
    private CollectionManager collectionManager;
    private CustomModelDataManager modelDataManager;
    private ItemRepository itemRepository;
      // Caches for better performance
    private final Map<String, List<ItemPropertiesDTO>> itemNameCache = new ConcurrentHashMap<>();
    private final Map<String, ItemPropertiesDTO> loreEntryIdCache = new ConcurrentHashMap<>();
    private final Map<Integer, List<ItemPropertiesDTO>> collectionCache = new ConcurrentHashMap<>();
    private boolean cacheInitialized = false;
    
    public ItemManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ItemManager");
        logger.info("Initializing ItemManager...");

        // Initialize database repository
        if (plugin.getDatabaseManager() != null) {
            this.itemRepository = new ItemRepository(plugin, plugin.getDatabaseManager());
            logger.info("ItemRepository initialized");
        } else {
            logger.warning("DatabaseManager not available - some item features may be limited");
        }
        
        // Initialize cosmetic manager
        this.cosmeticItem = new CosmeticsManager(plugin);
        logger.info("CosmeticItem initialized");
        
        // Initialize enchant manager
        this.enchantManager = new EnchantManager(plugin);
        logger.info("EnchantManager initialized");
        
        // Initialize model data manager
        this.modelDataManager = new CustomModelDataManager(plugin);
        logger.info("ModelDataManager initialized");
        
        // Initialize collection manager after other managers
        this.collectionManager = new CollectionManager(plugin);
        logger.info("CollectionManager initialized");
        
        // Initial cache load in async task to avoid blocking startup
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::initializeCache);
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
     * Give a lore item to a player (async).
     *
     * @param itemName Name of the item to give
     * @param player Player to receive the item
     * @return CompletableFuture<Boolean> True if item was given successfully, false otherwise
     */
    public CompletableFuture<Boolean> giveItemToPlayerAsync(String itemName, org.bukkit.entity.Player player) {
        if (player == null) {
            logger.error("Cannot give item - player is null", null);
            return CompletableFuture.completedFuture(false);
        }
        return createLoreItemAsync(itemName).thenApply(item -> {
            if (item == null) {
                return false;
            }
            player.getInventory().addItem(item);
            return true;
        });
    }

    /**
     * Display detailed information about an item to a CommandSender (async).
     *
     * @param itemName The name of the item to show information for
     * @param sender The CommandSender to show information to
     * @return CompletableFuture<Boolean> True if item was found and info displayed, false otherwise
     */
    public CompletableFuture<Boolean> displayItemInfoAsync(String itemName, org.bukkit.command.CommandSender sender) {
        return createLoreItemAsync(itemName).thenApply(item -> {
            if (item == null) {
                return false;
            }
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            sender.sendMessage(org.bukkit.ChatColor.GOLD + "===== Item Info: " + itemName + " =====");
            sender.sendMessage(org.bukkit.ChatColor.YELLOW + "Material: " + item.getType());
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
        });
    }

    /**
     * Display a list of all available items to a CommandSender (async).
     *
     * @param sender The CommandSender to show available items to
     */
    public void displayAvailableItemsAsync(org.bukkit.command.CommandSender sender) {
        getAllItemNamesAsync().thenAccept(allItems -> {
            sender.sendMessage(org.bukkit.ChatColor.GOLD + "Available Items:");
            if (allItems.isEmpty()) {
                sender.sendMessage(org.bukkit.ChatColor.GRAY + "   No items available.");
                return;
            }
            for (String name : allItems) {
                sender.sendMessage(org.bukkit.ChatColor.YELLOW + " - " + name);
            }
        });
    }
    
    /**
     * Format enchantment name for user-friendly display
     * Converts snake_case to Title Case
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
     */
    public ItemStack createLoreItem(ItemType type, String name, ItemProperties properties) {
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
                    // Map ItemType to ModelDataCategory if possible, else use ModelDataCategory.COSMETIC as default
                    org.fourz.RVNKLore.lore.item.custommodeldata.CustomModelDataCategory category = org.fourz.RVNKLore.lore.item.custommodeldata.CustomModelDataCategory.COSMETIC;
                    try {
                        category = org.fourz.RVNKLore.lore.item.custommodeldata.CustomModelDataCategory.valueOf(type.name());
                    } catch (IllegalArgumentException ignored) {}
                    return modelDataManager.applyModelData(item, name, category);
                }
                // Fallback to standard item creation
                ItemStack fallback = new ItemStack(properties.getMaterial());
                org.bukkit.inventory.meta.ItemMeta fallbackMeta = fallback.getItemMeta();
                if (fallbackMeta != null) {
                    if (properties.getDisplayName() != null) {
                        fallbackMeta.setDisplayName(properties.getDisplayName());
                    }
                    if (properties.getLore() != null && !properties.getLore().isEmpty()) {
                        fallbackMeta.setLore(properties.getLore());
                    }
                    fallbackMeta.setCustomModelData(properties.getCustomModelData());
                    fallback.setItemMeta(fallbackMeta);
                }
                return fallback;
            default:
                // For standard items, create a basic item with the properties
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
    }

    /**
     * Returns a list of all registered item names for tab completion and lookup.
     * Now async and DTO-based.
     */
    public CompletableFuture<List<String>> getAllItemNamesAsync() {
        if (itemRepository != null && cacheInitialized) {
            return CompletableFuture.completedFuture(new ArrayList<>(itemNameCache.keySet()));
        } else if (itemRepository != null) {
            // Fallback: fetch all items from DB
            return itemRepository.getItemsByType(null).thenApply(list -> {
                List<String> names = new ArrayList<>();
                for (ItemPropertiesDTO dto : list) {
                    names.add(dto.getDisplayName());
                }
                return names;
            });
        } else {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    /**
     * Create a lore item by name. If multiple items exist with the same name, returns the first found.
     * Now async and DTO-based.
     */
    public CompletableFuture<ItemStack> createLoreItemAsync(String itemName) {
        String key = itemName.toLowerCase();
        if (itemRepository != null && cacheInitialized && itemNameCache.containsKey(key)) {
            ItemPropertiesDTO dto = itemNameCache.get(key).get(0);
            return CompletableFuture.completedFuture(createLoreItem(dto.toItemProperties().getItemType(), itemName, dto.toItemProperties()));
        }
        if (itemRepository != null) {
            return itemRepository.getItemsByType(null).thenApply(list -> {
                for (ItemPropertiesDTO dto : list) {
                    if (dto.getDisplayName().equalsIgnoreCase(itemName)) {
                        itemNameCache.computeIfAbsent(key, k -> new ArrayList<>()).add(dto);
                        return createLoreItem(dto.toItemProperties().getItemType(), itemName, dto.toItemProperties());
                    }
                }
                return null;
            });
        }
        // Fallback to cosmetic/collection managers (sync)
        if (cosmeticItem != null) {
            var variant = cosmeticItem.getHeadVariant(itemName);
            if (variant != null) {
                return CompletableFuture.completedFuture(cosmeticItem.createHeadItem(variant));
            }
        }
        if (collectionManager != null) {
            var collection = collectionManager.getCollection(itemName);
            if (collection != null) {
                var props = new ItemProperties(org.bukkit.Material.PAPER, collection.getName());
                props.setCollectionId(collection.getId());
                return CompletableFuture.completedFuture(collectionManager.createCollectionItem(props));
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Initialize or refresh the item cache from database (async, DTO-based)
     */
    private void initializeCache() {
        if (itemRepository == null) {
            logger.warning("Cannot initialize cache: ItemRepository is null");
            return;
        }
        logger.info("Initializing item cache from database (async)...");
        itemRepository.getItemsByType(null).thenAccept(allItems -> {
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
            logger.info("Item cache initialized (async) with " + itemNameCache.size() + " item names and " + collectionCache.size() + " collections");
        }).exceptionally(e -> {
            logger.error("Error initializing item cache (async)", e);
            return null;
        });
    }
    
    /**
     * Refresh the item cache, reloading all data from the database.
     */
    public void refreshCache() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::initializeCache);
    }
    
    /**
     * Shutdown all sub-managers and clean up resources.
     */
    public void shutdown() {
        logger.info("Shutting down ItemManager...");
        
        if (modelDataManager != null) {
            modelDataManager.shutdown();
        }
        
        if (collectionManager != null) {
            collectionManager.shutdown();
        }
        
        if (cosmeticItem != null) {
            cosmeticItem.shutdown();
        }
        
        if (enchantManager != null) {
            enchantManager.shutdown();
        }
        
        logger.info("ItemManager shutdown complete");
    }
    
    /**
     * Clean up resources and shutdown (alias for shutdown).
     */
    public void cleanup() {
        shutdown();
    }

    /**
     * Get the plugin instance.
     * 
     * @return The RVNKLore plugin instance
     */
    protected RVNKLore getPlugin() {
        return plugin;
    }
    
    /**
     * Get the logger instance.
     * 
     * @return The LogManager instance
     */
    protected LogManager getLogger() {
        return logger;
    }

    /**
     * Get all items with their properties, including creation timestamp for sorting
     *
     * @return A list of ItemProperties for all items
     */
    public List<ItemProperties> getAllItemsWithProperties() {
        List<ItemProperties> result = new ArrayList<>();
        if (itemRepository != null && cacheInitialized) {
            for (List<ItemPropertiesDTO> list : itemNameCache.values()) {
                for (ItemPropertiesDTO dto : list) {
                    result.add(dto.toItemProperties());
                }
            }
        } else {
            if (cosmeticItem != null) {
                for (var collection : cosmeticItem.getAllCollections()) {
                    collection.getAllHeads().forEach(head -> {
                        ItemProperties props = new ItemProperties(org.bukkit.Material.PLAYER_HEAD, head.getName());
                        props.setItemType(ItemType.COSMETIC);
                        props.setCreatedAt(System.currentTimeMillis() - (long)(Math.random() * 10000000));
                        result.add(props);
                    });
                }
            }
            if (collectionManager != null) {
                for (var entry : collectionManager.getAllCollections().entrySet()) {
                    ItemProperties props = new ItemProperties(org.bukkit.Material.PAPER, entry.getValue().getName());
                    props.setItemType(ItemType.COLLECTION);
                    props.setCollectionId(entry.getKey());
                    props.setCreatedAt(entry.getValue().getCreatedAt());
                    result.add(props);
                }
            }
        }
        return result;
    }
    
    /**
     * Register a lore item with a reference to its lore entry ID (async, DTO-based)
     * @param loreEntryId The UUID of the lore entry in the lore_entry table
     * @param properties The properties of the item to register
     * @return CompletableFuture<Boolean> true if the item was registered successfully, false otherwise
     */
    public CompletableFuture<Boolean> registerLoreItemAsync(java.util.UUID loreEntryId, ItemProperties properties) {
        if (loreEntryId == null || properties == null) {
            logger.warning("Cannot register lore item with null ID or properties");
            return CompletableFuture.completedFuture(false);
        }
        logger.info("Registering lore item (async): " + properties.getDisplayName() + " with lore entry ID: " + loreEntryId);
        properties.setLoreEntryId(loreEntryId.toString());
        ItemPropertiesDTO dto = ItemPropertiesDTO.fromItemProperties(properties);
        if (itemRepository != null) {
            return itemRepository.saveItem(dto).thenApply(itemId -> {
                if (itemId > 0) {
                    String key = properties.getDisplayName().toLowerCase();
                    itemNameCache.computeIfAbsent(key, k -> new ArrayList<>()).add(dto);
                    loreEntryIdCache.put(loreEntryId.toString(), dto);
                    logger.info("Registered item in database with ID: " + itemId);
                    return true;
                } else {
                    logger.warning("Failed to insert item into database");
                    return false;
                }
            }).exceptionally(e -> {
                logger.error("Error registering lore item (async)", e);
                return false;
            });
        } else {
            logger.warning("ItemRepository is not available, item will not be persisted");
            String key = properties.getDisplayName().toLowerCase();
            itemNameCache.computeIfAbsent(key, k -> new ArrayList<>()).add(dto);
            loreEntryIdCache.put(loreEntryId.toString(), dto);
            return CompletableFuture.completedFuture(true);
        }
    }
}

package org.fourz.RVNKLore.lore.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseConnection;
import org.fourz.RVNKLore.data.ItemRepository;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.item.enchant.EnchantManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.cosmetic.CosmeticsManager;
import org.fourz.RVNKLore.lore.item.custommodeldata.CustomModelDataManager;
import org.fourz.RVNKLore.service.IItemService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base manager class for all item-related functionality in the lore system.
 * Acts as a central orchestrator for enchantments, cosmetics, collections, and model data.
 * Implements IItemService for RVNKCore ServiceRegistry integration.
 */
public class ItemManager implements IItemService {

    // Fallback mode flag for IItemService contract
    private boolean fallbackMode = false;
    private final RVNKLore plugin;
    private final LogManager logger;
    
    // Sub-managers for different item domains
    private EnchantManager enchantManager;
    private CosmeticsManager cosmeticItem;
    private CollectionManager collectionManager;
    private CustomModelDataManager modelDataManager;
    private ItemRepository itemRepository;
      // Caches for better performance
    private final Map<String, List<ItemProperties>> itemNameCache = new ConcurrentHashMap<>();
    private final Map<String, ItemProperties> loreEntryIdCache = new ConcurrentHashMap<>();
    private final Map<Integer, List<ItemProperties>> collectionCache = new ConcurrentHashMap<>();
    private boolean cacheInitialized = false;
    
    public ItemManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ItemManager");
        logger.info("Initializing ItemManager...");

        // Initialize database repository
        if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
            DatabaseConnection dbConnection = plugin.getDatabaseManager().getDatabaseConnection();
            if (dbConnection != null) {
                this.itemRepository = new ItemRepository(plugin, dbConnection);
                logger.info("ItemRepository initialized");
            } else {
                logger.warning("Database connection is null, ItemRepository will not be available");
            }
        } else {
            logger.warning("Database not available - some item features may be limited");
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
     * Give a lore item to a player (synchronous internal method).
     *
     * @param itemName Name of the item to give
     * @param player Player to receive the item
     * @return True if item was given successfully, false otherwise
     */
    private boolean giveItemToPlayerSync(String itemName, org.bukkit.entity.Player player) {
        if (player == null) {
            logger.error("Cannot give item - player is null", null);
            return false;
        }

        ItemStack item = createLoreItemByNameInternal(itemName);
        if (item == null) {
            return false;
        }

        player.getInventory().addItem(item);
        return true;
    }
    
    /**
     * Display detailed information about an item to a CommandSender.
     *
     * @param itemName The name of the item to show information for
     * @param sender The CommandSender to show information to
     * @return True if item was found and info displayed, false otherwise
     */
    public boolean displayItemInfo(String itemName, org.bukkit.command.CommandSender sender) {
        ItemStack item = createLoreItemByNameInternal(itemName);
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
                    sender.sendMessage(org.bukkit.ChatColor.GRAY + "  " +
                            formatEnchantmentName(enchantName) + " " + entry.getValue());
                }
            }
        }

        return true;
    }

    /**
     * Display a list of all available items to a CommandSender.
     *
     * @param sender The CommandSender to show available items to
     */
    public void displayAvailableItems(org.bukkit.command.CommandSender sender) {
        sender.sendMessage(org.bukkit.ChatColor.GOLD + "Available Items:");
        List<String> allItems = getAllItemNamesSync();
        
        if (allItems.isEmpty()) {
            sender.sendMessage(org.bukkit.ChatColor.GRAY + "   No items available.");
            return;
        }
        
        for (String name : allItems) {
            sender.sendMessage(org.bukkit.ChatColor.YELLOW + " - " + name);
        }
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
     * Create a generic lore item with basic properties (synchronous internal method).
     */
    private ItemStack createLoreItemInternal(ItemType type, String name, ItemProperties properties) {
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
     * Returns a list of all registered item names for tab completion and lookup (synchronous).
     */
    private List<String> getAllItemNamesSync() {
        List<String> names = new ArrayList<>();
        
        if (itemRepository != null && cacheInitialized) {
            names.addAll(itemNameCache.keySet());
        } else {
            if (cosmeticItem != null) {
                for (var collection : cosmeticItem.getAllCollections()) {
                    collection.getAllHeads().forEach(head -> names.add(head.getId()));
                }
            }
            if (collectionManager != null) {
                names.addAll(collectionManager.getAllCollections().keySet());
            }
        }
        
        return names;
    }

    /**
     * Create a lore item by name (synchronous internal method).
     * If multiple items exist with the same name, returns the first found.
     */
    private ItemStack createLoreItemByNameInternal(String itemName) {
        String key = itemName.toLowerCase();
        if (itemRepository != null && cacheInitialized && itemNameCache.containsKey(key)) {
            ItemProperties props = itemNameCache.get(key).get(0);
            return createLoreItemInternal(props.getItemType(), itemName, props);
        }
        if (itemRepository != null) {
            List<ItemProperties> propsList = itemRepository.getAllItemsByName(itemName);
            if (!propsList.isEmpty()) {
                itemNameCache.put(key, propsList);
                return createLoreItemInternal(propsList.get(0).getItemType(), itemName, propsList.get(0));
            }
        }
        if (cosmeticItem != null) {
            var variant = cosmeticItem.getHeadVariant(itemName);
            if (variant != null) {
                return cosmeticItem.createHeadItem(variant);
            }
        }
        if (collectionManager != null) {
            var collection = collectionManager.getCollection(itemName);
            if (collection != null) {
                var props = new ItemProperties(org.bukkit.Material.PAPER, collection.getName());
                props.setCollectionId(collection.getId());
                return collectionManager.createCollectionItem(props);
            }
        }
        return null;
    }

    /**
     * Initialize or refresh the item cache from database
     */
    private void initializeCache() {
        if (itemRepository == null) {
            logger.warning("Cannot initialize cache: ItemRepository is null");
            return;
        }
        
        try {
            logger.info("Initializing item cache from database...");
            
            itemNameCache.clear();
            collectionCache.clear();
            
            // Load all items
            List<ItemProperties> allItems = itemRepository.getAllItems();
            for (ItemProperties item : allItems) {
                String key = item.getDisplayName().toLowerCase();
                itemNameCache.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
                if (item.getLoreEntryId() != null) {
                    loreEntryIdCache.put(item.getLoreEntryId(), item);
                }
            }
            
            // Load all collections
            Map<Integer, String> collections = itemRepository.getAllCollections();
            for (Integer collectionId : collections.keySet()) {
                collectionCache.put(collectionId, itemRepository.getItemsByCollection(collectionId));
            }
            
            cacheInitialized = true;
            logger.info("Item cache initialized with " + itemNameCache.size() + " item names and " + collectionCache.size() + " collections");
        } catch (Exception e) {
            logger.error("Error initializing item cache", e);
        }
    }
    
    /**
     * Refresh the item cache synchronously (internal use, called from async wrapper).
     */
    private void refreshCacheSync() {
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
     * Get all items with their properties (synchronous internal method).
     *
     * @return A list of ItemProperties for all items
     */
    private List<ItemProperties> getAllItemsWithPropertiesSync() {
        List<ItemProperties> result = new ArrayList<>();

        if (itemRepository != null && cacheInitialized) {
            for (List<ItemProperties> list : itemNameCache.values()) {
                result.addAll(list);
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
     * Register a lore item with a reference to its lore entry ID (synchronous internal method).
     * This method should be called by LoreManager when a lore entry of type ITEM is created.
     *
     * @param loreEntryId The UUID of the lore entry in the lore_entry table
     * @param properties The properties of the item to register
     * @return true if the item was registered successfully, false otherwise
     */
    private boolean registerLoreItemSync(java.util.UUID loreEntryId, ItemProperties properties) {
        if (loreEntryId == null || properties == null) {
            logger.warning("Cannot register lore item with null ID or properties");
            return false;
        }
        logger.info("Registering lore item: " + properties.getDisplayName() + " with lore entry ID: " + loreEntryId);
        // Add lore entry ID reference to item properties
        properties.setLoreEntryId(loreEntryId.toString());
        // Store in database
        if (itemRepository != null) {
            try {
                int itemId = itemRepository.insertItem(properties);
                if (itemId > 0) {
                    // Add to name cache
                    String key = properties.getDisplayName().toLowerCase();
                    itemNameCache.computeIfAbsent(key, k -> new ArrayList<>()).add(properties);
                    // Add to loreEntryId cache
                    loreEntryIdCache.put(loreEntryId.toString(), properties);
                    logger.info("Registered item in database with ID: " + itemId);
                    return true;
                } else {
                    logger.warning("Failed to insert item into database");
                }
            } catch (Exception e) {
                logger.error("Error registering lore item", e);
            }
        } else {
            logger.warning("ItemRepository is not available, item will not be persisted");
            // Add to cache anyway
            String key = properties.getDisplayName().toLowerCase();
            itemNameCache.computeIfAbsent(key, k -> new ArrayList<>()).add(properties);
            loreEntryIdCache.put(loreEntryId.toString(), properties);
            return true;
        }
        return false;
    }

    // ============================================================
    // PUBLIC SYNC METHODS FOR COMMAND COMPATIBILITY
    // These methods are for internal plugin use (commands, display)
    // External plugins should use the IItemService async interface
    // ============================================================

    /**
     * Create a lore item by name (synchronous, for command use).
     * If multiple items exist with the same name, returns the first found.
     *
     * @param itemName The name of the item to create
     * @return The created ItemStack, or null if not found
     */
    public ItemStack createLoreItemSync(String itemName) {
        return createLoreItemByNameInternal(itemName);
    }

    /**
     * Returns a list of all registered item names (synchronous, for tab completion).
     *
     * @return List of all item names
     */
    public List<String> getAllItemNamesForCommands() {
        return getAllItemNamesSync();
    }

    /**
     * Get all items with their properties (synchronous, for command display).
     *
     * @return A list of ItemProperties for all items
     */
    public List<ItemProperties> getAllItemsWithPropertiesForCommands() {
        return getAllItemsWithPropertiesSync();
    }

    /**
     * Refresh the item cache (public method for commands).
     */
    public void refreshCacheForCommands() {
        refreshCacheSync();
    }

    // ============================================================
    // IItemService ASYNC IMPLEMENTATIONS
    // ============================================================

    /**
     * {@inheritDoc}
     * Async wrapper for createLoreItem(String).
     */
    @Override
    public CompletableFuture<Optional<ItemStack>> createLoreItem(String itemName) {
        return CompletableFuture.supplyAsync(() -> {
            ItemStack item = createLoreItemByNameInternal(itemName);
            return Optional.ofNullable(item);
        });
    }

    /**
     * {@inheritDoc}
     * Async wrapper for createLoreItem(ItemType, String, ItemProperties).
     */
    @Override
    public CompletableFuture<ItemStack> createLoreItem(ItemType type, String name, ItemProperties properties) {
        return CompletableFuture.supplyAsync(() -> createLoreItemInternal(type, name, properties));
    }

    /**
     * {@inheritDoc}
     * Async wrapper for giveItemToPlayer.
     */
    @Override
    public CompletableFuture<Boolean> giveItemToPlayer(String itemName, Player player) {
        return CompletableFuture.supplyAsync(() -> giveItemToPlayerSync(itemName, player));
    }

    /**
     * {@inheritDoc}
     * Async wrapper for getAllItemNames.
     */
    @Override
    public CompletableFuture<List<String>> getAllItemNames() {
        return CompletableFuture.supplyAsync(this::getAllItemNamesSync);
    }

    /**
     * {@inheritDoc}
     * Async wrapper for getAllItemsWithProperties.
     */
    @Override
    public CompletableFuture<List<ItemProperties>> getAllItemsWithProperties() {
        return CompletableFuture.supplyAsync(this::getAllItemsWithPropertiesSync);
    }

    /**
     * {@inheritDoc}
     * Async wrapper for registerLoreItem.
     */
    @Override
    public CompletableFuture<Boolean> registerLoreItem(UUID loreEntryId, ItemProperties properties) {
        return CompletableFuture.supplyAsync(() -> registerLoreItemSync(loreEntryId, properties));
    }

    /**
     * {@inheritDoc}
     * Async cache refresh.
     */
    @Override
    public CompletableFuture<Void> refreshCache() {
        return CompletableFuture.runAsync(this::initializeCache);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInFallbackMode() {
        return fallbackMode;
    }

    /**
     * Set fallback mode status.
     */
    protected void setFallbackMode(boolean fallbackMode) {
        this.fallbackMode = fallbackMode;
    }
}

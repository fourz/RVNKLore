package org.fourz.RVNKLore.lore.item;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseConnection;
import org.fourz.RVNKLore.data.ItemRepository;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.lore.item.enchant.EnchantManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.cosmetic.CosmeticsManager;
import org.fourz.RVNKLore.lore.item.custommodeldata.CustomModelDataManager;
import org.fourz.RVNKLore.service.IItemService;
import org.fourz.RVNKLore.service.ILoreItemResolver;

import java.util.ArrayList;
import java.util.HashMap;
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
public class ItemManager implements IItemService, ILoreItemResolver {

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
        // Initialize database repository
        if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
            DatabaseConnection dbConnection = plugin.getDatabaseManager().getDatabaseConnection();
            if (dbConnection != null) {
                this.itemRepository = new ItemRepository(plugin, dbConnection);
            } else {
                logger.warning("Database connection is null, ItemRepository will not be available");
            }
        } else {
            logger.warning("Database not available - some item features may be limited");
        }

        // Initialize subsystems
        this.cosmeticItem = new CosmeticsManager(plugin);
        this.enchantManager = new EnchantManager(plugin);
        this.modelDataManager = new CustomModelDataManager(plugin);
        this.collectionManager = new CollectionManager(plugin);
        
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

    // ── ILoreItemResolver ────────────────────────────────────────────────────────

    @Override
    public String resolveItemId(ItemStack item) {
        if (item == null) return null;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "lore_item_name"),
            org.bukkit.persistence.PersistentDataType.STRING);
    }

    // ── Item display helpers ─────────────────────────────────────────────────────

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
    /**
     * Stamp the cross-plugin id PDC keys (lore_item_id / lore_entry_id / lore_item_name)
     * onto an item. Shared so non-STANDARD factory paths (e.g. ENCHANTED) resolve too (#1506).
     */
    private void applyIdPdc(ItemStack item, ItemProperties properties, String name) {
        if (item == null) return;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (properties.getDatabaseId() > 0) {
            meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "lore_item_id"),
                org.bukkit.persistence.PersistentDataType.INTEGER, properties.getDatabaseId());
        }
        if (properties.getLoreEntryId() != null && !properties.getLoreEntryId().isEmpty()) {
            meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "lore_entry_id"),
                org.bukkit.persistence.PersistentDataType.STRING, properties.getLoreEntryId());
        }
        if (name != null && !name.isEmpty()) {
            meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "lore_item_name"),
                org.bukkit.persistence.PersistentDataType.STRING, name);
        }
        item.setItemMeta(meta);
    }

    private ItemStack createLoreItemInternal(ItemType type, String name, ItemProperties properties) {
        switch (type) {
            case ENCHANTED: {
                // #1506: stamp the id PDC keys the STANDARD path applies but ENCHANTED skipped,
                // so enchanted lore items are resolvable via resolveItemId.
                ItemStack enchanted = enchantManager.createEnchantedItem(properties);
                applyIdPdc(enchanted, properties, name);
                return enchanted;
            }
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
                ItemStack item = new ItemStack(properties.getMaterial());
                // Handle written books with pages
                if (properties.getMaterial() == org.bukkit.Material.WRITTEN_BOOK && 
                    properties.getPages() != null && !properties.getPages().isEmpty()) {
                    org.bukkit.inventory.meta.BookMeta bookMeta = (org.bukkit.inventory.meta.BookMeta) item.getItemMeta();
                    if (bookMeta != null) {
                        if (properties.getDisplayName() != null) {
                            bookMeta.setDisplayName(properties.getDisplayName());
                        }
                        // Add all pages to the book
                        for (String page : properties.getPages()) {
                            bookMeta.addPage(page);
                        }
                        // Add generation flag for written book
                        bookMeta.setGeneration(org.bukkit.inventory.meta.BookMeta.Generation.ORIGINAL);
                        // PDC tags for cross-plugin resolution
                        if (properties.getDatabaseId() > 0) {
                            bookMeta.getPersistentDataContainer().set(
                                new org.bukkit.NamespacedKey(plugin, "lore_item_id"),
                                org.bukkit.persistence.PersistentDataType.INTEGER,
                                properties.getDatabaseId());
                        }
                        if (properties.getLoreEntryId() != null && !properties.getLoreEntryId().isEmpty()) {
                            bookMeta.getPersistentDataContainer().set(
                                new org.bukkit.NamespacedKey(plugin, "lore_entry_id"),
                                org.bukkit.persistence.PersistentDataType.STRING,
                                properties.getLoreEntryId());
                        }
                        if (name != null && !name.isEmpty()) {
                            bookMeta.getPersistentDataContainer().set(
                                new org.bukkit.NamespacedKey(plugin, "lore_item_name"),
                                org.bukkit.persistence.PersistentDataType.STRING,
                                name);
                        }
                        item.setItemMeta(bookMeta);
                    }
                    return item;
                }
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    if (properties.getDisplayName() != null) {
                        meta.setDisplayName(properties.getDisplayName());
                    }
                    if (properties.getLore() != null && !properties.getLore().isEmpty()) {
                        meta.setLore(properties.getLore());
                    } else if (properties.getRarity() != null && !properties.getRarity().isEmpty()) {
                        meta.setLore(java.util.Arrays.asList(
                            org.bukkit.ChatColor.GRAY + "" + org.bukkit.ChatColor.ITALIC + properties.getRarity()
                        ));
                    }
                    if (properties.getCustomModelData() > 0) {
                        meta.setCustomModelData(properties.getCustomModelData());
                    }
                    if (properties.getDatabaseId() > 0) {
                        org.bukkit.NamespacedKey itemIdKey = new org.bukkit.NamespacedKey(plugin, "lore_item_id");
                        meta.getPersistentDataContainer().set(itemIdKey,
                            org.bukkit.persistence.PersistentDataType.INTEGER,
                            properties.getDatabaseId());
                    }
                    if (properties.getLoreEntryId() != null && !properties.getLoreEntryId().isEmpty()) {
                        org.bukkit.NamespacedKey entryKey = new org.bukkit.NamespacedKey(plugin, "lore_entry_id");
                        meta.getPersistentDataContainer().set(entryKey,
                            org.bukkit.persistence.PersistentDataType.STRING,
                            properties.getLoreEntryId());
                    }
                    if (name != null && !name.isEmpty()) {
                        meta.getPersistentDataContainer().set(
                            new org.bukkit.NamespacedKey(plugin, "lore_item_name"),
                            org.bukkit.persistence.PersistentDataType.STRING,
                            name);
                    }
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
                names.addAll(collectionManager.getAllCollectionsSync().keySet());
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
            List<ItemProperties> propsList = itemRepository.getAllItemsByName(itemName).join();
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
            var collection = collectionManager.getCollectionSync(itemName);
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
            logger.debug("Initializing item cache from database...");
            
            itemNameCache.clear();
            collectionCache.clear();
            
            // Load all items
            List<ItemProperties> allItems = itemRepository.getAllItems().join();
            for (ItemProperties item : allItems) {
                String key = item.getDisplayName().toLowerCase();
                itemNameCache.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
                if (item.getLoreEntryId() != null) {
                    loreEntryIdCache.put(item.getLoreEntryId(), item);
                }
            }
            
            // Load all collections in parallel
            Map<Integer, String> collections = itemRepository.getAllCollections().join();
            Map<Integer, CompletableFuture<List<ItemProperties>>> collectionFutures = new HashMap<>();
            for (Integer collectionId : collections.keySet()) {
                collectionFutures.put(collectionId, itemRepository.getItemsByCollection(collectionId));
            }
            CompletableFuture.allOf(collectionFutures.values().toArray(new CompletableFuture[0])).join();
            for (Map.Entry<Integer, CompletableFuture<List<ItemProperties>>> entry : collectionFutures.entrySet()) {
                collectionCache.put(entry.getKey(), entry.getValue().join());
            }
            
            cacheInitialized = true;
            logger.debug("Item cache initialized with " + itemNameCache.size() + " item names and " + collectionCache.size() + " collections");
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
                for (var entry : collectionManager.getAllCollectionsSync().entrySet()) {
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
    private int registerLoreItemSync(java.util.UUID loreEntryId, ItemProperties properties) {
        if (loreEntryId == null || properties == null) {
            logger.warning("Cannot register lore item with null ID or properties");
            return -1;
        }
        logger.debug("Registering lore item: " + properties.getDisplayName() + " with lore entry ID: " + loreEntryId);
        // Add lore entry ID reference to item properties
        properties.setLoreEntryId(loreEntryId.toString());
        // Store in database
        if (itemRepository != null) {
            try {
                // A lore item already registered for this entry (e.g. a book re-placed on a
                // lectern) is not an error — short-circuit to the existing id instead of attempting
                // a duplicate insert that would hit the UNIQUE constraint and spam the log (#1427).
                Optional<ItemProperties> existing = itemRepository.getItemByLoreEntryId(loreEntryId.toString()).join();
                if (existing.isPresent()) {
                    invalidateNameCache(properties.getDisplayName());
                    loreEntryIdCache.put(loreEntryId.toString(), properties);
                    logger.debug("Lore item already registered for entry " + loreEntryId +
                        ", skipping duplicate insert: " + properties.getDisplayName());
                    return existing.get().getDatabaseId();
                }
                int itemId = itemRepository.insertItem(properties).join();
                if (itemId > 0) {
                    // Invalidate name cache so the next give re-reads this row fresh from the DB
                    invalidateNameCache(properties.getDisplayName());
                    // Add to loreEntryId cache
                    loreEntryIdCache.put(loreEntryId.toString(), properties);
                    logger.debug("Registered item in database with ID: " + itemId);
                    return itemId;
                } else {
                    logger.warning("Failed to insert item into database");
                }
            } catch (Exception e) {
                logger.error("Error registering lore item", e);
            }
        } else {
            logger.warning("ItemRepository is not available, item will not be persisted");
            // Add to cache anyway (id unknown without a repository)
            String key = properties.getDisplayName().toLowerCase();
            itemNameCache.computeIfAbsent(key, k -> new ArrayList<>()).add(properties);
            loreEntryIdCache.put(loreEntryId.toString(), properties);
            return 0;
        }
        return -1;
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

    /**
     * Invalidate the name cache for a single item so the next lookup re-reads the authoritative
     * DB row instead of a stale or duplicate cached {@link ItemProperties} (#1642).
     *
     * <p>The write paths previously <em>appended</em> the new props to the cached list
     * ({@code itemNameCache.computeIfAbsent(key, ...).add(props)}). Because a mint first registers
     * a page-less base row and then upserts the full properties, that left the empty base copy at
     * index 0, and {@code createLoreItemByNameInternal} always hands out {@code get(0)} — so a
     * freshly minted/updated book gave empty until a manual {@code /lore item list} refresh.
     * Removing the key instead forces the next give to re-query the DB (a single, current row).</p>
     */
    private void invalidateNameCache(String displayName) {
        if (displayName != null) {
            itemNameCache.remove(displayName.toLowerCase());
        }
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
        return CompletableFuture.supplyAsync(() -> registerLoreItemSync(loreEntryId, properties) > 0);
    }

    /**
     * Upsert the full {@link ItemProperties} (incl. enchantments/lore/pages) onto the lore_item
     * row for {@code loreEntryId}, returning its id. Used by the REST mint endpoint (#1517).
     *
     * <p>Creating an ITEM lore_entry already inserts a bare lore_item row (name/material/type,
     * but no {@code item_properties}); this method fills that row in via
     * {@link ItemRepository#updateItem} (which serializes enchantments per #1503), or inserts a
     * fresh row if none exists. Returns the item id, or a value {@code <= 0} on failure.</p>
     */
    public CompletableFuture<Integer> registerLoreItemForId(UUID loreEntryId, ItemProperties properties) {
        return CompletableFuture.supplyAsync(() -> upsertItemPropertiesSync(loreEntryId, properties));
    }

    private int upsertItemPropertiesSync(UUID loreEntryId, ItemProperties properties) {
        if (loreEntryId == null || properties == null) {
            logger.warning("Cannot upsert lore item with null ID or properties");
            return -1;
        }
        if (itemRepository == null) {
            logger.warning("ItemRepository is not available, item properties will not be persisted");
            return -1;
        }
        properties.setLoreEntryId(loreEntryId.toString());
        try {
            Optional<ItemProperties> existing = itemRepository.getItemByLoreEntryId(loreEntryId.toString()).join();
            int itemId;
            if (existing.isPresent()) {
                itemId = existing.get().getDatabaseId();
                if (!itemRepository.updateItem(itemId, properties).join()) {
                    logger.warning("Failed to update item_properties for item id " + itemId);
                    return -1;
                }
                logger.debug("Updated item_properties for existing lore_item id " + itemId
                    + ": " + properties.getDisplayName());
            } else {
                itemId = itemRepository.insertItem(properties).join();
                if (itemId <= 0) {
                    logger.warning("Failed to insert lore item: " + properties.getDisplayName());
                    return -1;
                }
                logger.debug("Inserted lore_item id " + itemId + ": " + properties.getDisplayName());
            }
            invalidateNameCache(properties.getDisplayName());
            loreEntryIdCache.put(loreEntryId.toString(), properties);
            return itemId;
        } catch (Exception e) {
            logger.error("Error upserting lore item properties", e);
            return -1;
        }
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
    public CompletableFuture<Optional<org.bukkit.inventory.ItemStack>> createLoreItem(int itemId) {
        if (itemRepository == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return itemRepository.getItemById(itemId).thenApply(optProps ->
            optProps.map(props -> createLoreItemInternal(props.getItemType(), props.getDisplayName(), props)));
    }

    public CompletableFuture<Optional<ItemProperties>> getItemPropertiesById(int itemId) {
        if (itemRepository == null) {
            logger.warning("getItemPropertiesById(" + itemId + "): itemRepository is null");
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return itemRepository.getItemById(itemId).thenApply(opt -> {
            logger.debug("getItemPropertiesById(" + itemId + "): " + (opt.isPresent() ? opt.get().getDisplayName() : "empty"));
            return opt;
        });
    }

    // ── Versioned item surface (#1528) — thin delegates to ItemRepository ──────────

    /** Snapshot the item's current properties into its v1 submission (call after create). */
    public CompletableFuture<Boolean> snapshotItemVersion(int itemId) {
        return itemRepository == null ? CompletableFuture.completedFuture(false)
                : itemRepository.snapshotCurrentVersion(itemId);
    }

    /** Update an item as a new content_version; returns the new version or -1. */
    public CompletableFuture<Integer> updateItemVersioned(int itemId, ItemProperties properties) {
        return itemRepository == null ? CompletableFuture.completedFuture(-1)
                : itemRepository.updateItemVersioned(itemId, properties)
                    .thenApply(ver -> {
                        // The materialized item_properties changed (e.g. book pages) — drop the
                        // stale name-cache entry so the next give reads the new props from the DB.
                        // Without this the REST PUT updated the DB but left the cache serving the
                        // pre-update copy (#1642).
                        invalidateNameCache(properties.getDisplayName());
                        return ver;
                    });
    }

    /** Version history for an item. */
    public CompletableFuture<List<java.util.Map<String, Object>>> getItemVersions(int itemId) {
        return itemRepository == null ? CompletableFuture.completedFuture(new ArrayList<>())
                : itemRepository.getItemVersions(itemId);
    }

    /** Roll an item back to a prior content_version. */
    public CompletableFuture<Boolean> rollbackItemToVersion(int itemId, int version) {
        return itemRepository == null ? CompletableFuture.completedFuture(false)
                : itemRepository.rollbackItemToVersion(itemId, version)
                    .whenComplete((ok, ex) -> { if (Boolean.TRUE.equals(ok)) refreshCacheForCommands(); });
    }

    /** Soft-delete (hide + archive, recoverable). */
    public CompletableFuture<Boolean> softDeleteItem(int itemId) {
        return itemRepository == null ? CompletableFuture.completedFuture(false)
                : itemRepository.softDeleteItem(itemId)
                    .whenComplete((ok, ex) -> { if (Boolean.TRUE.equals(ok)) refreshCacheForCommands(); });
    }

    /** Hard-delete (purge entry + CASCADE). */
    public CompletableFuture<Boolean> hardDeleteItem(int itemId) {
        return itemRepository == null ? CompletableFuture.completedFuture(false)
                : itemRepository.hardDeleteItem(itemId)
                    .whenComplete((ok, ex) -> { if (Boolean.TRUE.equals(ok)) refreshCacheForCommands(); });
    }

    /**
     * {@inheritDoc}
     * Delegates to ItemRepository.getPresetsForQuest().
     */
    @Override
    public CompletableFuture<List<ItemProperties>> getPresetsForQuest(String questId) {
        if (itemRepository == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        return itemRepository.getPresetsForQuest(questId);
    }

    // ── #1496: RNG pool + preset authoring — thin delegators to ItemRepository ──

    /**
     * Resolve an item argument to its {@code lore_item.id}. Accepts a numeric id directly, or a
     * display name matched case-insensitively against the item cache. Returns -1 if unresolved.
     */
    public int resolveDatabaseId(String itemArg) {
        if (itemArg == null || itemArg.isBlank()) return -1;
        String trimmed = itemArg.trim();
        if (trimmed.matches("\\d+")) {
            return Integer.parseInt(trimmed);
        }
        for (ItemProperties p : getAllItemsWithPropertiesForCommands()) {
            if (p.getDisplayName() != null && p.getDisplayName().equalsIgnoreCase(trimmed) && p.getDatabaseId() > 0) {
                return p.getDatabaseId();
            }
        }
        return -1;
    }

    /** Add an item to an RNG pool. */
    public CompletableFuture<Boolean> addPoolEntry(String poolId, int loreItemId, String rarityTier, int weight) {
        return itemRepository == null ? CompletableFuture.completedFuture(false)
                : itemRepository.addPoolEntry(poolId, loreItemId, rarityTier, weight);
    }

    /** Remove an item from an RNG pool. */
    public CompletableFuture<Boolean> removePoolEntry(String poolId, int loreItemId) {
        return itemRepository == null ? CompletableFuture.completedFuture(false)
                : itemRepository.removePoolEntry(poolId, loreItemId);
    }

    /** List every entry in an RNG pool. */
    public CompletableFuture<List<ItemRepository.PoolEntryRow>> listPoolEntries(String poolId) {
        return itemRepository == null ? CompletableFuture.completedFuture(new ArrayList<>())
                : itemRepository.listPoolEntries(poolId);
    }

    /** Bind an item to a quest as a preset. */
    public CompletableFuture<Boolean> addPreset(String questId, int loreItemId, String label) {
        return itemRepository == null ? CompletableFuture.completedFuture(false)
                : itemRepository.addPreset(questId, loreItemId, label);
    }

    /** Unbind an item preset from a quest. */
    public CompletableFuture<Boolean> removePreset(String questId, int loreItemId) {
        return itemRepository == null ? CompletableFuture.completedFuture(false)
                : itemRepository.removePreset(questId, loreItemId);
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


# Items & Inventory API Reference

This document covers comprehensive ItemStack management, custom items, inventories, and item-related systems for RVNKLore development.

## ItemStack Fundamentals

### Basic ItemStack Operations
```java
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemStackBasics {
    
    // Create basic items
    public ItemStack createBasicItem(Material material, int amount) {
        return new ItemStack(material, amount);
    }
    
    // Clone and modify items safely
    public ItemStack cloneAndModify(ItemStack original) {
        ItemStack copy = original.clone();
        // Modify the copy without affecting original
        return copy;
    }
    
    // Check item validity
    public boolean isValidItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }
}
```

### ItemMeta Manipulation
```java
public class ItemMetaManager {
    
    public ItemStack setItemName(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + name);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    public ItemStack setItemLore(ItemStack item, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Apply color codes and formatting
            List<String> formattedLore = lore.stream()
                .map(line -> ChatColor.GRAY + line)
                .collect(Collectors.toList());
            meta.setLore(formattedLore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    public ItemStack setCustomModelData(ItemStack item, int modelData) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(modelData);
            item.setItemMeta(meta);
        }
        return item;
    }
}
```

## Custom Item Creation for RVNKLore

### Lore Item Builder Pattern
```java
public class LoreItemBuilder {
    private ItemStack item;
    private ItemMeta meta;
    private final RVNKLore plugin;
    
    public LoreItemBuilder(RVNKLore plugin, Material material) {
        this.plugin = plugin;
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }
    
    public LoreItemBuilder name(String name) {
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + ChatColor.GOLD + name);
        }
        return this;
    }
    
    public LoreItemBuilder lore(String... loreLines) {
        if (meta != null) {
            List<String> lore = Arrays.stream(loreLines)
                .map(line -> ChatColor.GRAY + ChatColor.ITALIC + line)
                .collect(Collectors.toList());
            meta.setLore(lore);
        }
        return this;
    }
    
    public LoreItemBuilder modelData(int modelData) {
        if (meta != null) {
            meta.setCustomModelData(modelData);
        }
        return this;
    }
    
    public LoreItemBuilder enchantment(Enchantment enchant, int level) {
        if (meta != null) {
            meta.addEnchant(enchant, level, true);
        }
        return this;
    }
    
    public LoreItemBuilder unbreakable() {
        if (meta != null) {
            meta.setUnbreakable(true);
        }
        return this;
    }
    
    public LoreItemBuilder loreId(String loreId) {
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(plugin, "lore_id");
            meta.getPersistentDataContainer()
                .set(key, PersistentDataType.STRING, loreId);
        }
        return this;
    }
    
    public LoreItemBuilder rarity(RarityLevel rarity) {
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(plugin, "rarity");
            meta.getPersistentDataContainer()
                .set(key, PersistentDataType.STRING, rarity.name());
        }
        return this;
    }
    
    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }
}
```

### Usage Example
```java
public class LoreItemFactory {
    
    public ItemStack createLegendaryWeapon(String name, String description) {
        return new LoreItemBuilder(plugin, Material.DIAMOND_SWORD)
            .name(name)
            .lore(description, "", ChatColor.GOLD + "★ Legendary Item ★")
            .modelData(plugin.getItemManager().getModelDataManager()
                     .getNextModelId(ModelDataCategory.WEAPONS))
            .enchantment(Enchantment.SHARPNESS, 5)
            .enchantment(Enchantment.UNBREAKING, 3)
            .unbreakable()
            .rarity(RarityLevel.LEGENDARY)
            .loreId(generateLoreId())
            .build();
    }
}
```

## Persistent Data Container (PDC)

### Advanced PDC Usage
```java
public class LorePDCManager {
    private final RVNKLore plugin;
    
    // Define namespace keys
    public static final NamespacedKey LORE_ID = new NamespacedKey(plugin, "lore_id");
    public static final NamespacedKey ITEM_TYPE = new NamespacedKey(plugin, "item_type");
    public static final NamespacedKey CREATION_DATE = new NamespacedKey(plugin, "creation_date");
    public static final NamespacedKey CREATOR = new NamespacedKey(plugin, "creator");
    public static final NamespacedKey COLLECTION_ID = new NamespacedKey(plugin, "collection_id");
    
    public void setLoreItemData(ItemStack item, LoreItemData data) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        // Store basic lore data
        pdc.set(LORE_ID, PersistentDataType.STRING, data.getLoreId());
        pdc.set(ITEM_TYPE, PersistentDataType.STRING, data.getItemType().name());
        pdc.set(CREATION_DATE, PersistentDataType.LONG, data.getCreationDate().getTime());
        pdc.set(CREATOR, PersistentDataType.STRING, data.getCreator());
        
        // Store collection data if part of a collection
        if (data.getCollectionId() != null) {
            pdc.set(COLLECTION_ID, PersistentDataType.STRING, data.getCollectionId());
        }
        
        item.setItemMeta(meta);
    }
    
    public LoreItemData getLoreItemData(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        if (!pdc.has(LORE_ID, PersistentDataType.STRING)) {
            return null; // Not a lore item
        }
        
        LoreItemData data = new LoreItemData();
        data.setLoreId(pdc.get(LORE_ID, PersistentDataType.STRING));
        data.setItemType(ItemType.valueOf(pdc.get(ITEM_TYPE, PersistentDataType.STRING)));
        data.setCreationDate(new Date(pdc.get(CREATION_DATE, PersistentDataType.LONG)));
        data.setCreator(pdc.get(CREATOR, PersistentDataType.STRING));
        data.setCollectionId(pdc.get(COLLECTION_ID, PersistentDataType.STRING));
        
        return data;
    }
    
    public boolean isLoreItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && 
               meta.getPersistentDataContainer().has(LORE_ID, PersistentDataType.STRING);
    }
}
```

## Inventory Management

### Custom Inventory Creation
```java
public class LoreInventoryManager {
    private final RVNKLore plugin;
    
    public Inventory createLoreBrowserInventory(Player player, List<LoreEntry> entries) {
        int size = Math.min(54, ((entries.size() + 8) / 9) * 9); // Round up to nearest 9
        Inventory inventory = Bukkit.createInventory(
            new LoreBrowserHolder(entries), 
            size, 
            ChatColor.DARK_PURPLE + "Lore Browser"
        );
        
        // Fill with lore items
        for (int i = 0; i < Math.min(entries.size(), size - 9); i++) {
            LoreEntry entry = entries.get(i);
            ItemStack icon = createLoreEntryIcon(entry);
            inventory.setItem(i, icon);
        }
        
        // Add navigation items
        addNavigationItems(inventory, size);
        
        return inventory;
    }
    
    private ItemStack createLoreEntryIcon(LoreEntry entry) {
        Material iconMaterial = determineMaterialForLoreType(entry.getType());
        
        return new LoreItemBuilder(plugin, iconMaterial)
            .name(entry.getName())
            .lore(
                "Type: " + entry.getType().getDisplayName(),
                "",
                "Description:",
                truncateDescription(entry.getDescription()),
                "",
                ChatColor.YELLOW + "Click to view details"
            )
            .loreId(String.valueOf(entry.getId()))
            .build();
    }
    
    private void addNavigationItems(Inventory inventory, int size) {
        // Close button
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Close");
        closeButton.setItemMeta(closeMeta);
        inventory.setItem(size - 1, closeButton);
        
        // Previous/Next page buttons (if needed)
        // Implementation depends on pagination requirements
    }
}

// Custom InventoryHolder for type safety
public class LoreBrowserHolder implements InventoryHolder {
    private final List<LoreEntry> entries;
    
    public LoreBrowserHolder(List<LoreEntry> entries) {
        this.entries = entries;
    }
    
    @Override
    public Inventory getInventory() {
        return null; // Not used, but required by interface
    }
    
    public List<LoreEntry> getEntries() {
        return entries;
    }
}
```

### Inventory Event Handling
```java
public class LoreInventoryListener implements Listener {
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof LoreBrowserHolder loreBrowser) {
            event.setCancelled(true); // Prevent item taking
            
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
            
            // Handle close button
            if (clickedItem.getType() == Material.BARRIER) {
                player.closeInventory();
                return;
            }
            
            // Handle lore entry clicks
            String loreId = getLoreIdFromItem(clickedItem);
            if (loreId != null) {
                displayLoreDetails(player, loreId);
            }
        }
    }
    
    private String getLoreIdFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "lore_id");
        return pdc.get(key, PersistentDataType.STRING);
    }
}
```

## Item Collection System

### Collection Management
```java
public class ItemCollectionManager {
    private final RVNKLore plugin;
    
    public void addItemToCollection(ItemStack item, String collectionId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "collection_id");
        pdc.set(key, PersistentDataType.STRING, collectionId);
        
        // Add collection marker to lore
        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();
        
        lore.add("");
        lore.add(ChatColor.BLUE + "Collection: " + getCollectionName(collectionId));
        meta.setLore(lore);
        
        item.setItemMeta(meta);
    }
    
    public List<ItemStack> getItemsInCollection(String collectionId) {
        List<ItemStack> collectionItems = new ArrayList<>();
        
        // Search through player inventories (if needed)
        // Or retrieve from database
        
        return collectionItems;
    }
    
    public boolean isPartOfCollection(ItemStack item, String collectionId) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "collection_id");
        String itemCollectionId = pdc.get(key, PersistentDataType.STRING);
        
        return collectionId.equals(itemCollectionId);
    }
}
```

## Item Durability & Enchantments

### Durability Management
```java
public class ItemDurabilityManager {
    
    public void setCustomDurability(ItemStack item, int current, int max) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        // For tools/weapons/armor, use actual durability
        if (item.getType().getMaxDurability() > 0) {
            short damage = (short) (item.getType().getMaxDurability() - current);
            item.setDurability(damage);
        }
        
        // Store custom durability in PDC for non-damageable items
        NamespacedKey currentKey = new NamespacedKey(plugin, "current_durability");
        NamespacedKey maxKey = new NamespacedKey(plugin, "max_durability");
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(currentKey, PersistentDataType.INTEGER, current);
        pdc.set(maxKey, PersistentDataType.INTEGER, max);
        
        // Update lore to show durability
        updateDurabilityLore(meta, current, max);
        item.setItemMeta(meta);
    }
    
    private void updateDurabilityLore(ItemMeta meta, int current, int max) {
        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();
        
        // Remove old durability line if exists
        lore.removeIf(line -> line.contains("Durability:"));
        
        // Add new durability line
        ChatColor color = current > max * 0.5 ? ChatColor.GREEN : 
                         current > max * 0.25 ? ChatColor.YELLOW : ChatColor.RED;
        
        lore.add(color + "Durability: " + current + "/" + max);
        meta.setLore(lore);
    }
}
```

### Custom Enchantment System
```java
public class CustomEnchantmentManager {
    private final Map<String, CustomEnchantment> customEnchantments = new HashMap<>();
    
    public void registerCustomEnchantment(String id, CustomEnchantment enchantment) {
        customEnchantments.put(id, enchantment);
    }
    
    public void applyCustomEnchantment(ItemStack item, String enchantmentId, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        // Store custom enchantment in PDC
        NamespacedKey key = new NamespacedKey(plugin, "custom_enchant_" + enchantmentId);
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, level);
        
        // Add enchantment glow
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        
        // Add to lore
        addEnchantmentToLore(meta, enchantmentId, level);
        item.setItemMeta(meta);
    }
    
    public Map<String, Integer> getCustomEnchantments(ItemStack item) {
        Map<String, Integer> enchantments = new HashMap<>();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return enchantments;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        for (String enchantId : customEnchantments.keySet()) {
            NamespacedKey key = new NamespacedKey(plugin, "custom_enchant_" + enchantId);
            if (pdc.has(key, PersistentDataType.INTEGER)) {
                enchantments.put(enchantId, pdc.get(key, PersistentDataType.INTEGER));
            }
        }
        
        return enchantments;
    }
}
```

## Item Serialization & Storage

### Item NBT Serialization
```java
public class ItemSerializationManager {
    
    public String serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            
            dataOutput.writeObject(item);
            dataOutput.close();
            
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            logger.error("Failed to serialize item", e);
            return null;
        }
    }
    
    public ItemStack deserializeItem(String data) {
        try {
            ByteArrayInputStream inputStream = 
                new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            
            return item;
        } catch (Exception e) {
            logger.error("Failed to deserialize item", e);
            return null;
        }
    }
    
    // JSON serialization for database storage
    public JsonObject itemToJson(ItemStack item) {
        JsonObject json = new JsonObject();
        
        json.addProperty("type", item.getType().name());
        json.addProperty("amount", item.getAmount());
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                json.addProperty("name", meta.getDisplayName());
            }
            
            if (meta.hasLore()) {
                JsonArray loreArray = new JsonArray();
                meta.getLore().forEach(loreArray::add);
                json.add("lore", loreArray);
            }
            
            if (meta.hasCustomModelData()) {
                json.addProperty("model_data", meta.getCustomModelData());
            }
            
            // Serialize PDC data
            serializePDCToJson(meta.getPersistentDataContainer(), json);
        }
        
        return json;
    }
}
```

## Performance Optimization

### Item Caching
```java
public class ItemCacheManager {
    private final Cache<String, ItemStack> itemCache;
    
    public ItemCacheManager() {
        this.itemCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();
    }
    
    public ItemStack getCachedItem(String cacheKey) {
        ItemStack cached = itemCache.getIfPresent(cacheKey);
        return cached != null ? cached.clone() : null; // Always return clones
    }
    
    public void cacheItem(String cacheKey, ItemStack item) {
        itemCache.put(cacheKey, item.clone()); // Store clone to prevent modifications
    }
    
    public void invalidateCache(String cacheKey) {
        itemCache.invalidate(cacheKey);
    }
}
```

### Bulk Operations
```java
public class BulkItemOperations {
    
    public void processItemsBatch(List<ItemStack> items, 
                                  Consumer<ItemStack> processor) {
        // Process in chunks to avoid blocking
        int chunkSize = 100;
        
        for (int i = 0; i < items.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, items.size());
            List<ItemStack> chunk = items.subList(i, end);
            
            // Process chunk asynchronously
            CompletableFuture.runAsync(() -> {
                chunk.forEach(processor);
            });
        }
    }
    
    public void updateItemsInInventories(Collection<Player> players, 
                                        Predicate<ItemStack> filter,
                                        UnaryOperator<ItemStack> updater) {
        for (Player player : players) {
            Inventory inventory = player.getInventory();
            
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                
                if (item != null && filter.test(item)) {
                    ItemStack updated = updater.apply(item);
                    inventory.setItem(i, updated);
                }
            }
        }
    }
}
```

This comprehensive Items & Inventory API reference provides the foundation for all item-related functionality in RVNKLore, from basic item creation to advanced collection management and performance optimization.

package org.fourz.RVNKLore.lore.item.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages custom model data allocation and tracking for resource pack integration.
 * Provides organized model ID allocation across different item categories and ensures
 * no conflicts between different item types.
 * 
 * Model ID ranges are allocated as follows:
 * - 1-100: Reserved for system use
 * - 101-200: Weapons
 * - 201-300: Armor
 * - 301-400: Tools
 * - 401-500: Cosmetic items
 * - 501-600: Decorative blocks
 * - 601-700: Consumables
 * - 701-800: Seasonal items
 * - 801-900: Event items
 * - 901-1000: Special/Legendary items
 */
public class ModelDataManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    
    // Model ID tracking
    private final Map<ModelDataCategory, Integer> categoryCounters = new ConcurrentHashMap<>();
    private final Map<String, Integer> itemModelIds = new ConcurrentHashMap<>();
    private final Map<Integer, String> modelIdItems = new ConcurrentHashMap<>();
    
    // Category ranges
    private static final Map<ModelDataCategory, ModelDataRange> CATEGORY_RANGES = new HashMap<>();
    
    static {
        CATEGORY_RANGES.put(ModelDataCategory.SYSTEM, new ModelDataRange(1, 100));
        CATEGORY_RANGES.put(ModelDataCategory.WEAPONS, new ModelDataRange(101, 200));
        CATEGORY_RANGES.put(ModelDataCategory.ARMOR, new ModelDataRange(201, 300));
        CATEGORY_RANGES.put(ModelDataCategory.TOOLS, new ModelDataRange(301, 400));
        CATEGORY_RANGES.put(ModelDataCategory.COSMETIC, new ModelDataRange(401, 500));
        CATEGORY_RANGES.put(ModelDataCategory.DECORATIVE, new ModelDataRange(501, 600));
        CATEGORY_RANGES.put(ModelDataCategory.CONSUMABLES, new ModelDataRange(601, 700));
        CATEGORY_RANGES.put(ModelDataCategory.SEASONAL, new ModelDataRange(701, 800));
        CATEGORY_RANGES.put(ModelDataCategory.EVENT, new ModelDataRange(801, 900));
        CATEGORY_RANGES.put(ModelDataCategory.LEGENDARY, new ModelDataRange(901, 1000));
    }
    
    public ModelDataManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ModelDataManager");
        
        initializeCategoryCounters();
        logger.info("ModelDataManager initialized");
    }
    
    /**
     * Initialize category counters to start of their respective ranges.
     */
    private void initializeCategoryCounters() {
        for (Map.Entry<ModelDataCategory, ModelDataRange> entry : CATEGORY_RANGES.entrySet()) {
            categoryCounters.put(entry.getKey(), entry.getValue().getStart());
        }
    }
    
    /**
     * Allocate a new model ID for an item in the specified category.
     * 
     * @param itemKey Unique identifier for the item
     * @param category The model data category
     * @return The allocated model ID, or -1 if allocation failed
     */
    public int allocateModelId(String itemKey, ModelDataCategory category) {
        // Check if item already has a model ID
        if (itemModelIds.containsKey(itemKey)) {
            logger.warning("Item already has allocated model ID: " + itemKey);
            return itemModelIds.get(itemKey);
        }
        
        ModelDataRange range = CATEGORY_RANGES.get(category);
        if (range == null) {
            logger.warning("Unknown model data category: " + category);
            return -1;
        }
        
        int currentCounter = categoryCounters.get(category);
        if (currentCounter > range.getEnd()) {
            logger.warning("Model ID range exhausted for category: " + category);
            return -1;
        }
        
        // Allocate the ID
        int modelId = currentCounter;
        categoryCounters.put(category, currentCounter + 1);
        
        // Register the allocation
        itemModelIds.put(itemKey, modelId);
        modelIdItems.put(modelId, itemKey);
        
        logger.info("Allocated model ID " + modelId + " to item: " + itemKey + " (category: " + category + ")");
        return modelId;
    }
    
    /**
     * Get the model ID for a registered item.
     * 
     * @param itemKey The item identifier
     * @return The model ID, or -1 if not found
     */
    public int getModelId(String itemKey) {
        return itemModelIds.getOrDefault(itemKey, -1);
    }
    
    /**
     * Get the item key for a model ID.
     * 
     * @param modelId The model ID
     * @return The item key, or null if not found
     */
    public String getItemKey(int modelId) {
        return modelIdItems.get(modelId);
    }
    
    /**
     * Check if a model ID is allocated.
     * 
     * @param modelId The model ID to check
     * @return True if allocated
     */
    public boolean isModelIdAllocated(int modelId) {
        return modelIdItems.containsKey(modelId);
    }
    
    /**
     * Apply custom model data to an ItemStack.
     * 
     * @param item The item to modify
     * @param itemKey The item identifier
     * @param category The model data category
     * @return The modified ItemStack
     */
    public ItemStack applyModelData(ItemStack item, String itemKey, ModelDataCategory category) {
        if (item == null) {
            return null;
        }
        
        int modelId = getModelId(itemKey);
        if (modelId == -1) {
            modelId = allocateModelId(itemKey, category);
        }
        
        if (modelId != -1) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(modelId);
                item.setItemMeta(meta);
                logger.debug("Applied model data " + modelId + " to item: " + itemKey);
            }
        }
        
        return item;
    }
    
    /**
     * Get the category for a model ID.
     * 
     * @param modelId The model ID
     * @return The category, or null if not found
     */
    public ModelDataCategory getCategoryForModelId(int modelId) {
        for (Map.Entry<ModelDataCategory, ModelDataRange> entry : CATEGORY_RANGES.entrySet()) {
            ModelDataRange range = entry.getValue();
            if (modelId >= range.getStart() && modelId <= range.getEnd()) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Get the range for a category.
     * 
     * @param category The category
     * @return The model data range
     */
    public ModelDataRange getCategoryRange(ModelDataCategory category) {
        return CATEGORY_RANGES.get(category);
    }
    
    /**
     * Get all allocated model IDs.
     * 
     * @return Map of item keys to model IDs
     */
    public Map<String, Integer> getAllocatedModelIds() {
        return new HashMap<>(itemModelIds);
    }
    
    /**
     * Release a model ID allocation.
     * 
     * @param itemKey The item identifier
     * @return True if released successfully
     */
    public boolean releaseModelId(String itemKey) {
        Integer modelId = itemModelIds.remove(itemKey);
        if (modelId != null) {
            modelIdItems.remove(modelId);
            logger.info("Released model ID " + modelId + " for item: " + itemKey);
            return true;
        }
        return false;
    }
    
    /**
     * Get usage statistics for model data allocation.
     * 
     * @return Map of categories to usage counts
     */
    public Map<ModelDataCategory, Integer> getUsageStatistics() {
        Map<ModelDataCategory, Integer> stats = new HashMap<>();
        
        for (ModelDataCategory category : ModelDataCategory.values()) {
            ModelDataRange range = CATEGORY_RANGES.get(category);
            int startCounter = range.getStart();
            int currentCounter = categoryCounters.get(category);
            int used = currentCounter - startCounter;
            stats.put(category, used);
        }
        
        return stats;
    }
    
    /**
     * Shutdown the model data manager and clean up resources.
     */
    public void shutdown() {
        itemModelIds.clear();
        modelIdItems.clear();
        categoryCounters.clear();
        logger.info("ModelDataManager shutdown");
    }
}

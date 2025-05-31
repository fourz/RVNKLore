# RVNKLore ModelDataManager

The `ModelDataManager` handles custom model data allocation and tracking for resource pack integration in RVNKLore. It ensures organized, conflict-free model ID assignment across item categories.

## Responsibilities
- Allocate and track custom model data IDs for items
- Enforce category-based model ID ranges (system, weapons, armor, etc.)
- Prevent model ID conflicts between item types
- Provide lookup and reverse-lookup for model IDs and item keys
- Apply model data to `ItemStack` objects
- Support resource cleanup and statistics reporting

## Model ID Ranges
- 1-100: System
- 101-200: Weapons
- 201-300: Armor
- 301-400: Tools
- 401-500: Cosmetic items
- 501-600: Decorative blocks
- 601-700: Consumables
- 701-800: Seasonal items
- 801-900: Event items
- 901-1000: Legendary items

## Key Methods
- `allocateModelId(String, ModelDataCategory)`: Allocate a new model ID
- `getModelId(String)`: Lookup model ID by item key
- `getItemKey(int)`: Reverse-lookup item key by model ID
- `applyModelData(ItemStack, String, ModelDataCategory)`: Apply model data to an item
- `getUsageStatistics()`: Get allocation stats
- `shutdown()`: Cleanup

## Example Usage
```java
int modelId = modelDataManager.allocateModelId("frost_edge", ModelDataCategory.WEAPONS);
ItemStack item = modelDataManager.applyModelData(new ItemStack(Material.DIAMOND_SWORD), "frost_edge", ModelDataCategory.WEAPONS);
```

## Design Notes
- Uses concurrent maps for thread safety
- Logs all allocations and shutdowns via `LogManager`
- Category ranges are enforced and configurable in code

# RVNKLore ItemManager

The `ItemManager` is the central orchestrator for all item-related functionality in the RVNKLore plugin. It manages sub-managers for enchantments, cosmetics, collections, and model data, providing a unified interface for item creation and management.

## Recent Edits
- Implemented asynchronous cache refresh for improved performance.
- Enhanced integration with Cosmetic and Collection managers.
- Added fallback mechanisms for item lookup from both database and memory.
- Refined logging to align with the new LogManager standards.
- **Design Change:** Item descriptions are no longer stored in the `lore_item` table. Descriptions are now managed exclusively at the `lore_entry` and `lore_submission` level for versioning and content management. All item-related description logic should reference these tables.

## Responsibilities
- Initialize and manage sub-managers:
  - `EnchantManager` (enchantments)
  - `CosmeticsManager` (cosmetic items and heads)
  - `CollectionManager` (item collections)
  - `CustomModelDataManager` (custom model data)
- Provide a single entry point for item creation via `createLoreItem()`
- Handle resource cleanup and shutdown for all sub-managers
- Maintain in-memory and database-backed caches for item properties and collections
- Support paginated, sorted item listing for commands

## Key Methods
- `getEnchantManager()`: Access the enchantment manager
- `getCosmeticItem()`: Access the cosmetics manager
- `getCollectionManager()`: Access the collection manager
- `getModelDataManager()`: Access the custom model data manager
- `createLoreItem(ItemType, String, ItemProperties)`: Unified item creation
- `createLoreItem(String)`: Lookup and create item by name
- `getAllItemNames()`: List all registered item names
- `getAllItemsWithProperties()`: List all items with metadata for sorting and display
- `shutdown()`, `cleanup()`: Resource management

## Example Usage
```java
ItemManager itemManager = plugin.getItemManager();
ItemStack enchantedSword = itemManager.createLoreItem(ItemType.ENCHANTED, "Frost Edge", properties);
List<String> allItems = itemManager.getAllItemNames();
```

## Design Notes
- Follows the manager pattern for modularity and separation of concerns
- Integrates with the plugin's logging system via `LogManager`
- Sub-managers are initialized in a fail-safe manner
- CosmeticsManager is now managed exclusively through ItemManager
- Supports async cache initialization and refresh for performance
- Provides sorted, paginated item lists for command output via DisplayFactory
- **Descriptions:** Item descriptions are not stored in `lore_item`. For all description needs, use the `lore_entry` or `lore_submission` tables, which support versioning and content history.

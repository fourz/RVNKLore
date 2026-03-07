# RVNKLore ItemManager

The `ItemManager` is the central orchestrator for all item-related functionality in the RVNKLore plugin. It manages sub-managers for enchantments, cosmetics, collections, and model data, providing a unified interface for item creation and management.

## Recent Edits
- **Refactored to use async, DTO-based repository/service architecture for all database operations.**
- All item persistence and queries now use `ItemPropertiesDTO`, `LoreEntryDTO`, and related DTOs.
- Integrated with the new `DatabaseManager` as the single entry point for all data access.
- All cache and batch logic now operate on DTOs and async flows.
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
- **All database operations are asynchronous and use DTOs for data transfer.**

## Key Methods
- `getEnchantManager()`: Access the enchantment manager
- `getCosmeticItem()`: Access the cosmetics manager
- `getCollectionManager()`: Access the collection manager
- `getModelDataManager()`: Access the custom model data manager
- `createLoreItem(ItemType, String, ItemProperties)`: Unified item creation
- `createLoreItem(String)`: Lookup and create item by name
- `getAllItemNames()`: List all registered item names
- `getAllItemsWithProperties()`: List all items with metadata for sorting and display
- `reloadItemsFromDatabase()`: Async reload of all items from the database
- `saveItemAsync(ItemPropertiesDTO)`: Async save of item properties
- `shutdown()`, `cleanup()`: Resource management

## Example Usage
```java
ItemManager itemManager = plugin.getItemManager();
CompletableFuture<ItemStack> itemFuture = itemManager.createLoreItemAsync(ItemType.ENCHANTED, "Frost Edge", properties);
itemFuture.thenAccept(enchantedSword -> {
    // Use the enchanted sword
});
CompletableFuture<List<String>> allItemsFuture = itemManager.getAllItemNamesAsync();
```

## Design Notes
- Follows the manager pattern for modularity and separation of concerns
- Integrates with the plugin's logging system via `LogManager`
- Sub-managers are initialized in a fail-safe manner
- CosmeticsManager and CollectionManager are managed exclusively through ItemManager
- **All database and cache operations are asynchronous and use DTOs for data transfer.**
- Provides sorted, paginated item lists for command output via DisplayFactory
- **Descriptions:** Item descriptions are not stored in `lore_item`. For all description needs, use the `lore_entry` or `lore_submission` tables, which support versioning and content history.
- **Migration Note:** All legacy direct SQL/config/database connection usage has been removed from ItemManager. All persistence is now handled via async repository/service methods and DTOs.

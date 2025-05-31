# RVNKLore ItemManager

The `ItemManager` is the central orchestrator for all item-related functionality in the RVNKLore plugin. It manages sub-managers for enchantments, cosmetics, collections, and model data, providing a unified interface for item creation and management.

## Responsibilities
- Initialize and manage sub-managers:
  - `EnchantManager` (enchantments)
  - `CosmeticManager` (cosmetic items and heads)
  - `CollectionManager` (item collections)
  - `ModelDataManager` (custom model data)
- Provide a single entry point for item creation via `createLoreItem()`
- Handle resource cleanup and shutdown for all sub-managers

## Key Methods
- `getEnchantManager()`: Access the enchantment manager
- `getCosmeticManager()`: Access the cosmetic manager
- `getCollectionManager()`: Access the collection manager
- `getModelDataManager()`: Access the model data manager
- `createLoreItem(ItemType, String, ItemProperties)`: Unified item creation
- `shutdown()`, `cleanup()`: Resource management

## Example Usage
```java
ItemManager itemManager = plugin.getItemManager();
ItemStack enchantedSword = itemManager.createLoreItem(ItemType.ENCHANTED, "Frost Edge", properties);
```

## Design Notes
- Follows the manager pattern for modularity and separation of concerns
- Integrates with the plugin's logging system via `LogManager`
- Sub-managers are initialized in a fail-safe manner
- CosmeticManager is now managed exclusively through ItemManager

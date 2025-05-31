# RVNKLore EnchantManager

The `EnchantManager` is responsible for generating and managing enchanted items in the RVNKLore plugin. It wraps the `EnchantedItemGenerator` and provides advanced features for enchantment profiles, tiers, and compatibility.

## Responsibilities
- Create enchanted items using flexible property objects
- Support enchantment profiles and tiers
- Provide backward compatibility with legacy APIs
- Apply tier-specific effects and display formatting
- Integrate with the plugin's logging system

## Key Methods
- `createEnchantedItem(ItemProperties)`: Create an enchanted item from properties
- `createEnchantedItem(Material, Map<Enchantment, Integer>, String)`: Legacy API
- `createEnchantedItem(Material, EnchantmentProfile, String)`: Profile-based creation
- `createEnchantedItem(EnchantmentTemplate, String)`: Template-based creation
- `shutdown()`: Cleanup

## Example Usage
```java
ItemStack item = enchantManager.createEnchantedItem(properties);
```

## Design Notes
- All item creation is logged for traceability
- Tier effects are applied to display names and metadata
- Follows the manager pattern for modularity

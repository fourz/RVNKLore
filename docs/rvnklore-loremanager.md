# RVNKLore LoreManager

The `LoreManager` is the central registry and orchestrator for all lore content in the RVNKLore plugin. It manages the lifecycle, registration, and retrieval of lore entries across all supported types.

## Responsibilities
- Initialize and manage all lore-related systems
- Provide access to the `ItemManager` and other domain managers
- Register, retrieve, and manage lore entries (items, locations, characters, etc.)
- Handle plugin lifecycle events for lore systems
- Integrate with the plugin's logging and handler systems

## Key Methods
- `initializeLore()`: Initialize all lore systems
- `getItemManager()`: Access the item manager
- `getLoreEntryByName(String)`, `getLoreEntry(int)`: Lookup lore entries
- `addLoreEntry(LoreEntry)`, `approveLoreEntry(int)`: Manage entries
- `cleanup()`: Resource management

## Example Usage
```java
LoreManager loreManager = plugin.getLoreManager();
LoreEntry entry = loreManager.getLoreEntryByName("Frost Edge");
```

## Design Notes
- Follows the manager pattern for extensibility
- All logging is handled via `LogManager`
- Integrates with HandlerFactory for lore type handlers

# IPlayerService Quick Reference

**Version:** 1.0
**Package:** `org.fourz.RVNKLore.service`
**Implementation:** `PlayerManager`

## Quick Access

```java
// Get service from RVNKCore
IPlayerService playerService = RVNKCore.getInstance()
    .getServiceRegistry()
    .getService(IPlayerService.class);
```

## Methods at a Glance

| Method | Return Type | Purpose |
|--------|-------------|---------|
| `hasPlayer(UUID)` | `CompletableFuture<Boolean>` | Check if player has lore entries |
| `getPlayerName(UUID)` | `CompletableFuture<Optional<String>>` | Get stored player name |
| `getNameChangeHistory(UUID)` | `CompletableFuture<List<NameChangeRecord>>` | Get name change history |
| `getPlayerLoreEntryIds(UUID)` | `CompletableFuture<List<String>>` | Get all player lore entry IDs |
| `getPlayerLoreEntriesByType(UUID, String)` | `CompletableFuture<List<String>>` | Filter entries by type |
| `isInFallbackMode()` | `boolean` | Check service health |

## Common Patterns

### Check Player Exists

```java
playerService.hasPlayer(uuid).thenAccept(exists -> {
    if (exists) {
        // Player has lore
    }
});
```

### Get Player Name

```java
playerService.getPlayerName(uuid).thenAccept(nameOpt -> {
    String name = nameOpt.orElse("Unknown");
});
```

### Get Name Changes

```java
playerService.getNameChangeHistory(uuid).thenAccept(history -> {
    for (NameChangeRecord record : history) {
        logger.info(record.previousName + " → " + record.newName);
    }
});
```

### Get First Join Entry

```java
playerService.getPlayerLoreEntriesByType(uuid, "first_join")
    .thenAccept(entries -> {
        if (!entries.isEmpty()) {
            String firstJoinId = entries.get(0);
            // Process entry
        }
    });
```

### Batch Check Multiple Players

```java
List<UUID> players = Arrays.asList(uuid1, uuid2, uuid3);

CompletableFuture<?>[] futures = players.stream()
    .map(uuid -> playerService.hasPlayer(uuid).thenAccept(exists -> {
        // Process each result
    }))
    .toArray(CompletableFuture[]::new);

CompletableFuture.allOf(futures).thenRun(() -> {
    logger.info("All checks complete");
});
```

## Entry Types

- `"first_join"` - Player's first join entry
- `"name_change"` - Name change entries
- `"player_character"` - Player character entries

## Error Handling

```java
playerService.getPlayerName(uuid)
    .thenAccept(nameOpt -> {
        // Success
    })
    .exceptionally(throwable -> {
        logger.warning("Error: " + throwable.getMessage());
        return null;
    });
```

## Health Check

```java
if (playerService.isInFallbackMode()) {
    logger.warning("PlayerService degraded - using fallback mode");
}
```

## NameChangeRecord

```java
public record NameChangeRecord(
    String previousName,  // Old username
    String newName,       // New username
    long timestamp        // Unix timestamp
)

// Usage
String display = record.toString(); // "oldName → newName"
```

## Performance Tips

1. **Use async patterns** - Never call `.join()` on main thread
2. **Batch operations** - Use `CompletableFuture.allOf()` for multiple queries
3. **Cache results** - Store frequently accessed data in memory
4. **Check fallback mode** - Handle degraded operation gracefully

## Integration Checklist

- [ ] Get service from ServiceRegistry
- [ ] Check service is not null
- [ ] Use async methods (thenAccept/thenApply)
- [ ] Add error handling (exceptionally)
- [ ] Test with fallback mode
- [ ] Verify no blocking on main thread

## See Also

- [Full API Documentation](IPlayerService.md)
- [RVNKCore Integration](../standard/rvnkcore-integration.md)
- [Async Patterns](../standard/coding-standards.md#async-patterns)

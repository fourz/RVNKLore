# IPlayerService Interface Documentation

**Package:** `org.fourz.RVNKLore.service`
**Implementation:** `org.fourz.RVNKLore.lore.player.PlayerManager`
**Since:** RVNKCore Integration (impl-07)

## Overview

The `IPlayerService` interface provides cross-plugin access to player-related lore operations through the RVNKCore ServiceRegistry. It enables other plugins to query player lore entries, name change history, and player existence without requiring direct database access.

## Purpose

- Expose player lore data to other RVNK plugins (BarterShops, RVNKWorlds, etc.)
- Provide async-first API for all player data operations
- Abstract database implementation details from consumers
- Enable cross-plugin player data correlation

## Service Registration

### RVNKLore Registration

The service is automatically registered during plugin initialization:

```java
// In RVNKLore.onEnable()
if (playerManager != null) {
    registerMethod.invoke(serviceRegistry, IPlayerService.class, playerManager);
    logger.info("Registered IPlayerService with RVNKCore");
}
```

### Consumer Access

Other plugins can access the service via RVNKCore's ServiceRegistry:

```java
// In another plugin (e.g., BarterShops)
IPlayerService playerService = RVNKCore.getInstance()
    .getServiceRegistry()
    .getService(IPlayerService.class);

if (playerService != null) {
    // Use the service
    playerService.hasPlayer(playerUuid).thenAccept(exists -> {
        if (exists) {
            // Player has lore entries
        }
    });
}
```

## API Methods

### hasPlayer

```java
CompletableFuture<Boolean> hasPlayer(UUID playerId)
```

**Purpose:** Check if a player has any lore entries in the system.

**Parameters:**
- `playerId` - The UUID of the player to check

**Returns:** Future that completes with `true` if player has lore entries, `false` otherwise

**Example:**
```java
playerService.hasPlayer(playerUuid).thenAccept(exists -> {
    if (exists) {
        plugin.getLogger().info("Player has lore history");
    }
});
```

---

### getPlayerName

```java
CompletableFuture<Optional<String>> getPlayerName(UUID playerId)
```

**Purpose:** Get the current player name stored in the lore system.

**Parameters:**
- `playerId` - The UUID of the player

**Returns:** Future containing `Optional<String>` with the stored player name, or empty if not found

**Example:**
```java
playerService.getPlayerName(playerUuid).thenAccept(nameOpt -> {
    nameOpt.ifPresent(name -> {
        plugin.getLogger().info("Stored name: " + name);
    });
});
```

---

### getNameChangeHistory

```java
CompletableFuture<List<NameChangeRecord>> getNameChangeHistory(UUID playerId)
```

**Purpose:** Get the complete history of name changes for a player.

**Parameters:**
- `playerId` - The UUID of the player

**Returns:** Future containing list of `NameChangeRecord` objects, ordered from oldest to newest

**Example:**
```java
playerService.getNameChangeHistory(playerUuid).thenAccept(history -> {
    if (!history.isEmpty()) {
        for (NameChangeRecord record : history) {
            plugin.getLogger().info("Name change: " + record.toString());
            // Output: "oldName → newName"
        }
    }
});
```

**NameChangeRecord Structure:**
```java
public record NameChangeRecord(
    String previousName,
    String newName,
    long timestamp
)
```

---

### getPlayerLoreEntryIds

```java
CompletableFuture<List<String>> getPlayerLoreEntryIds(UUID playerId)
```

**Purpose:** Get all lore entry IDs associated with a player.

**Parameters:**
- `playerId` - The UUID of the player

**Returns:** Future containing list of lore entry IDs (UUIDs as strings)

**Example:**
```java
playerService.getPlayerLoreEntryIds(playerUuid).thenAccept(entryIds -> {
    plugin.getLogger().info("Player has " + entryIds.size() + " lore entries");

    // Can then query ILoreService for full entry details
    ILoreService loreService = serviceRegistry.getService(ILoreService.class);
    for (String entryId : entryIds) {
        loreService.getLoreEntry(UUID.fromString(entryId)).thenAccept(entry -> {
            // Process lore entry
        });
    }
});
```

---

### getPlayerLoreEntriesByType

```java
CompletableFuture<List<String>> getPlayerLoreEntriesByType(UUID playerId, String entryType)
```

**Purpose:** Get player lore entries filtered by entry type.

**Parameters:**
- `playerId` - The UUID of the player
- `entryType` - The type filter (e.g., "first_join", "name_change", "player_character")

**Returns:** Future containing list of entry IDs matching the type

**Entry Type Values:**
- `"first_join"` - Player's first join lore entry
- `"name_change"` - Name change lore entries
- `"player_character"` - Player character lore entries

**Example:**
```java
playerService.getPlayerLoreEntriesByType(playerUuid, "first_join")
    .thenAccept(firstJoinEntries -> {
        if (!firstJoinEntries.isEmpty()) {
            String firstJoinId = firstJoinEntries.get(0);
            // Query for full entry details
        }
    });
```

---

### isInFallbackMode

```java
boolean isInFallbackMode()
```

**Purpose:** Check if the service is operating in degraded mode due to database connectivity issues.

**Returns:** `true` if in fallback mode, `false` for normal operation

**Example:**
```java
if (playerService.isInFallbackMode()) {
    plugin.getLogger().warning("PlayerService is in fallback mode - data may be incomplete");
}
```

## Implementation Details

### Async Architecture

All data operations return `CompletableFuture` for non-blocking execution:

```java
// Good: Async execution
playerService.hasPlayer(uuid).thenAccept(exists -> {
    // Runs on async thread
});

// Avoid: Blocking the main thread
boolean exists = playerService.hasPlayer(uuid).join(); // Blocks!
```

### Error Handling

```java
playerService.getPlayerName(uuid)
    .thenAccept(nameOpt -> {
        // Success case
    })
    .exceptionally(throwable -> {
        plugin.getLogger().warning("Failed to get player name: " + throwable.getMessage());
        return null;
    });
```

### Null Safety

All methods handle null inputs gracefully and return appropriate empty results:

```java
// Will complete with Optional.empty() instead of throwing
playerService.getPlayerName(null).thenAccept(opt -> {
    // opt will be empty
});
```

## Integration Examples

### BarterShops Integration

```java
// Check if player has lore before creating shop
IPlayerService playerService = serviceRegistry.getService(IPlayerService.class);

playerService.hasPlayer(ownerUuid).thenAccept(hasLore -> {
    if (hasLore) {
        // Player is established, allow shop creation
        createShop(shopData);
    } else {
        // New player, show tutorial or require confirmation
        promptNewPlayerWarning(player);
    }
});
```

### RVNKWorlds Integration

```java
// Display player name history in world info
playerService.getNameChangeHistory(creatorUuid).thenAccept(history -> {
    if (!history.isEmpty()) {
        player.sendMessage("World creator name history:");
        for (NameChangeRecord record : history) {
            player.sendMessage("  - " + record.toString());
        }
    }
});
```

### Event-Driven Updates

```java
// Listen for player events and update lore
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();

    playerService.hasPlayer(uuid).thenAccept(exists -> {
        if (!exists) {
            // Notify other plugins of new player
            Bukkit.getPluginManager().callEvent(new NewPlayerLoreCreatedEvent(uuid));
        }
    });
}
```

## Testing

### Unit Test Example

```java
@Test
public void testHasPlayer_ExistingPlayer_ReturnsTrue() {
    // Arrange
    UUID testUuid = UUID.randomUUID();
    IPlayerRepository mockRepo = mock(IPlayerRepository.class);
    when(mockRepo.playerExists(testUuid))
        .thenReturn(CompletableFuture.completedFuture(true));

    PlayerManager manager = new PlayerManager(plugin, mockRepo);

    // Act
    CompletableFuture<Boolean> result = manager.hasPlayer(testUuid);

    // Assert
    assertTrue(result.join());
}
```

## Performance Considerations

### Batch Operations

For multiple player queries, use `CompletableFuture.allOf()`:

```java
List<UUID> playerUuids = Arrays.asList(uuid1, uuid2, uuid3);

CompletableFuture<?>[] futures = playerUuids.stream()
    .map(uuid -> playerService.hasPlayer(uuid).thenAccept(exists -> {
        // Process result
    }))
    .toArray(CompletableFuture[]::new);

CompletableFuture.allOf(futures).thenRun(() -> {
    // All queries complete
});
```

### Caching Strategy

Consider caching frequently accessed data:

```java
private final Map<UUID, String> playerNameCache = new ConcurrentHashMap<>();

public CompletableFuture<Optional<String>> getCachedPlayerName(UUID uuid) {
    if (playerNameCache.containsKey(uuid)) {
        return CompletableFuture.completedFuture(Optional.of(playerNameCache.get(uuid)));
    }

    return playerService.getPlayerName(uuid).thenApply(opt -> {
        opt.ifPresent(name -> playerNameCache.put(uuid, name));
        return opt;
    });
}
```

## Migration Notes

### From Direct Database Access

**Before:**
```java
// Old: Direct database query
String name = database.query("SELECT name FROM player_lore WHERE uuid = ?", uuid);
```

**After:**
```java
// New: Service-based async access
playerService.getPlayerName(uuid).thenAccept(nameOpt -> {
    String name = nameOpt.orElse("Unknown");
});
```

### From Sync PlayerManager Methods

**Before:**
```java
// Old: Deprecated sync method
String name = playerManager.getStoredPlayerName(uuid);
```

**After:**
```java
// New: Async service method
playerService.getPlayerName(uuid).thenAccept(nameOpt -> {
    String name = nameOpt.orElse(null);
});
```

## Related Services

- **ILoreService** - Get full lore entry details by ID
- **IItemService** - Player item lore integration
- **ICollectionService** - Player collection progress
- **ISubmissionService** - Player lore submissions

## See Also

- [ILoreService.md](ILoreService.md) - Lore entry operations
- [ServiceRegistry Integration](../architecture/rvnkcore-integration.md) - Cross-plugin service access
- [Async Patterns](../standard/coding-standards.md#async-patterns) - CompletableFuture best practices

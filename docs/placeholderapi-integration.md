# PlaceholderAPI Integration

## Overview

RVNKLore integrates with PlaceholderAPI to provide lore statistics and discovery progress placeholders for use in chat, scoreboards, holograms, and other plugin integrations.

## Installation

1. Install PlaceholderAPI plugin on your server
2. RVNKLore will automatically detect and register its expansion on startup
3. No additional configuration required

## Available Placeholders

### Discovery Statistics

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%rvnklore_total_discovered%` | Total lore entries discovered by player | `42` |
| `%rvnklore_total_entries%` | Total available lore entries on server | `150` |
| `%rvnklore_discovery_percentage%` | Discovery completion percentage | `28.0%` |

### Type-Specific Discovery

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%rvnklore_items_discovered%` | Number of ITEM entries discovered | `15` |
| `%rvnklore_locations_discovered%` | Number of LANDMARK + CITY entries discovered | `23` |
| `%rvnklore_characters_discovered%` | Number of PLAYER + FACTION entries discovered | `8` |

### Collection Progress

| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%rvnklore_collection_<name>_progress%` | Progress on specific collection | `65.0%` |

Examples:
- `%rvnklore_collection_legendary_progress%` - Progress on "legendary" collection
- `%rvnklore_collection_micky_hats_progress%` - Progress on "micky_hats" collection

### Future Placeholders (Planned)

| Placeholder | Description | Status |
|------------|-------------|--------|
| `%rvnklore_rarest_item%` | Player's rarest discovered item | Requires rarity tracking schema |
| `%rvnklore_latest_discovery%` | Most recent discovery name | Requires discovery timestamp tracking |

## Performance

### Caching

The expansion implements a 5-second cache (TTL) for all placeholder values to reduce database load:
- Repeated placeholder requests within 5 seconds return cached values
- Cache automatically expires after TTL
- Ideal for scoreboards and chat plugins with frequent updates

### Async Data Retrieval

All data is retrieved asynchronously from the database using CompletableFuture:
- Non-blocking placeholder resolution
- 1-second timeout per placeholder request
- Graceful fallback to default values on timeout

## Usage Examples

### Chat Plugin (Essentials, CMI)

**Welcome message:**
```
Welcome back {PLAYER}! You've discovered %rvnklore_discovery_percentage% of all lore.
```

### Scoreboard (FeatherBoard, AnimatedScoreboard)

```yaml
scoreboard:
  lines:
    - "&6Lore Progress"
    - "&7Discovered: &f%rvnklore_total_discovered%&7/&f%rvnklore_total_entries%"
    - "&7Items: &e%rvnklore_items_discovered%"
    - "&7Locations: &a%rvnklore_locations_discovered%"
    - "&7Characters: &b%rvnklore_characters_discovered%"
```

### Hologram (HolographicDisplays, DecentHolograms)

```yaml
holograms:
  lore_stats:
    lines:
      - "&6&lLore Collection"
      - "&7Progress: &e%rvnklore_discovery_percentage%"
      - "&7Legendary: &6%rvnklore_collection_legendary_progress%"
```

### Tab List (TAB, Deluxe Tab)

```yaml
tablist:
  header:
    - "&6%player_name%"
    - "&7Lore: &f%rvnklore_total_discovered% entries"
```

## Technical Implementation

### Architecture

```
RVNKLorePlaceholderExpansion
├── Cache Layer (ConcurrentHashMap with 5s TTL)
├── Service Integration
│   ├── IPlayerService (discovery tracking)
│   ├── ILoreService (total entries)
│   └── ICollectionService (collection progress)
└── Async Processing (CompletableFuture with timeout)
```

### Registration

The expansion is registered automatically in `RVNKLore.onEnable()`:

```java
// Reflection-based registration (no hard dependency)
Plugin placeholderAPI = getServer().getPluginManager().getPlugin("PlaceholderAPI");
if (placeholderAPI != null && placeholderAPI.isEnabled()) {
    placeholderExpansion = new RVNKLorePlaceholderExpansion(this);
    placeholderExpansion.register();
}
```

### Fallback Handling

If data retrieval fails (database timeout, service unavailable):
- Numeric placeholders return `"0"`
- Percentage placeholders return `"0%"`
- String placeholders return `"None"`
- Errors logged at DEBUG level (not spamming console)

## Troubleshooting

### Placeholders Return "0" or "None"

1. Check database connectivity: `/lore list`
2. Verify player has discovered entries: `/lore <player>`
3. Check logs for errors: `[RVNKLore] [PlaceholderExpansion]`

### Placeholders Not Updating

- Placeholders are cached for 5 seconds
- Wait a few seconds and check again
- Use `/papi reload` to force reload (clears cache)

### Expansion Not Registered

1. Verify PlaceholderAPI is installed: `/plugins`
2. Check startup logs for registration message
3. Manually register: `/papi register rvnklore`

### Performance Issues

- Cache TTL is 5 seconds (adjustable in code)
- Reduce placeholder refresh rate in scoreboard/tab plugins
- Check for excessive concurrent requests

## API Access

External plugins can access the expansion:

```java
RVNKLore plugin = (RVNKLore) Bukkit.getPluginManager().getPlugin("RVNKLore");
if (plugin.isPlaceholderAPIAvailable()) {
    RVNKLorePlaceholderExpansion expansion = plugin.getPlaceholderExpansion();

    // Clear cache
    expansion.clearCache();

    // Get cache stats
    Map<String, Object> stats = expansion.getCacheStats();
    int cacheSize = (int) stats.get("size");
    long expired = (long) stats.get("expired");
}
```

## Configuration

No configuration required. The expansion uses default settings:
- **Cache TTL**: 5 seconds
- **Async Timeout**: 1 second
- **Identifier**: `rvnklore`

To modify these values, edit `RVNKLorePlaceholderExpansion.java` and recompile.

## Future Enhancements

### Phase 1 (Requires Schema Changes)
- Rarity tracking for `%rvnklore_rarest_item%`
- Discovery timestamps for `%rvnklore_latest_discovery%`
- Per-type discovery percentages

### Phase 2 (Advanced Features)
- Top discoverers leaderboard placeholders
- Configurable cache TTL via config.yml
- Placeholder aliases for shorter names
- Custom format strings via config

## See Also

- [PlaceholderAPI Wiki](https://github.com/PlaceholderAPI/PlaceholderAPI/wiki)
- [RVNKLore Service Interfaces](../service/)
- [IPlayerService API](../service/IPlayerService.java)
- [ICollectionService API](../service/ICollectionService.java)

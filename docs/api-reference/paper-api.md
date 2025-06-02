# Paper API Reference

Paper is a high-performance fork of Spigot that focuses on performance improvements and additional APIs for plugin developers.

## Paper API Version: 1.21.5-R0.1-SNAPSHOT

### Key Advantages
- **Performance**: Significant performance improvements over Spigot
- **Additional APIs**: Extended functionality not available in Bukkit/Spigot
- **Bug Fixes**: Many vanilla Minecraft and Bukkit bugs are fixed
- **Configuration**: Extensive configuration options for fine-tuning

## Paper-Specific APIs

### Component API (Adventure)
Paper uses the Adventure library for modern text handling:

```java
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

// Modern text components instead of legacy ChatColor
Component message = Component.text("Welcome to RVNKLore!")
    .color(NamedTextColor.GOLD)
    .decoration(TextDecoration.BOLD, true);

player.sendMessage(message);

// Rich text with hover and click events
Component loreLink = Component.text("Click to view lore")
    .color(NamedTextColor.BLUE)
    .decoration(TextDecoration.UNDERLINED, true)
    .clickEvent(ClickEvent.runCommand("/lore get example"))
    .hoverEvent(HoverEvent.showText(Component.text("Opens lore entry")));
```

### Async Chunk Loading
```java
import io.papermc.paper.util.Tick;

// Async chunk loading for better performance
public void loadChunkAsync(World world, int x, int z) {
    world.getChunkAtAsync(x, z).thenAccept(chunk -> {
        // Chunk loaded asynchronously
        logger.info("Chunk loaded: " + x + ", " + z);
    });
}
```

### Enhanced Entity API
```java
import io.papermc.paper.entity.LookAnchor;

// Enhanced entity targeting
public void makeEntityLookAtPlayer(Entity entity, Player player) {
    if (entity instanceof Mob mob) {
        mob.lookAt(player, LookAnchor.EYES);
    }
}

// Better entity removal
public void removeEntitySafely(Entity entity) {
    if (entity.isValid()) {
        entity.remove();
    }
}
```

### Data Components API
Paper provides enhanced data component support for items:

```java
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;

// Working with data components (1.21+)
public void setCustomModelData(ItemStack item, int modelData) {
    item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, 
                 CustomModelData.customModelData(modelData));
}

// Custom name with components
public void setItemName(ItemStack item, Component name) {
    item.setData(DataComponentTypes.CUSTOM_NAME, name);
}
```

### Persistent Data Container Enhancements
```java
import io.papermc.paper.persistence.PersistentDataContainerView;

// Enhanced PDC operations
public class LorePersistentData {
    private static final NamespacedKey LORE_ID_KEY = 
        new NamespacedKey(plugin, "lore_id");
    
    public void setLoreId(ItemStack item, String loreId) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer()
                .set(LORE_ID_KEY, PersistentDataType.STRING, loreId);
            item.setItemMeta(meta);
        }
    }
    
    public String getLoreId(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            return meta.getPersistentDataContainer()
                      .get(LORE_ID_KEY, PersistentDataType.STRING);
        }
        return null;
    }
}
```

## Performance Features

### Async Event System
```java
import io.papermc.paper.event.player.AsyncChatEvent;

// Async chat processing
@EventHandler
public void onAsyncChat(AsyncChatEvent event) {
    Player player = event.getPlayer();
    Component message = event.message();
    
    // Process chat asynchronously - no blocking main thread
    processLoreCommand(player, message);
}
```

### Scheduler Improvements
```java
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;

// Region-based scheduling (Folia compatibility)
public class PaperScheduling {
    
    public void scheduleGlobalTask(Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task);
    }
    
    public void scheduleRegionTask(Location location, Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, location, task);
    }
}
```

### Memory Optimizations
```java
// Paper provides better memory management
public class MemoryOptimization {
    
    // Use Paper's optimized collections where available
    public void optimizeDataStructures() {
        // Paper optimizes many internal collections automatically
        logger.info("Using Paper's optimized data structures");
    }
}
```

## Paper-Specific Events

### Enhanced Player Events
```java
import io.papermc.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.player.PlayerUseItemEvent;

// Jump event for potential lore triggers
@EventHandler
public void onPlayerJump(PlayerJumpEvent event) {
    Player player = event.getPlayer();
    Location jumpLocation = player.getLocation();
    
    // Could trigger special lore for jumping in specific locations
    checkForJumpLore(player, jumpLocation);
}

// Item use event for lore items
@EventHandler
public void onPlayerUseItem(PlayerUseItemEvent event) {
    ItemStack item = event.getItem();
    
    if (isLoreItem(item)) {
        // Handle lore item usage
        handleLoreItemUse(event.getPlayer(), item);
    }
}
```

### Connection Events
```java
import io.papermc.paper.event.player.PlayerConnectionCloseEvent;

@EventHandler
public void onPlayerConnectionClose(PlayerConnectionCloseEvent event) {
    UUID playerId = event.getPlayerUniqueId();
    // Clean up player-specific lore data
    cleanupPlayerData(playerId);
}
```

## Configuration Enhancements

### Paper Configuration
Paper provides extensive configuration options in `paper-global.yml` and `paper-world.yml`:

```yaml
# paper-global.yml relevant settings
chunk-loading:
  async-chunks: true
  enable-async-chunk-io: true

misc:
  use-alternative-luck-formula: true
  disable-relative-projectile-velocity: false

# paper-world.yml relevant settings
entities:
  spawning:
    spawn-limits:
      monster: 70
      creature: 10
      ambient: 15
      axolotls: 5
      underground_water_creature: 5
      water_creature: 5
      water_ambient: 20

feature-seeds:
  generate-random-seeds-for-all: false
```

### Plugin Configuration Integration
```java
public class PaperConfigIntegration {
    
    public void checkPaperFeatures() {
        // Check if async chunks are enabled
        boolean asyncChunks = Boolean.parseBoolean(
            System.getProperty("paper.async-chunks", "false"));
        
        if (asyncChunks) {
            logger.info("Paper async chunks enabled - using optimized loading");
        }
    }
}
```

## RVNKLore Integration Examples

### Lore Item Creation with Paper APIs
```java
public class PaperLoreItemManager {
    
    public ItemStack createLoreItem(String name, String description, int modelData) {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        
        // Use Paper's Component API for rich text
        Component itemName = Component.text(name)
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);
        
        Component loreDesc = Component.text(description)
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, true);
        
        // Set data using Paper's data component system
        item.setData(DataComponentTypes.CUSTOM_NAME, itemName);
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, 
                     CustomModelData.customModelData(modelData));
        
        // Add lore description
        List<Component> lore = List.of(loreDesc);
        item.setData(DataComponentTypes.LORE, lore);
        
        return item;
    }
}
```

### Async Database Operations
```java
public class AsyncLoreOperations {
    
    public void saveLoreEntryAsync(LoreEntry entry) {
        // Use Paper's async capabilities for database operations
        Bukkit.getAsyncScheduler().runNow(plugin, (task) -> {
            try {
                databaseManager.saveLoreEntry(entry);
                
                // Switch back to main thread for player notification
                Bukkit.getScheduler().runTask(plugin, () -> {
                    notifyPlayersOfNewLore(entry);
                });
            } catch (Exception e) {
                logger.error("Failed to save lore entry async", e);
            }
        });
    }
}
```

### Enhanced Event Handling
```java
public class PaperEventHandlers {
    
    @EventHandler
    public void onAsyncPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Component message = event.message();
        
        // Extract plain text for lore command detection
        String plainText = PlainTextComponentSerializer.plainText()
            .serialize(message);
        
        if (plainText.startsWith("/lore")) {
            // Handle lore commands asynchronously
            handleLoreCommandAsync(player, plainText);
        }
    }
    
    private void handleLoreCommandAsync(Player player, String command) {
        // Process on async thread, avoiding main thread blocking
        CompletableFuture.runAsync(() -> {
            // Process lore command
            processLoreCommand(player, command);
        }).exceptionally(throwable -> {
            logger.error("Error processing lore command", throwable);
            return null;
        });
    }
}
```

## Performance Best Practices

### Chunk Loading Optimization
```java
public class ChunkOptimization {
    
    public void loadLoreLocationChunks(List<Location> loreLocations) {
        // Use Paper's async chunk loading
        List<CompletableFuture<Chunk>> futures = loreLocations.stream()
            .map(loc -> loc.getWorld().getChunkAtAsync(loc))
            .collect(Collectors.toList());
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                logger.info("All lore location chunks loaded");
            });
    }
}
```

### Entity Management
```java
public class EntityOptimization {
    
    public void optimizeEntityOperations() {
        // Use Paper's enhanced entity removal
        world.getEntities().stream()
            .filter(entity -> shouldRemoveEntity(entity))
            .forEach(Entity::remove);
        
        // Paper handles cleanup more efficiently
    }
}
```

## Migration from Spigot

### Key Changes
1. **Replace ChatColor with Components**: Use Adventure API
2. **Update Event Handlers**: Take advantage of async events
3. **Enhance Item Management**: Use data components
4. **Optimize Database Operations**: Use async schedulers

### Compatibility Notes
- Paper is fully compatible with Bukkit/Spigot plugins
- Additional Paper APIs are optional enhancements
- Gradual migration is possible - can mix old and new APIs

### Version Detection
```java
public class PaperDetection {
    
    public static boolean isPaperServer() {
        try {
            Class.forName("io.papermc.paper.plugin.lifecycle.event.LifecycleEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    public void initializePaperFeatures() {
        if (isPaperServer()) {
            logger.info("Paper detected - enabling enhanced features");
            enablePaperOptimizations();
        } else {
            logger.info("Running on Spigot/Bukkit - using standard APIs");
        }
    }
}
```

## Troubleshooting

### Common Issues
1. **Component Serialization**: Ensure proper Adventure API usage
2. **Async Context**: Be careful about thread safety in async operations
3. **Data Components**: Check version compatibility for 1.21+ features

### Debug Tools
```java
public class PaperDebugging {
    
    public void debugPaperFeatures() {
        logger.info("Paper Version: " + Bukkit.getServer().getClass().getPackage().getImplementationVersion());
        logger.info("Adventure Support: " + hasAdventureSupport());
        logger.info("Async Chunks: " + hasAsyncChunkSupport());
    }
    
    private boolean hasAdventureSupport() {
        try {
            Class.forName("net.kyori.adventure.text.Component");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

This Paper API reference provides the foundation for leveraging Paper's enhanced features in RVNKLore development while maintaining compatibility with standard Bukkit/Spigot environments.

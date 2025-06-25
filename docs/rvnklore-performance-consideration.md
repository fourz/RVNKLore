# RVNKLore Performance Considerations

**Last Updated: August 19, 2025**

This document outlines the performance best practices and patterns for the RVNKLore plugin, providing guidance on optimizing code for Minecraft server environments.

## General Performance Guidelines

### Asynchronous Operations

Always use asynchronous tasks for potentially blocking operations:

```java
// Database operations
CompletableFuture<List<LoreEntry>> futureEntries = databaseManager.getAllLoreEntries();
futureEntries.thenAccept(entries -> {
    // Process entries on the async thread
    
    // Use bukkit scheduler for any operations that need to run on the main thread
    Bukkit.getScheduler().runTask(plugin, () -> {
        // Operations that must run on main thread (e.g., modifying game state)
    });
});

// File I/O operations
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    // File reading/writing code here
    
    // Use bukkit scheduler for any operations that need to run on the main thread
    Bukkit.getScheduler().runTask(plugin, () -> {
        // Operations that must run on main thread
    });
});
```

### Batch Operations

When performing multiple database operations, use batch processing to reduce overhead:

```java
// Instead of:
for (LoreEntry entry : entries) {
    databaseManager.saveLoreEntry(entry);
}

// Use:
databaseManager.saveLoreEntries(entries);
```

### Memory Usage Optimization

Be mindful of memory allocation and garbage collection:

1. **Reuse objects** when possible instead of creating new instances
2. **Use primitive types** over boxed types when appropriate
3. **Avoid string concatenation** in loops (use StringBuilder)
4. **Clean up resources** properly (close connections, streams, etc.)

```java
// Bad:
String result = "";
for (int i = 0; i < 1000; i++) {
    result += "Item " + i;
}

// Good:
StringBuilder builder = new StringBuilder();
for (int i = 0; i < 1000; i++) {
    builder.append("Item ").append(i);
}
String result = builder.toString();
```

### Code Profiling

Regularly profile your code to identify bottlenecks:

1. Use **Minecraft profiling tools** like Spark or Timings
2. Add **temporary debug logging** for critical operations
3. Implement **performance metrics tracking** for key operations

## Caching Implementation Pattern

To optimize performance for frequently accessed data, implement a two-level caching system:

### Abstract Caching Service Template

```java
public abstract class AbstractCachingService<K, V> {
    private final Map<K, V> cache = new ConcurrentHashMap<>();
    private final Map<K, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 300000; // 5 minutes in milliseconds
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    
    public AbstractCachingService(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.databaseManager = plugin.getDatabaseManager();
    }
    
    /**
     * Gets a value from cache or loads it from the database.
     *
     * @param key The cache key
     * @return A CompletableFuture that will contain the value
     */
    protected CompletableFuture<V> getOrLoad(K key) {
        // Check if the value is in cache and not expired
        if (isCached(key)) {
            V value = cache.get(key);
            logger.debug("Cache hit for key: " + key);
            return CompletableFuture.completedFuture(value);
        }
        
        // Not in cache, load from database
        logger.debug("Cache miss for key: " + key + ", loading from database");
        return loadFromDatabase(key).thenApply(value -> {
            if (value != null) {
                cache.put(key, value);
                cacheTimestamps.put(key, System.currentTimeMillis());
            }
            return value;
        });
    }
    
    /**
     * Checks if a key is in the cache and not expired.
     *
     * @param key The cache key
     * @return true if cached and valid, false otherwise
     */
    protected boolean isCached(K key) {
        if (!cache.containsKey(key)) {
            return false;
        }
        
        Long timestamp = cacheTimestamps.get(key);
        if (timestamp == null) {
            return false;
        }
        
        return (System.currentTimeMillis() - timestamp) < CACHE_DURATION;
    }
    
    /**
     * Invalidates a specific cache entry.
     *
     * @param key The key to invalidate
     */
    public void invalidateCache(K key) {
        cache.remove(key);
        cacheTimestamps.remove(key);
    }
    
    /**
     * Clears the entire cache.
     */
    public void clearCache() {
        cache.clear();
        cacheTimestamps.clear();
        logger.info("Cache cleared for " + getClass().getSimpleName());
    }
    
    /**
     * Loads a value from the database.
     * Must be implemented by concrete service classes.
     *
     * @param key The key to load
     * @return A CompletableFuture that will contain the loaded value
     */
    protected abstract CompletableFuture<V> loadFromDatabase(K key);
}
```

### Implementation Example

```java
public class LoreService extends AbstractCachingService<String, LoreEntry> {
    
    public LoreService(RVNKLore plugin) {
        super(plugin);
    }
    
    public CompletableFuture<LoreEntry> getLoreEntry(String id) {
        return getOrLoad(id);
    }
    
    @Override
    protected CompletableFuture<LoreEntry> loadFromDatabase(String id) {
        return databaseManager.getLoreEntry(id);
    }
    
    public CompletableFuture<Void> saveLoreEntry(LoreEntry entry) {
        return databaseManager.saveLoreEntry(entry).thenRun(() -> {
            // Invalidate cache after saving
            invalidateCache(entry.getId());
        });
    }
}
```

### Cache Lifecycle Management

To properly manage the cache lifecycle:

1. **Initialize cache** during service creation
2. **Clear cache** on plugin disable
3. **Invalidate entries** after updates
4. **Monitor cache performance** to adjust expiration times

```java
// In the plugin's onDisable method
@Override
public void onDisable() {
    // Clear all caches
    services.forEach(service -> {
        if (service instanceof AbstractCachingService) {
            ((AbstractCachingService<?, ?>) service).clearCache();
        }
    });
    
    // Other cleanup code...
}
```

## Event Handler Optimization

Event handlers can significantly impact server performance. Follow these guidelines:

1. **Use appropriate event priorities**
   ```java
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   ```

2. **Keep handlers focused and lightweight**
   ```java
   // Bad - doing too much in one handler
   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
       Player player = event.getPlayer();
       
       // Load player data
       PlayerData data = loadPlayerData(player); // Blocking database operation!
       
       // Give items
       giveWelcomeItems(player);
       
       // Show tutorial
       showTutorial(player);
   }
   
   // Good - lightweight and async where needed
   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
       Player player = event.getPlayer();
       
       // Async database operation
       Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
           PlayerData data = loadPlayerData(player);
           
           // Back to main thread for game operations
           Bukkit.getScheduler().runTask(plugin, () -> {
               if (data.isFirstJoin()) {
                   giveWelcomeItems(player);
                   showTutorial(player);
               }
           });
       });
   }
   ```

3. **Unregister listeners** when no longer needed
   ```java
   public void unregisterListeners() {
       HandlerList.unregisterAll(this);
   }
   ```

## Task Scheduling Best Practices

When scheduling tasks:

1. **Use the appropriate scheduler** for the task type
   ```java
   // One-time delayed task
   Bukkit.getScheduler().runTaskLater(plugin, () -> {
       // Task code
   }, delayInTicks);
   
   // Repeating task
   BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
       // Task code
   }, delayInTicks, periodInTicks);
   
   // Async task
   Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
       // Async task code
   });
   ```

2. **Cancel tasks** when they're no longer needed
   ```java
   task.cancel();
   ```

3. **Store task IDs** for proper cleanup on plugin disable
   ```java
   private final Set<Integer> activeTasks = new HashSet<>();
   
   public void scheduleTask() {
       int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
           // Task code
       }, 20L, 20L).getTaskId();
       
       activeTasks.add(taskId);
   }
   
   public void cleanup() {
       for (int taskId : activeTasks) {
           Bukkit.getScheduler().cancelTask(taskId);
       }
       activeTasks.clear();
   }
   ```

## Resource Management

Proper resource management is essential for stable performance:

1. **Use try-with-resources** for autocloseable resources
   ```java
   try (Connection conn = connectionProvider.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query);
        ResultSet rs = stmt.executeQuery()) {
       
       // Process results
       
   } catch (SQLException e) {
       logger.error("Database error", e);
   }
   ```

2. **Limit connection pooling** to prevent resource exhaustion
   ```java
   // In configuration
   private static final int MAX_POOL_SIZE = 10;
   
   // In connection provider setup
   hikariConfig.setMaximumPoolSize(MAX_POOL_SIZE);
   ```

3. **Monitor resource usage** and adjust limits as needed

## Performance Monitoring Implementation

Add performance monitoring to critical operations:

```java
public class PerformanceMonitor {
    private final Map<String, Long> operationCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> totalOperationTime = new ConcurrentHashMap<>();
    private final LogManager logger;
    
    public PerformanceMonitor(RVNKLore plugin) {
        this.logger = LogManager.getInstance(plugin);
        
        // Schedule periodic logging of performance statistics
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::logStatistics, 12000L, 12000L); // Every 10 minutes
    }
    
    public void recordOperation(String operation, long timeMs) {
        operationCounts.compute(operation, (k, v) -> (v == null) ? 1L : v + 1L);
        totalOperationTime.compute(operation, (k, v) -> (v == null) ? timeMs : v + timeMs);
    }
    
    public <T> T timeOperation(String operation, Supplier<T> supplier) {
        long startTime = System.currentTimeMillis();
        try {
            return supplier.get();
        } finally {
            long endTime = System.currentTimeMillis();
            recordOperation(operation, endTime - startTime);
        }
    }
    
    private void logStatistics() {
        logger.info("Performance Statistics:");
        
        for (Map.Entry<String, Long> entry : operationCounts.entrySet()) {
            String operation = entry.getKey();
            long count = entry.getValue();
            long totalTime = totalOperationTime.getOrDefault(operation, 0L);
            double avgTime = count > 0 ? (double) totalTime / count : 0;
            
            logger.info(String.format("  %s: %d operations, %.2f ms avg", operation, count, avgTime));
        }
    }
    
    public void reset() {
        operationCounts.clear();
        totalOperationTime.clear();
    }
}
```

Usage example:

```java
// In service class
private final PerformanceMonitor perfMonitor;

public CompletableFuture<List<LoreEntry>> getAllLoreEntries() {
    return perfMonitor.timeOperation("getAllLoreEntries", () -> {
        return databaseManager.getAllLoreEntries();
    });
}
```

## Benchmarking Critical Operations

Implement a simple benchmarking utility for performance testing:

```java
public class Benchmark {
    public static <T> T run(String operationName, Supplier<T> operation) {
        long startTime = System.currentTimeMillis();
        T result = operation.get();
        long endTime = System.currentTimeMillis();
        
        System.out.println(operationName + " took " + (endTime - startTime) + "ms");
        return result;
    }
    
    public static void run(String operationName, Runnable operation) {
        long startTime = System.currentTimeMillis();
        operation.run();
        long endTime = System.currentTimeMillis();
        
        System.out.println(operationName + " took " + (endTime - startTime) + "ms");
    }
}
```

Usage:

```java
// In debug command or test
Benchmark.run("Load all lore entries", () -> {
    List<LoreEntry> entries = databaseManager.getAllLoreEntries().join();
    System.out.println("Loaded " + entries.size() + " entries");
});
```

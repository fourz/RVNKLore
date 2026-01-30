# Bukkit API Reference

## Overview

Bukkit is the foundational plugin development framework for Minecraft servers. It provides the core APIs and interfaces that all Spigot and Paper implementations build upon. This reference covers Bukkit API features specifically relevant to RVNKLore development.

## Core Concepts

### Plugin Lifecycle

```java
public class RVNKLore extends JavaPlugin {
    @Override
    public void onEnable() {
        // Plugin initialization
        getLogger().info("RVNKLore enabling...");
        
        // Initialize managers in order
        configManager = new ConfigManager(this);
        databaseManager = new DatabaseManager(this);
        loreManager = new LoreManager(this);
    }
    
    @Override
    public void onDisable() {
        // Cleanup resources
        if (loreManager != null) {
            loreManager.cleanup();
        }
        getLogger().info("RVNKLore disabled");
    }
}
```

### Configuration Management

```java
// Built-in configuration handling
public class ConfigManager {
    private FileConfiguration config;
    
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        // Access configuration values
        String dbType = config.getString("storage.type", "sqlite");
        int port = config.getInt("storage.mysql.port", 3306);
        boolean requireApproval = config.getBoolean("lore.requireApproval", true);
    }
    
    public void saveConfig() {
        plugin.saveConfig();
    }
}
```

## Command System

### Basic Command Implementation

```java
public class LoreCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, 
                           String label, String[] args) {
        // Permission check
        if (!sender.hasPermission("rvnklore.command.use")) {
            sender.sendMessage("§c✖ You don't have permission to use this command");
            return true;
        }
        
        // Argument validation
        if (args.length == 0) {
            sender.sendMessage("§c▶ Usage: /lore <add|get|list>");
            return true;
        }
        
        // Handle subcommands
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "add":
                return handleAddCommand(sender, args);
            case "get":
                return handleGetCommand(sender, args);
            default:
                sender.sendMessage("§c✖ Unknown subcommand: " + subCommand);
                return true;
        }
    }
}
```

### Tab Completion

```java
public class LoreCommand implements CommandExecutor, TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                    String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // First argument - subcommands
            completions.addAll(Arrays.asList("add", "get", "list", "approve"));
            return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("get")) {
            // Second argument for 'get' - lore entry names
            completions.addAll(getLoreEntryNames());
            return StringUtil.copyPartialMatches(args[1], completions, new ArrayList<>());
        }
        
        return completions;
    }
}
```

## Event System

### Event Registration

```java
public class LoreEventListener implements Listener {
    private final RVNKLore plugin;
    
    public LoreEventListener(RVNKLore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block block = event.getClickedBlock();
        if (block == null) return;
        
        // Check for lore interaction
        List<LoreEntry> nearbyLore = plugin.getLoreManager()
            .findNearbyLoreEntries(block.getLocation(), 5.0);
        
        if (!nearbyLore.isEmpty()) {
            Player player = event.getPlayer();
            displayLoreToPlayer(player, nearbyLore.get(0));
        }
    }
}
```

### Custom Events

```java
public class LoreDiscoveryEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    
    private final Player player;
    private final LoreEntry loreEntry;
    private final Location discoveryLocation;
    
    public LoreDiscoveryEvent(Player player, LoreEntry loreEntry, Location location) {
        this.player = player;
        this.loreEntry = loreEntry;
        this.discoveryLocation = location;
    }
    
    // Event methods
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
    
    public static HandlerList getHandlerList() {
        return handlers;
    }
    
    @Override
    public boolean isCancelled() {
        return cancelled;
    }
    
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
    
    // Getters
    public Player getPlayer() { return player; }
    public LoreEntry getLoreEntry() { return loreEntry; }
    public Location getDiscoveryLocation() { return discoveryLocation; }
}

// Calling the event
public void triggerLoreDiscovery(Player player, LoreEntry entry, Location location) {
    LoreDiscoveryEvent event = new LoreDiscoveryEvent(player, entry, location);
    Bukkit.getPluginManager().callEvent(event);
    
    if (!event.isCancelled()) {
        // Process the discovery
        awardDiscoveryRewards(player, entry);
    }
}
```

## Scheduler System

### Task Scheduling

```java
public class DatabaseManager {
    private final RVNKLore plugin;
    private int backupTaskId = -1;
    
    public void startPeriodicBackup() {
        // Schedule repeating task (every 6 hours = 432000 ticks)
        backupTaskId = plugin.getServer().getScheduler()
            .scheduleSyncRepeatingTask(plugin, () -> {
                // Async backup to avoid blocking main thread
                plugin.getServer().getScheduler()
                    .runTaskAsynchronously(plugin, this::performBackup);
            }, 432000L, 432000L);
    }
    
    public void stopPeriodicBackup() {
        if (backupTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(backupTaskId);
            backupTaskId = -1;
        }
    }
    
    private void performBackup() {
        try {
            String backupPath = "backups/lore_" + System.currentTimeMillis() + ".db";
            boolean success = backupDatabase(backupPath);
            
            // Switch back to main thread for logging
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (success) {
                    plugin.getLogger().info("Database backup completed: " + backupPath);
                } else {
                    plugin.getLogger().warning("Database backup failed");
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("Backup error: " + e.getMessage());
        }
    }
}
```

### Async Operations

```java
public class LoreManager {
    public void loadLoreEntriesAsync(Runnable callback) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // Database operations on async thread
            List<LoreEntry> entries = databaseManager.getAllLoreEntries();
            
            // Return to main thread for cache updates
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                cachedEntries.clear();
                cachedEntries.addAll(entries);
                
                if (callback != null) {
                    callback.run();
                }
                
                logger.info("Loaded " + entries.size() + " lore entries");
            });
        });
    }
}
```

## World & Location Management

### Location Handling

```java
public class LoreLocationUtils {
    public static String serializeLocation(Location location) {
        if (location == null) return null;
        
        return location.getWorld().getName() + "," +
               location.getX() + "," +
               location.getY() + "," +
               location.getZ();
    }
    
    public static Location deserializeLocation(String locationString) {
        if (locationString == null || locationString.isEmpty()) {
            return null;
        }
        
        String[] parts = locationString.split(",");
        if (parts.length != 4) return null;
        
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public static List<LoreEntry> findNearbyEntries(Location center, double radius, 
                                                   List<LoreEntry> allEntries) {
        return allEntries.stream()
            .filter(entry -> {
                Location entryLoc = entry.getLocation();
                return entryLoc != null && 
                       entryLoc.getWorld().equals(center.getWorld()) &&
                       entryLoc.distance(center) <= radius;
            })
            .collect(Collectors.toList());
    }
}
```

### World Events

```java
@EventHandler
public void onWorldLoad(WorldLoadEvent event) {
    World world = event.getWorld();
    logger.info("World loaded: " + world.getName());
    
    // Load lore entries for this world
    loadWorldLoreEntries(world.getName());
}

@EventHandler
public void onWorldUnload(WorldUnloadEvent event) {
    World world = event.getWorld();
    logger.info("World unloading: " + world.getName());
    
    // Cache lore entries for this world
    cacheWorldLoreEntries(world.getName());
}
```

## Permissions System

### Permission Checks

```java
public class PermissionUtils {
    public static boolean hasLorePermission(CommandSender sender, String permission) {
        return sender.hasPermission("rvnklore." + permission) || 
               sender.hasPermission("rvnklore.admin");
    }
    
    public static boolean canManageLore(CommandSender sender) {
        return hasLorePermission(sender, "command.approve") ||
               hasLorePermission(sender, "command.delete");
    }
    
    public static boolean canAccessDebug(CommandSender sender) {
        return hasLorePermission(sender, "command.debug");
    }
}

// Usage in commands
if (!PermissionUtils.hasLorePermission(sender, "command.add")) {
    sender.sendMessage("§c✖ You don't have permission to add lore entries");
    return true;
}
```

### Permission Defaults (plugin.yml)

```yaml
permissions:
  rvnklore.admin:
    description: Full access to all RVNKLore features
    default: op
    children:
      rvnklore.command.add: true
      rvnklore.command.get: true
      rvnklore.command.list: true
      rvnklore.command.approve: true
      rvnklore.command.delete: true
      rvnklore.command.export: true
      rvnklore.command.reload: true
      rvnklore.command.debug: true
      rvnklore.command.getitem: true
  
  rvnklore.user:
    description: Basic lore access for players
    default: true
    children:
      rvnklore.command.add: true
      rvnklore.command.get: true
      rvnklore.command.list: true
  
  rvnklore.moderator:
    description: Moderation access for staff
    default: false
    children:
      rvnklore.user: true
      rvnklore.command.approve: true
      rvnklore.command.getitem: true
```

## Database Integration

### Basic JDBC Operations

```java
public class DatabaseConnection {
    private Connection connection;
    
    public void executeUpdate(String sql, Object... params) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            stmt.executeUpdate();
        }
    }
    
    public <T> T executeQuery(String sql, ResultSetHandler<T> handler, 
                             Object... params) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                return handler.handle(rs);
            }
        }
    }
    
    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }
}
```

## Resource Management

### File Operations

```java
public class ConfigManager {
    public void saveResource(String resourcePath, String targetPath, boolean replace) {
        File targetFile = new File(plugin.getDataFolder(), targetPath);
        
        if (!targetFile.exists() || replace) {
            targetFile.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, replace);
        }
    }
    
    public InputStream getResource(String filename) {
        return plugin.getResource(filename);
    }
    
    public void exportLoreTemplate() {
        saveResource("templates/lore_template.yml", "templates/lore_template.yml", false);
    }
}
```

### Data Folder Management

```java
public class DataManager {
    private final File dataFolder;
    private final File backupFolder;
    private final File cacheFolder;
    
    public DataManager(RVNKLore plugin) {
        this.dataFolder = plugin.getDataFolder();
        this.backupFolder = new File(dataFolder, "backups");
        this.cacheFolder = new File(dataFolder, "cache");
        
        createDirectories();
    }
    
    private void createDirectories() {
        if (!dataFolder.exists()) dataFolder.mkdirs();
        if (!backupFolder.exists()) backupFolder.mkdirs();
        if (!cacheFolder.exists()) cacheFolder.mkdirs();
    }
    
    public File getBackupFile(String filename) {
        return new File(backupFolder, filename);
    }
    
    public File getCacheFile(String filename) {
        return new File(cacheFolder, filename);
    }
}
```

## Error Handling

### Exception Management

```java
public class DatabaseManager {
    private final LogManager logger;
    
    public boolean addLoreEntry(LoreEntry entry) {
        try {
            validateEntry(entry);
            return loreRepository.addLoreEntry(entry);
        } catch (ValidationException e) {
            logger.warning("Invalid lore entry: " + e.getMessage());
            return false;
        } catch (SQLException e) {
            logger.error("Database error adding lore entry", e);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error adding lore entry", e);
            return false;
        }
    }
    
    private void validateEntry(LoreEntry entry) throws ValidationException {
        if (entry == null) {
            throw new ValidationException("Lore entry cannot be null");
        }
        if (entry.getName() == null || entry.getName().trim().isEmpty()) {
            throw new ValidationException("Lore entry name cannot be empty");
        }
        if (entry.getType() == null) {
            throw new ValidationException("Lore entry type must be specified");
        }
    }
}
```

## Integration Patterns

### Plugin Dependency Management

```java
public class IntegrationManager {
    private final RVNKLore plugin;
    private boolean votingPluginEnabled = false;
    private boolean worldGuardEnabled = false;
    
    public void checkDependencies() {
        PluginManager pm = plugin.getServer().getPluginManager();
        
        // Check for VotingPlugin
        Plugin votingPlugin = pm.getPlugin("VotingPlugin");
        if (votingPlugin != null && votingPlugin.isEnabled()) {
            votingPluginEnabled = true;
            logger.info("VotingPlugin integration enabled");
        }
        
        // Check for WorldGuard
        Plugin worldGuard = pm.getPlugin("WorldGuard");
        if (worldGuard != null && worldGuard.isEnabled()) {
            worldGuardEnabled = true;
            setupWorldGuardIntegration();
            logger.info("WorldGuard integration enabled");
        }
    }
    
    public boolean isVotingPluginAvailable() {
        return votingPluginEnabled;
    }
    
    public boolean isWorldGuardAvailable() {
        return worldGuardEnabled;
    }
}
```

## Best Practices

### 1. Thread Safety
- Always use Bukkit scheduler for thread management
- Keep database operations on async threads
- Return to main thread for Bukkit API calls

### 2. Resource Cleanup
- Cancel tasks in onDisable()
- Close database connections properly
- Unregister event listeners

### 3. Configuration Management
- Provide sensible defaults
- Validate configuration values
- Support hot-reloading where possible

### 4. Error Handling
- Log errors with context
- Gracefully handle missing dependencies
- Provide helpful error messages to users

### 5. Performance
- Cache frequently accessed data
- Use async operations for I/O
- Batch database operations when possible

## Version Compatibility

### API Version Declaration (plugin.yml)
```yaml
name: RVNKLore
version: 1.0-SNAPSHOT
api-version: 1.21
main: org.fourz.RVNKLore.RVNKLore
authors: [YourName]
description: Advanced lore management system
website: https://github.com/yourusername/RVNKLore

# Bukkit compatibility
softdepend: [VotingPlugin, WorldGuard, PlaceholderAPI]
```

### Version-Specific Code
```java
public class VersionUtils {
    private static final String BUKKIT_VERSION = Bukkit.getBukkitVersion();
    private static final boolean IS_1_21_OR_HIGHER = 
        compareVersions(BUKKIT_VERSION, "1.21") >= 0;
    
    public static boolean supports1_21Features() {
        return IS_1_21_OR_HIGHER;
    }
    
    private static int compareVersions(String version1, String version2) {
        String[] v1Parts = version1.split("\\.");
        String[] v2Parts = version2.split("\\.");
        
        // Version comparison logic
        // Returns: < 0 if v1 < v2, 0 if equal, > 0 if v1 > v2
    }
}
```

This Bukkit API reference covers the fundamental patterns and systems that form the foundation of the RVNKLore plugin, focusing on stable, cross-version compatible approaches that work reliably across different server implementations.

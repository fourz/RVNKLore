# World & Block API Reference

This document provides comprehensive reference for working with worlds, blocks, and environments in Minecraft plugin development, specifically tailored for RVNKLore project patterns.

## Table of Contents
- [World Management](#world-management)
- [Block Operations](#block-operations)
- [Chunk Management](#chunk-management)
- [Environmental Effects](#environmental-effects)
- [World Generation](#world-generation)
- [Block Data & States](#block-data--states)
- [Multi-World Support](#multi-world-support)
- [Performance Optimization](#performance-optimization)
- [RVNKLore Integration](#rvnklore-integration)

## World Management

### Basic World Operations

```java
// Getting worlds
World world = Bukkit.getWorld("world_name");
World defaultWorld = Bukkit.getWorlds().get(0);
List<World> allWorlds = Bukkit.getWorlds();

// World properties
String worldName = world.getName();
World.Environment environment = world.getEnvironment();
long seed = world.getSeed();
WorldType worldType = world.getWorldType();

// Time and weather
world.setTime(1000); // Day time
world.setStorm(true);
world.setThundering(false);
long fullTime = world.getFullTime();
```

### World Creation and Loading

```java
// Create new world
WorldCreator creator = new WorldCreator("new_world");
creator.environment(World.Environment.NETHER);
creator.type(WorldType.FLAT);
creator.seed(12345L);
World newWorld = creator.createWorld();

// Load existing world
World loadedWorld = Bukkit.getWorld("existing_world");
if (loadedWorld == null) {
    loadedWorld = new WorldCreator("existing_world").createWorld();
}
```

### World Events

```java
@EventHandler
public void onWorldLoad(WorldLoadEvent event) {
    World world = event.getWorld();
    logger.info("World loaded: " + world.getName());
    
    // Initialize world-specific lore locations
    initializeLoreLocations(world);
}

@EventHandler
public void onWorldUnload(WorldUnloadEvent event) {
    World world = event.getWorld();
    logger.info("World unloading: " + world.getName());
    
    // Clean up world-specific data
    cleanupWorldData(world);
}
```

## Block Operations

### Basic Block Manipulation

```java
// Getting blocks
Block block = world.getBlockAt(x, y, z);
Block blockAtLocation = location.getBlock();
BlockState state = block.getState();

// Setting blocks
block.setType(Material.STONE);
block.setBlockData(Bukkit.createBlockData(Material.CHEST));

// Block properties
Material material = block.getType();
BlockData blockData = block.getBlockData();
boolean isSolid = block.getType().isSolid();
boolean isOccluding = block.getType().isOccluding();
```

### Advanced Block Data

```java
// Working with specific block types
if (block.getBlockData() instanceof Chest) {
    Chest chest = (Chest) block.getBlockData();
    chest.setFacing(BlockFace.NORTH);
    block.setBlockData(chest);
}

// Directional blocks
if (block.getBlockData() instanceof Directional) {
    Directional directional = (Directional) block.getBlockData();
    directional.setFacing(BlockFace.EAST);
    block.setBlockData(directional);
}

// Waterlogged blocks
if (block.getBlockData() instanceof Waterlogged) {
    Waterlogged waterlogged = (Waterlogged) block.getBlockData();
    waterlogged.setWaterlogged(true);
    block.setBlockData(waterlogged);
}
```

### Block State Management

```java
// Working with tile entities
BlockState state = block.getState();

if (state instanceof Chest) {
    Chest chest = (Chest) state;
    Inventory inventory = chest.getInventory();
    // Modify inventory
    chest.update(); // Apply changes
}

if (state instanceof Sign) {
    Sign sign = (Sign) state;
    sign.setLine(0, "§6RVNKLore");
    sign.setLine(1, "§eLore Location");
    sign.update();
}
```

### Block Events

```java
@EventHandler
public void onBlockPlace(BlockPlaceEvent event) {
    Block block = event.getBlock();
    Player player = event.getPlayer();
    
    // Check if placing lore-related blocks
    if (isLoreBlock(block)) {
        handleLoreBlockPlacement(block, player);
    }
}

@EventHandler
public void onBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    Player player = event.getPlayer();
    
    // Protect lore locations
    if (isProtectedLoreLocation(block.getLocation())) {
        event.setCancelled(true);
        MessageUtil.sendMessage(player, "&c✖ This location is protected by lore!");
    }
}

@EventHandler
public void onBlockInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    
    Block block = event.getClickedBlock();
    Player player = event.getPlayer();
    
    // Handle lore interaction blocks
    if (hasLoreContent(block)) {
        event.setCancelled(true);
        displayLoreContent(player, block);
    }
}
```

## Chunk Management

### Chunk Operations

```java
// Getting chunks
Chunk chunk = world.getChunkAt(chunkX, chunkZ);
Chunk playerChunk = player.getLocation().getChunk();

// Chunk properties
int x = chunk.getX();
int z = chunk.getZ();
boolean isLoaded = chunk.isLoaded();
boolean isForceLoaded = chunk.isForceLoaded();

// Load/unload chunks
chunk.load();
chunk.unload();
chunk.setForceLoaded(true);
```

### Chunk Events

```java
@EventHandler
public void onChunkLoad(ChunkLoadEvent event) {
    Chunk chunk = event.getChunk();
    World world = event.getWorld();
    
    // Load lore data for chunk
    loadChunkLoreData(chunk);
}

@EventHandler
public void onChunkUnload(ChunkUnloadEvent event) {
    Chunk chunk = event.getChunk();
    
    // Save and cleanup chunk lore data
    saveChunkLoreData(chunk);
    unloadChunkLoreData(chunk);
}
```

### Chunk Generation

```java
public class LoreChunkGenerator extends ChunkGenerator {
    
    @Override
    public ChunkData generateChunkData(World world, Random random, 
                                      int chunkX, int chunkZ, 
                                      BiomeGrid biome) {
        ChunkData chunk = createChunkData(world);
        
        // Generate terrain with lore locations
        generateTerrainWithLore(chunk, chunkX, chunkZ, random);
        
        return chunk;
    }
    
    private void generateTerrainWithLore(ChunkData chunk, int chunkX, 
                                       int chunkZ, Random random) {
        // Custom terrain generation logic
        // Include special lore locations
    }
}
```

## Environmental Effects

### Particle Effects

```java
// Spawn particles
world.spawnParticle(Particle.VILLAGER_HAPPY, location, 10);
world.spawnParticle(Particle.ENCHANTMENT_TABLE, location, 5, 
                   0.5, 0.5, 0.5, 0.1);

// Custom particle effects for lore
public void createLoreParticleEffect(Location location) {
    World world = location.getWorld();
    
    // Create mystical effect
    world.spawnParticle(Particle.PORTAL, location, 20, 
                       0.5, 1.0, 0.5, 0.02);
    world.spawnParticle(Particle.ENCHANTMENT_TABLE, location, 10, 
                       0.3, 0.3, 0.3, 0.1);
}
```

### Sound Effects

```java
// Play sounds
world.playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
world.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 
               SoundCategory.MASTER, 0.5f, 1.2f);

// Lore-specific sounds
public void playLoreDiscoverySound(Location location) {
    World world = location.getWorld();
    world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 
                   SoundCategory.MASTER, 0.7f, 1.5f);
    
    // Add mystical ambiance
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        world.playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 
                       SoundCategory.AMBIENT, 0.3f, 0.8f);
    }, 10L);
}
```

### Weather and Time

```java
// Weather control
world.setStorm(true);
world.setThundering(true);
world.setWeatherDuration(6000); // 5 minutes

// Time control
world.setTime(0); // Dawn
world.setTime(6000); // Noon
world.setTime(12000); // Dusk
world.setTime(18000); // Midnight

// Smooth time transitions
public void smoothTimeTransition(World world, long targetTime, int durationTicks) {
    long currentTime = world.getTime();
    long difference = targetTime - currentTime;
    long stepSize = difference / durationTicks;
    
    new BukkitRunnable() {
        int ticksElapsed = 0;
        
        @Override
        public void run() {
            if (ticksElapsed >= durationTicks) {
                world.setTime(targetTime);
                cancel();
                return;
            }
            
            world.setTime(currentTime + (stepSize * ticksElapsed));
            ticksElapsed++;
        }
    }.runTaskTimer(plugin, 0L, 1L);
}
```

## World Generation

### Custom Biomes (1.21.4+)

```java
// Working with biomes
Biome biome = world.getBiome(location);
world.setBiome(location, Biome.ANCIENT_CITY);

// Custom biome effects for lore areas
public void createLoreBiomeArea(Location center, int radius) {
    World world = center.getWorld();
    
    for (int x = -radius; x <= radius; x++) {
        for (int z = -radius; z <= radius; z++) {
            Location loc = center.clone().add(x, 0, z);
            if (loc.distance(center) <= radius) {
                world.setBiome(loc, Biome.DEEP_DARK);
            }
        }
    }
}
```

### Structure Generation

```java
// Generate structures
world.generateTree(location, TreeType.CHORUS_PLANT);

// Custom lore structures
public void generateLoreShrine(Location location) {
    World world = location.getWorld();
    
    // Create shrine base
    for (int x = -2; x <= 2; x++) {
        for (int z = -2; z <= 2; z++) {
            Location blockLoc = location.clone().add(x, 0, z);
            blockLoc.getBlock().setType(Material.CHISELED_STONE_BRICKS);
        }
    }
    
    // Central pedestal
    location.clone().add(0, 1, 0).getBlock().setType(Material.ENCHANTING_TABLE);
    
    // Corner pillars
    for (int[] corner : new int[][]{{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
        Location pillarBase = location.clone().add(corner[0], 1, corner[1]);
        for (int y = 0; y < 3; y++) {
            pillarBase.clone().add(0, y, 0).getBlock().setType(Material.STONE_BRICKS);
        }
    }
    
    // Add particle effects
    createLoreParticleEffect(location.clone().add(0, 2, 0));
}
```

## Block Data & States

### Material Properties

```java
// Check material properties
Material material = Material.DIAMOND_SWORD;
boolean isItem = material.isItem();
boolean isBlock = material.isBlock();
boolean isSolid = material.isSolid();
boolean isTransparent = material.isTransparent();
boolean isFlammable = material.isFlammable();
boolean isEdible = material.isEdible();

// Material categories (1.21+)
boolean isWood = Tag.LOGS.isTagged(material);
boolean isOre = Tag.DIAMOND_ORES.isTagged(material);
boolean isStone = Tag.STONE_BRICKS.isTagged(material);
```

### Block Data Manipulation

```java
// Create block data
BlockData data = Bukkit.createBlockData(Material.CHEST);
BlockData dataFromString = Bukkit.createBlockData("minecraft:chest[facing=north]");

// Modify block data
if (data instanceof Directional) {
    Directional directional = (Directional) data;
    directional.setFacing(BlockFace.SOUTH);
}

// Clone and modify
BlockData newData = data.clone();
```

### Custom Block States

```java
// Working with container blocks
public void setupLoreContainer(Block block) {
    if (!(block.getState() instanceof Container)) return;
    
    Container container = (Container) block.getState();
    Inventory inventory = container.getInventory();
    
    // Set custom name
    if (container instanceof Nameable) {
        ((Nameable) container).setCustomName("§6Ancient Lore Archive");
    }
    
    // Add lore items
    populateLoreContainer(inventory);
    container.update();
}

private void populateLoreContainer(Inventory inventory) {
    // Add lore books, scrolls, artifacts
    ItemStack loreBook = ItemManager.createLoreBook("ancient_tale_001");
    ItemStack loreScroll = ItemManager.createLoreScroll("prophecy_fragment");
    
    inventory.setItem(4, loreBook);
    inventory.setItem(13, loreScroll);
}
```

## Multi-World Support

### World-Specific Configuration

```java
public class WorldConfigManager {
    private final Map<String, WorldConfig> worldConfigs = new HashMap<>();
    
    public void loadWorldConfig(World world) {
        String worldName = world.getName();
        WorldConfig config = new WorldConfig(worldName);
        
        // Load world-specific lore settings
        config.setLoreEnabled(ConfigManager.getBoolean("worlds." + worldName + ".lore-enabled", true));
        config.setMaxLoreLocations(ConfigManager.getInt("worlds." + worldName + ".max-lore-locations", 100));
        config.setAllowLoreCreation(ConfigManager.getBoolean("worlds." + worldName + ".allow-creation", true));
        
        worldConfigs.put(worldName, config);
    }
    
    public boolean isLoreEnabledInWorld(World world) {
        WorldConfig config = worldConfigs.get(world.getName());
        return config != null && config.isLoreEnabled();
    }
}
```

### Cross-World Lore Management

```java
public class CrossWorldLoreManager {
    
    public void transferLoreToWorld(String loreId, World sourceWorld, World targetWorld) {
        // Get lore from source world
        LoreLocation sourceLore = LoreManager.getLoreLocation(loreId, sourceWorld);
        if (sourceLore == null) return;
        
        // Create equivalent location in target world
        Location targetLocation = findEquivalentLocation(sourceLore.getLocation(), targetWorld);
        
        // Transfer lore data
        LoreLocation targetLore = new LoreLocation(loreId, targetLocation, sourceLore.getContent());
        LoreManager.registerLoreLocation(targetLore, targetWorld);
        
        logger.info("Transferred lore " + loreId + " from " + sourceWorld.getName() + 
                   " to " + targetWorld.getName());
    }
    
    private Location findEquivalentLocation(Location source, World targetWorld) {
        // Convert coordinates proportionally or use equivalent landmarks
        return new Location(targetWorld, source.getX(), source.getY(), source.getZ());
    }
}
```

## Performance Optimization

### Efficient Block Operations

```java
// Batch block changes
public void setBatchBlocks(List<Location> locations, Material material) {
    // Group by chunk for efficiency
    Map<Chunk, List<Location>> chunkGroups = locations.stream()
        .collect(Collectors.groupingBy(loc -> loc.getChunk()));
    
    for (Map.Entry<Chunk, List<Location>> entry : chunkGroups.entrySet()) {
        Chunk chunk = entry.getKey();
        
        // Ensure chunk is loaded
        if (!chunk.isLoaded()) {
            chunk.load();
        }
        
        // Set blocks in batch
        for (Location loc : entry.getValue()) {
            loc.getBlock().setType(material, false); // Skip physics updates
        }
        
        // Update physics once per chunk
        chunk.getWorld().refreshChunk(chunk.getX(), chunk.getZ());
    }
}
```

### Async World Operations

```java
// Async world loading
public CompletableFuture<World> loadWorldAsync(String worldName) {
    return CompletableFuture.supplyAsync(() -> {
        return new WorldCreator(worldName).createWorld();
    }).thenApply(world -> {
        // Initialize on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            initializeWorldLore(world);
        });
        return world;
    });
}
```

### Memory-Efficient Chunk Management

```java
public class ChunkLoreCache {
    private final Map<Long, Set<String>> chunkLoreMap = new ConcurrentHashMap<>();
    private final int maxCachedChunks = 100;
    
    public void addLoreToChunk(Chunk chunk, String loreId) {
        long chunkKey = getChunkKey(chunk);
        chunkLoreMap.computeIfAbsent(chunkKey, k -> new HashSet<>()).add(loreId);
        
        // Cleanup old entries
        if (chunkLoreMap.size() > maxCachedChunks) {
            cleanupOldEntries();
        }
    }
    
    private long getChunkKey(Chunk chunk) {
        return (long) chunk.getX() << 32 | chunk.getZ() & 0xFFFFFFFFL;
    }
    
    private void cleanupOldEntries() {
        // Remove entries for unloaded chunks
        chunkLoreMap.entrySet().removeIf(entry -> {
            // Check if chunk is still loaded
            return !isChunkLoaded(entry.getKey());
        });
    }
}
```

## RVNKLore Integration

### Lore Location Management

```java
public class LoreLocationManager {
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    
    public LoreLocationManager(RVNKLore plugin) {
        this.logger = LogManager.getInstance(plugin);
        this.databaseManager = DatabaseManager.getInstance();
    }
    
    public void registerLoreLocation(Location location, String loreContent) {
        Block block = location.getBlock();
        
        // Mark block as lore location
        if (block.getState() instanceof TileState) {
            TileState tileState = (TileState) block.getState();
            PersistentDataContainer pdc = tileState.getPersistentDataContainer();
            
            NamespacedKey loreKey = new NamespacedKey(plugin, "lore_content");
            pdc.set(loreKey, PersistentDataType.STRING, loreContent);
            
            NamespacedKey markerKey = new NamespacedKey(plugin, "is_lore_location");
            pdc.set(markerKey, PersistentDataType.BOOLEAN, true);
            
            tileState.update();
        }
        
        // Save to database
        saveLoreLocationToDatabase(location, loreContent);
        
        // Add visual indicators
        createLoreLocationVisuals(location);
        
        logger.info("Registered lore location at " + LocationUtil.formatLocation(location));
    }
    
    private void createLoreLocationVisuals(Location location) {
        // Spawn particles periodically
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isValidLoreLocation(location)) {
                    cancel();
                    return;
                }
                
                createLoreParticleEffect(location.clone().add(0.5, 1, 0.5));
            }
        }.runTaskTimer(plugin, 0L, 60L); // Every 3 seconds
    }
}
```

### World-Based Lore Systems

```java
public class WorldLoreSystem {
    
    public void initializeWorldLore(World world) {
        String worldName = world.getName();
        
        // Load world-specific lore configuration
        ConfigurationSection worldConfig = ConfigManager.getConfig()
            .getConfigurationSection("worlds." + worldName);
        
        if (worldConfig == null) {
            createDefaultWorldLoreConfig(worldName);
            return;
        }
        
        // Initialize lore spawn points
        List<Map<?, ?>> spawnPoints = worldConfig.getMapList("lore-spawn-points");
        for (Map<?, ?> point : spawnPoints) {
            initializeLoreSpawnPoint(world, point);
        }
        
        // Set up world-specific lore rules
        setupWorldLoreRules(world, worldConfig);
        
        logger.info("Initialized lore system for world: " + worldName);
    }
    
    private void initializeLoreSpawnPoint(World world, Map<?, ?> pointConfig) {
        double x = ((Number) pointConfig.get("x")).doubleValue();
        double y = ((Number) pointConfig.get("y")).doubleValue();
        double z = ((Number) pointConfig.get("z")).doubleValue();
        
        Location location = new Location(world, x, y, z);
        String loreType = (String) pointConfig.get("type");
        
        // Generate appropriate lore for this location
        generateLoreAtLocation(location, loreType);
    }
}
```

### Integration with Existing Systems

```java
// Integration with ItemManager
public ItemStack createWorldSpecificLoreItem(World world, String loreId) {
    String worldName = world.getName();
    ItemStack baseItem = ItemManager.createLoreItem(loreId);
    
    // Add world-specific metadata
    ItemMeta meta = baseItem.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    
    NamespacedKey worldKey = new NamespacedKey(plugin, "origin_world");
    pdc.set(worldKey, PersistentDataType.STRING, worldName);
    
    NamespacedKey coordinatesKey = new NamespacedKey(plugin, "discovery_coordinates");
    String coordinates = world.getSpawnLocation().getBlockX() + "," + 
                        world.getSpawnLocation().getBlockY() + "," + 
                        world.getSpawnLocation().getBlockZ();
    pdc.set(coordinatesKey, PersistentDataType.STRING, coordinates);
    
    baseItem.setItemMeta(meta);
    return baseItem;
}

// Integration with ConfigManager
public void reloadWorldConfigurations() {
    for (World world : Bukkit.getWorlds()) {
        reloadWorldConfiguration(world);
    }
}

private void reloadWorldConfiguration(World world) {
    String worldName = world.getName();
    
    // Reload world-specific settings
    boolean loreEnabled = ConfigManager.getBoolean("worlds." + worldName + ".enabled", true);
    int maxLocations = ConfigManager.getInt("worlds." + worldName + ".max-locations", 50);
    
    // Apply new settings
    if (!loreEnabled) {
        disableLoreInWorld(world);
    } else {
        enableLoreInWorld(world, maxLocations);
    }
}
```

## Best Practices

### 1. **Performance Considerations**
- Batch block operations when possible
- Use async operations for world loading/generation
- Cache frequently accessed chunk data
- Minimize particle effects in populated areas

### 2. **Memory Management**
- Unload unused world data
- Clean up chunk caches regularly
- Use weak references for temporary world objects
- Monitor memory usage in multi-world setups

### 3. **Error Handling**
- Always check if worlds/chunks are loaded
- Handle world loading failures gracefully
- Validate coordinates before block operations
- Provide fallback locations for failed operations

### 4. **Integration Patterns**
- Use consistent coordinate systems across worlds
- Implement world-agnostic lore management
- Maintain world-specific configurations
- Provide cross-world compatibility utilities

This World & Block API reference provides the foundation for managing environmental aspects of the RVNKLore plugin, ensuring proper integration with the existing codebase while maintaining performance and reliability standards.

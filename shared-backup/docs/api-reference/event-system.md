# Event System API Reference

Comprehensive guide to Bukkit/Spigot/Paper event handling for RVNKLore development, covering event registration, custom events, and lore-specific event patterns.

## Event Basics

### Event Registration
```java
public class LoreEventListener implements Listener {
    private final RVNKLore plugin;
    private final LogManager logger;
    
    public LoreEventListener(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreEventListener");
    }
    
    // Register the listener
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logger.info("Lore event listener registered");
    }
    
    // Unregister when cleaning up
    public void unregister() {
        HandlerList.unregisterAll(this);
        logger.info("Lore event listener unregistered");
    }
}
```

### Event Handler Patterns
```java
public class EventHandlerExamples implements Listener {
    
    // Basic event handler
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        handlePlayerJoin(player);
    }
    
    // Priority-based handling
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractHigh(PlayerInteractEvent event) {
        // Handle with high priority (after most other plugins)
    }
    
    // Ignore cancelled events
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Only process if event wasn't cancelled by other plugins
    }
    
    // Handle cancelled events specifically
    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onBlockBreakMonitor(BlockBreakEvent event) {
        // Monitor all break attempts, even cancelled ones
        if (event.isCancelled()) {
            logger.debug("Block break was cancelled");
        }
    }
}
```

## Player Events for Lore Generation

### Player Lifecycle Events
```java
public class PlayerLoreEvents implements Listener {
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Generate first-join lore
        if (!player.hasPlayedBefore()) {
            generateFirstJoinLore(player);
        }
        
        // Update player activity
        updatePlayerActivity(player);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        savePlayerSession(player);
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();
        List<ItemStack> drops = event.getDrops();
        
        // Generate death lore for notable deaths
        if (isNotableDeath(player, killer, drops)) {
            generateDeathLore(player, killer, drops);
        }
    }
    
    private boolean isNotableDeath(Player player, Player killer, List<ItemStack> drops) {
        // Check if death is notable enough for lore
        boolean hasValuableItems = drops.stream()
            .anyMatch(item -> isValuableItem(item));
        
        boolean isPlayerKill = killer != null;
        boolean isNotablePlayer = player.hasPermission("rvnklore.notable");
        
        return hasValuableItems || isPlayerKill || isNotablePlayer;
    }
}
```

### Item Interaction Events
```java
public class ItemLoreEvents implements Listener {
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null) return;
        
        // Handle lore item interactions
        if (isLoreItem(item)) {
            handleLoreItemInteraction(player, item, event.getAction());
            event.setCancelled(true); // Prevent normal item usage
        }
        
        // Check for item enchanting at anvils
        if (event.getClickedBlock() != null && 
            event.getClickedBlock().getType() == Material.ANVIL) {
            
            handleAnvilInteraction(player, item);
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        // Handle custom inventory interactions
        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof LoreInventoryHolder loreHolder) {
            handleLoreInventoryClick(player, event, loreHolder);
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        
        // Prevent dropping of special lore items
        if (isSpecialLoreItem(item)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "▶ This lore item cannot be dropped!");
        }
    }
}
```

### World Interaction Events
```java
public class WorldLoreEvents implements Listener {
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        // Generate lore for significant building
        if (isSignificantStructure(block)) {
            generateStructureLore(player, block);
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (to == null) return;
        
        // Check for biome changes
        if (from.getBlock().getBiome() != to.getBlock().getBiome()) {
            handleBiomeChange(player, from.getBlock().getBiome(), to.getBlock().getBiome());
        }
        
        // Check for entering lore locations
        checkLoreLocationEntry(player, to);
    }
    
    private void handleBiomeChange(Player player, Biome from, Biome to) {
        // Generate lore for discovering rare biomes
        if (isRareBiome(to)) {
            generateBiomeDiscoveryLore(player, to);
        }
    }
    
    private boolean isRareBiome(Biome biome) {
        return biome == Biome.MUSHROOM_FIELDS || 
               biome == Biome.ICE_SPIKES ||
               biome == Biome.ERODED_BADLANDS ||
               biome == Biome.PALE_GARDEN; // 1.21.4
    }
}
```

## Custom Events

### Defining Custom Events
```java
public class LoreEntryCreatedEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    
    private final LoreEntry loreEntry;
    private final Player creator;
    private final LoreCreationReason reason;
    
    public LoreEntryCreatedEvent(LoreEntry loreEntry, Player creator, LoreCreationReason reason) {
        this.loreEntry = loreEntry;
        this.creator = creator;
        this.reason = reason;
    }
    
    public LoreEntry getLoreEntry() {
        return loreEntry;
    }
    
    public Player getCreator() {
        return creator;
    }
    
    public LoreCreationReason getReason() {
        return reason;
    }
    
    @Override
    public boolean isCancelled() {
        return cancelled;
    }
    
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
    
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
```

### Custom Event Usage
```java
public class LoreEventManager {
    private final RVNKLore plugin;
    
    public boolean createLoreEntry(LoreEntry entry, Player creator, LoreCreationReason reason) {
        // Fire custom event
        LoreEntryCreatedEvent event = new LoreEntryCreatedEvent(entry, creator, reason);
        plugin.getServer().getPluginManager().callEvent(event);
        
        // Check if event was cancelled
        if (event.isCancelled()) {
            logger.info("Lore entry creation cancelled by event handler");
            return false;
        }
        
        // Proceed with creation
        return saveLoreEntry(entry);
    }
    
    // Listening to our custom event
    @EventHandler
    public void onLoreEntryCreated(LoreEntryCreatedEvent event) {
        LoreEntry entry = event.getLoreEntry();
        Player creator = event.getCreator();
        
        // Send notification to online staff
        notifyStaffOfNewLore(entry, creator);
        
        // Update statistics
        updateLoreStatistics(entry.getType());
    }
}
```

### Event Chain Management
```java
public class EventChainManager {
    private final Map<UUID, List<Event>> playerEventChains = new HashMap<>();
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void trackPlayerEvents(PlayerEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        
        playerEventChains.computeIfAbsent(playerId, k -> new ArrayList<>())
                         .add(event);
        
        // Check for event patterns
        checkEventPatterns(playerId);
    }
    
    private void checkEventPatterns(UUID playerId) {
        List<Event> events = playerEventChains.get(playerId);
        if (events == null || events.size() < 3) return;
        
        // Look for patterns that might generate lore
        if (hasExplorationPattern(events)) {
            generateExplorationLore(playerId, events);
        }
        
        if (hasBuildingPattern(events)) {
            generateBuildingLore(playerId, events);
        }
    }
}
```

## Entity Events

### Mob and Entity Events
```java
public class EntityLoreEvents implements Listener {
    
    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        
        // Generate lore for rare mob spawns
        if (isRareEntity(entity)) {
            generateRareEntityLore(entity);
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        
        // Generate lore for boss kills or named entities
        if (isBossEntity(entity) || entity.getCustomName() != null) {
            generateEntityDeathLore(entity, killer);
        }
    }
    
    @EventHandler
    public void onEntityTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        
        Entity entity = event.getEntity();
        
        // Generate lore for taming rare or special entities
        if (isSpecialTameableEntity(entity)) {
            generateTamingLore(player, entity);
        }
    }
    
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        
        // Track special spawn events
        if (reason == CreatureSpawnEvent.SpawnReason.LIGHTNING ||
            reason == CreatureSpawnEvent.SpawnReason.INFECTION) {
            
            generateSpecialSpawnLore(entity, reason);
        }
    }
    
    private boolean isRareEntity(Entity entity) {
        return entity.getType() == EntityType.BREEZE ||     // 1.21
               entity.getType() == EntityType.CREAKING ||   // 1.21.4
               entity.getType() == EntityType.WITHER ||
               entity.getType() == EntityType.ENDER_DRAGON;
    }
}
```

## Async Event Handling

### Paper Async Events
```java
public class AsyncEventHandlers implements Listener {
    
    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // Process lore commands in async chat
        if (message.startsWith("!lore")) {
            // Handle async lore commands
            handleAsyncLoreCommand(player, message);
        }
    }
    
    private void handleAsyncLoreCommand(Player player, String message) {
        // Process asynchronously to avoid blocking
        CompletableFuture.runAsync(() -> {
            // Process the lore command
            String response = processLoreCommand(message);
            
            // Switch back to main thread for player interaction
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(response);
            });
        });
    }
}
```

### Thread-Safe Event Processing
```java
public class ThreadSafeEventProcessor {
    private final Queue<LoreEvent> eventQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processing = new AtomicBoolean(false);
    
    public void queueLoreEvent(LoreEvent event) {
        eventQueue.offer(event);
        
        // Start processing if not already running
        if (processing.compareAndSet(false, true)) {
            processEventsAsync();
        }
    }
    
    private void processEventsAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                LoreEvent event;
                while ((event = eventQueue.poll()) != null) {
                    processLoreEvent(event);
                }
            } finally {
                processing.set(false);
                
                // Check if more events were added while processing
                if (!eventQueue.isEmpty() && processing.compareAndSet(false, true)) {
                    processEventsAsync();
                }
            }
        });
    }
}
```

## Event Performance Optimization

### Event Filtering
```java
public class OptimizedEventHandlers implements Listener {
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();
    private final Cache<String, Boolean> locationCache = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build();
    
    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Only track certain players
        if (!trackedPlayers.contains(player.getUniqueId())) {
            return;
        }
        
        Location to = event.getTo();
        if (to == null) return;
        
        // Use caching to avoid repeated calculations
        String locationKey = getLocationKey(to);
        Boolean hasLore = locationCache.getIfPresent(locationKey);
        
        if (hasLore == null) {
            hasLore = checkForLoreLocation(to);
            locationCache.put(locationKey, hasLore);
        }
        
        if (hasLore) {
            handleLoreLocationEntry(player, to);
        }
    }
    
    private String getLocationKey(Location location) {
        return location.getWorld().getName() + ":" + 
               (location.getBlockX() / 16) + ":" + 
               (location.getBlockZ() / 16);
    }
}
```

### Event Debouncing
```java
public class EventDebouncer {
    private final Map<String, Long> lastEventTimes = new ConcurrentHashMap<>();
    private final long debounceTime;
    
    public EventDebouncer(long debounceTimeMs) {
        this.debounceTime = debounceTimeMs;
    }
    
    public boolean shouldProcess(String eventKey) {
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastEventTimes.get(eventKey);
        
        if (lastTime == null || (currentTime - lastTime) > debounceTime) {
            lastEventTimes.put(eventKey, currentTime);
            return true;
        }
        
        return false;
    }
    
    // Usage in event handler
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        String eventKey = player.getUniqueId() + ":" + event.getAction();
        
        // Debounce rapid interactions
        if (!debouncer.shouldProcess(eventKey)) {
            return;
        }
        
        // Process the event
        handlePlayerInteraction(event);
    }
}
```

## Event Integration Patterns

### Plugin Integration Events
```java
public class PluginIntegrationEvents implements Listener {
    
    // VotingPlugin integration
    @EventHandler
    public void onVotifierEvent(VotifierEvent event) {
        Vote vote = event.getVote();
        Player player = Bukkit.getPlayer(vote.getUsername());
        
        if (player != null) {
            handleVoteReward(player, vote);
        }
    }
    
    // WorldGuard region events (if available)
    public void onRegionEnter(Player player, ProtectedRegion region) {
        // Check if region has associated lore
        String regionId = region.getId();
        LoreEntry regionLore = plugin.getLoreManager()
            .getLoreEntryByName(regionId);
        
        if (regionLore != null) {
            displayRegionLore(player, regionLore);
        }
    }
    
    // Economy plugin integration
    @EventHandler
    public void onPlayerTransaction(EconomyTransactionEvent event) {
        if (event.getAmount() > getSignificantAmount()) {
            generateEconomyLore(event);
        }
    }
}
```

### Event Data Collection
```java
public class EventDataCollector implements Listener {
    private final Map<String, AtomicInteger> eventCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> firstEventTimes = new ConcurrentHashMap<>();
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void collectEventData(Event event) {
        String eventType = event.getClass().getSimpleName();
        
        // Count events
        eventCounts.computeIfAbsent(eventType, k -> new AtomicInteger(0))
                  .incrementAndGet();
        
        // Track first occurrence
        firstEventTimes.putIfAbsent(eventType, System.currentTimeMillis());
        
        // Log interesting patterns
        if (eventCounts.get(eventType).get() % 1000 == 0) {
            logger.info("Event " + eventType + " has occurred 1000 times");
        }
    }
    
    public Map<String, Integer> getEventStatistics() {
        return eventCounts.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().get()
            ));
    }
}
```

This comprehensive Event System API reference provides the foundation for handling all types of events in RVNKLore, from basic player interactions to complex custom event chains and performance optimization strategies.

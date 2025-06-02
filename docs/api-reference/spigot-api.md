# Spigot API Reference

## Overview

Spigot extends the Bukkit API with performance improvements, additional features, and optimization-focused enhancements. This reference covers Spigot-specific features and improvements relevant to RVNKLore development.

## Spigot Configuration Integration

### Spigot.yml Settings

```java
public class SpigotConfigIntegration {
    private final RVNKLore plugin;
    
    public void checkSpigotSettings() {
        // Access Spigot configuration
        Configuration spigotConfig = Bukkit.spigot().getConfig();
        
        // Check settings that might affect our plugin
        boolean bungeeCord = spigotConfig.getBoolean("settings.bungeecord", false);
        int playerSample = spigotConfig.getInt("settings.sample-count", 12);
        
        if (bungeeCord) {
            logger.info("BungeeCord mode detected - enabling cross-server lore sync");
            enableCrossServerFeatures();
        }
        
        // Adjust performance settings based on server configuration
        adjustPerformanceSettings(spigotConfig);
    }
    
    private void adjustPerformanceSettings(Configuration config) {
        // Optimize based on Spigot settings
        int entityTrackingRange = config.getInt("world-settings.default.entity-tracking-range.players", 48);
        int viewDistance = config.getInt("world-settings.default.view-distance", 10);
        
        // Adjust lore discovery radius based on view distance
        double maxLoreRadius = Math.min(viewDistance * 16, 100);
        plugin.getConfigManager().setMaxLoreDiscoveryRadius(maxLoreRadius);
    }
}
```

## Spigot Event Enhancements

### Async Event Processing

```java
public class SpigotAsyncEvents implements Listener {
    private final RVNKLore plugin;
    private final ExecutorService asyncExecutor;
    
    public SpigotAsyncEvents(RVNKLore plugin) {
        this.plugin = plugin;
        this.asyncExecutor = Executors.newFixedThreadPool(2, 
            r -> new Thread(r, "RVNKLore-Async"));
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Process join events asynchronously to avoid blocking
        asyncExecutor.submit(() -> {
            try {
                processPlayerJoinAsync(player);
            } catch (Exception e) {
                logger.error("Error processing async player join", e);
            }
        });
    }
    
    private void processPlayerJoinAsync(Player player) {
        // Load player-specific lore data
        List<LoreEntry> playerDiscoveries = loadPlayerDiscoveries(player.getUniqueId());
        
        // Return to main thread for Bukkit API calls
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Cache player discoveries
            cachePlayerData(player, playerDiscoveries);
            
            // Send welcome message with lore count
            int discoveryCount = playerDiscoveries.size();
            player.sendMessage("§a✓ Welcome back! You've discovered " + 
                             discoveryCount + " lore entries.");
        });
    }
    
    public void shutdown() {
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

### BungeeCord Integration

```java
public class BungeeCordMessaging implements PluginMessageListener {
    private final RVNKLore plugin;
    private final String CHANNEL = "rvnklore:main";
    
    public BungeeCordMessaging(RVNKLore plugin) {
        this.plugin = plugin;
        
        // Register plugin messaging channel
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }
    
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();
            
            switch (subchannel) {
                case "LoreSync":
                    handleLoreSync(in);
                    break;
                case "PlayerDiscovery":
                    handlePlayerDiscovery(in, player);
                    break;
            }
        } catch (Exception e) {
            logger.error("Error processing plugin message", e);
        }
    }
    
    public void sendLoreDiscovery(Player player, LoreEntry entry) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("PlayerDiscovery");
        out.writeUTF(player.getUniqueId().toString());
        out.writeUTF(entry.getId().toString());
        out.writeUTF(entry.getName());
        
        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }
    
    private void handleLoreSync(ByteArrayDataInput in) {
        String serverId = in.readUTF();
        String loreData = in.readUTF();
        
        // Process cross-server lore synchronization
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            syncLoreFromServer(serverId, loreData);
        });
    }
}
```

## Performance Optimizations

### Entity and Chunk Management

```java
public class SpigotPerformanceOptimizer {
    private final RVNKLore plugin;
    private final Map<Chunk, Set<LoreEntry>> chunkLoreCache = new ConcurrentHashMap<>();
    
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        
        // Load lore entries for this chunk asynchronously
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            loadChunkLoreEntries(chunk);
        });
    }
    
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        
        // Remove chunk from cache to free memory
        chunkLoreCache.remove(chunk);
    }
    
    private void loadChunkLoreEntries(Chunk chunk) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        
        // Calculate chunk bounds
        double minX = chunkX * 16;
        double maxX = minX + 16;
        double minZ = chunkZ * 16;
        double maxZ = minZ + 16;
        
        // Find lore entries within chunk bounds
        List<LoreEntry> allEntries = plugin.getLoreManager().getAllLoreEntries();
        Set<LoreEntry> chunkEntries = allEntries.stream()
            .filter(entry -> {
                Location loc = entry.getLocation();
                return loc != null && 
                       loc.getWorld().equals(world) &&
                       loc.getX() >= minX && loc.getX() < maxX &&
                       loc.getZ() >= minZ && loc.getZ() < maxZ;
            })
            .collect(Collectors.toSet());
        
        // Cache the results
        if (!chunkEntries.isEmpty()) {
            chunkLoreCache.put(chunk, chunkEntries);
        }
    }
    
    public Set<LoreEntry> getChunkLoreEntries(Chunk chunk) {
        return chunkLoreCache.getOrDefault(chunk, Collections.emptySet());
    }
}
```

### Packet-Level Optimizations

```java
public class PacketOptimizer {
    private final RVNKLore plugin;
    
    public void sendOptimizedLoreDisplay(Player player, LoreEntry entry) {
        // Use Spigot's title API for better performance
        player.sendTitle(
            "§6" + entry.getName(),
            "§7" + entry.getDescription(),
            10, 70, 20  // fadeIn, stay, fadeOut
        );
        
        // Send action bar for additional info
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
            new ComponentBuilder("§e⚠ Lore discovered: " + entry.getType().getDisplayName())
                .create());
    }
    
    public void sendBatchedMessages(Player player, List<String> messages) {
        // Batch messages to reduce packet overhead
        ComponentBuilder builder = new ComponentBuilder("");
        
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) {
                builder.append("\n");
            }
            builder.append(messages.get(i));
        }
        
        player.spigot().sendMessage(builder.create());
    }
}
```

## Spigot Configuration API

### Dynamic Configuration Updates

```java
public class SpigotConfigManager {
    private final RVNKLore plugin;
    private final Configuration spigotConfig;
    
    public SpigotConfigManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.spigotConfig = Bukkit.spigot().getConfig();
        
        // Monitor Spigot configuration changes
        startConfigMonitoring();
    }
    
    private void startConfigMonitoring() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            checkConfigurationChanges();
        }, 1200L, 1200L); // Check every minute
    }
    
    private void checkConfigurationChanges() {
        // Check if configuration has been reloaded
        Configuration currentConfig = Bukkit.spigot().getConfig();
        
        // Compare relevant settings
        boolean currentBungee = currentConfig.getBoolean("settings.bungeecord", false);
        boolean cachedBungee = spigotConfig.getBoolean("settings.bungeecord", false);
        
        if (currentBungee != cachedBungee) {
            logger.info("BungeeCord setting changed, updating plugin configuration");
            
            // Update on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                updateBungeeCordSettings(currentBungee);
            });
        }
    }
    
    private void updateBungeeCordSettings(boolean enabled) {
        if (enabled) {
            plugin.getBungeeCordMessaging().enable();
        } else {
            plugin.getBungeeCordMessaging().disable();
        }
    }
}
```

## Advanced Chat Components

### Rich Text Messaging

```java
public class SpigotChatManager {
    
    public void sendLoreEntryDetails(Player player, LoreEntry entry) {
        // Create interactive lore display
        ComponentBuilder message = new ComponentBuilder("§6=== ").append(entry.getName()).append(" ===\n")
            .color(ChatColor.GOLD);
        
        // Description with hover details
        message.append("§7").append(entry.getDescription()).append("\n")
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder("§fType: §e" + entry.getType().getDisplayName() + "\n")
                    .append("§fCreated: §e" + formatDate(entry.getCreatedAt()) + "\n")
                    .append("§fLocation: §e" + formatLocation(entry.getLocation()))
                    .create()));
        
        // Add clickable actions
        if (entry.getLocation() != null) {
            message.append("\n§a[Teleport]")
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                    "/lore teleport " + entry.getId().toString()))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    new ComponentBuilder("§fClick to teleport to this lore location").create()));
        }
        
        message.append(" §b[Share]")
            .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, 
                "/lore share " + entry.getName()))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder("§fClick to share this lore with others").create()));
        
        player.spigot().sendMessage(message.create());
    }
    
    public void sendLoreList(Player player, List<LoreEntry> entries) {
        ComponentBuilder header = new ComponentBuilder("§6=== Lore Entries ===")
            .color(ChatColor.GOLD);
        
        player.spigot().sendMessage(header.create());
        
        for (LoreEntry entry : entries) {
            ComponentBuilder entryLine = new ComponentBuilder("§e• " + entry.getName())
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                    "/lore get " + entry.getName()))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    new ComponentBuilder("§fClick to view details\n§7" + entry.getDescription())
                        .create()));
            
            player.spigot().sendMessage(entryLine.create());
        }
    }
    
    private String formatDate(Date date) {
        return new SimpleDateFormat("MMM dd, yyyy").format(date);
    }
    
    private String formatLocation(Location location) {
        if (location == null) return "Unknown";
        return String.format("%s (%d, %d, %d)", 
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ());
    }
}
```

## Database Connection Pooling

### HikariCP Integration (Spigot Optimization)

```java
public class SpigotDatabaseManager extends DatabaseManager {
    private HikariDataSource dataSource;
    
    @Override
    protected void initializeDatabase() {
        if ("mysql".equalsIgnoreCase(getStorageType())) {
            setupConnectionPool();
        } else {
            super.initializeDatabase();
        }
    }
    
    private void setupConnectionPool() {
        HikariConfig config = new HikariConfig();
        
        // MySQL configuration
        Map<String, Object> mysqlSettings = plugin.getConfigManager().getMySQLSettings();
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s",
            mysqlSettings.get("host"),
            mysqlSettings.get("port"),
            mysqlSettings.get("database"));
        
        config.setJdbcUrl(jdbcUrl);
        config.setUsername((String) mysqlSettings.get("username"));
        config.setPassword((String) mysqlSettings.get("password"));
        
        // Performance settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        
        // Additional MySQL optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        dataSource = new HikariDataSource(config);
        logger.info("Connection pool initialized with " + config.getMaximumPoolSize() + " max connections");
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        return super.getConnection();
    }
    
    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
        super.close();
    }
}
```

## Custom Metrics with bStats

### Spigot Metrics Integration

```java
public class SpigotMetrics {
    private final RVNKLore plugin;
    private Metrics metrics;
    
    public void initializeMetrics() {
        metrics = new Metrics(plugin, 12345); // Replace with actual plugin ID
        
        // Custom charts for RVNKLore
        addLoreTypeChart();
        addDatabaseTypeChart();
        addPerformanceMetrics();
    }
    
    private void addLoreTypeChart() {
        metrics.addCustomChart(new Metrics.AdvancedPie("lore_types_distribution", () -> {
            Map<String, Integer> typeMap = new HashMap<>();
            
            for (LoreType type : LoreType.values()) {
                int count = plugin.getLoreManager().getLoreEntriesByType(type).size();
                if (count > 0) {
                    typeMap.put(type.getDisplayName(), count);
                }
            }
            
            return typeMap;
        }));
    }
    
    private void addDatabaseTypeChart() {
        metrics.addCustomChart(new Metrics.SimplePie("database_type", () -> {
            return plugin.getConfigManager().getStorageType();
        }));
    }
    
    private void addPerformanceMetrics() {
        metrics.addCustomChart(new Metrics.SingleLineChart("lore_entries_total", () -> {
            return plugin.getLoreManager().getAllLoreEntries().size();
        }));
        
        metrics.addCustomChart(new Metrics.SingleLineChart("active_discoveries_per_day", () -> {
            return getDiscoveriesInLastDay();
        }));
    }
    
    private int getDiscoveriesInLastDay() {
        // Calculate discoveries in the last 24 hours
        long dayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        return (int) plugin.getDatabaseManager().getDiscoveriesAfter(dayAgo);
    }
}
```

## Resource Pack Integration

### Spigot Resource Pack Features

```java
public class ResourcePackManager {
    private final RVNKLore plugin;
    private String resourcePackUrl;
    private String resourcePackHash;
    
    public void enableResourcePack() {
        resourcePackUrl = plugin.getConfig().getString("resourcepack.url");
        resourcePackHash = plugin.getConfig().getString("resourcepack.hash");
        
        if (resourcePackUrl != null && !resourcePackUrl.isEmpty()) {
            // Send to all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                sendResourcePack(player);
            }
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Delay slightly to ensure player is fully loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            sendResourcePack(player);
        }, 20L);
    }
    
    private void sendResourcePack(Player player) {
        if (resourcePackUrl == null) return;
        
        try {
            if (resourcePackHash != null && !resourcePackHash.isEmpty()) {
                player.setResourcePack(resourcePackUrl, resourcePackHash);
            } else {
                player.setResourcePack(resourcePackUrl);
            }
            
            player.sendMessage("§6⚙ Downloading RVNKLore resource pack...");
        } catch (Exception e) {
            logger.warning("Failed to send resource pack to " + player.getName() + ": " + e.getMessage());
        }
    }
    
    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        
        switch (status) {
            case SUCCESSFULLY_LOADED:
                player.sendMessage("§a✓ Resource pack loaded successfully!");
                enableCustomModelData(player);
                break;
            case DECLINED:
                player.sendMessage("§e⚠ Resource pack declined. Some features may not display correctly.");
                break;
            case FAILED_DOWNLOAD:
                player.sendMessage("§c✖ Failed to download resource pack. Check your connection.");
                break;
        }
    }
    
    private void enableCustomModelData(Player player) {
        // Enable enhanced visuals for items with custom model data
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager != null) {
            itemManager.refreshPlayerItems(player);
        }
    }
}
```

## Server List Ping Integration

### Custom Server Info

```java
public class ServerPingListener implements Listener {
    private final RVNKLore plugin;
    
    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        int totalLoreEntries = plugin.getLoreManager().getAllLoreEntries().size();
        int approvedEntries = plugin.getLoreManager().getApprovedLoreEntries().size();
        
        // Modify MOTD to include lore statistics
        String motd = event.getMotd();
        String loreInfo = String.format("§7[§e%d§7 lore entries, §a%d§7 approved]", 
            totalLoreEntries, approvedEntries);
        
        // Add lore info to second line if space allows
        if (motd.length() + loreInfo.length() < 59) { // Max MOTD length per line
            event.setMotd(motd + "\n" + loreInfo);
        }
    }
}
```

## Version-Specific Optimizations

### Spigot Version Detection

```java
public class SpigotVersionManager {
    private final String spigotVersion;
    private final boolean hasAsyncChatSupport;
    private final boolean hasModernResourcePacks;
    
    public SpigotVersionManager() {
        this.spigotVersion = Bukkit.getServer().getVersion();
        
        // Feature detection based on version
        this.hasAsyncChatSupport = checkAsyncChatSupport();
        this.hasModernResourcePacks = checkModernResourcePackSupport();
    }
    
    private boolean checkAsyncChatSupport() {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    private boolean checkModernResourcePackSupport() {
        try {
            Player.class.getMethod("setResourcePack", String.class, String.class, boolean.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    public void applyVersionSpecificOptimizations() {
        if (hasAsyncChatSupport) {
            logger.info("Enabling async chat support");
            enableAsyncChatProcessing();
        }
        
        if (hasModernResourcePacks) {
            logger.info("Enabling modern resource pack features");
            enableModernResourcePackFeatures();
        }
    }
}
```

This Spigot API reference provides comprehensive coverage of Spigot-specific features and optimizations that enhance the RVNKLore plugin's performance and functionality while maintaining compatibility with the broader Bukkit ecosystem.

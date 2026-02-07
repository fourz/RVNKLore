package org.fourz.RVNKLore;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKLore.handler.HandlerFactory;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.config.ConfigManager;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.command.CommandManager;
import org.fourz.RVNKLore.service.ILoreService;
import org.fourz.RVNKLore.service.IItemService;
import org.fourz.RVNKLore.service.ICollectionService;
import org.fourz.RVNKLore.service.ISubmissionService;
import org.fourz.RVNKLore.service.IPlayerLoreService;
import org.fourz.RVNKLore.util.PlayerLookup;
import org.fourz.RVNKLore.util.UtilityManager;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.submission.SubmissionManager;
import org.fourz.RVNKLore.lore.player.PlayerManager;
import org.fourz.RVNKLore.api.LoreApiInitializer;
import org.fourz.RVNKLore.discovery.DiscoveryManager;
import org.fourz.RVNKLore.achievement.AchievementManager;
import org.fourz.RVNKLore.gui.GuiListener;
import org.fourz.RVNKLore.integration.placeholder.RVNKLorePlaceholderExpansion;

public class RVNKLore extends JavaPlugin {
    private LoreManager loreManager;
    private LogManager logger;
    private ConfigManager configManager;
    private CommandManager commandManager;
    private DatabaseManager databaseManager;
    private HandlerFactory handlerFactory;
    private UtilityManager utilityManager;
    private ItemManager itemManager;
    private PlayerManager playerManager;
    private PlayerLookup playerLookup;
    private SubmissionManager submissionManager;
    private LoreApiInitializer apiInitializer;
    private DiscoveryManager discoveryManager;
    private AchievementManager achievementManager;
    private int healthCheckTaskId = -1;
    private Thread shutdownHook;
    private boolean shuttingDown = false;
    private final Object shutdownLock = new Object();

    // RVNKCore integration
    private boolean rvnkCoreAvailable = false;
    private Object rvnkCoreInstance = null;

    // PlaceholderAPI integration
    private RVNKLorePlaceholderExpansion placeholderExpansion = null;

    @Override
    public void onEnable() {
        // Initialize logger first
        logger = LogManager.getInstance(this, "RVNKLore");

        // Initialize ConfigManager first to get the log level
        configManager = new ConfigManager(this);

        // Initialize debug in ConfigManager
        configManager.initDebugLogging();

        registerShutdownHook();

        logger.info("Initializing RVNKLore...");

        try {
            // First try to initialize the database
            databaseManager = new DatabaseManager(this);

            // Check database connection - allow fallback mode to continue
            if (!databaseManager.isConnected()) {
                // Check if fallback is disabled - only then is this fatal
                if (!databaseManager.isFallbackEnabled()) {
                    throw new Exception("Database connection failed and fallback is disabled. Plugin cannot function without storage.");
                }
                throw new Exception("Database connection failed and fallback also failed. Plugin cannot function without storage.");
            }

            // Log if running in fallback mode
            if (databaseManager.isInFallbackMode()) {
                logger.warning("=== PLUGIN RUNNING IN FALLBACK MODE ===");
                logger.warning("MySQL unavailable - using SQLite fallback storage");
                logger.warning("Some features may have limited functionality");
            }

            // Create handler factory but don't initialize it yet
            handlerFactory = new HandlerFactory(this);

            // Initialize utility manager for diagnostics
            utilityManager = UtilityManager.getInstance(this);
              // First initialize the handler factory completely before LoreManager needs it
            logger.info("Initializing core systems...");
            handlerFactory.initialize();
              // Now initialize LoreManager after HandlerFactory is fully initialized
            loreManager = LoreManager.getInstance(this);
            loreManager.initializeLore();

            // Initialize PlayerLookup for RVNKCore name resolution
            this.playerLookup = new PlayerLookup(this);

            // Initialize PlayerManager for player-related lore operations
            this.playerManager = new PlayerManager(this);
            this.playerManager.setPlayerLookup(playerLookup);
            this.playerManager.initialize();

            // Initialize ItemManager through LoreManager
            this.itemManager = loreManager.getItemManager();

            // Initialize SubmissionManager for lore submission workflow
            this.submissionManager = new SubmissionManager(this);

            // Initialize DiscoveryManager for lore discovery events
            this.discoveryManager = new DiscoveryManager(this);
            this.discoveryManager.initialize();

            // Initialize AchievementManager for collection achievements
            this.achievementManager = new AchievementManager(this);
            this.achievementManager.initialize();

            // Register GUI listener for browse menus
            getServer().getPluginManager().registerEvents(new GuiListener(), this);

            // Remove direct CosmeticManager initialization (now handled by ItemManager)
            // cosmeticManager = new CosmeticManager(this);
            // cosmeticManager.initialize();

            // Finally initialize command system
            commandManager = new CommandManager(this);

            // Register with RVNKCore ServiceRegistry if available
            registerWithRVNKCore();

            // Initialize REST API if RVNKCore is available
            initializeRestApi();

            // Register PlaceholderAPI expansion if available
            registerPlaceholderAPI();

            // Start periodic health check
            startHealthCheck();

            logger.info("RVNKLore has been enabled!");
        } catch (Exception e) {
            logger.error("Failed to initialize plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerShutdownHook() {
        shutdownHook = new Thread(() -> {            synchronized(shutdownLock) {
                if (!shuttingDown) {
                    shuttingDown = true;
                    logger.info("Server shutdown detected - cleaning up resources");
                    cleanupManagers();
                }
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private void startHealthCheck() {
        healthCheckTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (databaseManager == null) {
                return;
            }

            // Check database connection
            if (!databaseManager.isConnected()) {
                logger.warning("Database connection lost, attempting reconnect");
                databaseManager.reconnect();
            }

            // If in fallback mode, periodically attempt primary reconnection
            if (databaseManager.isInFallbackMode()) {
                var tracker = databaseManager.getFallbackTracker();
                if (tracker != null && !tracker.isInFallbackMode()) {
                    logger.info("Recovery period elapsed, attempting primary database reconnection");
                    databaseManager.reconnect();
                }
            }
        }, 1200L, 1200L); // Check every minute (20 ticks/sec * 60 sec)
    }

    @Override
    public void onDisable() {
        synchronized(shutdownLock) {
            if (shuttingDown) {
                return; // Already shutting down from shutdown hook
            }
            shuttingDown = true;
        }
          if (logger == null) {
            getLogger().warning("Logger was null during shutdown");
            return;
        }

        logger.info("RVNKLore is shutting down...");

        try {
            // Cancel health check task if running
            if (healthCheckTaskId != -1) {
                getServer().getScheduler().cancelTask(healthCheckTaskId);
                healthCheckTaskId = -1;
            }

            // Remove shutdown hook to prevent duplicate cleanup
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                // JVM is already shutting down, ignore
            }

            cleanupManagers();
        } catch (Exception e) {
            logger.error("Failed to cleanup managers", e);
        } finally {
            logger.info("RVNKLore has been disabled!");
            logger = null;
        }
    }

    /**
     * Initializes the REST API layer if RVNKCore is available.
     */
    private void initializeRestApi() {
        if (!rvnkCoreAvailable) {
            logger.debug("Skipping REST API initialization - RVNKCore not available");
            return;
        }

        apiInitializer = new LoreApiInitializer(this);
        if (apiInitializer.initialize()) {
            logger.info("REST API layer initialized successfully");
        } else {
            logger.debug("REST API initialization skipped or failed");
        }
    }

    /**
     * Registers PlaceholderAPI expansion if PlaceholderAPI is available.
     * Uses reflection to avoid hard dependency on PlaceholderAPI.
     */
    private void registerPlaceholderAPI() {
        Plugin placeholderAPI = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderAPI == null || !placeholderAPI.isEnabled()) {
            logger.info("PlaceholderAPI not found - placeholder support disabled");
            return;
        }

        try {
            placeholderExpansion = new RVNKLorePlaceholderExpansion(this);
            if (placeholderExpansion.register()) {
                logger.info("PlaceholderAPI integration enabled - placeholders registered");
            } else {
                logger.warning("Failed to register PlaceholderAPI expansion");
                placeholderExpansion = null;
            }
        } catch (Exception e) {
            logger.warning("Failed to register PlaceholderAPI expansion: " + e.getMessage());
            placeholderExpansion = null;
        }
    }

    /**
     * Unregisters PlaceholderAPI expansion if it was registered.
     */
    private void unregisterPlaceholderAPI() {
        if (placeholderExpansion != null) {
            try {
                placeholderExpansion.unregister();
                logger.info("PlaceholderAPI expansion unregistered");
            } catch (Exception e) {
                logger.warning("Failed to unregister PlaceholderAPI expansion: " + e.getMessage());
            }
            placeholderExpansion = null;
        }
    }

    private void cleanupManagers() {
        // Shutdown REST API first
        if (apiInitializer != null) {
            apiInitializer.shutdown();
            apiInitializer = null;
        }

        // Unregister PlaceholderAPI expansion
        unregisterPlaceholderAPI();

        // Unregister from RVNKCore first
        unregisterFromRVNKCore();

        // Clear LogManager instances to prevent memory leaks
        LogManager.clearLoggers(this);

        if (utilityManager != null) {
            utilityManager.cleanup();
            utilityManager = null;
        }

        if (handlerFactory != null) {
            handlerFactory.unregisterAllHandlers();
            handlerFactory = null;
        }
          if (loreManager != null) {
            loreManager.cleanup();
            loreManager = null;
        }
        // Remove direct CosmeticManager shutdown (now handled by ItemManager)
        // if (cosmeticManager != null) {
        //     cosmeticManager.shutdown();
        //     cosmeticManager = null;
        // }
        if (discoveryManager != null) {
            discoveryManager.shutdown();
            discoveryManager = null;
        }

        if (achievementManager != null) {
            achievementManager.shutdown();
            achievementManager = null;
        }

        if (itemManager != null) {
            itemManager.shutdown();
            itemManager = null;
        }

        if (commandManager != null) {
            commandManager = null;
        }

        if (configManager != null) {
            configManager = null;
        }

        if (databaseManager != null) {
            databaseManager.close();
            databaseManager = null;
        }
    }

    public LoreManager getLoreManager() {
        return loreManager;
    }
    public LogManager getLogManager() {
        return logger;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Get the handler factory for this plugin
     *
     * @return The handler factory
     */    public HandlerFactory getHandlerFactory() {
        if (handlerFactory == null) {
            logger.warning("Handler factory requested but was null. Creating new instance.");
            handlerFactory = new HandlerFactory(this);
            // Only initialize if it's actually null - avoids repeated initialization
            handlerFactory.initialize();
        }
        return handlerFactory;
    }

    /**
     * Get the utility manager
     */
    public UtilityManager getUtilityManager() {
        return utilityManager;
    }

    /**
     * Get the player manager for player lore operations
     *
     * @return The player manager
     */
    public PlayerManager getPlayerManager() {
        if (playerManager == null) {
            logger.warning("Player manager requested but was null. Creating new instance.");
            playerManager = new PlayerManager(this);
            if (playerLookup != null) {
                playerManager.setPlayerLookup(playerLookup);
            }
            playerManager.initialize();
        }
        return playerManager;
    }

    /**
     * Get the discovery manager for lore discovery events
     *
     * @return The discovery manager
     */
    public DiscoveryManager getDiscoveryManager() {
        if (discoveryManager == null) {
            logger.warning("Discovery manager requested but was null. Creating new instance.");
            discoveryManager = new DiscoveryManager(this);
            discoveryManager.initialize();
        }
        return discoveryManager;
    }

    /**
     * Get the achievement manager for collection achievements
     *
     * @return The achievement manager
     */
    public AchievementManager getAchievementManager() {
        if (achievementManager == null) {
            logger.warning("Achievement manager requested but was null. Creating new instance.");
            achievementManager = new AchievementManager(this);
            achievementManager.initialize();
        }
        return achievementManager;
    }

    /**
     * Get the PlaceholderAPI expansion instance.
     *
     * @return The expansion instance, or null if not registered
     */
    public RVNKLorePlaceholderExpansion getPlaceholderExpansion() {
        return placeholderExpansion;
    }

    /**
     * Checks if PlaceholderAPI integration is available.
     *
     * @return true if PlaceholderAPI is loaded and expansion is registered
     */
    public boolean isPlaceholderAPIAvailable() {
        return placeholderExpansion != null;
    }

    /**
     * Checks if RVNKCore integration is available.
     * @return true if RVNKCore is loaded and services are registered
     */
    public boolean isRVNKCoreAvailable() {
        return rvnkCoreAvailable;
    }

    /**
     * Registers services with RVNKCore ServiceRegistry if available.
     * Uses reflection to avoid hard dependency on RVNKCore classes.
     */
    private void registerWithRVNKCore() {
        Plugin rvnkCorePlugin = getServer().getPluginManager().getPlugin("RVNKCore");
        if (rvnkCorePlugin == null || !rvnkCorePlugin.isEnabled()) {
            logger.info("RVNKCore not found - running in standalone mode");
            return;
        }

        try {
            // Get RVNKCore instance via static getInstance() method
            Class<?> rvnkCoreClass = Class.forName("org.fourz.rvnkcore.RVNKCore");
            Object coreInstance = rvnkCoreClass.getMethod("getInstance").invoke(null);
            if (coreInstance == null) {
                logger.warning("RVNKCore instance is null - services not registered");
                return;
            }

            // Get the ServiceRegistry from RVNKCore
            Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(coreInstance);
            if (serviceRegistry == null) {
                logger.warning("RVNKCore ServiceRegistry is null - services not registered");
                return;
            }

            // Get the registerService method
            Class<?> registryClass = serviceRegistry.getClass();
            java.lang.reflect.Method registerMethod = registryClass.getMethod("registerService", Class.class, Object.class);

            // Register our services
            // Note: LoreManager implements ILoreService, ItemManager implements IItemService, etc.
            registerMethod.invoke(serviceRegistry, ILoreService.class, loreManager);
            logger.info("Registered ILoreService with RVNKCore");

            registerMethod.invoke(serviceRegistry, IItemService.class, itemManager);
            logger.info("Registered IItemService with RVNKCore");

            // Register CollectionService (via ItemManager's CollectionManager)
            CollectionManager collectionManager = itemManager.getCollectionManager();
            if (collectionManager != null) {
                registerMethod.invoke(serviceRegistry, ICollectionService.class, collectionManager);
                logger.info("Registered ICollectionService with RVNKCore");
            }

            // Register SubmissionService
            if (submissionManager != null) {
                registerMethod.invoke(serviceRegistry, ISubmissionService.class, submissionManager);
                logger.info("Registered ISubmissionService with RVNKCore");
            }

            // Register PlayerLoreService
            if (playerManager != null) {
                registerMethod.invoke(serviceRegistry, IPlayerLoreService.class, playerManager);
                logger.info("Registered IPlayerLoreService with RVNKCore");
            }

            rvnkCoreAvailable = true;
            rvnkCoreInstance = coreInstance;
            logger.info("RVNKCore integration enabled - services registered");

        } catch (ClassNotFoundException e) {
            logger.info("RVNKCore classes not found - running in standalone mode");
        } catch (Exception e) {
            logger.warning("Failed to register with RVNKCore: " + e.getMessage());
            logger.warning("Running in standalone mode");
        }
    }

    /**
     * Unregisters services from RVNKCore ServiceRegistry.
     */
    private void unregisterFromRVNKCore() {
        if (!rvnkCoreAvailable || rvnkCoreInstance == null) {
            return;
        }

        try {
            Class<?> rvnkCoreClass = rvnkCoreInstance.getClass();
            Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(rvnkCoreInstance);
            if (serviceRegistry == null) {
                return;
            }

            Class<?> registryClass = serviceRegistry.getClass();
            java.lang.reflect.Method unregisterMethod = registryClass.getMethod("unregisterService", Class.class);

            // Unregister services in reverse order
            unregisterMethod.invoke(serviceRegistry, IPlayerLoreService.class);
            unregisterMethod.invoke(serviceRegistry, ISubmissionService.class);
            unregisterMethod.invoke(serviceRegistry, ICollectionService.class);
            unregisterMethod.invoke(serviceRegistry, IItemService.class);
            unregisterMethod.invoke(serviceRegistry, ILoreService.class);

            logger.info("Services unregistered from RVNKCore");

        } catch (Exception e) {
            logger.warning("Failed to unregister from RVNKCore: " + e.getMessage());
        }

        rvnkCoreAvailable = false;
        rvnkCoreInstance = null;
    }
}

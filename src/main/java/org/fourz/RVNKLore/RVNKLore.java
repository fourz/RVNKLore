package org.fourz.RVNKLore;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKLore.handler.HandlerFactory;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.config.ConfigManager;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.debug.Debug;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.command.CommandManager;
import org.fourz.RVNKLore.util.UtilityManager;
import org.fourz.RVNKLore.lore.item.ItemManager;
import org.fourz.RVNKLore.lore.player.PlayerManager;

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
    private int healthCheckTaskId = -1;
    private Thread shutdownHook;
    private boolean shuttingDown = false;
    private final Object shutdownLock = new Object();@Override
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
            
            // Check database connection
            if (!databaseManager.isConnected()) {
                throw new Exception("Database connection failed. Plugin cannot function without storage.");
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

            // Initialize PlayerManager for player-related lore operations
            this.playerManager = new PlayerManager(this);
            this.playerManager.initialize();

            // Initialize ItemManager through LoreManager
            this.itemManager = loreManager.getItemManager();
            

            
            // Remove direct CosmeticManager initialization (now handled by ItemManager)
            // cosmeticManager = new CosmeticManager(this);
            // cosmeticManager.initialize();
            
            // Finally initialize command system
            commandManager = new CommandManager(this);
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
    
    private void startHealthCheck() {        healthCheckTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            // Check database connection
            if (databaseManager != null && !databaseManager.isConnected()) {
                logger.warning("Database connection lost, attempting reconnect");
                databaseManager.reconnect();
            }
            
            // Log any accumulated errors
            // int errorCount = Debug.getErrorCount();
            // if (errorCount > 0) {
            //     logger.warning("There have been " + errorCount + " errors since last health check");
            //     Debug.resetErrorCount();
            // }
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

    private void cleanupManagers() {
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

    public Debug getDebugger() {
        return logger != null ? logger.getDebug() : null;
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
            playerManager.initialize();
        }
        return playerManager;
    }
}

package org.fourz.RVNKLore;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKLore.handler.HandlerFactory;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.config.ConfigManager;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.debug.Debug;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.exception.LoreException;
import org.fourz.RVNKLore.exception.LoreException.LoreExceptionType;
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
            // First try to initialize the database with settings from config
            logger.info("Initializing database...");
            databaseManager = new DatabaseManager(this, configManager);
            
            // Create required utility systems
            logger.info("Initializing utility systems...");
            utilityManager = UtilityManager.getInstance(this);
            handlerFactory = new HandlerFactory(this);
            
            // Initialize database schema and then start core systems
            logger.info("Loading database schema...");
            databaseManager.executeAsync(conn -> {
                // Schema is loaded by DatabaseManager constructor
                logger.info("Schema loaded successfully");
                return null;
            }).thenRun(() -> {
                try {
                    // Now initialize core systems in order
                    logger.info("Initializing core systems...");
                    handlerFactory.initialize();
                    
                    loreManager = LoreManager.getInstance(this);
                    loreManager.initializeLore();
                    
                    // Initialize managers
                    playerManager = new PlayerManager(this);
                    playerManager.initialize();
                    
                    // Get item manager from LoreManager
                    itemManager = loreManager.getItemManager();
                    
                    // Finally initialize command system
                    commandManager = new CommandManager(this);
                    
                    // Start health monitoring
                    startHealthCheck();
                    
                    logger.info("RVNKLore has been enabled!");
                } catch (Exception e) {
                    logger.error("Failed to initialize core systems", e);
                    getServer().getPluginManager().disablePlugin(this);
                }
            }).exceptionally(ex -> {
                logger.error("Failed to initialize database schema", ex);
                getServer().getPluginManager().disablePlugin(this);
                return null;
            });
        }catch (Exception e) {
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
        // Health checks are scheduled by individual systems.
        // We just schedule a periodic task to check their status.
        healthCheckTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (databaseManager != null) {
                logger.info("Database status: " + databaseManager.getDatabaseInfo());
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

    private void cleanupManagers() {
        // Cancel any pending tasks
        if (healthCheckTaskId != -1) {
            getServer().getScheduler().cancelTask(healthCheckTaskId);
            healthCheckTaskId = -1;
        }
        
        // Clean up in reverse order of initialization
        if (commandManager != null) {
            commandManager = null;
        }

        if (itemManager != null) {
            itemManager.shutdown();
            itemManager = null;
        }

        if (playerManager != null) {
            playerManager = null;
        }

        if (loreManager != null) {
            loreManager.cleanup();
            loreManager = null;
        }

        if (handlerFactory != null) {
            handlerFactory.unregisterAllHandlers();
            handlerFactory = null;
        }

        if (utilityManager != null) {
            utilityManager.cleanup();
            utilityManager = null;
        }

        if (databaseManager != null) {
            try {
                databaseManager.close(); // This should be called last since other systems might need DB access during cleanup
            } catch (Exception e) {
                logger.error("Error closing database connection", e);
            }
            databaseManager = null;
        }
        
        configManager = null; // ConfigManager doesn't need cleanup
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

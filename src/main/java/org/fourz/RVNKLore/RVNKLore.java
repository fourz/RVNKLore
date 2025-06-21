package org.fourz.RVNKLore;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKLore.handler.HandlerFactory;
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
    private LogManager logger;
    private ConfigManager configManager;
    private CommandManager commandManager;
    private DatabaseManager databaseManager;
    private HandlerFactory handlerFactory;
    private UtilityManager utilityManager;
    private ItemManager itemManager;
    private PlayerManager playerManager;
    private Thread shutdownHook;
    private boolean shuttingDown = false;
    private final Object shutdownLock = new Object();@Override
    public void onEnable() {
        // Initialize logging
        this.logger = LogManager.getInstance(this, "RVNKLore");
        logger.info("&6⚙ Enabling RVNKLore plugin...");

        // Load configuration (DTO-based, no explicit load() call)
        this.configManager = new ConfigManager(this);
        logger.info("&a✓ Configuration manager initialized");

        // Initialize database manager (central hub for all DB operations)
        this.databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        logger.info("&a✓ DatabaseManager initialized");

        // Register commands, events, and managers as needed
        // ...existing code for command/event registration...
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

    @Override
    public void onDisable() {
        logger.info("&6⚙ Disabling RVNKLore plugin...");
        if (databaseManager != null) {
            databaseManager.close();
            logger.info("&a✓ DatabaseManager closed");
        }
        synchronized(shutdownLock) {
            if (!shuttingDown) {
                shuttingDown = true;
                logger.info("Plugin disable detected - cleaning up resources");
                if (databaseManager != null) {
                    databaseManager.stopHealthService();
                    databaseManager.close();
                }
                cleanupManagers();
            }
        }
          if (logger == null) {
            getLogger().warning("Logger was null during shutdown");
            return;
        }

        logger.info("RVNKLore is shutting down...");
        
        try {
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

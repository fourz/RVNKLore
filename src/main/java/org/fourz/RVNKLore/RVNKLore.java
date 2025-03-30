package org.fourz.RVNKLore;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKLore.handler.HandlerFactory;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.util.Debug;
import org.fourz.RVNKLore.config.ConfigManager;
import org.fourz.RVNKLore.command.CommandManager;
import org.fourz.RVNKLore.database.DatabaseManager;
import org.fourz.RVNKLore.util.UtilityManager;
import java.util.logging.Level;

public class RVNKLore extends JavaPlugin {
    private LoreManager loreManager;
    private Debug debugger;
    private ConfigManager configManager;
    private CommandManager commandManager;
    private DatabaseManager databaseManager;
    private HandlerFactory handlerFactory;
    private UtilityManager utilityManager;
    private int healthCheckTaskId = -1;
    
    @Override
    public void onEnable() {
        // Initialize ConfigManager first
        configManager = new ConfigManager(this);
        debugger = new Debug(this, "RVNKLore", configManager.getLogLevel()) {};
        configManager.initDebugLogging(); // Initialize debug in the config manager
        debugger.info("Initializing RVNKLore...");
        
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
            debugger.info("Initializing core systems...");
            handlerFactory.initialize();
            
            // Now initialize LoreManager after HandlerFactory is fully initialized
            loreManager = LoreManager.getInstance(this);
            loreManager.initializeLore();
            
            // Finally initialize command system
            commandManager = new CommandManager(this);
            
            debugger.info("RVNKLore has been enabled!");
        } catch (Exception e) {
            debugger.error("Failed to initialize plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (debugger == null) {
            getLogger().warning("Debugger was null during shutdown");
            return;
        }

        debugger.info("RVNKLore is shutting down...");
        
        try {
            // Cancel health check task if running
            if (healthCheckTaskId != -1) {
                getServer().getScheduler().cancelTask(healthCheckTaskId);
                healthCheckTaskId = -1;
            }
            
            cleanupManagers();
        } catch (Exception e) {
            debugger.error("Failed to cleanup managers", e);
        } finally {
            debugger.info("RVNKLore has been disabled!");
            debugger = null;
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

    public Debug getDebugger() {
        return debugger;
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
     */
    public HandlerFactory getHandlerFactory() {
        if (handlerFactory == null) {
            debugger.warning("Handler factory requested but was null. Creating new instance.");
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
}

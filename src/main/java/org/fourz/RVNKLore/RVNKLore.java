package org.fourz.RVNKLore;

import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.RVNKLore.handler.LoreHandlerManager;
import org.fourz.RVNKLore.handler.HandlerFactory;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.util.Debug;
import org.fourz.RVNKLore.config.ConfigManager;
import org.fourz.RVNKLore.command.CommandManager;
import org.fourz.RVNKLore.database.DatabaseManager;
import java.util.logging.Level;

public class RVNKLore extends JavaPlugin {
    private LoreManager loreManager;
    private Debug debugger;
    private ConfigManager configManager;
    private CommandManager commandManager;
    private DatabaseManager databaseManager;
    private LoreHandlerManager handlerManager;
    private HandlerFactory handlerFactory;
    
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
            
            // Initialize handler factory and manager
            handlerFactory = new HandlerFactory(this);
            handlerManager = new LoreHandlerManager(this);
            
            // Continue with other managers
            loreManager = new LoreManager(this);
            loreManager.initializeLore();
            commandManager = new CommandManager(this);
            
            debugger.info("RVNKLore has been enabled!");
        } catch (Exception e) {
            debugger.error("Failed to initialize plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializeManagers() {
        try {
            if (databaseManager == null) {
                databaseManager = new DatabaseManager(this);
            }
            
            if (handlerFactory == null) {
                handlerFactory = new HandlerFactory(this);
            }
            
            if (loreManager == null) {
                loreManager = new LoreManager(this);
                loreManager.initializeLore();
            }
            
            if (commandManager == null) {
                commandManager = new CommandManager(this);
            }
        } catch (Exception e) {
            debugger.error("Failed to initialize managers", e);
            throw new RuntimeException("Manager initialization failed", e);
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
            cleanupManagers();
        } catch (Exception e) {
            debugger.error("Failed to cleanup managers", e);
        } finally {
            debugger.info("RVNKLore has been disabled!");
            debugger = null;
        }
    }

    private void cleanupManagers() {
        if (handlerManager != null) {
            handlerManager.unregisterAllHandlers();
            handlerManager = null;
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
    
    public LoreHandlerManager getHandlerManager() {
        return handlerManager;
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
        }
        return handlerFactory;
    }
}

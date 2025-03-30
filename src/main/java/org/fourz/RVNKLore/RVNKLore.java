package org.fourz.RVNKLore;

import org.bukkit.plugin.java.JavaPlugin;
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
    
    @Override
    public void onEnable() {
        // Initialize ConfigManager first
        configManager = new ConfigManager(this);
        debugger = new Debug(this, "RVNKLore", configManager.getLogLevel()) {};
        debugger.info("Initializing RVNKLore...");
        
        try {
            initializeManagers();
            debugger.info("RVNKLore has been enabled!");
        } catch (Exception e) {
            debugger.error("Failed to initialize plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializeManagers() {
        databaseManager = new DatabaseManager(this);
        loreManager = new LoreManager(this);
        loreManager.initializeLore();
        commandManager = new CommandManager(this);
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
}

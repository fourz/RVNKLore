package org.fourz.RVNKLore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.handler.HandlerFactory;
import org.fourz.RVNKLore.handler.LoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.util.Debug;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {
    private static final String CLASS_NAME = "ConfigManager";
    private final RVNKLore plugin;
    private FileConfiguration config;
    private Debug debug;
    private HandlerFactory handlerFactory;
    private Level globalLogLevel;

    public ConfigManager(RVNKLore plugin) {
        this.plugin = plugin;
        loadConfig();
        // Initialize global log level immediately after loading config
        this.globalLogLevel = parseLogLevel();
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        // Set config defaults if they don't exist
        setDefaults();
    }
    
    private void setDefaults() {
        config.addDefault("general.logLevel", "INFO");
        config.addDefault("storage.type", "sqlite");
        config.addDefault("storage.mysql.host", "localhost");
        config.addDefault("storage.mysql.port", 3306);
        config.addDefault("storage.mysql.database", "minecraft");
        config.addDefault("storage.mysql.username", "root");
        config.addDefault("storage.mysql.password", "");
        config.addDefault("lore.nearbyRadius", 50.0);
        config.addDefault("lore.requireApproval", true);
        
        // Add handler configuration defaults
        for (LoreType type : LoreType.values()) {
            config.addDefault("lore.handlers." + type.name(), "DEFAULT");
        }
        
        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    // Called after Debug is initialized
    public void initDebugLogging() {
        this.debug = new Debug(plugin, CLASS_NAME, globalLogLevel) {};
        debug.debug("Configuration system initialized with log level: " + globalLogLevel.getName());
    }

    /**
     * Parse log level from config with proper error handling
     */
    private Level parseLogLevel() {
        String levelStr = config.getString("general.logLevel", "INFO");
        try {
            // Special handling for DEBUG which maps to FINE
            if (levelStr.equalsIgnoreCase("DEBUG")) {
                return Level.FINE;
            }
            return Level.parse(levelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid log level in config: " + levelStr + ". Using INFO level instead.");
            return Level.INFO;
        }
    }

    /**
     * Get the globally configured log level
     */
    public Level getLogLevel() {
        return globalLogLevel;
    }

    /**
     * Update global log level after config reload
     */
    private void updateGlobalLogLevel() {
        Level newLevel = parseLogLevel();
        
        // Only update and propagate if the level has actually changed
        if (!newLevel.equals(globalLogLevel)) {
            globalLogLevel = newLevel;
            plugin.getLogger().info("Log level changed to: " + newLevel.getName());
            
            // Update debug instance for this class
            if (debug != null) {
                debug.setLogLevel(newLevel);
            }
            
            // Update main plugin debugger if available
            if (plugin.getDebugger() != null) {
                plugin.getDebugger().setLogLevel(newLevel);
            }
        }
    }

    /**
     * Initialize the handler factory
     */
    public void initHandlerFactory() {
        this.handlerFactory = new HandlerFactory(plugin);
    }

    /**
     * Load lore handlers from configuration
     * 
     * @return A map of lore types to their handlers
     */
    public Map<LoreType, LoreHandler> loadLoreHandlers() {
        if (debug == null) {
            initDebugLogging();
        }
        
        if (handlerFactory == null) {
            initHandlerFactory();
        }
        
        debug.debug("Loading lore handlers from configuration...");
        Map<LoreType, LoreHandler> handlers = new HashMap<>();
        
        // Register handlers for all lore types
        for (LoreType type : LoreType.values()) {
            try {
                LoreHandler handler = handlerFactory.getHandler(type);
                handlers.put(type, handler);
                debug.debug("Registered handler for " + type + ": " + handler.getClass().getSimpleName());
            } catch (Exception e) {
                debug.error("Failed to create handler for type " + type + ", using default handler", e);
                handlers.put(type, new DefaultLoreHandler(plugin));
            }
        }
        
        return handlers;
    }
    
    /**
     * Export lore entries to file
     * 
     * @param entries The entries to export
     * @param filePath The file path to export to
     * @return True if successful, false otherwise
     */
    public boolean exportLoreEntriesToFile(List<LoreEntry> entries, String filePath) {
        if (debug == null) {
            initDebugLogging();
        }
        
        debug.debug("Exporting " + entries.size() + " lore entries to file: " + filePath);
        try {
            // Use the database manager to handle the export
            return plugin.getDatabaseManager().exportLoreEntriesToFile(entries, filePath);
        } catch (Exception e) {
            debug.warning("Failed to export lore entries: " + e.getMessage());
            return false;
        }
    }

    public String getStorageType() {
        return config.getString("storage.type", "sqlite");
    }
    
    public double getNearbyRadius() {
        return config.getDouble("lore.nearbyRadius", 50.0);
    }
    
    public boolean requireApproval() {
        return config.getBoolean("lore.requireApproval", true);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        // Update log level whenever config is reloaded
        updateGlobalLogLevel();
        
        if (debug != null) {
            debug.debug("Configuration reloaded with log level: " + globalLogLevel.getName());
        }
    }

    /**
     * Get the handler factory
     * 
     * @return The handler factory
     */
    public HandlerFactory getHandlerFactory() {
        if (handlerFactory == null) {
            initHandlerFactory();
        }
        return handlerFactory;
    }

    /**
     * Configure log level for any new debug instances
     */
    public void configureDebugInstance(Debug debugInstance) {
        if (debugInstance != null) {
            debugInstance.setLogLevel(globalLogLevel);
        }
    }
}

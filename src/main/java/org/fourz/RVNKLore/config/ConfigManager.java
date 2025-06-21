package org.fourz.RVNKLore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.Debug;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.handler.HandlerFactory;
import org.fourz.RVNKLore.handler.LoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final RVNKLore plugin;
    private FileConfiguration config;
    private HandlerFactory handlerFactory;
    private LogManager logger;

    public ConfigManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ConfigManager");
        loadConfig();
    }    private void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        validateConfig();
        setDefaults();
        
        // Apply log level from config to all LogManager instances
        java.util.logging.Level configLevel = getLogLevel();
        updateAllLogManagers(configLevel);
    }
    
    private void validateConfig() {
        // Check essential configuration sections
        if (!config.contains("general.logLevel")) {
            logger.warning("No log level defined in config, using default: INFO");
        }
        if (!config.contains("storage.type")) {
            logger.warning("No storage type defined in config, using default: sqlite");
        }
        // Validate database connection settings
        String storageType = getStorageType();
        if ("mysql".equalsIgnoreCase(storageType)) {
            if (!config.contains("storage.mysql.host") || 
                !config.contains("storage.mysql.database") ||
                !config.contains("storage.mysql.username")) {
                logger.warning("Missing required MySQL settings - check your config.yml");
            }
        }
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
    }    /**
     * Initialize logging system and apply configuration.
     * This method ensures LogManager instances use the correct log level from config.
     */
    public void initDebugLogging() {
        java.util.logging.Level configLevel = getLogLevel();
        updateAllLogManagers(configLevel);
        logger.info("Configuration system initialized with log level: " + configLevel.getName());
    }/**
     * Get the configured log level from config file.
     * @return The log level from configuration
     */
    public java.util.logging.Level getLogLevel() {
        String levelString = config.getString("general.logLevel", "INFO").toUpperCase();
        // Map DEBUG to FINE for Java logging compatibility
        if (levelString.equals("DEBUG")) {
            levelString = "FINE";
        }
        try {
            return java.util.logging.Level.parse(levelString);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid log level in config: " + levelString + ", using INFO as default");
            return java.util.logging.Level.INFO;
        }
    }

    /**
     * Set the log level in configuration and update all LogManager instances.
     * @param level The new log level to set
     */
    public void setLogLevel(java.util.logging.Level level) {
        config.set("general.logLevel", level.getName());
        plugin.saveConfig();
        updateAllLogManagers(level);
        logger.info("Log level changed to: " + level.getName());
    }

    /**
     * Update log level for all LogManager instances and legacy Debug instances.
     * This is called when the configuration changes.
     */
    private void updateAllLogManagers(java.util.logging.Level newLevel) {
        // Update all LogManager instances
        LogManager.updateAllLogLevels(newLevel);
        
        // Update legacy Debug instance if it exists (for backward compatibility)
        if (plugin.getDebugger() != null) {
            plugin.getDebugger().setLogLevel(newLevel);
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
        if (handlerFactory == null) {
            initHandlerFactory();
        }
        logger.info("Loading lore handlers from configuration...");
        Map<LoreType, LoreHandler> handlers = new HashMap<>();
        // Register handlers for all lore types
        for (LoreType type : LoreType.values()) {
            try {
                LoreHandler handler = handlerFactory.getHandler(type);
                handlers.put(type, handler);
                logger.info("Registered handler for " + type + ": " + handler.getClass().getSimpleName());
            } catch (Exception e) {
                logger.error("Failed to create handler for type " + type + ", using default handler", e);
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
     */    public boolean exportLoreEntriesToFile(List<LoreEntry> entries, String filePath) {
        logger.info("Exporting " + entries.size() + " lore entries to file: " + filePath);
        // Export functionality is not yet implemented in the new database API
        logger.warning("Export functionality not yet implemented");
        return false;
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
    }    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        java.util.logging.Level newLevel = getLogLevel();
        updateAllLogManagers(newLevel);
        logger.info("Configuration reloaded with log level: " + newLevel.getName());
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
    }    /**
     * Configure log level for any new debug instances
     */
    public void configureDebugInstance(Debug debugInstance) {
        if (debugInstance != null) {
            debugInstance.setLogLevel(getLogLevel());
        }
    }

    /**
     * Set log level using string value (for command usage).
     * @param levelString The log level as a string (e.g., "INFO", "WARNING", "SEVERE")
     * @return True if successful, false if invalid level string
     */
    public boolean setLogLevel(String levelString) {
        try {
            java.util.logging.Level level = java.util.logging.Level.parse(levelString.toUpperCase());
            setLogLevel(level);
            return true;
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid log level: " + levelString + ". Valid levels are: SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST");
            return false;
        }
    }

    /**
     * Get available log level names.
     * @return Array of valid log level names
     */
    public String[] getAvailableLogLevels() {
        return new String[]{"SEVERE", "WARNING", "INFO", "CONFIG", "FINE", "FINER", "FINEST"};
    }
    
    /**
     * Get MySQL database configuration with defaults.
     */
    public Map<String, Object> getMySQLSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("host", config.getString("storage.mysql.host", "localhost"));
        settings.put("port", config.getInt("storage.mysql.port", 3306));
        settings.put("database", config.getString("storage.mysql.database", "minecraft"));
        settings.put("username", config.getString("storage.mysql.username", "root"));
        settings.put("password", config.getString("storage.mysql.password", ""));
        settings.put("useSSL", config.getBoolean("storage.mysql.useSSL", false));
        settings.put("poolSize", config.getInt("storage.mysql.poolSize", 10));
        settings.put("connectionTimeout", config.getInt("storage.mysql.connectionTimeout", 30000));
        settings.put("idleTimeout", config.getInt("storage.mysql.idleTimeout", 600000));
        settings.put("maxLifetime", config.getInt("storage.mysql.maxLifetime", 1800000));
        settings.put("tablePrefix", config.getString("storage.mysql.tablePrefix", ""));
        return settings;
    }
    
    /**
     * Get SQLite database configuration with defaults.
     */
    public Map<String, Object> getSQLiteSettings() {
        Map<String, Object> settings = new HashMap<>();
        String dbFile = config.getString("storage.sqlite.database", "data.db");
        String dbPath = new java.io.File(plugin.getDataFolder(), "database/" + dbFile).getAbsolutePath();
        settings.put("database", dbFile);
        settings.put("path", dbPath);
        settings.put("busyTimeout", config.getInt("storage.sqlite.busyTimeout", 3000));
        settings.put("walMode", config.getBoolean("storage.sqlite.walMode", true));
        settings.put("synchronous", config.getString("storage.sqlite.synchronous", "NORMAL"));
        return settings;
    }

    /**
     * Get database connection retry settings.
     */
    public Map<String, Object> getDatabaseRetrySettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("maxRetries", config.getInt("storage.maxRetries", 3));
        settings.put("retryDelay", config.getInt("storage.retryDelay", 1000));
        settings.put("connectionTimeout", config.getInt("storage.connectionTimeout", 30000));
        return settings;
    }
}

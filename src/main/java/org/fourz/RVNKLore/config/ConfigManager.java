package org.fourz.RVNKLore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.config.dto.DatabaseSettingsDTO;
import org.fourz.rvnkcore.config.dto.MySQLSettingsDTO;
import org.fourz.rvnkcore.config.dto.SQLiteSettingsDTO;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.handler.HandlerFactory;
import org.fourz.RVNKLore.handler.LoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.bukkit.configuration.ConfigurationSection;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {
    private final RVNKLore plugin;
    private FileConfiguration config;
    private HandlerFactory handlerFactory;
    private LogManager logger;
    private DatabaseSettingsDTO databaseSettings;

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

        // Initialize and validate database settings DTO
        try {
            this.databaseSettings = createDatabaseSettings();
            logger.info("Database configuration validated successfully");
        } catch (IllegalArgumentException e) {
            logger.error("Invalid database configuration: " + e.getMessage());
            logger.warning("Plugin will continue but database features may be unavailable");
        }
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
    }

    /**
     * Create DatabaseSettingsDTO from configuration.
     * Called during initialization and config reload.
     * @return Configured DatabaseSettingsDTO instance
     * @throws IllegalArgumentException if configuration is invalid
     */
    private DatabaseSettingsDTO createDatabaseSettings() {
        String storageType = config.getString("storage.type", "sqlite");
        DatabaseSettingsDTO.DatabaseType type = storageType.equalsIgnoreCase("mysql")
            ? DatabaseSettingsDTO.DatabaseType.MYSQL
            : DatabaseSettingsDTO.DatabaseType.SQLITE;

        int connectionTimeout = config.getInt("storage.mysql.connectionTimeout", 30000);
        int maxRetries = config.getInt("storage.connection.retryAttempts", 5);

        MySQLSettingsDTO mysqlSettings = null;
        if (type == DatabaseSettingsDTO.DatabaseType.MYSQL) {
            mysqlSettings = new MySQLSettingsDTO(
                config.getString("storage.mysql.host", "localhost"),
                config.getInt("storage.mysql.port", 3306),
                config.getString("storage.mysql.database", "minecraft"),
                config.getString("storage.mysql.username", "root"),
                config.getString("storage.mysql.password", ""),
                config.getBoolean("storage.mysql.useSSL", false),
                config.getString("storage.mysql.tablePrefix", "")
            );
        }

        SQLiteSettingsDTO sqliteSettings = null;
        if (type == DatabaseSettingsDTO.DatabaseType.SQLITE) {
            String dbFile = config.getString("storage.sqlite.database", "lore.db");
            String filePath = new File(plugin.getDataFolder(), dbFile).getAbsolutePath();
            sqliteSettings = new SQLiteSettingsDTO(
                filePath,
                config.getString("storage.sqlite.tablePrefix", "")
            );
        }

        DatabaseSettingsDTO dto = new DatabaseSettingsDTO(
            type, mysqlSettings, sqliteSettings, connectionTimeout, maxRetries
        );
        dto.validate();
        return dto;
    }

    /**
     * Get the database configuration settings as a strongly-typed DTO.
     * The DTO is cached and only recreated when the configuration is reloaded.
     *
     * @return DatabaseSettingsDTO instance with validated settings
     */
    public DatabaseSettingsDTO getDatabaseSettings() {
        if (databaseSettings == null) {
            databaseSettings = createDatabaseSettings();
        }
        return databaseSettings;
    }

    /**
     * Initialize logging system and apply configuration.
     * This method ensures LogManager instances use the correct log level from config.
     */
    public void initDebugLogging() {
        Level configLevel = getLogLevel();
        updateAllLogManagers(configLevel);
        logger.info("Configuration system initialized with log level: " + configLevel.getName());
    }

    /**
     * Get the configured log level from config file.
     * Uses RVNKCore LogManager.parseLevel() which supports aliases (DEBUG, WARN, ERROR).
     * @return The log level from configuration
     */
    public Level getLogLevel() {
        String levelString = config.getString("general.logLevel", "INFO");
        return LogManager.parseLevel(levelString);
    }

    /**
     * Set the log level in configuration and update all LogManager instances.
     * @param level The new log level to set
     */
    public void setLogLevel(Level level) {
        config.set("general.logLevel", level.getName());
        plugin.saveConfig();
        updateAllLogManagers(level);
        logger.info("Log level changed to: " + level.getName());
    }

    /**
     * Update log level for all LogManager instances.
     * This is called when the configuration changes.
     */
    private void updateAllLogManagers(Level newLevel) {
        // Update all LogManager instances for this plugin
        LogManager.setPluginLogLevel(plugin, newLevel);
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
     */
    public boolean exportLoreEntriesToFile(List<LoreEntry> entries, String filePath) {
        logger.info("Exporting " + entries.size() + " lore entries to file: " + filePath);
        try {
            // Use the database manager to handle the export
            return plugin.getDatabaseManager().exportLoreEntriesToFile(entries, filePath);
        } catch (Exception e) {
            logger.warning("Failed to export lore entries: " + e.getMessage());
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
    }    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Refresh database settings DTO
        try {
            this.databaseSettings = createDatabaseSettings();
            logger.info("Database configuration reloaded and validated");
        } catch (IllegalArgumentException e) {
            logger.error("Invalid database configuration after reload: " + e.getMessage());
        }

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
    }

    /**
     * Set log level using string value (for command usage).
     * Uses RVNKCore LogManager.parseLevel() which supports aliases (DEBUG, WARN, ERROR).
     * @param levelString The log level as a string (e.g., "INFO", "DEBUG", "ERROR")
     * @return True if successful (always true as parseLevel defaults to INFO)
     */
    public boolean setLogLevel(String levelString) {
        Level level = LogManager.parseLevel(levelString);
        setLogLevel(level);
        return true;
    }

    /**
     * Get available log level names.
     * Includes RVNKCore LogManager aliases for convenience.
     * @return Array of valid log level names
     */
    public String[] getAvailableLogLevels() {
        return new String[]{"DEBUG", "INFO", "WARN", "WARNING", "ERROR", "SEVERE", "OFF"};
    }
    
    /**
     * Get test mode setting
     */
    public boolean isTestMode() {
        return "yes".equalsIgnoreCase(config.getString("storage.test-mode", "no"));
    }

    // ==================== Dynmap Configuration ====================

    public boolean isDynmapEnabled() {
        return config.getBoolean("dynmap.enabled", true);
    }

    public String getDynmapMarkerSetId() {
        return config.getString("dynmap.marker-set.id", "rvnklore");
    }

    public String getDynmapMarkerSetLabel() {
        return config.getString("dynmap.marker-set.label", "Lore Entries");
    }

    public boolean isDynmapMarkerSetHidden() {
        return config.getBoolean("dynmap.marker-set.hide-by-default", false);
    }

    public int getDynmapLayerPriority() {
        return config.getInt("dynmap.marker-set.layer-priority", 10);
    }

    public String getDynmapIcon(LoreType type) {
        String key = "dynmap.icons." + type.name();
        return config.getString(key, config.getString("dynmap.icons.default", "sign"));
    }

    public boolean isDynmapOnlyApproved() {
        return config.getBoolean("dynmap.only-approved", true);
    }

    public boolean isDynmapPopupEnabled() {
        return config.getBoolean("dynmap.popup.enabled", true);
    }

    public int getDynmapMaxDescriptionLength() {
        return config.getInt("dynmap.popup.max-description-length", 200);
    }

    // ==================== Collection Marker Configuration ====================

    public boolean isCollectionMarkersEnabled() {
        return config.getBoolean("dynmap.collection-markers.enabled", true);
    }

    public String getCollectionMarkerIcon(String theme) {
        String key = "dynmap.collection-markers.icons." + theme;
        return config.getString(key, config.getString("dynmap.collection-markers.icons.default", "pin"));
    }
}

package org.fourz.RVNKLore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.config.dto.DatabaseSettingsDTO;
import org.fourz.rvnkcore.config.dto.MySQLSettingsDTO;
import org.fourz.rvnkcore.config.dto.SQLiteSettingsDTO;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.bukkit.configuration.ConfigurationSection;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

public class ConfigManager {
    private final RVNKLore plugin;
    private FileConfiguration config;
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
            logger.debug("Database configuration validated successfully");
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
        config.addDefault("lore.nearbyRadius", 20.0);
        config.addDefault("lore.requireApproval", true);
        config.addDefault("lore.playerDeath.mode", "none");
        config.addDefault("features.collections.enabled", true);
        config.addDefault("features.discovery.enabled", true);
        config.addDefault("features.achievements.enabled", true);
        config.addDefault("features.approval-workflow.enabled", true);
        config.addDefault("features.rest-api.enabled", true);
        
        // Add handler configuration defaults
        for (LoreType type : LoreType.values()) {
            config.addDefault("lore.handlers." + type.name(), "DEFAULT");
        }

        // Per-type discovery notification defaults (hard gate, #1475). Record-keeping types
        // (PLAYER/EVENT/GENERIC/HEAD) default off so they record silently instead of blasting.
        for (LoreType type : LoreType.values()) {
            config.addDefault("notifications.perType." + type.name() + ".enabled",
                              !DEFAULT_NOTIFY_DISABLED.contains(type));
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
        logger.debug("Configuration system initialized with log level: " + configLevel.getName());
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
        return config.getDouble("lore.nearbyRadius", 20.0);
    }

    /**
     * Player-death lore creation mode: "none" (no death lore), "significant"
     * (only deaths flagged by the significance evaluator), or "all".
     * Unrecognized values are treated as "none".
     */
    public String getPlayerDeathLoreMode() {
        String mode = config.getString("lore.playerDeath.mode", "none").trim().toLowerCase();
        switch (mode) {
            case "significant":
            case "all":
                return mode;
            default:
                return "none";
        }
    }
    
    public boolean requireApproval() {
        // When approval workflow is disabled, treat as no approval required
        if (!isFeatureEnabled("approval-workflow")) return false;
        return config.getBoolean("lore.requireApproval", true);
    }

    // ==================== Feature Toggles ====================

    /**
     * Master operational mode (#1824): {@code full}, {@code quiet}, or {@code off}, read from
     * {@code general.mode}. Absent or unrecognized values default to {@link LoreMode#FULL} so an
     * existing config that lacks the key behaves exactly as before (the packaged default is never
     * written over an existing file — #1592). Composes over the {@code features.*.enabled} flags.
     */
    public LoreMode getMode() {
        String raw = config.getString("general.mode", "full").trim().toLowerCase();
        switch (raw) {
            // "true" is not a documented spelling — it is what an unquoted `on` becomes, and it is
            // accepted only for symmetry with the `off` case below.
            case "full":
            case "true":
                return LoreMode.FULL;
            case "quiet": return LoreMode.QUIET;
            // "false" IS the documented spelling arriving in disguise. YAML 1.1 — which SnakeYAML
            // implements — resolves bare off/no/false to boolean false, so `mode: off` written
            // exactly as this plugin's own config.yml documents it reaches Bukkit as Boolean.FALSE
            // and getString() hands back "false". Without this case the sole mode #1826 exists to
            // deliver was unreachable by its own documented spelling: the plugin logged
            // "Unknown general.mode 'false'" and silently ran FULL. Verified on Dev 2026-08-09.
            case "off":
            case "false":
                return LoreMode.OFF;
            default:
                logger.warning("Unknown general.mode '" + raw + "' — defaulting to 'full'. Valid: full | quiet | off");
                return LoreMode.FULL;
        }
    }

    /**
     * True when player-facing lore notifications must be suppressed globally (mode {@code quiet}).
     * The underlying data (discoveries, achievements, collection progress) is still recorded upstream;
     * only the player-facing send is gated. Per-type control (#1475) layers on top of this. (#1827)
     */
    public boolean areNotificationsSuppressed() {
        return getMode().suppressesNotifications();
    }

    /**
     * Check if a named feature subsystem is enabled.
     * All features default to true when the key is absent.
     */
    public boolean isFeatureEnabled(String feature) {
        return config.getBoolean("features." + feature + ".enabled", true);
    }

    public boolean isCollectionsEnabled() { return isFeatureEnabled("collections"); }
    public boolean isDiscoveryEnabled()   { return isFeatureEnabled("discovery"); }
    public boolean isAchievementsEnabled(){ return isFeatureEnabled("achievements"); }
    public boolean isApprovalWorkflowEnabled() { return isFeatureEnabled("approval-workflow"); }
    public boolean isRestApiEnabled()     { return isFeatureEnabled("rest-api"); }

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
    
    /** Drop and recreate all lore tables on next server load. Implies purgeData. Dev-only. */
    public boolean isPurgeSchema() {
        return config.getBoolean("storage.dev.purgeSchema", false);
    }

    /** Delete all rows from every lore table on next server load (keeps schema). Dev-only. */
    public boolean isPurgeData() {
        return config.getBoolean("storage.dev.purgeData", false);
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

    // ==================== Per-Type Dynmap Layer Configuration ====================

    /**
     * Default display labels for each location-capable LoreType.
     */
    private static final java.util.Map<LoreType, String> DEFAULT_LAYER_LABELS;
    static {
        java.util.Map<LoreType, String> m = new java.util.EnumMap<>(LoreType.class);
        m.put(LoreType.CITY, "Cities");
        m.put(LoreType.LANDMARK, "Landmarks");
        m.put(LoreType.MONUMENT, "Monuments");
        m.put(LoreType.TAVERN, "Taverns & Inns");
        m.put(LoreType.GUILD, "Guilds");
        m.put(LoreType.SHRINE, "Shrines");
        m.put(LoreType.PATH, "Paths & Roads");
        m.put(LoreType.EVENT, "Events");
        m.put(LoreType.FACTION, "Factions");
        DEFAULT_LAYER_LABELS = java.util.Collections.unmodifiableMap(m);
    }

    /**
     * LoreTypes whose discovery notifications are OFF by default (#1475).
     * Record-keeping types that would otherwise blast on proximity discovery:
     * new-player arrivals (PLAYER), deaths/events (EVENT), catch-all (GENERIC),
     * and cosmetic heads (HEAD). All other types notify by default.
     */
    private static final java.util.EnumSet<LoreType> DEFAULT_NOTIFY_DISABLED =
        java.util.EnumSet.of(LoreType.PLAYER, LoreType.EVENT, LoreType.GENERIC, LoreType.HEAD);

    public String getDynmapLayerLabel(LoreType type) {
        String defaultLabel = DEFAULT_LAYER_LABELS.getOrDefault(type, type.name());
        return config.getString("dynmap.layers." + type.name() + ".label", defaultLabel);
    }

    public boolean isDynmapLayerHidden(LoreType type) {
        return config.getBoolean("dynmap.layers." + type.name() + ".hidden", false);
    }

    public int getDynmapLayerPriority(LoreType type) {
        return config.getInt("dynmap.layers." + type.name() + ".priority", getDynmapLayerPriority());
    }

    public String getDynmapIcon(LoreType type) {
        String key = "dynmap.icons." + type.name();
        return config.getString(key, config.getString("dynmap.icons.default", "sign"));
    }

    public boolean isDynmapOnlyApproved() {
        return config.getBoolean("dynmap.only-approved", true);
    }

    /**
     * Whether discovery notifications (title/actionbar/chat/sound) fire for a given lore type (#1475).
     * Hard server-side gate: when false, no player is notified for that type, but the discovery is
     * still recorded. Read live from config so /lore reload takes effect without a restart.
     */
    public boolean isDiscoveryNotificationEnabled(LoreType type) {
        if (type == null) return true;
        return config.getBoolean("notifications.perType." + type.name() + ".enabled",
                                 !DEFAULT_NOTIFY_DISABLED.contains(type));
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

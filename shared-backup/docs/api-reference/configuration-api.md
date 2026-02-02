# Configuration API Reference

This document provides comprehensive reference for implementing configuration management systems in Minecraft plugin development, specifically tailored for RVNKLore project patterns.

## Table of Contents
- [Configuration System Overview](#configuration-system-overview)
- [YAML Configuration](#yaml-configuration)
- [Configuration Loading](#configuration-loading)
- [Dynamic Configuration](#dynamic-configuration)
- [Configuration Validation](#configuration-validation)
- [Migration & Versioning](#migration--versioning)
- [Environment-Specific Configs](#environment-specific-configs)
- [Performance Optimization](#performance-optimization)
- [RVNKLore Integration](#rvnklore-integration)

## Configuration System Overview

### Configuration Manager Architecture

```java
public class ConfigManager {
    private static ConfigManager instance;
    private final RVNKLore plugin;
    private final LogManager logger;
    private FileConfiguration config;
    private FileConfiguration messagesConfig;
    private final Map<String, FileConfiguration> additionalConfigs;
    private final ConfigValidator validator;
    
    private ConfigManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.additionalConfigs = new HashMap<>();
        this.validator = new ConfigValidator();
    }
    
    public static ConfigManager getInstance() {
        return instance;
    }
    
    public static void initialize(RVNKLore plugin) {
        if (instance == null) {
            instance = new ConfigManager(plugin);
            instance.loadConfigurations();
        }
    }
    
    public void loadConfigurations() {
        try {
            loadMainConfig();
            loadMessagesConfig();
            loadAdditionalConfigs();
            validateConfigurations();
            
            logger.info("Configuration files loaded successfully");
            
        } catch (Exception e) {
            logger.error("Error loading configuration files", e);
            throw new RuntimeException("Failed to load configurations", e);
        }
    }
}
```

### Configuration Structure

```java
public class ConfigStructure {
    // Configuration file organization
    public static final String MAIN_CONFIG = "config.yml";
    public static final String MESSAGES_CONFIG = "messages.yml";
    public static final String LORE_CATEGORIES = "lore-categories.yml";
    public static final String WORLD_SETTINGS = "world-settings.yml";
    public static final String DATABASE_CONFIG = "database.yml";
    
    // Configuration sections
    public static final String SECTION_GENERAL = "general";
    public static final String SECTION_DATABASE = "database";
    public static final String SECTION_LORE = "lore";
    public static final String SECTION_PERFORMANCE = "performance";
    public static final String SECTION_INTEGRATIONS = "integrations";
    public static final String SECTION_WORLDS = "worlds";
    
    // Configuration keys
    public static final String KEY_DEBUG_MODE = "general.debug-mode";
    public static final String KEY_AUTO_SAVE = "general.auto-save-interval";
    public static final String KEY_MAX_LORE_PER_PLAYER = "lore.max-per-player";
    public static final String KEY_DISCOVERY_RADIUS = "lore.discovery-radius";
    public static final String KEY_CACHE_SIZE = "performance.cache-size";
}
```

## YAML Configuration

### Main Configuration (config.yml)

```yaml
# RVNKLore Configuration
# Version: 2.1.0

general:
  # Enable debug logging
  debug-mode: false
  
  # Plugin language (en, es, fr, de)
  language: en
  
  # Auto-save interval in minutes
  auto-save-interval: 15
  
  # Check for updates on startup
  check-updates: true
  
  # Plugin prefix for messages
  prefix: "&6[RVNKLore]&r"

database:
  # Database type: sqlite, mysql, postgresql
  type: sqlite
  
  # SQLite settings
  sqlite:
    file: "lore_data.db"
    enable-wal: true
    
  # MySQL/PostgreSQL settings
  mysql:
    host: localhost
    port: 3306
    database: rvnklore
    username: rvnklore_user
    password: ""
    
    # Connection pool settings
    pool:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      
    # SSL settings
    ssl:
      enabled: false
      trust-store: ""
      trust-store-password: ""

lore:
  # Maximum lore entries per player
  max-per-player: 50
  
  # Maximum lore content length
  max-content-length: 5000
  
  # Lore discovery settings
  discovery:
    radius: 3.0
    require-line-of-sight: true
    announce-to-nearby: true
    announce-radius: 20.0
    
  # Lore creation settings
  creation:
    enabled: true
    cooldown: 300 # seconds
    require-permission: true
    cost:
      enabled: false
      amount: 100.0
      
  # Visual effects
  effects:
    particles:
      enabled: true
      type: ENCHANTMENT_TABLE
      count: 10
      interval: 60 # ticks
      
    sounds:
      discovery: ENTITY_PLAYER_LEVELUP
      creation: BLOCK_ENCHANTMENT_TABLE_USE
      interaction: ENTITY_EXPERIENCE_ORB_PICKUP

performance:
  # Cache settings
  cache:
    lore-entries: 1000
    player-data: 500
    location-data: 200
    ttl: 3600 # seconds
    
  # Async operation settings
  async:
    core-pool-size: 2
    maximum-pool-size: 4
    keep-alive-time: 60
    
  # Batch operation settings
  batch:
    size: 100
    interval: 5000 # milliseconds

worlds:
  # Default world settings
  default:
    enabled: true
    max-lore-locations: 100
    allow-creation: true
    allow-deletion: false
    
  # Per-world overrides
  overrides:
    world_nether:
      enabled: false
    world_the_end:
      max-lore-locations: 25
      allow-creation: false

integrations:
  # PlaceholderAPI integration
  placeholderapi:
    enabled: true
    
  # Vault integration
  vault:
    enabled: true
    
  # WorldGuard integration
  worldguard:
    enabled: true
    respect-regions: true
    
  # Citizens integration
  citizens:
    enabled: false
    auto-create-npcs: false

# Version tracking (do not modify)
config-version: 3
```

### Messages Configuration (messages.yml)

```yaml
# RVNKLore Messages Configuration
# Use & for color codes
# Available placeholders: {player}, {lore}, {location}, {count}, etc.

messages:
  # Command messages
  commands:
    no-permission: "&c✖ You don't have permission to use this command"
    player-only: "&c✖ This command can only be used by players"
    invalid-usage: "&c▶ Usage: {usage}"
    player-not-found: "&c✖ Player not found: {player}"
    
  # Lore discovery messages
  discovery:
    found: "&a✓ You discovered: &e{lore}"
    already-discovered: "&e⚠ You have already discovered this lore"
    nearby-announcement: "&7{player} discovered &e{lore}&7 nearby!"
    progress: "&6⚙ Discovery progress: &f{current}/{total}"
    
  # Lore creation messages
  creation:
    started: "&6⚙ Creating new lore: &e{title}"
    content-prompt: "&7Type the lore content in chat"
    content-instructions: "&7Type 'done' to finish or 'cancel' to abort"
    success: "&a✓ Lore created successfully: &e{lore}"
    cancelled: "&e⚠ Lore creation cancelled"
    cooldown: "&c✖ You must wait {time} before creating more lore"
    
  # Error messages
  errors:
    database-error: "&c✖ Database error occurred. Please try again later"
    file-error: "&c✖ File operation failed"
    invalid-location: "&c✖ Invalid location specified"
    lore-not-found: "&c✖ Lore not found: {lore}"
    world-not-found: "&c✖ World not found: {world}"
    
  # Admin messages
  admin:
    reload-success: "&a✓ Configuration reloaded successfully"
    reload-error: "&c✖ Error reloading configuration"
    stats-header: "&6⚙ RVNKLore Statistics"
    migration-started: "&6⚙ Starting database migration..."
    migration-completed: "&a✓ Database migration completed"
    
  # Format templates
  formats:
    lore-title: "&6&l{title}"
    lore-author: "&7by &e{author}"
    lore-date: "&7Created: &f{date}"
    lore-location: "&7Location: &f{location}"
    page-header: "&6--- Page {page}/{total} ---"
    
# Version tracking (do not modify)
messages-version: 2
```

## Configuration Loading

### File Operations

```java
public class ConfigFileManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    
    public ConfigFileManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
    }
    
    public FileConfiguration loadConfig(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        
        // Create default file if it doesn't exist
        if (!configFile.exists()) {
            plugin.saveResource(fileName, false);
            logger.info("Created default configuration file: " + fileName);
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        // Load defaults from resources
        try (InputStream defaultStream = plugin.getResource(fileName)) {
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                config.setDefaults(defaultConfig);
            }
        } catch (IOException e) {
            logger.warning("Could not load default configuration for: " + fileName);
        }
        
        return config;
    }
    
    public void saveConfig(FileConfiguration config, String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        
        try {
            config.save(configFile);
            logger.info("Saved configuration file: " + fileName);
            
        } catch (IOException e) {
            logger.error("Could not save configuration file: " + fileName, e);
            throw new RuntimeException("Failed to save configuration", e);
        }
    }
    
    public void createBackup(String fileName) {
        File originalFile = new File(plugin.getDataFolder(), fileName);
        if (!originalFile.exists()) return;
        
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File backupFile = new File(plugin.getDataFolder(), 
            "backups/" + fileName + "." + timestamp + ".bak");
        
        try {
            // Create backup directory
            File backupDir = backupFile.getParentFile();
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            
            // Copy file
            Files.copy(originalFile.toPath(), backupFile.toPath(), 
                StandardCopyOption.REPLACE_EXISTING);
            
            logger.info("Created configuration backup: " + backupFile.getName());
            
        } catch (IOException e) {
            logger.warning("Could not create configuration backup for: " + fileName);
        }
    }
}
```

### Configuration Caching

```java
public class ConfigCache {
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private final long defaultTTL = 300_000; // 5 minutes
    
    public <T> T get(String key, Class<T> type) {
        // Check if cached value is still valid
        Long timestamp = cacheTimestamps.get(key);
        if (timestamp != null && (System.currentTimeMillis() - timestamp) < defaultTTL) {
            Object value = cache.get(key);
            if (type.isInstance(value)) {
                return type.cast(value);
            }
        }
        
        return null;
    }
    
    public void put(String key, Object value) {
        cache.put(key, value);
        cacheTimestamps.put(key, System.currentTimeMillis());
    }
    
    public void remove(String key) {
        cache.remove(key);
        cacheTimestamps.remove(key);
    }
    
    public void clear() {
        cache.clear();
        cacheTimestamps.clear();
    }
    
    public void cleanupExpired() {
        long currentTime = System.currentTimeMillis();
        
        cacheTimestamps.entrySet().removeIf(entry -> {
            if ((currentTime - entry.getValue()) > defaultTTL) {
                cache.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
}
```

## Dynamic Configuration

### Live Configuration Updates

```java
public class DynamicConfigManager {
    private final Map<String, ConfigListener> listeners = new HashMap<>();
    private final ScheduledExecutorService watcherService;
    
    public DynamicConfigManager() {
        this.watcherService = Executors.newSingleThreadScheduledExecutor();
        startConfigWatcher();
    }
    
    public void registerListener(String configKey, ConfigListener listener) {
        listeners.put(configKey, listener);
    }
    
    public void updateConfig(String key, Object value) {
        Object oldValue = ConfigManager.getValue(key);
        ConfigManager.setValue(key, value);
        
        // Notify listeners
        ConfigListener listener = listeners.get(key);
        if (listener != null) {
            listener.onConfigChanged(key, oldValue, value);
        }
        
        // Save to file
        saveConfigurationAsync();
    }
    
    private void startConfigWatcher() {
        watcherService.scheduleAtFixedRate(() -> {
            try {
                checkForConfigChanges();
            } catch (Exception e) {
                logger.warning("Error checking for config changes: " + e.getMessage());
            }
        }, 10, 10, TimeUnit.SECONDS);
    }
    
    private void checkForConfigChanges() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        long lastModified = configFile.lastModified();
        
        if (lastModified > lastConfigCheck) {
            logger.info("Configuration file changed, reloading...");
            reloadConfiguration();
            lastConfigCheck = System.currentTimeMillis();
        }
    }
    
    @FunctionalInterface
    public interface ConfigListener {
        void onConfigChanged(String key, Object oldValue, Object newValue);
    }
}

// Example configuration listeners
public class LoreConfigListener implements DynamicConfigManager.ConfigListener {
    
    @Override
    public void onConfigChanged(String key, Object oldValue, Object newValue) {
        switch (key) {
            case "lore.discovery.radius":
                LoreManager.updateDiscoveryRadius((Double) newValue);
                break;
                
            case "lore.max-per-player":
                LoreManager.updateMaxLorePerPlayer((Integer) newValue);
                break;
                
            case "lore.effects.particles.enabled":
                LoreManager.updateParticleEffects((Boolean) newValue);
                break;
        }
    }
}
```

### Runtime Configuration API

```java
public class RuntimeConfigAPI {
    
    public static void setConfigValue(String path, Object value) {
        // Update in-memory configuration
        ConfigManager.setValue(path, value);
        
        // Update file configuration
        FileConfiguration config = ConfigManager.getConfig();
        config.set(path, value);
        
        // Save to disk
        ConfigManager.saveConfig();
        
        // Notify components of change
        ConfigChangeEvent event = new ConfigChangeEvent(path, value);
        Bukkit.getPluginManager().callEvent(event);
    }
    
    public static <T> T getConfigValue(String path, Class<T> type, T defaultValue) {
        Object value = ConfigManager.getValue(path);
        
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        
        return defaultValue;
    }
    
    public static void resetToDefault(String path) {
        FileConfiguration defaults = ConfigManager.getDefaultConfig();
        Object defaultValue = defaults.get(path);
        
        if (defaultValue != null) {
            setConfigValue(path, defaultValue);
        }
    }
    
    public static Map<String, Object> getConfigSection(String sectionPath) {
        ConfigurationSection section = ConfigManager.getConfig().getConfigurationSection(sectionPath);
        
        if (section == null) {
            return new HashMap<>();
        }
        
        return section.getValues(false);
    }
}
```

## Configuration Validation

### Validation Framework

```java
public class ConfigValidator {
    private final List<ValidationRule> rules = new ArrayList<>();
    private final LogManager logger;
    
    public ConfigValidator() {
        this.logger = LogManager.getInstance(RVNKLore.getInstance());
        setupValidationRules();
    }
    
    private void setupValidationRules() {
        // Database validation
        rules.add(new ValidationRule("database.type", 
            value -> Arrays.asList("sqlite", "mysql", "postgresql").contains(value),
            "Database type must be one of: sqlite, mysql, postgresql"));
            
        // Numeric range validations
        rules.add(new ValidationRule("lore.max-per-player",
            value -> isInteger(value) && (Integer) value > 0 && (Integer) value <= 1000,
            "Max lore per player must be between 1 and 1000"));
            
        rules.add(new ValidationRule("lore.discovery.radius",
            value -> isDouble(value) && (Double) value > 0 && (Double) value <= 100,
            "Discovery radius must be between 0.1 and 100.0"));
            
        // String validations
        rules.add(new ValidationRule("general.language",
            value -> Arrays.asList("en", "es", "fr", "de").contains(value),
            "Language must be one of: en, es, fr, de"));
            
        // Boolean validations
        rules.add(new ValidationRule("general.debug-mode",
            value -> value instanceof Boolean,
            "Debug mode must be true or false"));
    }
    
    public ValidationResult validate(FileConfiguration config) {
        List<ValidationError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        for (ValidationRule rule : rules) {
            Object value = config.get(rule.getPath());
            
            if (value == null) {
                warnings.add("Missing configuration value: " + rule.getPath());
                continue;
            }
            
            if (!rule.validate(value)) {
                errors.add(new ValidationError(rule.getPath(), value, rule.getErrorMessage()));
            }
        }
        
        return new ValidationResult(errors, warnings);
    }
    
    private boolean isInteger(Object value) {
        return value instanceof Integer || value instanceof Long;
    }
    
    private boolean isDouble(Object value) {
        return value instanceof Double || value instanceof Float || isInteger(value);
    }
}

public class ValidationRule {
    private final String path;
    private final Predicate<Object> validator;
    private final String errorMessage;
    
    public ValidationRule(String path, Predicate<Object> validator, String errorMessage) {
        this.path = path;
        this.validator = validator;
        this.errorMessage = errorMessage;
    }
    
    public boolean validate(Object value) {
        return validator.test(value);
    }
    
    // Getters...
}

public class ValidationResult {
    private final List<ValidationError> errors;
    private final List<String> warnings;
    
    public ValidationResult(List<ValidationError> errors, List<String> warnings) {
        this.errors = errors;
        this.warnings = warnings;
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    // Getters...
}
```

### Configuration Sanitization

```java
public class ConfigSanitizer {
    
    public static FileConfiguration sanitize(FileConfiguration config) {
        // Remove potentially dangerous paths
        removeDangerousPaths(config);
        
        // Sanitize string values
        sanitizeStringValues(config);
        
        // Validate and correct numeric ranges
        correctNumericRanges(config);
        
        // Ensure required sections exist
        ensureRequiredSections(config);
        
        return config;
    }
    
    private static void removeDangerousPaths(FileConfiguration config) {
        List<String> dangerousPaths = Arrays.asList(
            "database.mysql.password", // Should not be in plain text
            "integrations.api-keys",   // Remove API keys if present
            "system.file-paths"        // Remove system paths
        );
        
        for (String path : dangerousPaths) {
            if (config.contains(path)) {
                config.set(path, null);
            }
        }
    }
    
    private static void sanitizeStringValues(FileConfiguration config) {
        for (String key : config.getKeys(true)) {
            Object value = config.get(key);
            
            if (value instanceof String) {
                String stringValue = (String) value;
                
                // Remove potential script injection
                stringValue = stringValue.replaceAll("<script[^>]*>.*?</script>", "");
                
                // Limit length
                if (stringValue.length() > 1000) {
                    stringValue = stringValue.substring(0, 1000) + "...";
                }
                
                config.set(key, stringValue);
            }
        }
    }
    
    private static void correctNumericRanges(FileConfiguration config) {
        Map<String, NumericRange> ranges = Map.of(
            "lore.max-per-player", new NumericRange(1, 1000, 50),
            "lore.discovery.radius", new NumericRange(0.1, 100.0, 3.0),
            "performance.cache.lore-entries", new NumericRange(100, 10000, 1000),
            "general.auto-save-interval", new NumericRange(1, 60, 15)
        );
        
        for (Map.Entry<String, NumericRange> entry : ranges.entrySet()) {
            String path = entry.getKey();
            NumericRange range = entry.getValue();
            
            if (config.contains(path)) {
                Object value = config.get(path);
                if (value instanceof Number) {
                    double numValue = ((Number) value).doubleValue();
                    
                    if (numValue < range.min || numValue > range.max) {
                        config.set(path, range.defaultValue);
                    }
                }
            }
        }
    }
    
    private static class NumericRange {
        final double min, max, defaultValue;
        
        NumericRange(double min, double max, double defaultValue) {
            this.min = min;
            this.max = max;
            this.defaultValue = defaultValue;
        }
    }
}
```

## Migration & Versioning

### Configuration Migration

```java
public class ConfigMigrator {
    private final LogManager logger;
    private final Map<Integer, MigrationStep> migrations;
    
    public ConfigMigrator() {
        this.logger = LogManager.getInstance(RVNKLore.getInstance());
        this.migrations = new HashMap<>();
        setupMigrations();
    }
    
    private void setupMigrations() {
        // Migration from version 1 to 2
        migrations.put(2, new MigrationStep() {
            @Override
            public void migrate(FileConfiguration config) {
                // Move old database settings
                if (config.contains("database-file")) {
                    config.set("database.sqlite.file", config.get("database-file"));
                    config.set("database-file", null);
                }
                
                // Add new performance section
                if (!config.contains("performance")) {
                    config.set("performance.cache.lore-entries", 1000);
                    config.set("performance.cache.player-data", 500);
                }
            }
        });
        
        // Migration from version 2 to 3
        migrations.put(3, new MigrationStep() {
            @Override
            public void migrate(FileConfiguration config) {
                // Update world settings structure
                if (config.contains("worlds")) {
                    ConfigurationSection worldsSection = config.getConfigurationSection("worlds");
                    if (worldsSection != null) {
                        migrateWorldSettings(worldsSection);
                    }
                }
                
                // Add integration settings
                config.set("integrations.placeholderapi.enabled", true);
                config.set("integrations.vault.enabled", true);
            }
        });
    }
    
    public boolean needsMigration(FileConfiguration config) {
        int currentVersion = config.getInt("config-version", 1);
        int latestVersion = getLatestVersion();
        
        return currentVersion < latestVersion;
    }
    
    public void migrate(FileConfiguration config) {
        int currentVersion = config.getInt("config-version", 1);
        int latestVersion = getLatestVersion();
        
        if (currentVersion >= latestVersion) {
            return; // No migration needed
        }
        
        logger.info("Migrating configuration from version " + currentVersion + " to " + latestVersion);
        
        // Create backup before migration
        createMigrationBackup(config);
        
        // Apply migrations sequentially
        for (int version = currentVersion + 1; version <= latestVersion; version++) {
            MigrationStep migration = migrations.get(version);
            if (migration != null) {
                logger.info("Applying migration step: " + version);
                migration.migrate(config);
            }
        }
        
        // Update version number
        config.set("config-version", latestVersion);
        
        logger.info("Configuration migration completed");
    }
    
    private void createMigrationBackup(FileConfiguration config) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String backupName = "config_pre_migration_" + timestamp + ".yml";
        
        try {
            File backupFile = new File(RVNKLore.getInstance().getDataFolder(), "backups/" + backupName);
            File backupDir = backupFile.getParentFile();
            
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            
            config.save(backupFile);
            logger.info("Created migration backup: " + backupName);
            
        } catch (IOException e) {
            logger.warning("Could not create migration backup: " + e.getMessage());
        }
    }
    
    private int getLatestVersion() {
        return migrations.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
    }
    
    @FunctionalInterface
    private interface MigrationStep {
        void migrate(FileConfiguration config);
    }
}
```

### Version Compatibility

```java
public class VersionCompatibility {
    private static final Map<String, String> COMPATIBILITY_MAP = Map.of(
        "1.20", "config-v1.yml",
        "1.21", "config-v2.yml",
        "1.21.4", "config-v3.yml"
    );
    
    public static String getCompatibleConfigVersion(String minecraftVersion) {
        // Find the best matching config version
        for (Map.Entry<String, String> entry : COMPATIBILITY_MAP.entrySet()) {
            if (minecraftVersion.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // Default to latest version
        return "config-v3.yml";
    }
    
    public static boolean isVersionSupported(String minecraftVersion) {
        return COMPATIBILITY_MAP.keySet().stream()
            .anyMatch(minecraftVersion::startsWith);
    }
    
    public static void checkCompatibility() {
        String mcVersion = Bukkit.getVersion();
        
        if (!isVersionSupported(mcVersion)) {
            LogManager.getInstance(RVNKLore.getInstance())
                .warning("Minecraft version " + mcVersion + " may not be fully supported");
        }
    }
}
```

## Environment-Specific Configs

### Environment Detection

```java
public class EnvironmentManager {
    public enum Environment {
        DEVELOPMENT,
        TESTING,
        STAGING,
        PRODUCTION
    }
    
    private static Environment currentEnvironment;
    
    public static Environment detectEnvironment() {
        // Check system properties
        String envProperty = System.getProperty("rvnklore.environment");
        if (envProperty != null) {
            try {
                return Environment.valueOf(envProperty.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid environment specified
            }
        }
        
        // Check environment variables
        String envVar = System.getenv("RVNKLORE_ENV");
        if (envVar != null) {
            try {
                return Environment.valueOf(envVar.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid environment specified
            }
        }
        
        // Detect based on server characteristics
        if (isDebugMode() || isLocalhost()) {
            return Environment.DEVELOPMENT;
        }
        
        // Default to production
        return Environment.PRODUCTION;
    }
    
    private static boolean isDebugMode() {
        return Boolean.getBoolean("debug") || 
               System.getProperty("java.vm.name", "").toLowerCase().contains("debug");
    }
    
    private static boolean isLocalhost() {
        try {
            String serverIP = Bukkit.getServer().getIp();
            return serverIP.isEmpty() || 
                   serverIP.equals("127.0.0.1") || 
                   serverIP.equals("localhost");
        } catch (Exception e) {
            return false;
        }
    }
    
    public static Environment getCurrentEnvironment() {
        if (currentEnvironment == null) {
            currentEnvironment = detectEnvironment();
        }
        return currentEnvironment;
    }
}
```

### Environment-Specific Configuration Loading

```java
public class EnvironmentConfigLoader {
    
    public static FileConfiguration loadEnvironmentConfig() {
        Environment env = EnvironmentManager.getCurrentEnvironment();
        String configFileName = getConfigFileName(env);
        
        File configFile = new File(RVNKLore.getInstance().getDataFolder(), configFileName);
        
        if (!configFile.exists()) {
            // Create environment-specific config from template
            createEnvironmentConfig(env, configFile);
        }
        
        return YamlConfiguration.loadConfiguration(configFile);
    }
    
    private static String getConfigFileName(Environment env) {
        switch (env) {
            case DEVELOPMENT:
                return "config-dev.yml";
            case TESTING:
                return "config-test.yml";
            case STAGING:
                return "config-staging.yml";
            case PRODUCTION:
            default:
                return "config.yml";
        }
    }
    
    private static void createEnvironmentConfig(Environment env, File configFile) {
        // Load base configuration
        FileConfiguration baseConfig = ConfigManager.getDefaultConfig();
        
        // Apply environment-specific overrides
        applyEnvironmentOverrides(baseConfig, env);
        
        // Save environment-specific config
        try {
            baseConfig.save(configFile);
            LogManager.getInstance(RVNKLore.getInstance())
                .info("Created environment-specific config: " + configFile.getName());
                
        } catch (IOException e) {
            LogManager.getInstance(RVNKLore.getInstance())
                .error("Failed to create environment config", e);
        }
    }
    
    private static void applyEnvironmentOverrides(FileConfiguration config, Environment env) {
        switch (env) {
            case DEVELOPMENT:
                config.set("general.debug-mode", true);
                config.set("database.type", "sqlite");
                config.set("lore.creation.cooldown", 10); // Reduced cooldown
                config.set("performance.cache.ttl", 60); // Shorter cache TTL
                break;
                
            case TESTING:
                config.set("general.debug-mode", true);
                config.set("database.type", "sqlite");
                config.set("database.sqlite.file", "test_lore_data.db");
                config.set("lore.max-per-player", 10); // Limited for testing
                break;
                
            case STAGING:
                config.set("general.debug-mode", false);
                config.set("database.type", "mysql");
                config.set("general.check-updates", false);
                break;
                
            case PRODUCTION:
                config.set("general.debug-mode", false);
                config.set("database.type", "mysql");
                config.set("performance.cache.ttl", 3600);
                config.set("lore.creation.cooldown", 300);
                break;
        }
    }
}
```

## Performance Optimization

### Configuration Caching Strategies

```java
public class OptimizedConfigManager {
    private final Map<String, ConfigEntry> configCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cacheCleanupService;
    
    public OptimizedConfigManager() {
        this.cacheCleanupService = Executors.newSingleThreadScheduledExecutor();
        startCacheCleanup();
    }
    
    public <T> T getConfigValue(String path, Class<T> type, T defaultValue) {
        ConfigEntry entry = configCache.get(path);
        
        if (entry != null && !entry.isExpired()) {
            if (type.isInstance(entry.getValue())) {
                return type.cast(entry.getValue());
            }
        }
        
        // Load from file and cache
        T value = loadConfigValue(path, type, defaultValue);
        cacheConfigValue(path, value);
        
        return value;
    }
    
    private void cacheConfigValue(String path, Object value) {
        long expirationTime = System.currentTimeMillis() + getCacheTTL(path);
        configCache.put(path, new ConfigEntry(value, expirationTime));
    }
    
    private long getCacheTTL(String path) {
        // Different TTL for different types of config
        if (path.startsWith("performance.")) {
            return 60_000; // 1 minute for performance settings
        } else if (path.startsWith("database.")) {
            return 300_000; // 5 minutes for database settings
        } else {
            return 180_000; // 3 minutes for general settings
        }
    }
    
    private void startCacheCleanup() {
        cacheCleanupService.scheduleAtFixedRate(() -> {
            configCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    private static class ConfigEntry {
        private final Object value;
        private final long expirationTime;
        
        public ConfigEntry(Object value, long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
        
        public Object getValue() {
            return value;
        }
    }
}
```

### Lazy Configuration Loading

```java
public class LazyConfigLoader {
    private final Map<String, Supplier<FileConfiguration>> configSuppliers = new HashMap<>();
    private final Map<String, FileConfiguration> loadedConfigs = new HashMap<>();
    
    public void registerConfig(String name, Supplier<FileConfiguration> supplier) {
        configSuppliers.put(name, supplier);
    }
    
    public FileConfiguration getConfig(String name) {
        return loadedConfigs.computeIfAbsent(name, key -> {
            Supplier<FileConfiguration> supplier = configSuppliers.get(key);
            if (supplier != null) {
                return supplier.get();
            }
            throw new IllegalArgumentException("Unknown config: " + key);
        });
    }
    
    public void unloadConfig(String name) {
        loadedConfigs.remove(name);
    }
    
    public void unloadAllConfigs() {
        loadedConfigs.clear();
    }
}
```

## RVNKLore Integration

### Configuration Events

```java
public class ConfigChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String configPath;
    private final Object oldValue;
    private final Object newValue;
    
    public ConfigChangeEvent(String configPath, Object oldValue, Object newValue) {
        this.configPath = configPath;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
    
    // Getters and HandlerList implementation...
}

// Event listener in LoreManager
@EventHandler
public void onConfigChange(ConfigChangeEvent event) {
    String path = event.getConfigPath();
    Object newValue = event.getNewValue();
    
    switch (path) {
        case "lore.discovery.radius":
            updateDiscoveryRadius((Double) newValue);
            break;
            
        case "lore.effects.particles.enabled":
            updateParticleEffects((Boolean) newValue);
            break;
            
        case "lore.max-per-player":
            updatePlayerLimits((Integer) newValue);
            break;
    }
}
```

### Integration with Existing Systems

```java
// Integration with DatabaseManager
public class DatabaseConfigIntegration {
    
    public static void updateDatabaseSettings() {
        String dbType = ConfigManager.getString("database.type", "sqlite");
        
        if ("mysql".equals(dbType)) {
            String host = ConfigManager.getString("database.mysql.host");
            int port = ConfigManager.getInt("database.mysql.port", 3306);
            String database = ConfigManager.getString("database.mysql.database");
            
            DatabaseManager.getInstance().updateConnectionSettings(host, port, database);
        }
    }
}

// Integration with ItemManager
public class ItemConfigIntegration {
    
    public static void updateItemSettings() {
        boolean customModelData = ConfigManager.getBoolean("items.custom-model-data.enabled", true);
        int baseModelData = ConfigManager.getInt("items.custom-model-data.base", 1000);
        
        ModelDataManager.getInstance().updateSettings(customModelData, baseModelData);
    }
}

// Integration with ConfigManager in main plugin class
public class RVNKLore extends JavaPlugin {
    
    @Override
    public void onEnable() {
        // Initialize configuration system
        ConfigManager.initialize(this);
        
        // Register configuration listeners
        registerConfigListeners();
        
        // Apply initial configuration
        applyConfiguration();
    }
    
    private void registerConfigListeners() {
        DynamicConfigManager configManager = new DynamicConfigManager();
        
        // Register listeners for different components
        configManager.registerListener("lore.*", new LoreConfigListener());
        configManager.registerListener("database.*", new DatabaseConfigListener());
        configManager.registerListener("performance.*", new PerformanceConfigListener());
    }
    
    private void applyConfiguration() {
        // Apply database configuration
        DatabaseConfigIntegration.updateDatabaseSettings();
        
        // Apply item configuration
        ItemConfigIntegration.updateItemSettings();
        
        // Apply lore configuration
        LoreManager.applyConfiguration();
    }
}
```

## Best Practices

### 1. **Configuration Structure**
- Use hierarchical organization with logical sections
- Provide comprehensive comments and examples
- Include version tracking for migration support
- Separate sensitive data from main configuration

### 2. **Validation & Security**
- Always validate configuration values
- Sanitize user input and file content
- Implement proper error handling for invalid configs
- Create configuration backups before changes

### 3. **Performance**
- Use caching for frequently accessed values
- Implement lazy loading for large configurations
- Monitor configuration reload impact
- Use efficient data structures for config storage

### 4. **Maintenance**
- Implement proper migration strategies
- Support environment-specific configurations
- Provide clear documentation for all settings
- Monitor configuration changes and their effects

This Configuration API reference provides comprehensive patterns for implementing robust configuration management in the RVNKLore plugin, ensuring maintainability, performance, and security while supporting the plugin's complex requirements.

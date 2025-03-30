package org.fourz.RVNKLore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.fourz.RVNKLore.RVNKLore;
import java.util.logging.Level;

public class ConfigManager {
    private final RVNKLore plugin;
    private FileConfiguration config;

    public ConfigManager(RVNKLore plugin) {
        this.plugin = plugin;
        loadConfig();
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
        
        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    // Called after Debug is initialized
    public void initDebugLogging() {
        plugin.getDebugger().debug("Configuration system initialized");
    }

    public Level getLogLevel() {
        String level = config.getString("general.logLevel", "INFO");
        if (level.equalsIgnoreCase("DEBUG")) {
            return Level.FINE;
        }
        return Level.parse(level.toUpperCase());
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
    }
}

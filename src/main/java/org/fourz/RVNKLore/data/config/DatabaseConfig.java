package org.fourz.RVNKLore.data.config;

import org.bukkit.configuration.ConfigurationSection;
import org.fourz.RVNKLore.config.ConfigManager;
import org.fourz.RVNKLore.data.DatabaseType;

/**
 * Provides database configuration by reading from the loaded ConfigManager.
 * This class does not load config from file directly.
 */
public class DatabaseConfig {
    private final DatabaseType type;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useSSL;

    /**
     * Constructs a DatabaseConfig using the already loaded ConfigManager.
     * @param configManager The plugin's ConfigManager
     */
    public DatabaseConfig(ConfigManager configManager) {
        // Get the storage section from the loaded config
        ConfigurationSection storage = configManager.getConfig().getConfigurationSection("storage");
        String typeStr = storage.getString("type", "sqlite").toLowerCase();
        this.type = typeStr.equals("mysql") ? DatabaseType.MYSQL : DatabaseType.SQLITE;

        if (this.type == DatabaseType.MYSQL) {
            ConfigurationSection mysqlConfig = storage.getConfigurationSection("mysql");
            this.host = mysqlConfig.getString("host", "localhost");
            this.port = mysqlConfig.getInt("port", 3306);
            this.database = mysqlConfig.getString("database", "rvnklore");
            this.username = mysqlConfig.getString("username", "root");
            this.password = mysqlConfig.getString("password", "");
            this.useSSL = mysqlConfig.getBoolean("useSSL", false);
        } else {
            ConfigurationSection sqliteConfig = storage.getConfigurationSection("sqlite");
            this.database = sqliteConfig.getString("database", "data.db");
            this.host = null;
            this.port = -1;
            this.username = null;
            this.password = null;
            this.useSSL = false;
        }
    }

    public DatabaseType getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isUseSSL() {
        return useSSL;
    }
}

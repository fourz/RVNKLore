package org.fourz.RVNKLore.data.config;

import org.bukkit.configuration.ConfigurationSection;
import org.fourz.RVNKLore.data.DatabaseType;

public class DatabaseConfig {
    private final DatabaseType type;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useSSL;

    public DatabaseConfig(ConfigurationSection config) {
        String typeStr = config.getString("type", "sqlite").toLowerCase();
        this.type = typeStr.equals("mysql") ? DatabaseType.MYSQL : DatabaseType.SQLITE;
        
        if (this.type == DatabaseType.MYSQL) {
            ConfigurationSection mysqlConfig = config.getConfigurationSection("mysql");
            this.host = mysqlConfig.getString("host", "localhost");
            this.port = mysqlConfig.getInt("port", 3306);
            this.database = mysqlConfig.getString("database", "rvnklore");
            this.username = mysqlConfig.getString("username", "root");
            this.password = mysqlConfig.getString("password", "");
            this.useSSL = mysqlConfig.getBoolean("useSSL", false);
        } else {
            ConfigurationSection sqliteConfig = config.getConfigurationSection("sqlite");
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

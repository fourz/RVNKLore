package org.fourz.RVNKLore.config.dto;

/**
 * DTO for general database configuration settings.
 * Encapsulates MySQL and SQLite settings and general database options.
 */
public class DatabaseSettingsDTO {
    public enum DatabaseType { MYSQL, SQLITE }

    private final DatabaseType type;
    private final MySQLSettingsDTO mysqlSettings;
    private final SQLiteSettingsDTO sqliteSettings;
    private final int connectionTimeout;
    private final int maxRetries;

    public DatabaseSettingsDTO(DatabaseType type, MySQLSettingsDTO mysqlSettings, SQLiteSettingsDTO sqliteSettings, int connectionTimeout, int maxRetries) {
        this.type = type;
        this.mysqlSettings = mysqlSettings;
        this.sqliteSettings = sqliteSettings;
        this.connectionTimeout = connectionTimeout;
        this.maxRetries = maxRetries;
    }

    public DatabaseType getType() { return type; }
    public MySQLSettingsDTO getMysqlSettings() { return mysqlSettings; }
    public SQLiteSettingsDTO getSqliteSettings() { return sqliteSettings; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public int getMaxRetries() { return maxRetries; }

    /**
     * Validates the configuration. Throws IllegalArgumentException if invalid.
     */
    public void validate() {
        if (type == null) throw new IllegalArgumentException("Database type must be specified");
        if (type == DatabaseType.MYSQL && mysqlSettings == null) throw new IllegalArgumentException("MySQL settings required");
        if (type == DatabaseType.SQLITE && sqliteSettings == null) throw new IllegalArgumentException("SQLite settings required");
        if (connectionTimeout < 0) throw new IllegalArgumentException("Connection timeout must be non-negative");
        if (maxRetries < 0) throw new IllegalArgumentException("Max retries must be non-negative");
    }
}

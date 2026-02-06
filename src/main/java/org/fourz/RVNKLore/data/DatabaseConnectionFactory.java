package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.dialect.MySQLDialect;
import org.fourz.RVNKLore.data.dialect.SQLDialect;
import org.fourz.RVNKLore.data.dialect.SQLiteDialect;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Factory for creating database connections with appropriate SQL dialects.
 *
 * <p>The factory determines the database type from configuration and creates:
 * <ul>
 *   <li>The appropriate DatabaseConnection subclass (MySQL or SQLite)</li>
 *   <li>The matching SQLDialect for database-specific SQL generation</li>
 * </ul>
 *
 * <p>Supports automatic fallback to SQLite when MySQL connection fails.</p>
 */
public class DatabaseConnectionFactory {
    private final RVNKLore plugin;
    private final LogManager logger;
    private SQLDialect dialect;
    private boolean usingFallback = false;

    public DatabaseConnectionFactory(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DatabaseConnectionFactory");
    }

    /**
     * Create a database connection based on configuration.
     * Also initializes the appropriate SQL dialect.
     *
     * @return DatabaseConnection configured for the storage type
     */
    public DatabaseConnection createConnection() {
        String storageType = plugin.getConfigManager().getStorageType();
        logger.debug("Creating database connection for storage type: " + storageType);

        if (storageType.equalsIgnoreCase("mysql")) {
            this.dialect = new MySQLDialect();
            logger.debug("Using MySQL dialect");
            this.usingFallback = false;
            return new MySQLConnection(plugin, dialect);
        } else {
            this.dialect = new SQLiteDialect();
            logger.debug("Using SQLite dialect");
            this.usingFallback = false;
            return new SQLiteConnection(plugin, dialect);
        }
    }

    /**
     * Create a SQLite fallback connection when primary (MySQL) connection fails.
     * This is used for graceful degradation when MySQL is unavailable.
     *
     * @return SQLiteConnection for fallback storage
     */
    public DatabaseConnection createFallbackConnection() {
        logger.warning("Creating SQLite fallback connection");
        this.dialect = new SQLiteDialect();
        this.usingFallback = true;
        return new SQLiteConnection(plugin, dialect);
    }

    /**
     * Check if fallback is enabled in configuration.
     *
     * @return true if fallback to SQLite is enabled
     */
    public boolean isFallbackEnabled() {
        return plugin.getConfig().getBoolean("storage.fallback.enabled", true);
    }

    /**
     * Check if currently using a fallback connection.
     *
     * @return true if the current connection is a fallback
     */
    public boolean isUsingFallback() {
        return usingFallback;
    }

    /**
     * Get the SQL dialect for the current connection type.
     * Must be called after createConnection().
     *
     * @return The SQLDialect instance, or null if no connection created yet
     */
    public SQLDialect getDialect() {
        return dialect;
    }
}

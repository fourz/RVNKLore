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
 */
public class DatabaseConnectionFactory {
    private final RVNKLore plugin;
    private final LogManager logger;
    private SQLDialect dialect;

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
            return new MySQLConnection(plugin, dialect);
        } else {
            this.dialect = new SQLiteDialect();
            logger.debug("Using SQLite dialect");
            return new SQLiteConnection(plugin, dialect);
        }
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

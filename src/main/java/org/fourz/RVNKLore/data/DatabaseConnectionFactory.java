package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.config.dto.DatabaseSettingsDTO;
import org.fourz.rvnkcore.config.dto.MySQLSettingsDTO;
import org.fourz.rvnkcore.config.dto.SQLiteSettingsDTO;
import org.fourz.RVNKLore.data.dialect.MySQLDialect;
import org.fourz.RVNKLore.data.dialect.SQLDialect;
import org.fourz.RVNKLore.data.dialect.SQLiteDialect;
import org.fourz.rvnkcore.util.log.LogManager;
import java.io.File;

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
        DatabaseSettingsDTO settings = plugin.getConfigManager().getDatabaseSettings();
        logger.debug("Creating database connection for storage type: " + settings.getType());

        if (settings.getType() == DatabaseSettingsDTO.DatabaseType.MYSQL) {
            this.dialect = new MySQLDialect();
            logger.debug("Using MySQL dialect - will reuse RVNKCore shared pool");
            this.usingFallback = false;
            return new MySQLConnection(plugin, dialect);
        } else {
            this.dialect = new SQLiteDialect();
            logger.debug("Using SQLite dialect");
            this.usingFallback = false;
            return new SQLiteConnection(plugin, dialect, settings.getSqliteSettings());
        }
    }

    /**
     * Create the cluster connection for shared lore content (#1834).
     *
     * <p>Reads {@code cluster.mysql.*}. Only a <b>member</b> tier needs this: on the authoritative
     * tier the cluster database is its own database, so {@link DatabaseManager} reuses the primary
     * connection instead of opening a second pool — the same idiom RVNKCore uses.</p>
     *
     * @return a cluster DatabaseConnection, or null when {@code cluster.mysql} is not configured
     */
    public DatabaseConnection createClusterConnection() {
        String host = plugin.getConfig().getString("cluster.mysql.host", null);
        String database = plugin.getConfig().getString("cluster.mysql.database", null);
        if (host == null || host.isBlank() || database == null || database.isBlank()) {
            logger.error("cluster.role is 'member' but cluster.mysql.host/database are not set - "
                    + "shared lore content cannot be reached", null);
            return null;
        }

        MySQLSettingsDTO clusterSettings = new MySQLSettingsDTO(
            host,
            plugin.getConfig().getInt("cluster.mysql.port", 3306),
            database,
            plugin.getConfig().getString("cluster.mysql.username", ""),
            plugin.getConfig().getString("cluster.mysql.password", ""),
            plugin.getConfig().getBoolean("cluster.mysql.useSSL", false),
            plugin.getConfig().getString("cluster.mysql.tablePrefix", "")
        );

        // The cluster is MySQL by definition — a shared pool cannot be a local SQLite file.
        SQLDialect clusterDialect = new MySQLDialect();
        logger.info("Cluster: shared lore content will be served from " + host + "/" + database);
        return new MySQLConnection(plugin, clusterDialect, clusterSettings, "cluster.mysql");
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

        String dbFile = plugin.getConfig().getString("storage.sqlite.database", "lore.db");
        String filePath = new File(plugin.getDataFolder(), dbFile).getAbsolutePath();
        SQLiteSettingsDTO fallbackSettings = new SQLiteSettingsDTO(
            filePath,
            plugin.getConfig().getString("storage.sqlite.tablePrefix", "")
        );

        return new SQLiteConnection(plugin, dialect, fallbackSettings);
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

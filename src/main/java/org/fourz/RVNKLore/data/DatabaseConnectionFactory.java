package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Factory for creating database connections
 */
public class DatabaseConnectionFactory {
    private final RVNKLore plugin;
    private final LogManager logger;

    public DatabaseConnectionFactory(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DatabaseConnectionFactory");
    }
    
    /**
     * Create a database connection based on configuration
     */
    public DatabaseConnection createConnection() {
        String storageType = plugin.getConfigManager().getStorageType();
        logger.debug("Creating database connection for storage type: " + storageType);
        
        if (storageType.equalsIgnoreCase("mysql")) {
            return new MySQLConnection(plugin);
        } else {
            return new SQLiteConnection(plugin);
        }
    }
}

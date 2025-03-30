package org.fourz.RVNKLore.database;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.util.Debug;

import java.util.logging.Level;

/**
 * Factory for creating database connections
 */
public class DatabaseConnectionFactory {
    private final RVNKLore plugin;
    private final Debug debug;
    
    public DatabaseConnectionFactory(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "DatabaseConnectionFactory", Level.FINE);
    }
    
    /**
     * Create a database connection based on configuration
     */
    public DatabaseConnection createConnection() {
        String storageType = plugin.getConfigManager().getStorageType();
        debug.debug("Creating database connection for storage type: " + storageType);
        
        if (storageType.equalsIgnoreCase("mysql")) {
            return new MySQLConnection(plugin);
        } else {
            return new SQLiteConnection(plugin);
        }
    }
}

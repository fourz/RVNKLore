package org.fourz.RVNKLore.data.connection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides and manages database connections for the plugin.
 * Implementations should handle pooling, health checks, and shutdown.
 */
public interface ConnectionProvider {
    Connection getConnection() throws SQLException;
    void close() throws SQLException;
    boolean isHealthy();
}

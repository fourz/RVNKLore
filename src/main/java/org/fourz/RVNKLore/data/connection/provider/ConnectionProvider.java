package org.fourz.RVNKLore.data.connection.provider;

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
    /**
     * Validates the current database connection.
     * @return true if the connection is valid, false otherwise
     */
    boolean validateConnection();
}

package org.fourz.RVNKLore.data.connection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides and manages database connections for the plugin.
 * Implementations should handle pooling, health checks, and shutdown.
 */
public interface IConnectionProvider {
    /**
     * Get a database connection from the pool.
     *
     * @return A database connection
     * @throws SQLException if a connection cannot be obtained
     */
    Connection getConnection() throws SQLException;

    /**
     * Close all connections and release resources.
     *
     * @throws SQLException if an error occurs during shutdown
     */
    void close() throws SQLException;

    /**
     * Check if the connection provider is healthy and can provide connections.
     *
     * @return true if healthy, false otherwise
     */
    boolean isHealthy();

    /**
     * Check if the provider is in fallback mode due to connection issues.
     *
     * @return true if in fallback mode, false otherwise
     */
    default boolean isInFallbackMode() {
        return !isHealthy();
    }
}

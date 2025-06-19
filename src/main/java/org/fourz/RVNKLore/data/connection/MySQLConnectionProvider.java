package org.fourz.RVNKLore.data.connection;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.config.dto.MySQLSettingsDTO;
import org.fourz.RVNKLore.debug.LogManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * MySQL implementation of the ConnectionProvider interface.
 * Uses HikariCP for connection pooling and efficient connection management.
 */
public class MySQLConnectionProvider implements ConnectionProvider {
    private final RVNKLore plugin;
    private final LogManager logger;
    private HikariDataSource dataSource;
    private final MySQLSettingsDTO settings;
    private String lastConnectionError;

    /**
     * Create a new MySQL connection provider.
     *
     * @param plugin The RVNKLore plugin instance
     * @param settings The MySQL connection settings
     */
    public MySQLConnectionProvider(RVNKLore plugin, MySQLSettingsDTO settings) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "MySQLConnectionProvider");
        this.settings = settings;
        
        initializeConnectionPool();
    }

    /**
     * Initialize the connection pool with the provided settings.
     */
    private void initializeConnectionPool() {
        try {
            logger.info("Initializing MySQL connection pool...");
            
            // Create Hikari configuration
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&serverTimezone=UTC", 
                settings.getHost(), 
                settings.getPort(), 
                settings.getDatabase(), 
                settings.isUseSSL()));
            config.setUsername(settings.getUsername());
            config.setPassword(settings.getPassword());
            
            // Configure pool settings
            config.setPoolName("RVNKLore-MySQL-Pool");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(5000);
            config.setMaxLifetime(1800000);
            
            // Configure connection testing
            config.setConnectionTestQuery("SELECT 1");
            config.setLeakDetectionThreshold(60000);
            
            // Create data source
            dataSource = new HikariDataSource(config);
            
            // Test connection
            try (Connection conn = dataSource.getConnection()) {
                logger.info("MySQL connection established successfully to " + settings.getHost() + ":" + settings.getPort() + "/" + settings.getDatabase());
            }
            
        } catch (SQLException e) {
            lastConnectionError = e.getMessage();
            logger.error("Failed to initialize MySQL connection pool", e);
            dataSource = null;
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("MySQL connection pool not initialized");
        }
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            lastConnectionError = e.getMessage();
            logger.error("Failed to get MySQL connection from pool", e);
            throw e;
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing MySQL connection pool...");
            dataSource.close();
            dataSource = null;
        }
    }

    @Override
    public boolean isHealthy() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }
        
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2); // Check if connection is valid with 2-second timeout
        } catch (SQLException e) {
            lastConnectionError = e.getMessage();
            logger.error("MySQL connection health check failed", e);
            return false;
        }
    }

    @Override
    public boolean validateConnection() {
        return isHealthy();
    }

    /**
     * Get the last connection error message.
     *
     * @return The last connection error message
     */
    public String getLastConnectionError() {
        return lastConnectionError;
    }
    
    /**
     * Get the current connection pool statistics.
     *
     * @return A string containing the connection pool statistics
     */
    public String getPoolStats() {
        if (dataSource == null) {
            return "Connection pool not initialized";
        }
        
        return String.format(
            "Active: %d, Idle: %d, Total: %d, Waiting: %d",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }
}

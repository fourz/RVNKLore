package org.fourz.RVNKLore.data.connection.provider;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * MySQL implementation of the ConnectionProvider interface.
 * Uses HikariCP for connection pooling.
 */
public class MySQLConnectionProvider implements ConnectionProvider {
    private final RVNKLore plugin;
    private final LogManager logger;
    private HikariDataSource dataSource;
    private String lastConnectionError;
    private final Map<String, Object> settings;

    /**
     * Create a new MySQL connection provider.
     *
     * @param plugin The RVNKLore plugin instance
     */
    public MySQLConnectionProvider(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "MySQLConnectionProvider");
        
        // Get MySQL settings directly from ConfigManager
        this.settings = plugin.getConfigManager().getMySQLSettings();
        
        initializeConnectionPool();
    }

    /**
     * Initialize the connection pool using HikariCP.
     */
    public void initializeConnectionPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing existing connection pool...");
            dataSource.close();
        }

        try {
            logger.info("Initializing MySQL connection pool...");

            HikariConfig config = new HikariConfig();
            config.setDriverClassName("com.mysql.jdbc.Driver");
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s",
                settings.get("host"),
                settings.get("port"),
                settings.get("database")));
            config.setUsername((String) settings.get("username"));
            config.setPassword((String) settings.get("password"));
            config.setMaximumPoolSize((Integer) settings.get("poolSize"));
            config.setConnectionTimeout((Integer) settings.get("connectionTimeout"));
            config.setIdleTimeout((Integer) settings.get("idleTimeout"));
            config.setMaxLifetime((Integer) settings.get("maxLifetime"));
            
            // Add MySQL-specific properties
            config.addDataSourceProperty("useSSL", settings.get("useSSL"));
            config.addDataSourceProperty("characterEncoding", "utf8");
            config.addDataSourceProperty("useUnicode", "true");
            config.addDataSourceProperty("serverTimezone", "UTC");
            config.addDataSourceProperty("tcpKeepAlive", true);
            config.addDataSourceProperty("autoReconnect", true);
            
            dataSource = new HikariDataSource(config);
            
            // Test connection
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
                logger.info("MySQL connection pool initialized successfully");
            }
            
            lastConnectionError = null;
            
        } catch (SQLException e) {
            lastConnectionError = e.getMessage();
            logger.error("Failed to initialize MySQL connection pool", e);
            if (dataSource != null) {
                dataSource.close();
            }
            dataSource = null;
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection pool is not initialized");
        }
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing MySQL connection pool...");
            dataSource.close();
        }
    }

    @Override
    public boolean isHealthy() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            return true;
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
     * Get connection pool statistics.
     *
     * @return A string containing connection pool statistics
     */
    public String getConnectionStats() {
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

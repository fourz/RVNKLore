package org.fourz.RVNKLore.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.dialect.SQLDialect;

import java.sql.*;

/**
 * MySQL implementation of database connection using HikariCP connection pooling.
 *
 * <p>Uses the MySQLDialect for database-specific SQL generation.
 * HikariCP manages connection lifecycle, preventing "connection closed" errors
 * during concurrent async operations.
 */
public class MySQLConnection extends DatabaseConnection {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useSSL;

    public MySQLConnection(RVNKLore plugin, SQLDialect dialect) {
        super(plugin, dialect);
        this.host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        this.port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        this.database = plugin.getConfig().getString("storage.mysql.database", "minecraft");
        this.username = plugin.getConfig().getString("storage.mysql.username", "root");
        this.password = plugin.getConfig().getString("storage.mysql.password", "");
        this.useSSL = plugin.getConfig().getBoolean("storage.mysql.useSSL", false);
    }

    @Override
    public void initialize() throws SQLException, ClassNotFoundException {
        logger.debug("Initializing MySQL connection pool...");
        lastConnectionError = null;

        // Load pool configuration from config.yml
        int poolSize = plugin.getConfig().getInt("storage.mysql.poolSize", 10);
        int connectionTimeout = plugin.getConfig().getInt("storage.mysql.connectionTimeout", 30000);
        int idleTimeout = plugin.getConfig().getInt("storage.mysql.idleTimeout", 600000);
        int maxLifetime = plugin.getConfig().getInt("storage.mysql.maxLifetime", 1800000);

        // Build JDBC URL
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true",
                host, port, database, useSSL);

        // Set up HikariCP configuration for MySQL
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setConnectionTestQuery("SELECT 1");
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.max(2, poolSize / 2));
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);

        // MySQL performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        // Create the connection pool
        connectionPool = new HikariDataSource(config);

        logger.debug("Connected to MySQL database with HikariCP pool (size: " + poolSize + ")");
    }

    @Override
    public String getDatabaseInfo() {
        if (connectionPool == null || connectionPool.isClosed()) {
            return "No active MySQL connection pool";
        }

        try (Connection conn = connectionPool.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            StringBuilder info = new StringBuilder();

            info.append("MySQL: ")
                .append(metaData.getDatabaseProductName())
                .append(" ")
                .append(metaData.getDatabaseProductVersion());

            // Check server variables for additional info
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'version%'")) {
                while (rs.next()) {
                    info.append(", ").append(rs.getString(1)).append(": ").append(rs.getString(2));
                }
            } catch (Exception e) {
                // Not critical if this fails
            }

            // Add pool stats
            info.append(", Pool: ")
                .append(connectionPool.getHikariPoolMXBean().getActiveConnections())
                .append("/")
                .append(connectionPool.getHikariPoolMXBean().getTotalConnections())
                .append(" active/total");

            return info.toString();
        } catch (SQLException e) {
            logger.error("Failed to get database info", e);
            return "Error retrieving MySQL info: " + e.getMessage();
        }
    }

    @Override
    public boolean isReadOnly() {
        if (connectionPool == null || connectionPool.isClosed()) {
            return true; // No connection means effectively read-only
        }

        try (Connection conn = connectionPool.getConnection()) {
            return conn.isReadOnly();
        } catch (SQLException e) {
            logger.error("Error checking if database is read-only", e);
            return true; // Assume read-only in case of error
        }
    }
}

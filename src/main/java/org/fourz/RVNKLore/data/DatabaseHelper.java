package org.fourz.RVNKLore.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.exception.LoreException;
import org.fourz.RVNKLore.exception.LoreException.LoreExceptionType;
import org.fourz.RVNKLore.data.query.QueryBuilder;
import org.fourz.RVNKLore.config.dto.DatabaseSettingsDTO;
import org.fourz.RVNKLore.config.ConfigManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enhanced database helper class providing async operations and connection pooling.
 * Implements HikariCP for efficient connection management.
 */
public class DatabaseHelper {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final ConfigManager configManager;
    private HikariDataSource dataSource;
    private final ExecutorService executorService;

    public DatabaseHelper(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "EnhancedDatabaseHelper");
        this.configManager = plugin.getConfigManager();
        this.executorService = Executors.newFixedThreadPool(10);
        initializeConnectionPool();
    }

    private void initializeConnectionPool() {
        DatabaseSettingsDTO settings = configManager.getDatabaseSettings();
        HikariConfig config = new HikariConfig();

        if (settings.getType() == DatabaseSettingsDTO.DatabaseType.MYSQL) {
            config.setDriverClassName("com.mysql.jdbc.Driver");
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s",
                settings.getMysqlSettings().getHost(),
                settings.getMysqlSettings().getPort(),
                settings.getMysqlSettings().getDatabase()));
            config.setUsername(settings.getMysqlSettings().getUsername());
            config.setPassword(settings.getMysqlSettings().getPassword());
        } else {
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl("jdbc:sqlite:" + settings.getSqliteSettings().getDatabase());
        }

        // Common HikariCP settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000); // 5 minutes
        config.setConnectionTimeout(settings.getConnectionTimeout());
        config.setLeakDetectionThreshold(60000); // 1 minute
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            dataSource = new HikariDataSource(config);
            logger.info("Connection pool initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize connection pool", e);
            throw new LoreException("Failed to initialize database connection pool", e, LoreExceptionType.DATABASE_ERROR);
        }
    }

    /**
     * Execute a query asynchronously with proper resource management and error handling.
     *
     * @param sql The SQL query
     * @param paramSetter A consumer that sets parameters on the prepared statement
     * @param resultHandler A function that processes the result set
     * @return A future containing the result of processing the query
     */
    public <T> CompletableFuture<T> executeQueryAsync(String sql, PreparedStatementSetter paramSetter, ResultSetHandler<T> resultHandler) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                if (paramSetter != null) {
                    paramSetter.setParameters(stmt);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    return resultHandler.handleResultSet(rs);
                }
            } catch (SQLException e) {
                throw new CompletionException(new LoreException("Database query failed", e, LoreExceptionType.DATABASE_ERROR));
            }
        }, executorService);
    }

    /**
     * Execute an update asynchronously with proper resource management and error handling.
     *
     * @param sql The SQL update statement
     * @param paramSetter A consumer that sets parameters on the prepared statement
     * @return A future containing the number of rows affected
     */
    public CompletableFuture<Integer> executeUpdateAsync(String sql, PreparedStatementSetter paramSetter) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                if (paramSetter != null) {
                    paramSetter.setParameters(stmt);
                }
                return stmt.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(new LoreException("Database update failed", e, LoreExceptionType.DATABASE_ERROR));
            }
        }, executorService);
    }

    /**
     * Execute a transaction asynchronously with proper resource management.
     *
     * @param transaction The transaction to execute
     * @return A future that completes when the transaction is done
     */
    public <T> CompletableFuture<T> executeTransactionAsync(Transaction<T> transaction) {
        return CompletableFuture.supplyAsync(() -> {
            Connection conn = null;
            try {
                conn = dataSource.getConnection();
                conn.setAutoCommit(false);
                
                T result = transaction.execute(conn);
                
                conn.commit();
                return result;
            } catch (Exception e) {
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        logger.error("Failed to rollback transaction", rollbackEx);
                    }
                }
                throw new CompletionException(new LoreException("Transaction failed", e, LoreExceptionType.DATABASE_ERROR));
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException e) {
                        logger.error("Failed to cleanup transaction resources", e);
                    }
                }
            }
        }, executorService);
    }

    /**
     * Check connection pool health.
     * @return True if the connection pool is healthy
     */
    public boolean isHealthy() {
        try (Connection conn = dataSource.getConnection()) {
            return true;
        } catch (SQLException e) {
            logger.error("Connection pool health check failed", e);
            return false;
        }
    }

    /**
     * Get stats about the connection pool.
     * @return A string containing pool statistics
     */
    public String getPoolStats() {
        return String.format("Pool Stats: Active=%d, Idle=%d, Total=%d, Waiting=%d",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }

    /**
     * Clean up resources when shutting down.
     */
    public void shutdown() {
        executorService.shutdown();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @FunctionalInterface
    public interface PreparedStatementSetter {
        void setParameters(PreparedStatement stmt) throws SQLException;
    }

    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handleResultSet(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    public interface Transaction<T> {
        T execute(Connection connection) throws SQLException;
    }
}

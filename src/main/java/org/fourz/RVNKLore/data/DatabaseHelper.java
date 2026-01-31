package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.exception.LoreException;
import org.fourz.RVNKLore.exception.LoreException.LoreExceptionType;
import org.fourz.rvnkcore.util.log.LogManager;

import org.fourz.RVNKLore.data.dialect.SQLDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Helper class for database operations with improved error handling
 */
public class DatabaseHelper {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;

    public DatabaseHelper(RVNKLore plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.logger = LogManager.getInstance(plugin, "DatabaseHelper");
    }
    
    /**
     * Execute a database operation with automatic error handling and retry
     * 
     * @param operation The database operation to execute
     * @return The result of the operation
     * @throws LoreException If the operation fails
     */
    public <T> T executeWithRetry(DatabaseOperation<T> operation) throws LoreException {
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                // Check if connection is valid
                if (!databaseManager.isConnected()) {
                    logger.warning("Database connection lost, attempting to reconnect...");
                    boolean reconnected = databaseManager.reconnect();
                    if (!reconnected) {
                        throw new SQLException("Failed to reconnect to database");
                    }
                }
                
                // Execute the operation
                return operation.execute();
                
            } catch (SQLException e) {
                retryCount++;
                logger.warning("Database operation failed (attempt " + retryCount + "/" + maxRetries + "): " + e.getMessage());
                
                // If we've reached max retries, throw an exception
                if (retryCount >= maxRetries) {
                    throw new LoreException("Database operation failed after " + maxRetries + " attempts", e, LoreExceptionType.DATABASE_ERROR);
                }
                
                // Wait before retrying
                try {
                    Thread.sleep(1000 * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LoreException("Operation interrupted", ie, LoreExceptionType.UNKNOWN_ERROR);
                }
            }
        }
        
        // This should never be reached, but just in case
        throw new LoreException("Failed to execute database operation", LoreExceptionType.DATABASE_ERROR);
    }
    
    /**
     * Functional interface for database operations
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute() throws SQLException;
    }
    
    /**
     * Execute a query with proper resource management and error handling
     * 
     * @param sql The SQL query
     * @param paramSetter A consumer that sets parameters on the prepared statement
     * @param resultHandler A function that processes the result set
     * @return The result of processing the query
     * @throws LoreException If the query fails
     */
    public <T> T executeQuery(String sql, PreparedStatementSetter paramSetter, ResultSetHandler<T> resultHandler) throws LoreException {
        return executeWithRetry(() -> {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                // Set parameters if provided
                if (paramSetter != null) {
                    paramSetter.setParameters(stmt);
                }
                
                // Execute query and process results
                try (ResultSet rs = stmt.executeQuery()) {
                    return resultHandler.handleResultSet(rs);
                }
            }
        });
    }
    
    /**
     * Execute an update with proper resource management and error handling
     *
     * @param sql The SQL update statement
     * @param paramSetter A consumer that sets parameters on the prepared statement
     * @return The number of rows affected
     * @throws LoreException If the update fails
     */
    public int executeUpdate(String sql, PreparedStatementSetter paramSetter) throws LoreException {
        return executeWithRetry(() -> {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                // Set parameters if provided
                if (paramSetter != null) {
                    paramSetter.setParameters(stmt);
                }

                // Execute update
                return stmt.executeUpdate();
            }
        });
    }

    /**
     * Execute an INSERT and return the generated key (auto-increment ID).
     * Uses dialect-specific approach:
     * - SQLite: Uses RETURNING clause with executeQuery()
     * - MySQL: Uses RETURN_GENERATED_KEYS flag with executeUpdate()
     *
     * @param baseInsertSql The INSERT SQL WITHOUT RETURNING clause
     * @param idColumn The name of the auto-increment column
     * @param paramSetter A consumer that sets parameters on the prepared statement
     * @return The generated ID, or -1 if insert failed
     * @throws LoreException If the insert fails
     */
    public int executeInsertWithGeneratedKey(String baseInsertSql, String idColumn,
            PreparedStatementSetter paramSetter) throws LoreException {
        SQLDialect dialect = databaseManager.getDatabaseConnection().getDialect();

        return executeWithRetry(() -> {
            Connection conn = databaseManager.getConnection();

            if (dialect.requiresGeneratedKeysFlag()) {
                // MySQL approach: use getGeneratedKeys()
                try (PreparedStatement stmt = conn.prepareStatement(baseInsertSql,
                        Statement.RETURN_GENERATED_KEYS)) {
                    if (paramSetter != null) {
                        paramSetter.setParameters(stmt);
                    }
                    stmt.executeUpdate();
                    return dialect.extractGeneratedId(stmt, null, idColumn);
                }
            } else {
                // SQLite approach: use RETURNING clause
                String sqlWithReturning = dialect.wrapInsertForGeneratedKey(baseInsertSql, idColumn);
                try (PreparedStatement stmt = conn.prepareStatement(sqlWithReturning)) {
                    if (paramSetter != null) {
                        paramSetter.setParameters(stmt);
                    }
                    try (ResultSet rs = stmt.executeQuery()) {
                        return dialect.extractGeneratedId(stmt, rs, idColumn);
                    }
                }
            }
        });
    }
    
    /**
     * Functional interface for setting parameters on prepared statements
     */
    @FunctionalInterface
    public interface PreparedStatementSetter {
        void setParameters(PreparedStatement stmt) throws SQLException;
    }
    
    /**
     * Functional interface for handling result sets
     */
    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handleResultSet(ResultSet rs) throws SQLException;
    }
}

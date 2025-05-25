package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.Debug;
import org.fourz.RVNKLore.exception.LoreException;
import org.fourz.RVNKLore.exception.LoreException.LoreExceptionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Helper class for database operations with improved error handling
 */
public class DatabaseHelper {
    private final RVNKLore plugin;
    private final Debug debug;
    private final DatabaseManager databaseManager;
    
    public DatabaseHelper(RVNKLore plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.debug = Debug.createDebugger(plugin, "DatabaseHelper", Level.FINE);
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
                    debug.warning("Database connection lost, attempting to reconnect...");
                    boolean reconnected = databaseManager.reconnect();
                    if (!reconnected) {
                        throw new SQLException("Failed to reconnect to database");
                    }
                }
                
                // Execute the operation
                return operation.execute();
                
            } catch (SQLException e) {
                retryCount++;
                debug.warning("Database operation failed (attempt " + retryCount + "/" + maxRetries + "): " + e.getMessage());
                
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

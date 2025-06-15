package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.exception.LoreException;
import org.fourz.RVNKLore.exception.LoreException.LoreExceptionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Helper class for database operations with improved error handling and connection state management
 */
public class DatabaseHelper {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    private final int maxRetries;
    private final int retryDelayMs;
    
    public DatabaseHelper(RVNKLore plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.logger = LogManager.getInstance(plugin, "DatabaseHelper");
        this.maxRetries = plugin.getConfig().getInt("database.max_retries", 3);
        this.retryDelayMs = plugin.getConfig().getInt("database.retry_delay_ms", 1000);
    }
    
    /**
     * Execute a database operation with automatic error handling and retry
     * 
     * @param operation The database operation to execute
     * @return The result of the operation
     * @throws LoreException If the operation fails
     */
    public <T> T executeWithRetry(DatabaseOperation<T> operation) throws LoreException {
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                // Check if connection is valid
                if (!validateConnection()) {
                    throw new SQLException("Invalid database connection");
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
                    Thread.sleep(retryDelayMs * retryCount);
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
     * Validate the database connection, attempt to reconnect if needed
     * 
     * @return true if connection is valid, false otherwise
     */
    private boolean validateConnection() {
        if (databaseManager.isConnected()) {
            return true;
        }
        
        logger.warning("Database connection lost, attempting to reconnect...");
        boolean reconnected = databaseManager.reconnect();
        
        if (reconnected) {
            logger.info("Successfully reconnected to database");
            return true;
        } else {
            logger.error("Failed to reconnect to database", null);
            return false;
        }
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
     * Execute an insert and retrieve the generated key
     * 
     * @param sql The SQL insert statement
     * @param paramSetter A consumer that sets parameters on the prepared statement
     * @return The generated key, or -1 if none
     * @throws LoreException If the insert fails
     */
    public int executeInsertAndGetKey(String sql, PreparedStatementSetter paramSetter) throws LoreException {
        return executeWithRetry(() -> {
            Connection conn = databaseManager.getConnection();
            
            // For SQLite with RETURNING clause
            if (sql.toUpperCase().contains("RETURNING")) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    if (paramSetter != null) {
                        paramSetter.setParameters(stmt);
                    }
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next() ? rs.getInt(1) : -1;
                    }
                }
            } 
            // For MySQL with auto-increment
            else {
                try (PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    if (paramSetter != null) {
                        paramSetter.setParameters(stmt);
                    }
                    
                    int affectedRows = stmt.executeUpdate();
                    
                    if (affectedRows == 0) {
                        return -1;
                    }
                    
                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        return generatedKeys.next() ? generatedKeys.getInt(1) : -1;
                    }
                }
            }
        });
    }
    
    /**
     * Begin a transaction
     * 
     * @return the Connection with autoCommit disabled
     * @throws SQLException if a database access error occurs
     */
    public Connection beginTransaction() throws SQLException {
        Connection conn = databaseManager.getConnection();
        conn.setAutoCommit(false);
        return conn;
    }
    
    /**
     * Commit a transaction
     * 
     * @param conn the Connection to commit
     * @throws SQLException if a database access error occurs
     */
    public void commitTransaction(Connection conn) throws SQLException {
        if (conn != null && !conn.getAutoCommit()) {
            conn.commit();
            conn.setAutoCommit(true);
        }
    }
    
    /**
     * Rollback a transaction
     * 
     * @param conn the Connection to rollback
     */
    public void rollbackTransaction(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                logger.error("Error rolling back transaction", e);
            }
        }
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

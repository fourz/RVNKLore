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
 * Helper class for database operations with improved error handling.
 *
 * <p>All methods use try-with-resources to properly close connections
 * obtained from the HikariCP pool. This prevents connection leaks
 * and ensures connections are returned to the pool after use.
 */
public class DatabaseHelper {
    private final RVNKLore plugin;
    private final LogManager logger;

    // Retry configuration
    private static final int maxRetries = 3;
    private static final long retryDelayMs = 1000;

    public DatabaseHelper(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DatabaseHelper");
    }

    /**
     * Resolve the DatabaseManager at use time.
     *
     * <p>This must not be captured in the constructor. Repositories wired by
     * {@code DatabaseManager.wireRepositories()} are built while the DatabaseManager constructor is
     * still running, so {@code plugin.getDatabaseManager()} is still null at that moment and the
     * captured reference would stay null for the object's life — every pooled write from those
     * repositories then NPEs (#1838). Resolving here also means a fallback or recovery swap is
     * picked up rather than frozen, the same reason ItemManager was changed in #1835.</p>
     *
     * @return the current DatabaseManager, or null before the plugin has built one
     */
    private DatabaseManager db() {
        return plugin.getDatabaseManager();
    }

    /**
     * Execute a database operation with automatic error handling and retry.
     * Each retry gets a fresh connection from the pool.
     *
     * @param operation The database operation to execute
     * @return The result of the operation
     * @throws LoreException If the operation fails after all retries
     */
    public <T> T executeWithRetry(DatabaseOperation<T> operation) throws LoreException {
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                // Check if connection pool is valid
                if (!db().isConnected()) {
                    logger.warning("Database connection pool unavailable, attempting to reconnect...");
                    boolean reconnected = db().reconnect();
                    if (!reconnected) {
                        throw new SQLException("Failed to reconnect to database");
                    }
                }

                // Execute the operation
                return operation.execute();

            } catch (java.sql.SQLIntegrityConstraintViolationException e) {
                // A constraint violation (e.g. duplicate key) will never succeed on retry.
                // Fail fast instead of emitting maxRetries identical WARN lines with a delay
                // between each — the caller decides whether a duplicate is expected (#1427).
                throw new LoreException("Database constraint violation", e, LoreExceptionType.DATABASE_ERROR);
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
        if (db().isConnected()) {
            return true;
        }
        
        logger.warning("Database connection lost, attempting to reconnect...");
        boolean reconnected = db().reconnect();
        
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
     * Execute a query with proper resource management and error handling.
     * Connection is obtained from pool and properly closed after use.
     *
     * @param sql The SQL query
     * @param paramSetter A consumer that sets parameters on the prepared statement
     * @param resultHandler A function that processes the result set
     * @return The result of processing the query
     * @throws LoreException If the query fails
     */
    public <T> T executeQuery(String sql, PreparedStatementSetter paramSetter, ResultSetHandler<T> resultHandler) throws LoreException {
        return executeWithRetry(() -> {
            // Get fresh connection from pool - MUST use try-with-resources
            try (Connection conn = db().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
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
     * Execute an update with proper resource management and error handling.
     * Connection is obtained from pool and properly closed after use.
     *
     * @param sql The SQL update statement
     * @param paramSetter A consumer that sets parameters on the prepared statement
     * @return The number of rows affected
     * @throws LoreException If the update fails
     */
    public int executeUpdate(String sql, PreparedStatementSetter paramSetter) throws LoreException {
        return executeWithRetry(() -> {
            // Get fresh connection from pool - MUST use try-with-resources
            try (Connection conn = db().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
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
     * Connection is obtained from pool and properly closed after use.
     *
     * @param baseInsertSql The INSERT SQL WITHOUT RETURNING clause
     * @param idColumn The name of the auto-increment column
     * @param paramSetter A consumer that sets parameters on the prepared statement
     * @return The generated ID, or -1 if insert failed
     * @throws LoreException If the insert fails
     */
    public int executeInsertWithGeneratedKey(String baseInsertSql, String idColumn,
            PreparedStatementSetter paramSetter) throws LoreException {
        SQLDialect dialect = db().getDatabaseConnection().getDialect();

        return executeWithRetry(() -> {
            // Get fresh connection from pool - MUST use try-with-resources
            try (Connection conn = db().getConnection()) {

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
            try (Connection conn = db().getConnection()) {
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
            }
        });
    }
    
    /**
     * Execute an update on a caller-supplied connection.
     *
     * <p>Companion to {@link #executeUpdate(String, PreparedStatementSetter)} for writes that must
     * stay on a specific connection — chiefly the multi-write transactions in
     * {@code LoreEntryRepository} and {@code SubmissionManager}. Routing those through the pooled
     * variant would scatter one transaction's writes across different connections and silently lose
     * atomicity, so they use this instead (#1838).</p>
     *
     * <p>Deliberately <b>not</b> wrapped in {@link #executeWithRetry}: retrying a single statement
     * inside a transaction that has already failed is incorrect — the transaction is poisoned and
     * the caller owns the rollback. The connection is likewise not closed here; the caller opened it
     * and owns its lifecycle.</p>
     *
     * <p>Funnelling every write through {@code DatabaseHelper} is what gives #1833 a single place to
     * record outage-era writes for reconcile-on-recovery.</p>
     *
     * @param conn The caller-managed connection to execute on (not closed by this method)
     * @param sql The SQL update statement
     * @param paramSetter Sets parameters on the prepared statement; may be null
     * @return The number of rows affected
     * @throws SQLException If the update fails — propagated so the caller can roll back
     */
    public int executeUpdateOn(Connection conn, String sql, PreparedStatementSetter paramSetter)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (paramSetter != null) {
                paramSetter.setParameters(stmt);
            }
            return stmt.executeUpdate();
        }
    }

    /**
     * Begin a transaction
     *
     * @return the Connection with autoCommit disabled
     * @throws SQLException if a database access error occurs
     */
    public Connection beginTransaction() throws SQLException {
        Connection conn = db().getConnection();
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

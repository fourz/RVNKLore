package org.fourz.RVNKLore.data;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;

import java.util.*;

/**
 * Factory for creating database connections and handling SQL-independent queries
 */
public class DatabaseConnectionFactory {
    private final RVNKLore plugin;
    private final LogManager logger;
    private DatabaseConnection connection;
    
    public DatabaseConnectionFactory(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DatabaseConnectionFactory");
    }
    
    /**
     * Create a database connection based on configuration
     */
    public DatabaseConnection createConnection() {
        String storageType = plugin.getConfigManager().getStorageType();
        logger.debug("Creating database connection for storage type: " + storageType);
        
        if (storageType.equalsIgnoreCase("mysql")) {
            connection = new MySQLConnection(plugin);
        } else {
            connection = new SQLiteConnection(plugin);
        }
        return connection;
    }
    
    /**
     * Get the active database connection
     */
    public DatabaseConnection getConnection() {
        return connection;
    }
    
    /**
     * Get the database type
     */
    public String getDatabaseType() {
        return connection != null ? connection.getDatabaseType() : "unknown";
    }
    
    /**
     * Create a SELECT statement
     * 
     * @param table The table to select from
     * @param columns The columns to select
     * @param whereClause The WHERE clause (without the "WHERE" keyword)
     * @return The SQL string
     */
    public String createSelectStatement(String table, String[] columns, String whereClause) {
        StringBuilder sql = new StringBuilder("SELECT ");
        
        if (columns == null || columns.length == 0) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", columns));
        }
        
        sql.append(" FROM ").append(table);
        
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }
        
        return sql.toString();
    }
    
    /**
     * Create an INSERT statement
     * 
     * @param table The table to insert into
     * @param columns The columns to insert
     * @param returnId Whether to return the generated ID
     * @return The SQL string
     */
    public String createInsertStatement(String table, String[] columns, boolean returnId) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(table).append(" (");
        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        
        sql.append(")");
        
        // Add RETURNING clause if needed (SQLite vs MySQL handling differs)
        if (returnId) {
            if (getDatabaseType().equals("sqlite")) {
                sql.append(" RETURNING id");
            }
        }
        
        return sql.toString();
    }
    
    /**
     * Create an UPDATE statement
     * 
     * @param table The table to update
     * @param columns The columns to update
     * @param whereClause The WHERE clause (without the "WHERE" keyword)
     * @return The SQL string
     */
    public String createUpdateStatement(String table, String[] columns, String whereClause) {
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(table).append(" SET ");
        
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(columns[i]).append(" = ?");
        }
        
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }
        
        return sql.toString();
    }
    
    /**
     * Create a DELETE statement
     * 
     * @param table The table to delete from
     * @param whereClause The WHERE clause (without the "WHERE" keyword)
     * @return The SQL string
     */
    public String createDeleteStatement(String table, String whereClause) {
        StringBuilder sql = new StringBuilder("DELETE FROM ");
        sql.append(table);
        
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }
        
        return sql.toString();
    }
    
    /**
     * Handle database-specific SQL dialect differences
     * 
     * @param sqlTemplate The template SQL with placeholders
     * @return The database-specific SQL
     */
    public String adaptSqlDialect(String sqlTemplate) {
        if (getDatabaseType().equals("sqlite")) {
            // Convert MySQL-style syntax to SQLite
            return sqlTemplate
                .replace("AUTO_INCREMENT", "AUTOINCREMENT")
                .replace("CURRENT_TIMESTAMP()", "CURRENT_TIMESTAMP")
                .replace("TRUE", "1")
                .replace("FALSE", "0")
                .replace("ON DUPLICATE KEY UPDATE", "ON CONFLICT DO UPDATE SET");
        } else {
            // Use MySQL syntax
            return sqlTemplate;
        }
    }
    
    /**
     * Create a paging clause for limits and offsets
     * 
     * @param limit The max number of results
     * @param offset The starting position
     * @return The SQL LIMIT/OFFSET clause
     */
    public String createPagingClause(int limit, int offset) {
        StringBuilder sql = new StringBuilder();
        
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
            
            if (offset > 0) {
                sql.append(" OFFSET ").append(offset);
            }
        }
        
        return sql.toString();
    }
    
    /**
     * Create an ORDER BY clause
     * 
     * @param orderBy The columns to order by
     * @param ascending Whether to sort in ascending order
     * @return The SQL ORDER BY clause
     */
    public String createOrderByClause(String[] orderBy, boolean ascending) {
        if (orderBy == null || orderBy.length == 0) {
            return "";
        }
        
        return " ORDER BY " + String.join(", ", orderBy) + (ascending ? " ASC" : " DESC");
    }
    
    /**
     * Get the auto-increment id retrieval sql based on database type
     */
    public String getLastInsertIdSql() {
        if (getDatabaseType().equals("sqlite")) {
            return "SELECT last_insert_rowid()";
        } else {
            return "SELECT LAST_INSERT_ID()";
        }
    }
}

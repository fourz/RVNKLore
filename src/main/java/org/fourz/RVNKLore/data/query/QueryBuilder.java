package org.fourz.RVNKLore.data.query;

/**
 * Interface for building SQL queries in a database-agnostic way.
 * Implementations should encapsulate dialect-specific logic (e.g., MySQL, SQLite).
 * Used by DatabaseManager and QueryExecutor to construct and execute queries.
 */
public interface QueryBuilder {
    /**
     * Select specific columns from a table.
     * 
     * @param columns The columns to select
     * @return This QueryBuilder for chaining
     */
    QueryBuilder select(String... columns);
    
    /**
     * Specify the table to select from.
     * 
     * @param table The table name
     * @return This QueryBuilder for chaining
     */
    QueryBuilder from(String table);
    
    /**
     * Add a JOIN clause.
     * 
     * @param table The table to join
     * @param condition The join condition
     * @param params Optional parameters for the join condition
     * @return This QueryBuilder for chaining
     */
    default QueryBuilder join(String table, String condition, Object... params) {
        return innerJoin(table, condition, params);
    }
    
    /**
     * Add an INNER JOIN clause.
     * 
     * @param table The table to join
     * @param condition The join condition
     * @param params Optional parameters for the join condition
     * @return This QueryBuilder for chaining
     */
    QueryBuilder innerJoin(String table, String condition, Object... params);
    
    /**
     * Add a LEFT JOIN clause.
     * 
     * @param table The table to join
     * @param condition The join condition
     * @param params Optional parameters for the join condition
     * @return This QueryBuilder for chaining
     */
    QueryBuilder leftJoin(String table, String condition, Object... params);
    
    /**
     * Add a RIGHT JOIN clause.
     * 
     * @param table The table to join
     * @param condition The join condition
     * @param params Optional parameters for the join condition
     * @return This QueryBuilder for chaining
     */
    QueryBuilder rightJoin(String table, String condition, Object... params);
    
    /**
     * Add a FULL OUTER JOIN clause.
     * 
     * @param table The table to join
     * @param condition The join condition
     * @param params Optional parameters for the join condition
     * @return This QueryBuilder for chaining
     */
    QueryBuilder fullOuterJoin(String table, String condition, Object... params);
    
    /**
     * Add a WHERE clause with parametrized values.
     * 
     * @param condition The condition with ? placeholders
     * @param params The values to substitute for the placeholders
     * @return This QueryBuilder for chaining
     */
    QueryBuilder where(String condition, Object... params);
    
    /**
     * Add an AND condition to an existing WHERE clause.
     * 
     * @param condition The condition with ? placeholders
     * @param params The values to substitute for the placeholders
     * @return This QueryBuilder for chaining
     */
    QueryBuilder and(String condition, Object... params);
    
    /**
     * Add an OR condition to an existing WHERE clause.
     * 
     * @param condition The condition with ? placeholders
     * @param params The values to substitute for the placeholders
     * @return This QueryBuilder for chaining
     */
    QueryBuilder or(String condition, Object... params);
    
    /**
     * Add an ORDER BY clause.
     * 
     * @param column The column to order by
     * @param ascending Whether to order in ascending order
     * @return This QueryBuilder for chaining
     */
    QueryBuilder orderBy(String column, boolean ascending);
    
    /**
     * Add a GROUP BY clause.
     * 
     * @param columns The columns to group by
     * @return This QueryBuilder for chaining
     */
    QueryBuilder groupBy(String... columns);
    
    /**
     * Add a HAVING clause.
     * 
     * @param condition The having condition
     * @param params The values to substitute for placeholders
     * @return This QueryBuilder for chaining
     */
    QueryBuilder having(String condition, Object... params);
    
    /**
     * Add a LIMIT clause.
     * 
     * @param limit The maximum number of rows to return
     * @return This QueryBuilder for chaining
     */
    QueryBuilder limit(int limit);
    
    /**
     * Add an OFFSET clause.
     * 
     * @param offset The number of rows to skip
     * @return This QueryBuilder for chaining
     */
    QueryBuilder offset(int offset);
    
    /**
     * Start building an INSERT statement.
     * 
     * @param table The table to insert into
     * @return This QueryBuilder for chaining
     */
    QueryBuilder insertInto(String table);

    /**
     * Specify columns for an INSERT statement.
     * 
     * @param columns The column names
     * @return This QueryBuilder for chaining
     */
    QueryBuilder columns(String... columns);

    /**
     * Specify values for an INSERT statement.
     * 
     * @param values The values to insert
     * @return This QueryBuilder for chaining
     */
    QueryBuilder values(Object... values);

    /**
     * Start building an UPDATE statement.
     * 
     * @param table The table to update
     * @return This QueryBuilder for chaining
     */
    QueryBuilder update(String table);

    /**
     * Add a SET clause to an UPDATE statement.
     * 
     * @param column The column to set
     * @param value The value to set
     * @return This QueryBuilder for chaining
     */
    QueryBuilder set(String column, Object value);

    /**
     * Start building a DELETE statement.
     * 
     * @param table The table to delete from
     * @return This QueryBuilder for chaining
     */
    QueryBuilder deleteFrom(String table);

    /**
     * Create a raw SQL query.
     * 
     * @param sql The SQL query string
     * @param params The parameters for the query
     * @return This QueryBuilder for chaining
     */
    QueryBuilder raw(String sql, Object... params);

    /**
     * Build the complete SQL query string.
     * 
     * @return The SQL query string
     */
    String build();

    /**
     * Get the parameters for this query.
     * 
     * @return An array of parameter values
     */
    Object[] getParameters();
}

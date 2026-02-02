package org.fourz.RVNKLore.data.query;

/**
 * Interface for building SQL queries in a database-agnostic way.
 * Implementations should encapsulate dialect-specific logic (e.g., MySQL, SQLite).
 * Used by DatabaseManager and IQueryExecutor to construct and execute queries.
 */
public interface IQueryBuilder {
    /**
     * Select specific columns from a table.
     *
     * @param columns The columns to select
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder select(String... columns);

    /**
     * Specify the table to select from.
     *
     * @param table The table name
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder from(String table);

    /**
     * Add a WHERE clause with parametrized values.
     *
     * @param condition The condition with ? placeholders
     * @param params The values to substitute for the placeholders
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder where(String condition, Object... params);

    /**
     * Add an AND condition to an existing WHERE clause.
     *
     * @param condition The condition with ? placeholders
     * @param params The values to substitute for the placeholders
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder and(String condition, Object... params);

    /**
     * Add an OR condition to an existing WHERE clause.
     *
     * @param condition The condition with ? placeholders
     * @param params The values to substitute for the placeholders
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder or(String condition, Object... params);

    /**
     * Add an ORDER BY clause.
     *
     * @param column The column to order by
     * @param ascending Whether to order in ascending order
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder orderBy(String column, boolean ascending);

    /**
     * Add a GROUP BY clause.
     *
     * @param columns The columns to group by
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder groupBy(String... columns);

    /**
     * Add a HAVING clause.
     *
     * @param condition The having condition
     * @param params The values to substitute for placeholders
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder having(String condition, Object... params);

    /**
     * Add a LIMIT clause.
     *
     * @param limit The maximum number of rows to return
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder limit(int limit);

    /**
     * Add an OFFSET clause.
     *
     * @param offset The number of rows to skip
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder offset(int offset);

    /**
     * Start building an INSERT statement.
     *
     * @param table The table to insert into
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder insertInto(String table);

    /**
     * Specify columns for an INSERT statement.
     *
     * @param columns The columns to insert into
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder columns(String... columns);

    /**
     * Specify values for an INSERT statement.
     *
     * @param values The values to insert
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder values(Object... values);

    /**
     * Start building an UPDATE statement.
     *
     * @param table The table to update
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder update(String table);

    /**
     * Set values for an UPDATE statement.
     *
     * @param column The column to set
     * @param value The value to set
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder set(String column, Object value);

    /**
     * Start building a DELETE statement.
     *
     * @param table The table to delete from
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder deleteFrom(String table);

    /**
     * Add a JOIN clause.
     *
     * @param table The table to join
     * @param condition The join condition
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder join(String table, String condition);

    /**
     * Add a LEFT JOIN clause.
     *
     * @param table The table to join
     * @param condition The join condition
     * @return This IQueryBuilder for chaining
     */
    IQueryBuilder leftJoin(String table, String condition);

    /**
     * Build the final SQL query.
     *
     * @return The SQL query string
     */
    String build();

    /**
     * Get the parameters to be used with the query.
     *
     * @return Array of parameter values
     */
    Object[] getParameters();
}

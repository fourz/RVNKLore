package org.fourz.RVNKLore.data.query;

/**
 * Interface for building SQL queries in a database-agnostic way.
 * Implementations should encapsulate dialect-specific logic (e.g., MySQL, SQLite).
 * Used by DatabaseManager and QueryExecutor to construct and execute queries.
 */
public interface QueryBuilder {
    QueryBuilder select(String... columns);
    QueryBuilder from(String table);
    QueryBuilder where(String condition, Object... params);
    QueryBuilder orderBy(String column, boolean ascending);
    QueryBuilder limit(int limit);
    String build();
    Object[] getParameters();
}

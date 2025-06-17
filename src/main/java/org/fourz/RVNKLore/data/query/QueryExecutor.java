package org.fourz.RVNKLore.data.query;

import java.util.concurrent.CompletableFuture;

/**
 * Executes SQL queries built by QueryBuilder and maps results to DTOs.
 * Used by DatabaseManager for all database operations.
 */
public interface QueryExecutor {
    <T> CompletableFuture<T> executeQuery(QueryBuilder builder, Class<T> dtoClass);
    <T> CompletableFuture<java.util.List<T>> executeQueryList(QueryBuilder builder, Class<T> dtoClass);
    CompletableFuture<Integer> executeUpdate(QueryBuilder builder);
}

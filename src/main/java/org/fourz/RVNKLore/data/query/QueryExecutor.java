package org.fourz.RVNKLore.data.query;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Executes SQL queries built by QueryBuilder and maps results to DTOs.
 * Used by DatabaseManager for all database operations.
 */
public interface QueryExecutor {
    /**
     * Executes a query and returns a single result mapped to the specified DTO class.
     * 
     * @param <T> The DTO type to return
     * @param builder The query builder containing the query
     * @param dtoClass The class of the DTO to map results to
     * @return A future containing the mapped result, or null if no result
     */
    <T> CompletableFuture<T> executeQuery(QueryBuilder builder, Class<T> dtoClass);
    
    /**
     * Executes a query and returns a list of results mapped to the specified DTO class.
     * 
     * @param <T> The DTO type to return
     * @param builder The query builder containing the query
     * @param dtoClass The class of the DTO to map results to
     * @return A future containing a list of mapped results
     */
    <T> CompletableFuture<List<T>> executeQueryList(QueryBuilder builder, Class<T> dtoClass);
    
    /**
     * Executes an update (INSERT, UPDATE, DELETE) and returns the number of affected rows.
     * 
     * @param builder The query builder containing the update statement
     * @return A future containing the number of affected rows
     */
    CompletableFuture<Integer> executeUpdate(QueryBuilder builder);
    
    /**
     * Executes an insert and returns the generated key.
     * 
     * @param builder The query builder containing the insert statement
     * @return A future containing the generated key, or -1 if no key was generated
     */
    CompletableFuture<Integer> executeInsert(QueryBuilder builder);
    
    /**
     * Executes a series of operations in a transaction.
     * 
     * @param <T> The return type of the transaction
     * @param transactionFunction A function that accepts a Connection and returns a result
     * @return A future containing the result of the transaction
     */
    <T> CompletableFuture<T> executeTransaction(Function<Connection, T> transactionFunction);
    
    /**
     * Executes a batch update with the same query but different parameters.
     * 
     * @param builder The query builder containing the base query
     * @param batchParams A list of parameter arrays for each batch execution
     * @return A future containing an array with the number of affected rows for each batch
     */
    CompletableFuture<int[]> executeBatch(QueryBuilder builder, List<Object[]> batchParams);
}

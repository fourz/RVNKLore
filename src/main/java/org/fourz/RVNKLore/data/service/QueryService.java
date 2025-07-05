package org.fourz.RVNKLore.data.service;

import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.connection.ConnectionProvider;
import org.fourz.RVNKLore.data.query.QueryBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

public class QueryService {
    private final ConnectionProvider provider;
    private final LogManager logger;

    public QueryService(ConnectionProvider provider, LogManager logger) {
        this.provider = provider;
        this.logger   = logger;
    }

    /**
     * Execute a SELECT and map its ResultSet via the supplied mapper.
     */
    public <T> CompletableFuture<T> execute(QueryBuilder qb, ResultSetMapper<T> mapper) {
        return CompletableFuture.supplyAsync(() -> {
            try (var conn = provider.getConnection();
                 var ps   = conn.prepareStatement(qb.build());
                 var rs   = ps.executeQuery()) {

                Object[] params = qb.getParameters();
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                return mapper.map(rs);

            } catch (SQLException e) {
                logger.error("Query failed", e);
                throw new CompletionException(e);
            }
        });
    }

    @FunctionalInterface
    public interface ResultSetMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}
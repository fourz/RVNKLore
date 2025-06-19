package org.fourz.RVNKLore.data.query;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.connection.ConnectionProvider;
import org.fourz.RVNKLore.debug.LogManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Implementation of the QueryExecutor interface.
 * Executes SQL queries asynchronously and maps results to DTOs.
 */
public class DefaultQueryExecutor implements QueryExecutor {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final ConnectionProvider connectionProvider;
    private final Executor databaseExecutor;

    /**
     * Create a new DefaultQueryExecutor.
     *
     * @param plugin The RVNKLore plugin instance
     * @param connectionProvider The connection provider
     */
    public DefaultQueryExecutor(RVNKLore plugin, ConnectionProvider connectionProvider) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "QueryExecutor");
        this.connectionProvider = connectionProvider;
        this.databaseExecutor = Executors.newFixedThreadPool(4);
    }

    @Override
    public <T> CompletableFuture<T> executeQuery(QueryBuilder builder, Class<T> dtoClass) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = builder.build();
            Object[] params = builder.getParameters();
            
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = prepareStatement(conn, sql, params);
                 ResultSet rs = stmt.executeQuery()) {
                
                if (rs.next()) {
                    return mapResultToDto(rs, dtoClass);
                } else {
                    return null;
                }
                
            } catch (SQLException e) {
                logger.error("Error executing query: " + sql, e);
                throw new RuntimeException("Database query error", e);
            } catch (ReflectiveOperationException e) {
                logger.error("Error mapping result to DTO: " + dtoClass.getName(), e);
                throw new RuntimeException("Data mapping error", e);
            }
        }, databaseExecutor);
    }

    @Override
    public <T> CompletableFuture<List<T>> executeQueryList(QueryBuilder builder, Class<T> dtoClass) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = builder.build();
            Object[] params = builder.getParameters();
            List<T> results = new ArrayList<>();
            
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = prepareStatement(conn, sql, params);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    T dto = mapResultToDto(rs, dtoClass);
                    results.add(dto);
                }
                
                return results;
                
            } catch (SQLException e) {
                logger.error("Error executing query: " + sql, e);
                throw new RuntimeException("Database query error", e);
            } catch (ReflectiveOperationException e) {
                logger.error("Error mapping result to DTO: " + dtoClass.getName(), e);
                throw new RuntimeException("Data mapping error", e);
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Integer> executeUpdate(QueryBuilder builder) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = builder.build();
            Object[] params = builder.getParameters();
            
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = prepareStatement(conn, sql, params)) {
                
                int rowsAffected = stmt.executeUpdate();
                conn.commit();
                
                return rowsAffected;
                
            } catch (SQLException e) {
                logger.error("Error executing update: " + sql, e);
                throw new RuntimeException("Database update error", e);
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Integer> executeInsert(QueryBuilder builder) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = builder.build();
            Object[] params = builder.getParameters();
            
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = prepareStatementWithKeys(conn, sql, params)) {
                
                int rowsAffected = stmt.executeUpdate();
                conn.commit();
                
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    } else {
                        // No keys generated but insert successful
                        return rowsAffected > 0 ? 1 : -1;
                    }
                }
                
            } catch (SQLException e) {
                logger.error("Error executing insert: " + sql, e);
                throw new RuntimeException("Database insert error", e);
            }
        }, databaseExecutor);
    }

    @Override
    public <T> CompletableFuture<T> executeTransaction(Function<Connection, T> transactionFunction) {
        return CompletableFuture.supplyAsync(() -> {
            Connection conn = null;
            try {
                conn = connectionProvider.getConnection();
                conn.setAutoCommit(false);
                
                T result = transactionFunction.apply(conn);
                
                conn.commit();
                return result;
                
            } catch (Exception e) {
                try {
                    if (conn != null) {
                        conn.rollback();
                    }
                } catch (SQLException rollbackEx) {
                    logger.error("Error rolling back transaction", rollbackEx);
                }
                
                logger.error("Error in transaction", e);
                throw new RuntimeException("Transaction error", e);
                
            } finally {
                try {
                    if (conn != null) {
                        conn.setAutoCommit(true);
                        conn.close();
                    }
                } catch (SQLException closeEx) {
                    logger.error("Error closing connection", closeEx);
                }
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<int[]> executeBatch(QueryBuilder builder, List<Object[]> batchParams) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = builder.build();
            
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                for (Object[] params : batchParams) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
                    stmt.addBatch();
                }
                
                int[] results = stmt.executeBatch();
                conn.commit();
                
                return results;
                
            } catch (SQLException e) {
                logger.error("Error executing batch: " + sql, e);
                throw new RuntimeException("Database batch error", e);
            }
        }, databaseExecutor);
    }

    /**
     * Execute a query and map the ResultSet using a custom function (for scalar or custom results).
     */
    public <T> CompletableFuture<T> executeQueryCustom(QueryBuilder builder, Function<ResultSet, T> mapper) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = builder.build();
            Object[] params = builder.getParameters();
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = prepareStatement(conn, sql, params);
                 ResultSet rs = stmt.executeQuery()) {
                return mapper.apply(rs);
            } catch (SQLException e) {
                logger.error("Error executing custom query: " + sql, e);
                throw new RuntimeException("Database query error", e);
            }
        }, databaseExecutor);
    }

    /**
     * Prepare a statement with the given parameters.
     *
     * @param conn The database connection
     * @param sql The SQL statement
     * @param params The statement parameters
     * @return The prepared statement
     * @throws SQLException If a database access error occurs
     */
    private PreparedStatement prepareStatement(Connection conn, String sql, Object[] params) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(sql);
        
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
        }
        
        return stmt;
    }

    /**
     * Prepare a statement with the given parameters and return generated keys.
     *
     * @param conn The database connection
     * @param sql The SQL statement
     * @param params The statement parameters
     * @return The prepared statement
     * @throws SQLException If a database access error occurs
     */
    private PreparedStatement prepareStatementWithKeys(Connection conn, String sql, Object[] params) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
        }
        
        return stmt;
    }

    /**
     * Map a result set to a DTO.
     *
     * @param rs The result set
     * @param dtoClass The DTO class
     * @return The mapped DTO
     * @throws SQLException If a database access error occurs
     * @throws ReflectiveOperationException If a reflection error occurs
     */
    private <T> T mapResultToDto(ResultSet rs, Class<T> dtoClass) throws SQLException, ReflectiveOperationException {
        ResultSetMetaData metaData = rs.getMetaData();
        Map<String, Object> rowData = new HashMap<>();
        
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String columnName = metaData.getColumnLabel(i).toLowerCase();
            Object value = rs.getObject(i);
            rowData.put(columnName, value);
        }
        
        // Create a new instance of the DTO
        Constructor<T> constructor = dtoClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        T dto = constructor.newInstance();
        
        // Map the result set columns to DTO fields
        for (Field field : dtoClass.getDeclaredFields()) {
            field.setAccessible(true);
            String fieldName = field.getName().toLowerCase();
            
            // Handle camelCase to snake_case conversion
            String snakeCaseName = camelToSnakeCase(fieldName);
            
            if (rowData.containsKey(fieldName)) {
                setFieldValue(field, dto, rowData.get(fieldName));
            } else if (rowData.containsKey(snakeCaseName)) {
                setFieldValue(field, dto, rowData.get(snakeCaseName));
            }
        }
        
        return dto;
    }

    /**
     * Set a field value on a DTO.
     *
     * @param field The field to set
     * @param dto The DTO instance
     * @param value The value to set
     * @throws IllegalAccessException If a reflection error occurs
     */
    private void setFieldValue(Field field, Object dto, Object value) throws IllegalAccessException {
        if (value == null) {
            field.set(dto, null);
            return;
        }
        
        Class<?> fieldType = field.getType();
        
        if (fieldType.isAssignableFrom(value.getClass())) {
            field.set(dto, value);
        } else if (fieldType == int.class || fieldType == Integer.class) {
            field.set(dto, ((Number) value).intValue());
        } else if (fieldType == long.class || fieldType == Long.class) {
            field.set(dto, ((Number) value).longValue());
        } else if (fieldType == double.class || fieldType == Double.class) {
            field.set(dto, ((Number) value).doubleValue());
        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
            if (value instanceof Number) {
                field.set(dto, ((Number) value).intValue() != 0);
            } else if (value instanceof String) {
                field.set(dto, Boolean.parseBoolean((String) value));
            } else {
                field.set(dto, value);
            }
        } else if (fieldType.isEnum() && value instanceof String) {
            try {
                @SuppressWarnings("unchecked")
                Enum<?> enumValue = Enum.valueOf(fieldType.asSubclass(Enum.class), (String) value);
                field.set(dto, enumValue);
            } catch (IllegalArgumentException e) {
                logger.warning("Failed to convert value to enum: " + value + " for field " + field.getName());
            }
        } else if (fieldType == String.class) {
            field.set(dto, String.valueOf(value));
        } else {
            logger.warning("Type mismatch for field " + field.getName() + ": expected " + fieldType.getName() + ", got " + value.getClass().getName());
        }
    }

    /**
     * Convert a camelCase string to snake_case.
     *
     * @param camelCase The camelCase string
     * @return The snake_case string
     */
    private String camelToSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}

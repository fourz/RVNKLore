package org.fourz.RVNKLore.data.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL-specific implementation of QueryBuilder.
 * Handles MySQL dialect and upsert syntax.
 */
public class MySQLQueryBuilder implements QueryBuilder {
    private enum QueryType {
        SELECT, INSERT, UPDATE, DELETE
    }
    
    private QueryType queryType;
    private String table;
    private List<String> columns = new ArrayList<>();
    private List<Object> parameters = new ArrayList<>();
    private StringBuilder whereClause = new StringBuilder();
    private StringBuilder orderByClause = new StringBuilder();
    private StringBuilder groupByClause = new StringBuilder();
    private StringBuilder havingClause = new StringBuilder();
    private StringBuilder joinClause = new StringBuilder();
    private Map<String, Object> updateValues = new LinkedHashMap<>();
    private List<List<Object>> insertValues = new ArrayList<>();
    private Integer limitValue;
    private Integer offsetValue;
    private boolean hasWhere = false;

    public MySQLQueryBuilder() {
        // Default constructor
    }

    @Override
    public QueryBuilder select(String... columns) {
        this.queryType = QueryType.SELECT;
        this.columns.addAll(Arrays.asList(columns));
        return this;
    }

    @Override
    public QueryBuilder from(String table) {
        this.table = table;
        return this;
    }

    @Override
    public QueryBuilder where(String condition, Object... params) {
        if (whereClause.length() > 0) {
            whereClause.append(" AND ");
        }
        whereClause.append(condition);
        parameters.addAll(Arrays.asList(params));
        hasWhere = true;
        return this;
    }

    @Override
    public QueryBuilder and(String condition, Object... params) {
        if (!hasWhere) {
            return where(condition, params);
        }
        whereClause.append(" AND ").append(condition);
        parameters.addAll(Arrays.asList(params));
        return this;
    }

    @Override
    public QueryBuilder or(String condition, Object... params) {
        if (!hasWhere) {
            return where(condition, params);
        }
        whereClause.append(" OR ").append(condition);
        parameters.addAll(Arrays.asList(params));
        return this;
    }

    @Override
    public QueryBuilder orderBy(String column, boolean ascending) {
        if (orderByClause.length() > 0) {
            orderByClause.append(", ");
        }
        orderByClause.append(column).append(ascending ? " ASC" : " DESC");
        return this;
    }

    @Override
    public QueryBuilder groupBy(String... columns) {
        if (groupByClause.length() > 0) {
            groupByClause.append(", ");
        }
        groupByClause.append(String.join(", ", columns));
        return this;
    }

    @Override
    public QueryBuilder having(String condition, Object... params) {
        if (havingClause.length() > 0) {
            havingClause.append(" AND ");
        }
        havingClause.append(condition);
        parameters.addAll(Arrays.asList(params));
        return this;
    }

    @Override
    public QueryBuilder limit(int limit) {
        this.limitValue = limit;
        return this;
    }

    @Override
    public QueryBuilder offset(int offset) {
        this.offsetValue = offset;
        return this;
    }

    @Override
    public QueryBuilder insertInto(String table) {
        this.queryType = QueryType.INSERT;
        this.table = table;
        return this;
    }

    @Override
    public QueryBuilder columns(String... columns) {
        this.columns.addAll(Arrays.asList(columns));
        return this;
    }

    @Override
    public QueryBuilder values(Object... values) {
        List<Object> valuesList = Arrays.asList(values);
        insertValues.add(valuesList);
        parameters.addAll(valuesList);
        return this;
    }

    @Override
    public QueryBuilder update(String table) {
        this.queryType = QueryType.UPDATE;
        this.table = table;
        return this;
    }

    @Override
    public QueryBuilder set(String column, Object value) {
        updateValues.put(column, value);
        parameters.add(value);
        return this;
    }

    @Override
    public QueryBuilder deleteFrom(String table) {
        this.queryType = QueryType.DELETE;
        this.table = table;
        return this;
    }

    @Override
    public QueryBuilder join(String table, String condition) {
        joinClause.append(" JOIN ").append(table).append(" ON ").append(condition);
        return this;
    }

    @Override
    public QueryBuilder leftJoin(String table, String condition) {
        joinClause.append(" LEFT JOIN ").append(table).append(" ON ").append(condition);
        return this;
    }

    @Override
    public String build() {
        StringBuilder query = new StringBuilder();
        
        switch (queryType) {
            case SELECT:
                buildSelectQuery(query);
                break;
            case INSERT:
                buildInsertQuery(query);
                break;
            case UPDATE:
                buildUpdateQuery(query);
                break;
            case DELETE:
                buildDeleteQuery(query);
                break;
            default:
                throw new IllegalStateException("Query type not set");
        }
        
        return query.toString();
    }

    private void buildSelectQuery(StringBuilder query) {
        query.append("SELECT ");
        
        if (columns.isEmpty()) {
            query.append("*");
        } else {
            query.append(String.join(", ", columns));
        }
        
        query.append(" FROM ").append(table);
        
        if (joinClause.length() > 0) {
            query.append(joinClause);
        }
        
        if (whereClause.length() > 0) {
            query.append(" WHERE ").append(whereClause);
        }
        
        if (groupByClause.length() > 0) {
            query.append(" GROUP BY ").append(groupByClause);
        }
        
        if (havingClause.length() > 0) {
            query.append(" HAVING ").append(havingClause);
        }
        
        if (orderByClause.length() > 0) {
            query.append(" ORDER BY ").append(orderByClause);
        }
        
        if (limitValue != null) {
            query.append(" LIMIT ").append(limitValue);
        }
        
        if (offsetValue != null) {
            query.append(" OFFSET ").append(offsetValue);
        }
    }

    private void buildInsertQuery(StringBuilder query) {
        query.append("INSERT INTO ").append(table).append(" (");
        query.append(String.join(", ", columns));
        query.append(") VALUES ");
        
        for (int i = 0; i < insertValues.size(); i++) {
            if (i > 0) {
                query.append(", ");
            }
            
            query.append("(");
            for (int j = 0; j < columns.size(); j++) {
                if (j > 0) {
                    query.append(", ");
                }
                query.append("?");
            }
            query.append(")");
        }
    }

    private void buildUpdateQuery(StringBuilder query) {
        query.append("UPDATE ").append(table).append(" SET ");
        
        int i = 0;
        for (Map.Entry<String, Object> entry : updateValues.entrySet()) {
            if (i > 0) {
                query.append(", ");
            }
            query.append(entry.getKey()).append(" = ?");
            i++;
        }
        
        if (whereClause.length() > 0) {
            query.append(" WHERE ").append(whereClause);
        }
        
        if (limitValue != null) {
            query.append(" LIMIT ").append(limitValue);
        }
    }

    private void buildDeleteQuery(StringBuilder query) {
        query.append("DELETE FROM ").append(table);
        
        if (whereClause.length() > 0) {
            query.append(" WHERE ").append(whereClause);
        }
        
        if (limitValue != null) {
            query.append(" LIMIT ").append(limitValue);
        }
    }

    @Override
    public Object[] getParameters() {
        return parameters.toArray();
    }

    /**
     * Creates an upsert query for MySQL (INSERT ... ON DUPLICATE KEY UPDATE).
     * This is MySQL-specific functionality.
     * 
     * @param updateColumns The columns to update on duplicate key
     * @return This QueryBuilder for chaining
     */
    public QueryBuilder onDuplicateKeyUpdate(String... updateColumns) {
        if (queryType != QueryType.INSERT) {
            throw new IllegalStateException("ON DUPLICATE KEY UPDATE can only be used with INSERT queries");
        }
        
        StringBuilder query = new StringBuilder(build());
        query.append(" ON DUPLICATE KEY UPDATE ");
        
        for (int i = 0; i < updateColumns.length; i++) {
            if (i > 0) {
                query.append(", ");
            }
            String column = updateColumns[i];
            query.append(column).append(" = VALUES(").append(column).append(")");
        }
        
        // We need to override the build() method's result with our custom query
        final String finalQuery = query.toString();
        
        return new MySQLQueryBuilder() {
            @Override
            public String build() {
                return finalQuery;
            }
            
            @Override
            public Object[] getParameters() {
                return MySQLQueryBuilder.this.getParameters();
            }
        };
    }
}

package org.fourz.RVNKLore.data.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * SQLite-specific implementation of QueryBuilder.
 * Handles SQLite dialect and upsert syntax.
 */
public class SQLiteQueryBuilder implements QueryBuilder {
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
    private String customSql = null;

    public SQLiteQueryBuilder() {
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
    }    @Override
    public QueryBuilder innerJoin(String table, String condition, Object... params) {
        joinClause.append(" INNER JOIN ").append(table).append(" ON ").append(condition);
        if (params != null && params.length > 0) {
            parameters.addAll(Arrays.asList(params));
        }
        return this;
    }

    @Override
    public QueryBuilder leftJoin(String table, String condition, Object... params) {
        joinClause.append(" LEFT JOIN ").append(table).append(" ON ").append(condition);
        if (params != null && params.length > 0) {
            parameters.addAll(Arrays.asList(params));
        }
        return this;
    }
    
    @Override
    public QueryBuilder rightJoin(String table, String condition, Object... params) {
        // SQLite doesn't support RIGHT JOIN directly
        throw new UnsupportedOperationException("RIGHT JOIN not supported in SQLite");
    }
    
    @Override
    public QueryBuilder fullOuterJoin(String table, String condition, Object... params) {
        // SQLite doesn't support FULL OUTER JOIN directly
        throw new UnsupportedOperationException("FULL OUTER JOIN not supported in SQLite");
    }    @Override
    public String build() {
        if (customSql != null) {
            return customSql;
        }
        
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
    }    private void buildInsertQuery(StringBuilder query) {
        query.append("INSERT INTO ").append(table);
        
        if (!columns.isEmpty()) {
            query.append(" (").append(String.join(", ", columns)).append(")");
        }
        
        if (!insertValues.isEmpty()) {
            query.append(" VALUES ");
            List<String> valueGroups = new ArrayList<>();
            for (int i = 0; i < insertValues.size(); i++) {
                List<Object> values = insertValues.get(i);
                valueGroups.add("(" + String.join(", ", Collections.nCopies(values.size(), "?")) + ")");
            }
            query.append(String.join(", ", valueGroups));
        }
    }    private void buildUpdateQuery(StringBuilder query) {
        query.append("UPDATE ").append(table).append(" SET ");
        
        List<String> setExpressions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : updateValues.entrySet()) {
            setExpressions.add(entry.getKey() + " = ?");
        }
        query.append(String.join(", ", setExpressions));
        
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
    }    /**
     * Creates an upsert query for SQLite (INSERT OR REPLACE).
     * This is SQLite-specific functionality.
     * 
     * @return This QueryBuilder for chaining
     */
    public QueryBuilder insertOrReplace() {
        if (queryType != QueryType.INSERT) {
            throw new IllegalStateException("INSERT OR REPLACE can only be used with INSERT queries");
        }
        
        StringBuilder query = new StringBuilder("INSERT OR REPLACE INTO ");
        query.append(table).append(" (");
        query.append(String.join(", ", columns));
        query.append(") VALUES ");
        
        List<String> valueGroups = new ArrayList<>();
        for (int i = 0; i < insertValues.size(); i++) {
            List<Object> values = insertValues.get(i);
            valueGroups.add("(" + String.join(", ", Collections.nCopies(values.size(), "?")) + ")");
        }
        query.append(String.join(", ", valueGroups));
        
        this.customSql = query.toString();
        return this;
    }@Override
    public QueryBuilder raw(String sql, Object... params) {
        this.customSql = sql;
        if (params != null && params.length > 0) {
            parameters.addAll(Arrays.asList(params));
        }
        return this;
    }
}

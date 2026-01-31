package org.fourz.RVNKLore.data.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * SQLite-specific SQL dialect implementation.
 *
 * <p>SQLite characteristics:
 * <ul>
 *   <li>Uses AUTOINCREMENT for auto-increment columns</li>
 *   <li>Supports RETURNING clause for generated keys (SQLite 3.35+)</li>
 *   <li>Uses ON CONFLICT ... DO UPDATE for upserts</li>
 *   <li>Uses INSERT OR REPLACE for replace operations</li>
 *   <li>Table metadata in sqlite_master</li>
 * </ul>
 */
public class SQLiteDialect implements SQLDialect {

    @Override
    public String getName() {
        return "SQLite";
    }

    // ============== DDL Generation ==============

    @Override
    public String getAutoIncrementPK() {
        return "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    @Override
    public String getBooleanType() {
        return "BOOLEAN";
    }

    @Override
    public String getTextType(int maxLength) {
        if (maxLength <= 0) {
            return "TEXT";
        }
        // SQLite treats VARCHAR(n) as TEXT, but we keep it for documentation
        return "VARCHAR(" + maxLength + ")";
    }

    @Override
    public String getTimestampType(boolean withDefault) {
        if (withDefault) {
            return "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
        }
        return "TIMESTAMP";
    }

    // ============== Query Generation ==============

    @Override
    public String getTableExistsQuery(String tableName) {
        return "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
    }

    @Override
    public String wrapInsertForGeneratedKey(String insertSql, String idColumn) {
        // SQLite 3.35+ supports RETURNING clause
        return insertSql + " RETURNING " + idColumn;
    }

    @Override
    public int extractGeneratedId(PreparedStatement stmt, ResultSet rs, String idColumn) throws SQLException {
        // For SQLite with RETURNING clause, the ID is in the ResultSet
        if (rs != null && rs.next()) {
            return rs.getInt(idColumn);
        }
        return -1;
    }

    @Override
    public String getUpsertSQL(String table, String[] keyColumns, String[] allColumns, String[] updateColumns) {
        StringBuilder sql = new StringBuilder();

        // INSERT INTO table (col1, col2, ...) VALUES (?, ?, ...)
        sql.append("INSERT INTO ").append(table).append(" (");
        sql.append(String.join(", ", allColumns));
        sql.append(") VALUES (");
        sql.append(placeholders(allColumns.length));
        sql.append(")");

        // ON CONFLICT(key1, key2) DO UPDATE SET
        sql.append(" ON CONFLICT(");
        sql.append(String.join(", ", keyColumns));
        sql.append(") DO UPDATE SET ");

        // col1 = ?, col2 = ?
        for (int i = 0; i < updateColumns.length; i++) {
            if (i > 0) sql.append(", ");
            sql.append(updateColumns[i]).append(" = ?");
        }

        return sql.toString();
    }

    @Override
    public String getReplaceSQL(String table, String[] columns) {
        StringBuilder sql = new StringBuilder();

        // INSERT OR REPLACE INTO table (col1, col2, ...) VALUES (?, ?, ...)
        sql.append("INSERT OR REPLACE INTO ").append(table).append(" (");
        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        sql.append(placeholders(columns.length));
        sql.append(")");

        return sql.toString();
    }

    @Override
    public boolean requiresGeneratedKeysFlag() {
        // SQLite uses RETURNING clause, not getGeneratedKeys()
        return false;
    }

    @Override
    public boolean upsertNeedsDuplicateBinding() {
        // SQLite: ON CONFLICT ... DO UPDATE SET col = ? needs separate binding
        return true;
    }

    // ============== Helper Methods ==============

    /**
     * Generate placeholder string for prepared statement.
     * @param count Number of placeholders
     * @return "?, ?, ..." string
     */
    private String placeholders(int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder("?");
        for (int i = 1; i < count; i++) {
            sb.append(", ?");
        }
        return sb.toString();
    }
}

package org.fourz.RVNKLore.data.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MySQL-specific SQL dialect implementation.
 *
 * <p>MySQL characteristics:
 * <ul>
 *   <li>Uses AUTO_INCREMENT for auto-increment columns</li>
 *   <li>Uses getGeneratedKeys() for retrieving generated IDs</li>
 *   <li>Uses ON DUPLICATE KEY UPDATE for upserts</li>
 *   <li>Uses REPLACE INTO for replace operations</li>
 *   <li>Table metadata in information_schema</li>
 * </ul>
 */
public class MySQLDialect implements SQLDialect {

    @Override
    public String getName() {
        return "MySQL";
    }

    // ============== DDL Generation ==============

    @Override
    public String getAutoIncrementPK() {
        return "INT AUTO_INCREMENT PRIMARY KEY";
    }

    @Override
    public String getBooleanType() {
        // MySQL BOOLEAN is alias for TINYINT(1)
        return "TINYINT(1)";
    }

    @Override
    public String getTextType(int maxLength) {
        if (maxLength <= 0) {
            return "TEXT";
        }
        if (maxLength <= 255) {
            return "VARCHAR(" + maxLength + ")";
        }
        if (maxLength <= 65535) {
            return "TEXT";
        }
        return "MEDIUMTEXT";
    }

    @Override
    public String getTimestampType(boolean withDefault) {
        if (withDefault) {
            return "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
        }
        return "TIMESTAMP NULL";
    }

    // ============== Query Generation ==============

    @Override
    public String getTableExistsQuery(String tableName) {
        // Uses information_schema to check table existence
        return "SELECT TABLE_NAME FROM information_schema.TABLES " +
               "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
    }

    @Override
    public String wrapInsertForGeneratedKey(String insertSql, String idColumn) {
        // MySQL doesn't use RETURNING; we rely on getGeneratedKeys()
        // Return the SQL unchanged
        return insertSql;
    }

    @Override
    public int extractGeneratedId(PreparedStatement stmt, ResultSet rs, String idColumn) throws SQLException {
        // For MySQL, use getGeneratedKeys() on the statement
        try (ResultSet keys = stmt.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getInt(1);
            }
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

        // ON DUPLICATE KEY UPDATE col1 = VALUES(col1), col2 = VALUES(col2)
        sql.append(" ON DUPLICATE KEY UPDATE ");
        for (int i = 0; i < updateColumns.length; i++) {
            if (i > 0) sql.append(", ");
            // MySQL 8.0.19+ prefers col = ? syntax over VALUES()
            // But VALUES() is more compatible with older versions
            sql.append(updateColumns[i]).append(" = VALUES(").append(updateColumns[i]).append(")");
        }

        return sql.toString();
    }

    @Override
    public String getReplaceSQL(String table, String[] columns) {
        StringBuilder sql = new StringBuilder();

        // REPLACE INTO table (col1, col2, ...) VALUES (?, ?, ...)
        sql.append("REPLACE INTO ").append(table).append(" (");
        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        sql.append(placeholders(columns.length));
        sql.append(")");

        return sql.toString();
    }

    @Override
    public boolean requiresGeneratedKeysFlag() {
        // MySQL needs Statement.RETURN_GENERATED_KEYS flag
        return true;
    }

    @Override
    public boolean upsertNeedsDuplicateBinding() {
        // MySQL: ON DUPLICATE KEY UPDATE col = VALUES(col) reuses insert values
        return false;
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

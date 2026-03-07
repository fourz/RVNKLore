package org.fourz.RVNKLore.data.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Abstraction for database-specific SQL syntax differences.
 * Enables the same repository code to work with both SQLite and MySQL.
 *
 * <p>Key differences handled:
 * <ul>
 *   <li>Auto-increment syntax (AUTOINCREMENT vs AUTO_INCREMENT)</li>
 *   <li>Generated key retrieval (RETURNING vs LAST_INSERT_ID)</li>
 *   <li>Upsert syntax (ON CONFLICT vs ON DUPLICATE KEY)</li>
 *   <li>Replace syntax (INSERT OR REPLACE vs REPLACE INTO)</li>
 *   <li>Table existence checks (sqlite_master vs information_schema)</li>
 * </ul>
 */
public interface SQLDialect {

    /**
     * Get the dialect name for logging/debugging.
     * @return "SQLite" or "MySQL"
     */
    String getName();

    // ============== DDL Generation ==============

    /**
     * Get the syntax for an auto-incrementing integer primary key.
     * @return "INTEGER PRIMARY KEY AUTOINCREMENT" for SQLite,
     *         "INT AUTO_INCREMENT PRIMARY KEY" for MySQL
     */
    String getAutoIncrementPK();

    /**
     * Get the SQL type for boolean columns.
     * @return "BOOLEAN" for SQLite, "TINYINT(1)" for MySQL
     */
    String getBooleanType();

    /**
     * Get the SQL type for text/string columns.
     * @param maxLength Maximum length (-1 for unlimited TEXT)
     * @return "TEXT" for unlimited, "VARCHAR(n)" for limited
     */
    String getTextType(int maxLength);

    /**
     * Get timestamp column definition with optional default.
     * @param withDefault true to include DEFAULT CURRENT_TIMESTAMP
     * @return Column type string
     */
    String getTimestampType(boolean withDefault);

    // ============== Query Generation ==============

    /**
     * Get the SQL query to check if a table exists.
     * @param tableName Name of the table to check
     * @return Parameterized SQL that returns a row if table exists
     */
    String getTableExistsQuery(String tableName);

    /**
     * Wrap an INSERT statement to return the generated ID.
     * For SQLite, appends "RETURNING id".
     * For MySQL, prepares for getGeneratedKeys() usage.
     *
     * @param insertSql The INSERT SQL without RETURNING clause
     * @param idColumn The name of the auto-generated ID column
     * @return Modified SQL for the dialect
     */
    String wrapInsertForGeneratedKey(String insertSql, String idColumn);

    /**
     * Extract the generated ID after an insert.
     * For SQLite, reads from ResultSet (RETURNING clause).
     * For MySQL, uses Statement.getGeneratedKeys().
     *
     * @param stmt The PreparedStatement that executed the insert
     * @param rs The ResultSet from the insert (may be null for MySQL)
     * @param idColumn The name of the ID column
     * @return The generated ID, or -1 if not available
     * @throws SQLException if database access fails
     */
    int extractGeneratedId(PreparedStatement stmt, ResultSet rs, String idColumn) throws SQLException;

    /**
     * Generate an UPSERT (INSERT or UPDATE) statement.
     * SQLite: INSERT ... ON CONFLICT(keys) DO UPDATE SET ...
     * MySQL: INSERT ... ON DUPLICATE KEY UPDATE ...
     *
     * @param table Table name
     * @param keyColumns Columns that form the unique constraint
     * @param allColumns All columns being inserted
     * @param updateColumns Columns to update on conflict (subset of allColumns)
     * @return Complete UPSERT SQL with placeholders
     */
    String getUpsertSQL(String table, String[] keyColumns, String[] allColumns, String[] updateColumns);

    /**
     * Generate a REPLACE statement (insert or replace entire row).
     * SQLite: INSERT OR REPLACE INTO ...
     * MySQL: REPLACE INTO ...
     *
     * @param table Table name
     * @param columns Column names
     * @return Complete REPLACE SQL with placeholders
     */
    String getReplaceSQL(String table, String[] columns);

    /**
     * Check if this dialect requires Statement.RETURN_GENERATED_KEYS flag.
     * @return true for MySQL, false for SQLite (uses RETURNING clause)
     */
    boolean requiresGeneratedKeysFlag();

    /**
     * Check if upsert requires duplicate parameter binding for update values.
     * SQLite: ON CONFLICT ... DO UPDATE SET col = ? (needs separate binding)
     * MySQL: ON DUPLICATE KEY UPDATE col = VALUES(col) (reuses insert values)
     * @return true for SQLite (needs duplicate binding), false for MySQL
     */
    boolean upsertNeedsDuplicateBinding();
}

package org.fourz.RVNKLore.data.query;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of the SchemaQueryBuilder interface.
 * Handles SQLite-specific syntax for schema operations.
 */
public class SQLiteSchemaQueryBuilder implements SchemaQueryBuilder {

    @Override
    public TableBuilder createTable(String tableName) {
        return new SQLiteTableBuilder(tableName);
    }

    @Override
    public String getAutoIncrementSyntax() {
        return "AUTOINCREMENT";
    }

    /**
     * SQLite-specific implementation of the TableBuilder interface.
     */
    private static class SQLiteTableBuilder implements TableBuilder {
        private final String tableName;
        private final List<String> columns = new ArrayList<>();
        private final List<String> primaryKeys = new ArrayList<>();
        private final List<String> foreignKeys = new ArrayList<>();
        private final List<String> indexes = new ArrayList<>();

        public SQLiteTableBuilder(String tableName) {
            this.tableName = tableName;
        }

        @Override
        public TableBuilder column(String name, String type, String constraints) {
            columns.add(String.format("\"%s\" %s %s", name, type, constraints));
            return this;
        }

        @Override
        public TableBuilder primaryKey(String columns) {
            primaryKeys.add(String.format("PRIMARY KEY (%s)", columns));
            return this;
        }

        @Override
        public TableBuilder foreignKey(String column, String referenceTable, String referenceColumn) {
            foreignKeys.add(String.format("FOREIGN KEY (\"%s\") REFERENCES \"%s\"(\"%s\") ON DELETE CASCADE",
                    column, referenceTable, referenceColumn));
            return this;
        }

        @Override
        public TableBuilder index(String name, String columns) {
            // SQLite doesn't support indexes in CREATE TABLE, so we'll store them for later
            indexes.add(String.format("CREATE INDEX IF NOT EXISTS \"%s\" ON \"%s\" (%s);",
                    name, tableName, columns));
            return this;
        }

        @Override
        public String build() {
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE IF NOT EXISTS \"").append(tableName).append("\" (\n  ");

            List<String> tableDefinitions = new ArrayList<>(columns);
            tableDefinitions.addAll(primaryKeys);
            tableDefinitions.addAll(foreignKeys);

            sb.append(String.join(",\n  ", tableDefinitions));
            sb.append("\n);");

            // Add indexes as separate statements
            for (String index : indexes) {
                sb.append("\n").append(index);
            }

            return sb.toString();
        }
    }
}

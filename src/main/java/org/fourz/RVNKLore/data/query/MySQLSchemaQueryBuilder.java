package org.fourz.RVNKLore.data.query;

import java.util.ArrayList;
import java.util.List;

/**
 * MySQL implementation of the SchemaQueryBuilder interface.
 * Handles MySQL-specific syntax for schema operations.
 */
public class MySQLSchemaQueryBuilder implements SchemaQueryBuilder {

    @Override
    public TableBuilder createTable(String tableName) {
        return new MySQLTableBuilder(tableName);
    }

    @Override
    public String getAutoIncrementSyntax() {
        return "AUTO_INCREMENT";
    }

    /**
     * MySQL-specific implementation of the TableBuilder interface.
     */
    private static class MySQLTableBuilder implements TableBuilder {
        private final String tableName;
        private final List<String> columns = new ArrayList<>();
        private final List<String> primaryKeys = new ArrayList<>();
        private final List<String> foreignKeys = new ArrayList<>();
        private final List<String> indexes = new ArrayList<>();

        public MySQLTableBuilder(String tableName) {
            this.tableName = tableName;
        }

        @Override
        public TableBuilder column(String name, String type, String constraints) {
            columns.add(String.format("`%s` %s %s", name, type, constraints));
            return this;
        }

        @Override
        public TableBuilder primaryKey(String columns) {
            primaryKeys.add(String.format("PRIMARY KEY (%s)", columns));
            return this;
        }

        @Override
        public TableBuilder foreignKey(String column, String referenceTable, String referenceColumn) {
            foreignKeys.add(String.format("FOREIGN KEY (`%s`) REFERENCES `%s`(`%s`) ON DELETE CASCADE",
                    column, referenceTable, referenceColumn));
            return this;
        }

        @Override
        public TableBuilder index(String name, String columns) {
            indexes.add(String.format("INDEX `%s` (%s)", name, columns));
            return this;
        }

        @Override
        public String build() {
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE IF NOT EXISTS `").append(tableName).append("` (\n  ");

            List<String> allDefinitions = new ArrayList<>(columns);
            allDefinitions.addAll(primaryKeys);
            allDefinitions.addAll(foreignKeys);
            allDefinitions.addAll(indexes);

            sb.append(String.join(",\n  ", allDefinitions));
            sb.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

            return sb.toString();
        }
    }
}

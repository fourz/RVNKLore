package org.fourz.RVNKLore.data.query;

/**
 * Interface for building SQL queries specific to database schema operations.
 * Provides methods for creating tables and other schema-related operations.
 */
public interface SchemaQueryBuilder {
    
    /**
     * Start building a CREATE TABLE query.
     *
     * @param tableName The name of the table to create
     * @return A builder for constructing the table schema
     */
    TableBuilder createTable(String tableName);
    
    /**
     * Get the database-specific auto increment syntax.
     *
     * @return The auto increment syntax for the current database
     */
    String getAutoIncrementSyntax();
    
    /**
     * Builder interface for constructing table schemas.
     */
    interface TableBuilder {
        /**
         * Add a column to the table.
         *
         * @param name The column name
         * @param type The column data type
         * @param constraints Additional constraints (e.g., "NOT NULL", "DEFAULT 0")
         * @return The table builder for method chaining
         */
        TableBuilder column(String name, String type, String constraints);
        
        /**
         * Add a primary key constraint to the table.
         *
         * @param columns The columns that form the primary key
         * @return The table builder for method chaining
         */
        TableBuilder primaryKey(String columns);
        
        /**
         * Add a foreign key constraint to the table.
         *
         * @param column The column name
         * @param referenceTable The referenced table
         * @param referenceColumn The referenced column
         * @return The table builder for method chaining
         */
        TableBuilder foreignKey(String column, String referenceTable, String referenceColumn);
        
        /**
         * Add an index to the table.
         *
         * @param name The index name
         * @param columns The columns to include in the index
         * @return The table builder for method chaining
         */
        TableBuilder index(String name, String columns);
        
        /**
         * Build the complete CREATE TABLE statement.
         *
         * @return The SQL CREATE TABLE statement
         */
        String build();
    }
}

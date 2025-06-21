# RVNKLore Database Architecture Copilot Instructions

## Architecture Overview

- `DatabaseManager`: Central entry point for database operations, manages connections and validation
- `DatabaseSetup`: Handles table creation and initialization, separate from the main database operations
- `ConnectionProvider`: Interface for different database backends (MySQL, SQLite)
- `QueryBuilder`: Interface for building queries compatible with different database dialects
- `SchemaQueryBuilder`: Interface for schema operations (table creation, indexes)

## Code Constraints

1. **Separation of Concerns**
   - Keep table validation in `DatabaseManager`
   - Keep table creation in `DatabaseSetup`
   - Keep connection management in `ConnectionProvider` implementations

2. **Async Operations**
   - All database operations should be performed asynchronously
   - Use `CompletableFuture` for async database operations
   - Never block the main thread with database operations

3. **Error Handling**
   - Log detailed error messages with context
   - Use appropriate exception types
   - Ensure all resources are properly closed in finally blocks or try-with-resources

4. **Schema Changes**
   - Do not create data migration methods unless explicitly asked
   - Assume no data in the database when making schema changes
   - Use the SchemaQueryBuilder interface for all schema operations

## Implementation Guidelines

1. **For Database Setup**
   - Use `SchemaQueryBuilder.createTable()` method for creating tables
   - Define all constraints (PRIMARY KEY, FOREIGN KEY) in the table creation
   - Create indexes as part of the table creation
   - Handle dialect differences through the SchemaQueryBuilder interface

2. **For Table Validation**
   - Validate tables exist in `DatabaseManager`
   - Check for required columns and their data types
   - Validate indexes and constraints

3. **For Query Execution**
   - Use QueryExecutor for executing non-schema queries
   - Handle result sets and parameter binding through the executor
   - Use prepared statements for all queries with user input

## Best Practices

1. **Logging**
   - Use LogManager for all logging
   - Log the start and completion of major operations
   - Include detailed context in error messages

2. **Transaction Management**
   - Use transactions for multi-step operations
   - Ensure proper commit/rollback handling
   - Use try-with-resources for managing connections

3. **Resource Cleanup**
   - Always close connections, statements, and result sets
   - Use try-with-resources for automatic cleanup
   - Implement proper cleanup in shutdown methods

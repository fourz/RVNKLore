# DatabaseSetup Class Copilot Instructions

## Class Purpose
- Handles database table creation and initialization
- Separate from the main DatabaseManager class
- Uses SchemaQueryBuilder for database-agnostic table creation

## Key Methods
- `initializeTables()`: Creates all database tables if they don't exist
- Table-specific creation methods (createPlayerTable, createLoreEntryTable, etc.)

## Usage
- Instantiated by DatabaseManager during initialization
- Called after connection provider and query builders are set up
- Should execute asynchronously to avoid blocking the main thread
- **Repository Access Pattern:**
  - All data access (CRUD, queries) should be performed through the appropriate repository class (e.g., `LoreEntryRepository`, `SubmissionRepository`, `ItemRepository`).
  - `DatabaseManager` should only provide access to repositories via `getXRepository()` methods, not direct data access methods.
  - Client code should obtain the repository from `DatabaseManager` and call repository methods directly:
    ```java
    LoreEntryRepository repo = databaseManager.getLoreEntryRepository();
    repo.getLoreEntryById(id);
    ```
  - Do **not** add pass-through data access methods to `DatabaseManager` (e.g., `getLoreEntry(int id)` should be removed in favor of direct repository access).
  - This pattern enforces single responsibility and keeps `DatabaseManager` focused on connection, transaction, and schema management.

## Schema Requirements
- Use the SchemaQueryBuilder interface for all schema operations
- Handle dialect differences through the interface, not with conditional code
- Use proper column naming conventions (snake_case, singular table names)
- Follow standard constraints naming (e.g., foreign keys, indexes)

## Error Handling
- Log detailed error messages with context
- Throw exceptions for critical failures
- Ensure all resources are properly closed

## Dependencies
- RVNKLore plugin instance
- ConnectionProvider for database connections
- SchemaQueryBuilder for generating dialect-specific SQL
- QueryExecutor for executing queries

## Execution
- All operations should be performed asynchronously
- Use CompletableFuture for async operations
- Return appropriate futures for client code to handle
- Repositories should expose async methods for all data access.

## Schema Standards
- IDs: Primary keys should be named "id" (except for junction tables)
- Foreign keys: Use "entity_name_id" naming
- Timestamps: Use "created_at", "updated_at", etc.
- Booleans: Use "is_" or "has_" prefix
- Status fields: Use VARCHAR with predefined values

## Note
- Do not create data migration methods unless explicitly asked
- Assume no data in the database when making schema changes
- Follow the repository-access pattern for all data access (see above).

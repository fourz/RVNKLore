# RVNKLore Database Architecture

*Last Updated: June 22, 2025*

This document provides a detailed overview of the RVNKLore plugin's database architecture, including class relationships, design patterns, and implementation details.

## Architecture Overview

RVNKLore implements a sophisticated database layer using a multi-tiered architecture with clear separation of concerns:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Command Layer  │────▶│  Service Layer  │────▶│  Database Layer │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │                       │
        ▼                       ▼                       ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Domain Models  │◀───▶│     DTOs        │◀───▶│  SQL Generation │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

The system follows established design patterns:

- **Repository Pattern**: For database access abstraction
- **Data Transfer Objects (DTOs)**: For clean data flow between layers
- **Strategy Pattern**: For SQL dialect handling (MySQL/SQLite)
- **Factory Pattern**: For creating appropriate database components
- **Singleton Pattern**: For connection and logging management

## Core Components

### 1. DatabaseManager

The `DatabaseManager` acts as the central hub for database connection, transaction, and schema management. It is **not** responsible for direct data access or CRUD operations. Instead, it provides access to repository instances for each table/entity.

```java
public class DatabaseManager {
    private final RVNKLore plugin;
    // ...
    public LoreEntryRepository getLoreEntryRepository() { /*...*/ }
    public SubmissionRepository getSubmissionRepository() { /*...*/ }
    public ItemRepository getItemRepository() { /*...*/ }
    // ...
    public void shutdown() { /*...*/ }
}
```

**Key Responsibilities:**
- Initialize and manage database connections
- Provide access to repository instances via `getXRepository()` methods
- Manage database schema setup and validation (delegated to DatabaseSetup)
- Handle database type selection (MySQL/SQLite)
- Coordinate transaction boundaries and resource cleanup

**Repository-Access Pattern:**
- All data access (CRUD, queries) is performed through the appropriate repository class (e.g., `LoreEntryRepository`, `SubmissionRepository`, `ItemRepository`).
- `DatabaseManager` should **not** provide pass-through data access methods (e.g., `getLoreEntry(int id)` is removed in favor of `getLoreEntryRepository().getLoreEntryById(id)`).
- Client code should obtain the repository from `DatabaseManager` and call repository methods directly:
    ```java
    LoreEntryRepository repo = databaseManager.getLoreEntryRepository();
    repo.getLoreEntryById(id);
    ```

This pattern enforces single responsibility and keeps `DatabaseManager` focused on connection, transaction, and schema management.

### 2. Connection Management

Connection management is handled through the `ConnectionProvider` interface, with specific implementations for each database type:

```java
public interface ConnectionProvider {
    Connection getConnection() throws SQLException;
    void close();
    boolean isValid();
    // Other connection management methods
}

public class SQLiteConnectionProvider implements ConnectionProvider {
    private Connection connection;
    private boolean initialized = false;
    
    public synchronized void initializeConnection() { /*...*/ }
    // Implementation of interface methods
}

public class MySQLConnectionProvider implements ConnectionProvider {
    private final HikariDataSource dataSource;
    
    // Implementation of interface methods
}
```

**Connection Lifecycle:**
1. Connection initialization during plugin startup
2. Connection pooling (for MySQL) or single connection (for SQLite)
3. Connection health monitoring via `DatabaseHealthService`
4. Graceful shutdown during plugin disable

### 3. Query Building

The `QueryBuilder` interface and its implementations provide a clean abstraction for SQL dialect differences:

```java
public interface QueryBuilder {
    QueryBuilder select(String... columns);
    QueryBuilder from(String table);
    QueryBuilder where(String condition, Object... params);
    QueryBuilder orderBy(String column, boolean ascending);
    QueryBuilder limit(int limit);
    String build();
    Object[] getParameters();
}

public class MySQLQueryBuilder implements QueryBuilder {
    // MySQL-specific implementations
}

public class SQLiteQueryBuilder implements QueryBuilder {
    // SQLite-specific implementations
}
```

**Schema Management:**
The `SchemaQueryBuilder` interface extends this pattern for database schema operations:

```java
public interface SchemaQueryBuilder {
    SchemaQueryBuilder createTable(String tableName);
    SchemaQueryBuilder column(String name, String type, String constraints);
    SchemaQueryBuilder foreignKey(String column, String refTable, String refColumn);
    String getAutoIncrementSyntax();
    String build();
}
```

### 4. Query Execution

Query execution is managed through the `QueryExecutor` interface and its `DefaultQueryExecutor` implementation:

```java
public interface QueryExecutor {
    <T> CompletableFuture<T> executeQuery(QueryBuilder builder, Class<T> dtoClass);
    <T> CompletableFuture<List<T>> executeQueryList(QueryBuilder builder, Class<T> dtoClass);
    CompletableFuture<Integer> executeUpdate(QueryBuilder builder);
}

public class DefaultQueryExecutor implements QueryExecutor {
    private final ConnectionProvider connectionProvider;
    private final LogManager logger;
    
    // Implementation of interface methods with robust error handling
    // and automatic mapping between ResultSets and DTOs
}
```

**Key Features:**
- Asynchronous execution via `CompletableFuture`
- Automatic mapping between `ResultSet` and DTO objects
- Comprehensive error handling and logging
- Transaction support for multi-statement operations

### 5. Data Transfer Objects (DTOs)

DTOs provide a clean data transfer mechanism between database and domain layers:

```java
public class LoreEntryDTO {
    private String id;
    private String entryType;
    private String name;
    private String description;
    private Timestamp createdAt;
    private UUID submittedBy;
    private boolean approved;
    private Map<String, Object> metadata;
    
    // Getters, setters, and conversion methods
}

public class ItemPropertiesDTO {
    private String id;
    private String material;
    private String displayName;
    private String itemType;
    private String rarity;
    private int customModelData;
    private Map<String, Object> properties;
    
    // Getters, setters, and conversion methods
}
```

**DTO Lifecycle:**
1. Created by `QueryExecutor` when reading from database
2. Passed to domain objects for business logic
3. Updated by domain logic
4. Passed back to `QueryExecutor` for database persistence

### 6. Repository Layer

Repositories handle all table-specific database operations and are the **only** entry point for CRUD and query logic. Each repository is responsible for a single table or closely related set of tables.

```java
public class LoreEntryRepository {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    // CRUD/query methods
    public CompletableFuture<LoreEntryDTO> getLoreEntryById(String id) { /*...*/ }
    public CompletableFuture<List<LoreEntryDTO>> getLoreEntriesByType(String type) { /*...*/ }
    // ...other CRUD/query methods...
}
```

**Repository Responsibilities:**
- Table-specific CRUD operations
- Custom queries for specific business needs
- Data validation before persistence
- Mapping between DTOs and domain objects (where appropriate)
- All data access is asynchronous and returns `CompletableFuture`

**Usage Example:**
```java
LoreEntryRepository repo = databaseManager.getLoreEntryRepository();
repo.getLoreEntryById(id).thenAccept(entry -> {
    // handle entry
});
```

**Best Practices:**
- Do not add data access methods to `DatabaseManager`—always use the repository directly.
- Use DTOs for all data transfer between repositories and domain logic.
- All repository methods should be asynchronous.

## Class Relationship Diagram

```
┌─────────────────────┐      ┌─────────────────────┐
│     RVNKLore        │◀─────│    ConfigManager    │
└─────────────────────┘      └─────────────────────┘
         │                             │
         │ creates                     │ provides settings
         ▼                             ▼
┌─────────────────────┐      ┌─────────────────────┐
│   DatabaseManager   │◀─────│ ConnectionProvider  │
└─────────────────────┘      └─────────────────────┘
         │                             ▲
         │ owns                        │ implements
         │                    ┌────────┴────────┐
         │                    │                 │
         │             ┌─────────────┐  ┌─────────────┐
         │             │MySQLProvider│  │SQLiteProvider│
         │             └─────────────┘  └─────────────┘
         │
         │ creates
         ├────────────────────┬─────────────────┬─────────────────┐
         │                    │                 │                 │
         ▼                    ▼                 ▼                 ▼
┌─────────────────┐  ┌─────────────────┐ ┌─────────────┐ ┌─────────────────┐
│ QueryBuilder    │  │ SchemaBuilder   │ │QueryExecutor│ │DatabaseSetup    │
└─────────────────┘  └─────────────────┘ └─────────────┘ └─────────────────┘
         ▲                    ▲                 │                 │
         │                    │                 │                 │ uses
         │                    │                 │                 ▼
┌────────┴────────┐  ┌────────┴────────┐        │         ┌─────────────────┐
│                 │  │                 │        │         │HealthService    │
│MySQLQueryBuilder│  │MySQLSchemaBuilder│        │         └─────────────────┘
└─────────────────┘  └─────────────────┘        │
┌─────────────────┐  ┌─────────────────┐        │
│                 │  │                 │        │ used by
│SQLiteQueryBuilder│ │SQLiteSchemaBuilder│       │
└─────────────────┘  └─────────────────┘        │
                                                │
         ┌─────────────────────────────────────┘
         │
         ▼
┌──────────────────────┐
│  Repository Classes  │──────┐
└──────────────────────┘      │
         │                    │
         │ return             │ use
         ▼                    ▼
┌──────────────────────┐    ┌──────────────────────┐
│   DTO Classes        │───▶│   Domain Classes     │
└──────────────────────┘    └──────────────────────┘
```

## Database Schema

The RVNKLore database uses the following core tables:

### Player Table
```sql
CREATE TABLE player (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    last_seen TIMESTAMP NOT NULL,
    first_join TIMESTAMP NOT NULL,
    is_banned BOOLEAN DEFAULT FALSE,
    has_submitted_lore BOOLEAN DEFAULT FALSE,
    lore_submission_count INT DEFAULT 0,
    lore_approval_count INT DEFAULT 0
)
```

### Name Change Record Table
```sql
CREATE TABLE name_change_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id VARCHAR(36) NOT NULL,
    old_username VARCHAR(16) NOT NULL,
    new_username VARCHAR(16) NOT NULL,
    change_date TIMESTAMP NOT NULL,
    FOREIGN KEY (player_id) REFERENCES player(id)
)
```

### Lore Entry Table
```sql
CREATE TABLE lore_entry (
    id VARCHAR(36) PRIMARY KEY,
    entry_type VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    submitted_by VARCHAR(36),
    is_approved BOOLEAN DEFAULT FALSE,
    metadata TEXT,
    FOREIGN KEY (submitted_by) REFERENCES player(id)
)
```

### Lore Submission Table
```sql
CREATE TABLE lore_submission (
    id VARCHAR(36) PRIMARY KEY,
    entry_type VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    submitted_by VARCHAR(36) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    reviewed_by VARCHAR(36),
    reviewed_at TIMESTAMP,
    feedback TEXT,
    metadata TEXT,
    FOREIGN KEY (submitted_by) REFERENCES player(id),
    FOREIGN KEY (reviewed_by) REFERENCES player(id)
)
```

### Item Properties Table
```sql
CREATE TABLE item_properties (
    id VARCHAR(36) PRIMARY KEY,
    material VARCHAR(100) NOT NULL,
    display_name VARCHAR(256),
    item_type VARCHAR(50) NOT NULL,
    rarity VARCHAR(50) NOT NULL,
    custom_model_data INT,
    created_at TIMESTAMP NOT NULL,
    properties TEXT,
    lore_entry_id VARCHAR(36),
    FOREIGN KEY (lore_entry_id) REFERENCES lore_entry(id)
)
```

### Lore Collection Table
```sql
CREATE TABLE lore_collection (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    collection_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    metadata TEXT
)
```

### Lore Collection Entry Table
```sql
CREATE TABLE lore_collection_entry (
    collection_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    added_at TIMESTAMP NOT NULL,
    PRIMARY KEY (collection_id, item_id),
    FOREIGN KEY (collection_id) REFERENCES lore_collection(id),
    FOREIGN KEY (item_id) REFERENCES item_properties(id)
)
```

## Implementation Status

As of June 22, 2025, the database architecture implementation is as follows:

| Component | Status | Notes |
|-----------|--------|-------|
| DatabaseManager | ✅ Complete | Central hub for all database operations |
| ConnectionProvider | ✅ Complete | Both MySQL and SQLite implementations |
| QueryBuilder | ✅ Complete | Fluent API for SQL generation |
| SchemaQueryBuilder | ✅ Complete | Database schema management |
| QueryExecutor | ✅ Complete | Asynchronous query execution with DTO mapping |
| DTOs | ✅ Complete | Full set of DTOs for all entities |
| Repositories | ✅ Complete | Table-specific repositories |
| DatabaseHealthService | ✅ Complete | Connection monitoring and recovery |
| DatabaseSetup | ✅ Complete | Schema creation and validation |
| Transaction Support | ✅ Complete | ACID-compliant transaction handling |
| Caching System | 🟡 Partial | Basic caching implemented, optimization ongoing |
| Async Query Batching | 🟡 Partial | Implemented for common operations |
| Migration Tools | 🟠 Planned | Data migration between schema versions |

## Key Design Decisions

### 1. Asynchronous Operations

All database operations are performed asynchronously using `CompletableFuture` to prevent blocking the main server thread:

```java
// Example from DatabaseManager
public CompletableFuture<LoreEntryDTO> getLoreEntry(String id) {
    return executor.submit(() -> {
        QueryBuilder query = queryBuilder.select("*")
            .from("lore_entry")
            .where("id = ?", id);
        
        return queryExecutor.executeQuery(query, LoreEntryDTO.class).join();
    });
}
```

### 2. Connection Management

- **MySQL**: Uses HikariCP connection pooling for optimal performance
- **SQLite**: Uses a single persistent connection with automatic reconnection
- **Health Monitoring**: Periodic checks ensure connection validity

### 3. Error Handling

Comprehensive error handling with detailed logging:

```java
try {
    // Database operation
} catch (SQLException e) {
    logger.error("Database error: " + e.getMessage(), e);
    throw new DatabaseException("Failed to execute query", e);
} catch (Exception e) {
    logger.error("Unexpected error: " + e.getMessage(), e);
    throw new RuntimeException("Unexpected error during database operation", e);
}
```

### 4. Transaction Management

ACID-compliant transaction handling:

```java
try (Connection conn = connectionProvider.getConnection()) {
    conn.setAutoCommit(false);
    try {
        // Multiple database operations
        conn.commit();
    } catch (Exception e) {
        conn.rollback();
        throw e;
    } finally {
        conn.setAutoCommit(true);
    }
}
```

## Performance Considerations

1. **Connection Pooling**: HikariCP for MySQL with optimized settings
2. **Prepared Statements**: Used exclusively for all queries
3. **Batch Operations**: For multi-row inserts/updates
4. **Result Set Streaming**: For large result sets
5. **Strategic Indexing**: On frequently queried columns
6. **Query Optimization**: Using EXPLAIN to analyze and optimize queries
7. **Caching**: In-memory caching for frequently accessed data

## Future Enhancements

1. **Enhanced Caching**: Multi-level caching with time-based expiration
2. **Query Optimization**: Profiling-based query optimization
3. **Schema Migration**: Version-controlled schema changes
4. **Query Metrics**: Performance monitoring for queries
5. **Read/Write Separation**: For high-traffic deployments
6. **Sharding Strategy**: For extreme scalability scenarios

## Best Practices

1. **Never use raw SQL**: Always use QueryBuilder
2. **Always use DTOs**: For data transfer between layers
3. **Use async operations**: To prevent server lag
4. **Implement proper error handling**: With detailed logging
5. **Validate data**: Before database operations
6. **Use transactions**: For multi-step operations
7. **Close resources**: In finally blocks or try-with-resources
8. **Follow naming conventions**: For consistency
9. **Document queries**: For complex operations
10. **Write unit tests**: For critical database operations

## Conclusion

The RVNKLore database architecture provides a robust, scalable, and maintainable foundation for the plugin's data needs. By following established design patterns and best practices, it ensures data integrity, performance, and developer productivity.

The architecture successfully abstracts database-specific details, providing a clean API for the rest of the plugin while maintaining flexibility for future enhancements and optimizations.

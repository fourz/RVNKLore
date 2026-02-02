# RVNKLore Database Architecture Refactor - Step-by-Step Plan

## Current State Analysis

The current data management strategy has several areas for improvement:

**Current Issues:**

- Repository classes handle direct SQL query construction
- Inconsistent connection management across repositories  
- DatabaseManager acts as facade but doesn't centralize data operations
- Configuration access is scattered throughout code with direct config.getString() calls
- Limited abstraction between MySQL and SQLite implementations
- No standardized DTO pattern for data transfer

**Current Architecture:**

```text
DatabaseManager (facade) → DatabaseConnection → Individual Repositories → Direct SQL
ConfigManager → Direct config.getString() calls throughout codebase
```

**Target Architecture:**

```text
DatabaseManager (central hub) → QueryExecutor → QueryBuilder (MySQL/SQLite) → DTOs
ConfigManager → Configuration DTOs → Domain classes
```

## Phase 1: Foundation - Core Infrastructure Refactor

### 1.1 Create Core Interface Abstractions

**Goal:** Establish standard interfaces for database operations and query building.

**Actions:**

- Create `QueryBuilder` interface with common methods for SELECT, INSERT, UPDATE, DELETE
- Create `MySQLQueryBuilder` and `SQLiteQueryBuilder` implementations
- Create `QueryExecutor` interface for executing queries with DTOs
- Create `ConnectionProvider` interface for standardized connection management

**Files to Create:**

```java
// New interfaces
src/main/java/org/fourz/RVNKLore/data/query/QueryBuilder.java
src/main/java/org/fourz/RVNKLore/data/query/MySQLQueryBuilder.java  
src/main/java/org/fourz/RVNKLore/data/query/SQLiteQueryBuilder.java
src/main/java/org/fourz/RVNKLore/data/query/QueryExecutor.java
src/main/java/org/fourz/RVNKLore/data/connection/ConnectionProvider.java
```

**Example Interface:**

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
```

### 1.2 Create Data Transfer Objects (DTOs)

**Goal:** Standardize data transfer between database and domain layers.

**Actions:**

- Create DTOs for all major entities: LoreEntryDTO, LoreSubmissionDTO, ItemPropertiesDTO
- Create configuration DTOs: MySQLSettingsDTO, SQLiteSettingsDTO, DatabaseSettingsDTO
- Implement conversion methods between DTOs and domain objects

**Files to Create:**

```java
src/main/java/org/fourz/RVNKLore/data/dto/LoreEntryDTO.java
src/main/java/org/fourz/RVNKLore/data/dto/LoreSubmissionDTO.java
src/main/java/org/fourz/RVNKLore/data/dto/ItemPropertiesDTO.java
src/main/java/org/fourz/RVNKLore/config/dto/MySQLSettingsDTO.java
src/main/java/org/fourz/RVNKLore/config/dto/SQLiteSettingsDTO.java
src/main/java/org/fourz/RVNKLore/config/dto/DatabaseSettingsDTO.java
```

**Example DTO:**

```java
public class LoreEntryDTO {
    private String id;
    private String entryType;
    private String name;
    private Timestamp createdAt;
    
    // Constructors, getters, setters
    public static LoreEntryDTO fromLoreEntry(LoreEntry entry) { /*...*/ }
    public LoreEntry toLoreEntry() { /*...*/ }
}
```

### 1.3 Refactor DatabaseManager as Central Hub

**Goal:** Make DatabaseManager the single entry point for all database operations.

**Actions:**

- Move all data operations from repositories into DatabaseManager
- Implement connection pooling and health monitoring within DatabaseManager
- Add async operation support with CompletableFuture
- Centralize transaction management

**Refactored DatabaseManager:**

```java
public class DatabaseManager {
    private final ConnectionProvider connectionProvider;
    private final QueryExecutor queryExecutor;
    private final Map<Class<?>, QueryBuilder> queryBuilders;
    
    // Centralized operations
    public CompletableFuture<LoreEntryDTO> getLoreEntry(String id);
    public CompletableFuture<List<LoreEntryDTO>> getAllLoreEntries();
    public CompletableFuture<Boolean> saveLoreEntry(LoreEntryDTO dto);
    public CompletableFuture<Boolean> deleteLoreEntry(String id);
    
    // Item operations  
    public CompletableFuture<ItemPropertiesDTO> getItem(String id);
    public CompletableFuture<List<ItemPropertiesDTO>> getItemsByType(String type);
    
    // Player operations
    public CompletableFuture<Boolean> playerExists(UUID playerUuid);
    public CompletableFuture<String> getStoredPlayerName(UUID playerUuid);
}
```

## Phase 2: Repository Layer Transformation

### 2.1 Convert Repositories to Service Layer

**Goal:** Transform repositories from data access objects to domain service objects.

**Actions:**

- Rename repositories to services (e.g., LoreEntryRepository → LoreEntryService)
- Remove direct SQL and database connection handling from services
- Make services delegate all data operations to DatabaseManager
- Focus services on business logic and data transformation

**Before (Repository):**

```java
public class LoreEntryRepository {
    public boolean addLoreEntry(LoreEntry entry) {
        Connection conn = dbConnection.getConnection();
        // Direct SQL handling...
    }
}
```

**After (Service):**

```java
public class LoreEntryService {
    private final DatabaseManager databaseManager;
    
    public CompletableFuture<Boolean> addLoreEntry(LoreEntry entry) {
        LoreEntryDTO dto = LoreEntryDTO.fromLoreEntry(entry);
        return databaseManager.saveLoreEntry(dto);
    }
}
```

### 2.2 Implement Query Builder Strategy Pattern

**Goal:** Isolate database-specific SQL generation behind clean interfaces.

**Actions:**

- Implement MySQLQueryBuilder with MySQL-specific optimizations
- Implement SQLiteQueryBuilder with SQLite-specific syntax
- Create QueryBuilderFactory to provide appropriate builder based on database type
- Update DatabaseManager to use appropriate query builder

**Example Implementation:**

```java
public class MySQLQueryBuilder implements QueryBuilder {
    public String buildUpsert(String table, String[] columns) {
        return "INSERT INTO " + table + " (" + String.join(",", columns) + 
               ") VALUES (" + "?".repeat(columns.length).replace("", ",").substring(1) + 
               ") ON DUPLICATE KEY UPDATE " + Arrays.stream(columns)
               .map(col -> col + "=VALUES(" + col + ")")
               .collect(Collectors.joining(","));
    }
}

public class SQLiteQueryBuilder implements QueryBuilder {
    public String buildUpsert(String table, String[] columns) {
        return "INSERT OR REPLACE INTO " + table + " (" + String.join(",", columns) + 
               ") VALUES (" + "?".repeat(columns.length).replace("", ",").substring(1) + ")";
    }
}
```

## Phase 3: Configuration Management Overhaul

### 3.1 Create Configuration DTO System

**Goal:** Eliminate scattered config.getString() calls and centralize configuration access.

**Actions:**

- Create comprehensive configuration DTOs for all major system components
- Refactor ConfigManager to load and validate configurations into DTOs
- Implement configuration change detection and propagation

**Configuration DTOs:**

```java
public class DatabaseSettingsDTO {
    private final DatabaseType type;
    private final MySQLSettingsDTO mysqlSettings;
    private final SQLiteSettingsDTO sqliteSettings;
    private final int connectionTimeout;
    private final int maxRetries;
    
    // Validation methods
    public void validate() throws ConfigurationException { /*...*/ }
}

public class MySQLSettingsDTO {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useSSL;
    private final String tablePrefix;
}
```

### 3.2 Refactor ConfigManager

**Goal:** Transform ConfigManager into a DTO-based configuration provider.

**Actions:**

- Load all configurations into appropriate DTOs during initialization
- Provide typed getter methods that return DTOs instead of raw config values
- Implement configuration validation and error reporting
- Add configuration reload capabilities that propagate changes

**Refactored ConfigManager:**

```java
public class ConfigManager {
    private DatabaseSettingsDTO databaseSettings;
    private ItemSettingsDTO itemSettings;
    private LoreSettingsDTO loreSettings;
    
    public DatabaseSettingsDTO getDatabaseSettings() { return databaseSettings; }
    public ItemSettingsDTO getItemSettings() { return itemSettings; }
    public LoreSettingsDTO getLoreSettings() { return loreSettings; }
    
    public void reloadConfiguration() {
        // Reload and validate all DTOs
        // Notify all dependent systems of changes
    }
}
```

## Phase 4: Domain Model Integration

### 4.1 Update Domain Classes to Use DTOs

**Goal:** Make domain classes work seamlessly with the new DTO-based system.

**Actions:**

- Add DTO fields to domain classes (LoreEntry, ItemProperties, etc.)
- Implement conversion methods between domain objects and DTOs
- Update all business logic to work with DTOs internally
- Maintain backward compatibility for existing API methods

**Example Domain Class Integration:**

```java
public class LoreEntry {
    private LoreEntryDTO dto;
    
    // Constructors
    public LoreEntry(LoreEntryDTO dto) { this.dto = dto; }
    public LoreEntry(String name, LoreType type) { 
        this.dto = new LoreEntryDTO(UUID.randomUUID().toString(), type.name(), name);
    }
    
    // Domain methods that delegate to DTO
    public String getName() { return dto.getName(); }
    public void setName(String name) { dto.setName(name); }
    
    // Conversion methods
    public LoreEntryDTO toDTO() { return dto; }
    public static LoreEntry fromDTO(LoreEntryDTO dto) { return new LoreEntry(dto); }
}
```

### 4.2 Refactor Command Classes

**Goal:** Update all command classes to use DatabaseManager exclusively for data operations.

**Actions:**

- Remove all direct repository dependencies from command classes
- Update commands to use DatabaseManager's async methods
- Implement proper error handling for async operations
- Add progress feedback for long-running operations

**Before:**

```java
public class LoreAddSubCommand {
    private final LoreEntryRepository repository;
    
    public boolean execute(CommandSender sender, String[] args) {
        LoreEntry entry = createEntry(args);
        return repository.addLoreEntry(entry);
    }
}
```

**After:**

```java
public class LoreAddSubCommand {
    private final DatabaseManager databaseManager;
    
    public boolean execute(CommandSender sender, String[] args) {
        LoreEntry entry = createEntry(args);
        
        databaseManager.saveLoreEntry(entry.toDTO())
            .thenAccept(success -> {
                if (success) {
                    sender.sendMessage("&a✓ Lore entry created successfully");
                } else {
                    sender.sendMessage("&c✖ Failed to create lore entry");
                }
            })
            .exceptionally(throwable -> {
                sender.sendMessage("&c✖ Error: " + throwable.getMessage());
                return null;
            });
        
        sender.sendMessage("&6⚙ Creating lore entry...");
        return true;
    }
}
```

## Phase 5: Migration and Testing Strategy

### 5.1 Gradual Migration Plan

**Goal:** Migrate system incrementally to minimize disruption.

**Phase 5.1.1: Core Infrastructure**

- Implement new interfaces and DTOs
- Set up DatabaseManager as central hub
- Create migration compatibility layer

**Phase 5.1.2: Repository Services Migration**

- Convert one repository at a time (start with LoreEntryRepository)
- Maintain parallel old/new implementations during transition
- Add comprehensive testing for each converted service

**Phase 5.1.3: Configuration System**

- Replace ConfigManager internals with DTO system
- Update all configuration access points incrementally
- Validate configuration loading and error handling

**Phase 5.1.4: Domain Integration**

- Update domain classes to use DTOs
- Convert command classes to async DatabaseManager operations
- Remove deprecated repository classes

### 5.2 Testing and Validation

**Goal:** Ensure system reliability throughout migration.

**Actions:**

- Create comprehensive unit tests for all new interfaces and DTOs
- Implement integration tests for DatabaseManager operations
- Add performance benchmarks to compare old vs new systems
- Create data migration scripts for any schema changes required

### 5.3 Rollback Strategy

**Goal:** Provide safety net during migration.

**Actions:**

- Maintain feature flags to switch between old and new implementations
- Create database backup procedures before major changes
- Document rollback procedures for each migration phase
- Implement system health checks to detect migration issues

## Phase 6: Optimization and Performance

### 6.1 Connection Pool Optimization

**Goal:** Optimize database connection management for better performance.

**Actions:**

- Implement HikariCP for connection pooling
- Configure optimal pool settings for both MySQL and SQLite
- Add connection health monitoring and automatic recovery
- Implement connection leak detection and prevention

### 6.2 Query Performance Optimization

**Goal:** Ensure efficient database operations with new architecture.

**Actions:**

- Implement query result caching for frequently accessed data
- Add database query performance monitoring
- Optimize query builders for specific database features
- Implement batch operations for bulk data manipulation

## Implementation Timeline

**Week 1-2: Foundation (Phase 1)**

- Create interfaces, DTOs, and core infrastructure
- Set up DatabaseManager as central hub

**Week 3-4: Repository Transformation (Phase 2)**

- Convert repositories to services
- Implement query builder pattern

**Week 5-6: Configuration Overhaul (Phase 3)**

- Create configuration DTO system
- Refactor ConfigManager

**Week 7-8: Domain Integration (Phase 4)**

- Update domain classes and command classes
- Implement async operation handling

**Week 9-10: Migration and Testing (Phase 5)**

- Execute gradual migration
- Comprehensive testing and validation

**Week 11-12: Optimization (Phase 6)**

- Performance tuning and optimization
- Final system validation and documentation

## Success Criteria

- All database operations go through DatabaseManager exclusively
- No direct SQL in domain or command classes
- Configuration accessed only through typed DTOs
- All database operations are asynchronous where appropriate
- 100% test coverage for new architecture components
- Performance equal to or better than current system
- Clean separation between MySQL and SQLite implementations
- Maintainable and extensible codebase structure

## Data Strategy Implementation Guidelines

### Database Interactions

- **Always use the DatabaseManager as the single entry point for setting up and managing connections.**
- **Perform all connection logic (initialization, reconnect, shutdown, and health checks) within DatabaseManager.**
- **Repository classes should delegate all connection-dependent operations to DatabaseManager and be limited to table-specific logic.**

### Query Builder Abstraction

- **Define a common interface (QueryBuilder) for constructing SQL queries.**
- **Implement MySQLQueryBuilder and SQLiteQueryBuilder to encapsulate dialect-specific logic.**
- **All Repository classes should use these interfaces to build and execute queries, ensuring reusability and consistency.**

### Data Transfer and Repository Behavior

- **Repositories should merely map table results to DTOs.**
- **Migration: Deprecate existing Repository logic that handles direct SQL query formation.**
- **Plan to remove such logic once all queries are refactored to use the new QueryBuilder and QueryExecutor interfaces.**

### Configuration

- **Load configuration values into dedicated DTOs for consistency.**
- **Access configuration settings strictly through these DTO getters rather than using raw config calls.**

### Migration of Command, Config, Item, and Lore Domain Classes

**Command Classes:** Refactor commands to invoke DatabaseManager for all data operations asynchronously.

*Example:*

```java
// Before
LoreEntry entry = loreRepository.getLoreEntryById(id);

// After
CompletableFuture<LoreEntry> futureEntry = databaseManager.getLoreEntry(id);
futureEntry.thenAccept(entry -> { /* Process entry */ });
```

**Configuration System:** Update ConfigManager to utilize DTOs (e.g., MySQLSettingsDTO) for settings retrieval.

*Example:*

```java
// Before
String host = config.getString("storage.mysql.host");

// After
MySQLSettingsDTO settings = configManager.getMySQLSettings();
String host = settings.getHost();
```

**Item and Lore Domain Classes:** Introduce DTO-based data handling in domain classes with conversion getters/setters.

*Example:*

```java
// Before
String name = loreEntry.getName();

// After
String name = loreEntry.getLoreEntryDTO().getName();
```

### Performance and Best Practices

- **Ensure all database operations are asynchronous to avoid blocking the main thread.**
- **Implement robust error handling and connection validation within DatabaseManager.**
- **Log all connection events using the designated LogManager.**

---

**Summary:**

*Align all code, comments, and structure with the standards in `README.md`, `ROADMAP.md`, and `docs/schema/`. When in doubt, refer to these documents.*

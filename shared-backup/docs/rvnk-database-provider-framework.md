# RVNK Database Provider Framework

A reusable database abstraction framework for Bukkit/Spigot plugins providing clean separation of concerns, asynchronous operations, and database dialect independence.

## Architecture Overview

```
┌───────────────────┐     ┌───────────────────┐     ┌───────────────────┐
│  Command Layer    │     │   Service Layer   │     │  Database Layer   │
│                   │     │                   │     │                   │
│  CommandManager   │────▶│  QueryService     │────▶│  DatabaseManager │
│  SubCommands      │     │  DatabaseHealth   │     │                   │
└─────────┬─────────┘     │  Service          │     └────────┬──────────┘
          │               │  DatabaseBackup   │              │
          │               │  Service          │              ▼
          │               └────────┬──────────┘    ┌───────────────────────┐
          │                        │               │  Connection Layer     │
          │                        │               │                       │
          │                        │               │  ConnectionProvider   │
          │                        │               │  ├ MySQLProvider     │
          │                        │               │  └ SQLiteProvider    │
          │                        │               └───────────┬───────────┘
          │                        │                           │
          ▼                        ▼                           ▼
┌───────────────────┐     ┌───────────────────┐     ┌───────────────────┐
│   Domain Layer    │     │   Data Layer      │     │   Query Layer     │
│                   │     │                   │     │                   │
│  Domain Models ◀──┼────▶│  DTOs            │     │  QueryBuilder     │
│  Domain Logic     │     │  Repositories     │◀───▶│  QueryExecutor    │
└─────────┬─────────┘     └────────┬──────────┘     │  SchemaBuilder   │
          │                        │                └────────┬──────────┘
          │                        │                         │
          ▼                        ▼                         ▼
┌───────────────────┐     ┌───────────────────┐     ┌───────────────────┐
│   Config Layer    │     │  Transaction      │     │   Health Layer    │
│                   │     │  Management       │     │                   │
│  DatabaseConfig   │────▶│  TransactionMgr  │     │  HealthService    │
│  ConfigManager    │     │  BatchOperations  │     │  HealthMonitor   │
└───────────────────┘     └───────────────────┘     └───────────────────┘
```

## Core Components

### 1. DatabaseManager

Central hub managing:
- Connection lifecycle
- Repository provisioning
- Schema management
- Health monitoring
- Transaction coordination

### 2. ConnectionProvider Interface

```java
public interface ConnectionProvider {
    Connection getConnection() throws SQLException;
    void close();
    boolean isValid();
    boolean isHealthy();
    boolean validateConnection();
}
```

### 3. Service Layer Components

#### QueryService
Provides higher-level query capabilities that build on the core QueryBuilder and QueryExecutor:
- Complex query construction with fluent API
- Domain-specific query templates
- Result transformation and mapping

```java
public class QueryService {
    private final DatabaseManager databaseManager;
    private final QueryExecutor queryExecutor;
    
    public <T> CompletableFuture<List<T>> queryByTemplate(QueryTemplate template, Class<T> resultClass) {
        QueryBuilder query = template.buildQuery(databaseManager.getQueryBuilder());
        return queryExecutor.executeQueryList(query, resultClass);
    }
    
    // Additional query helper methods
}
```

#### DatabaseHealthService
Monitors database connection health and provides recovery mechanisms:
- Periodic connection validity checks
- Connection pooling monitoring
- Automatic reconnection attempts
- Health metrics collection

```java
public class DatabaseHealthService {
    private static final long CHECK_INTERVAL = 5 * 60 * 1000; // 5 minutes
    private final ScheduledExecutorService scheduler;
    private final DatabaseManager databaseManager;
    private final LogManager logger;
    
    public void start() {
        scheduler.scheduleAtFixedRate(this::checkHealth, 
            CHECK_INTERVAL, CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    private void checkHealth() {
        if (!databaseManager.isConnectionValid()) {
            logger.warning("Database connection invalid, attempting reconnection");
            databaseManager.reconnect();
        }
    }
}
```

#### DatabaseBackupService
Manages database backups and restoration operations:
- Scheduled and on-demand backups
- Backup rotation policies
- Backup verification
- Restore operations

```java
public class DatabaseBackupService {
    private final DatabaseManager databaseManager;
    private final Path backupDirectory;
    private final LogManager logger;
    
    public CompletableFuture<Path> createBackup(String label) {
        return CompletableFuture.supplyAsync(() -> {
            // Backup implementation
            Path backupFile = backupDirectory.resolve("backup_" + label + "_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".db");
            // Create backup logic
            return backupFile;
        });
    }
    
    public CompletableFuture<Boolean> restoreFromBackup(Path backupFile) {
        // Restore implementation
    }
}
```

### 4. QueryBuilder Interface

```java
public interface QueryBuilder {
    QueryBuilder select(String... columns);
    QueryBuilder from(String table);
    QueryBuilder where(String condition, Object... params);
    QueryBuilder orderBy(String column, boolean ascending);
    QueryBuilder limit(int limit);
    QueryBuilder join(String table, String alias, String condition);
    String build();
    Object[] getParameters();
}
```

### 5. DatabaseConfig Pattern

```java
public class DatabaseConfig {
    private final DatabaseType type;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useSSL;
    
    public DatabaseConfig(ConfigManager configManager) {
        // Load from existing config
    }
}
```

### 6. Abstract Repository Pattern

```java
public abstract class BaseRepository<T, ID> {
    protected final Plugin plugin;
    protected final LogManager logger;
    protected final DatabaseManager databaseManager;
    protected final Class<T> entityClass;
    
    protected CompletableFuture<T> findById(ID id) {
        return queryExecutor.executeQuery(
            queryBuilder.select("*")
                       .from(getTableName())
                       .where("id = ?", id),
            entityClass
        );
    }
}
```

## Schema Management

### Schema Version Table Example

```sql
CREATE TABLE schema_version (
    version_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    version VARCHAR(50) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description TEXT,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    checksum VARCHAR(64),
    duration INTEGER,
    CONSTRAINT uq_schema_version UNIQUE (version)
);
```

### SchemaQueryBuilder Interface

```java
public interface SchemaQueryBuilder {
    SchemaQueryBuilder createTable(String tableName);
    SchemaQueryBuilder column(String name, String type, String constraints);
    SchemaQueryBuilder foreignKey(String column, String refTable, String refColumn);
    SchemaQueryBuilder primaryKey(String... columns);
    SchemaQueryBuilder index(String name, String... columns);
    String getAutoIncrementSyntax();
    String build();
}
```

## Asynchronous Operations

### QueryExecutor Interface

```java
public interface QueryExecutor {
    <T> CompletableFuture<T> executeQuery(QueryBuilder builder, Class<T> dtoClass);
    <T> CompletableFuture<List<T>> executeQueryList(QueryBuilder builder, Class<T> dtoClass);
    CompletableFuture<Integer> executeUpdate(QueryBuilder builder);
    CompletableFuture<Integer> executeInsert(QueryBuilder builder);
}
```

## Health Monitoring

### DatabaseHealthService

```java
public class DatabaseHealthService {
    private static final long CHECK_INTERVAL = 5 * 60 * 1000; // 5 minutes
    private final ScheduledExecutorService scheduler;
    private final DatabaseManager databaseManager;
    
    public void start() {
        scheduler.scheduleAtFixedRate(this::checkHealth, 
            CHECK_INTERVAL, CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    private void checkHealth() {
        if (!databaseManager.isConnectionValid()) {
            databaseManager.reconnect();
        }
    }
}
```

## Transaction Management

```java
public class TransactionManager {
    public <T> CompletableFuture<T> inTransaction(
            Supplier<CompletableFuture<T>> operation) {
        return CompletableFuture.supplyAsync(() -> {
            Connection conn = connectionProvider.getConnection();
            conn.setAutoCommit(false);
            try {
                T result = operation.get().join();
                conn.commit();
                return result;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        });
    }
}
```

## Usage Example

```java
// Repository implementation
public class UserRepository extends BaseRepository<UserDTO, UUID> {
    public CompletableFuture<UserDTO> findByUsername(String username) {
        return queryExecutor.executeQuery(
            queryBuilder.select("*")
                       .from("users")
                       .where("username = ?", username),
            UserDTO.class
        );
    }
}

// Service usage
public class UserService {
    private final UserRepository userRepo;
    
    public CompletableFuture<UserDTO> createUser(String username) {
        return transactionManager.inTransaction(() -> {
            return userRepo.save(new UserDTO(username));
        });
    }
}
```

## Configuration Example

```yaml
storage:
  type: sqlite  # sqlite or mysql
  mysql:
    host: localhost
    port: 3306
    username: root
    password: ''
    useSSL: false
    database: minecraft
    poolSize: 10
    connectionTimeout: 30000
    idleTimeout: 600000
    maxLifetime: 1800000
    tablePrefix: ''
  sqlite:
    database: data.db
    busyTimeout: 3000
    walMode: true
    synchronous: NORMAL
  maxRetries: 3
  retryDelay: 1000
  connectionTimeout: 30000
```

## Implementation Example

### Database Type Enum
```java
public enum DatabaseType {
    MYSQL,
    SQLITE;
    
    public static DatabaseType fromString(String type) {
        try {
            return valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SQLITE;
        }
    }
}
```

### Connection Provider Implementation
```java
public class MySQLConnectionProvider implements ConnectionProvider {
    private final HikariDataSource dataSource;
    private final LogManager logger;
    
    public MySQLConnectionProvider(DatabaseConfig config, LogManager logger) {
        this.logger = logger;
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s", 
            config.getHost(), config.getPort(), config.getDatabase()));
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(config.getPoolSize());
        
        dataSource = new HikariDataSource(hikariConfig);
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
    
    @Override
    public boolean isValid() {
        try (Connection conn = getConnection()) {
            return conn != null && conn.isValid(1000);
        } catch (SQLException e) {
            logger.error("Database connection validation failed", e);
            return false;
        }
    }
}
```

### Query Builder Implementation
```java
public class MySQLQueryBuilder implements QueryBuilder {
    private final StringBuilder query;
    private final List<Object> parameters;
    private boolean hasWhere;
    
    public MySQLQueryBuilder() {
        this.query = new StringBuilder();
        this.parameters = new ArrayList<>();
        this.hasWhere = false;
    }
    
    @Override
    public QueryBuilder select(String... columns) {
        query.append("SELECT ");
        if (columns.length == 0) {
            query.append("*");
        } else {
            query.append(String.join(", ", columns));
        }
        return this;
    }
    
    @Override
    public QueryBuilder from(String table) {
        query.append(" FROM ").append(table);
        return this;
    }
    
    @Override
    public QueryBuilder where(String condition, Object... params) {
        query.append(hasWhere ? " AND " : " WHERE ").append(condition);
        parameters.addAll(Arrays.asList(params));
        hasWhere = true;
        return this;
    }
    
    @Override
    public String build() {
        return query.toString();
    }
    
    @Override
    public Object[] getParameters() {
        return parameters.toArray();
    }
}
```

### Repository Base Class Implementation
```java
public abstract class BaseRepository<T, ID> {
    protected final Plugin plugin;
    protected final LogManager logger;
    protected final DatabaseManager databaseManager;
    protected final Class<T> entityClass;
    
    protected BaseRepository(Plugin plugin, DatabaseManager databaseManager, Class<T> entityClass) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin);
        this.databaseManager = databaseManager;
        this.entityClass = entityClass;
    }
    
    protected CompletableFuture<T> findById(ID id) {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from(getTableName())
            .where("id = ?", id);
            
        return databaseManager.getQueryExecutor()
            .executeQuery(query, entityClass)
            .exceptionally(e -> {
                logger.error("Error finding " + entityClass.getSimpleName() + " by ID: " + id, e);
                return null;
            });
    }
    
    protected CompletableFuture<List<T>> findAll() {
        QueryBuilder query = databaseManager.getQueryBuilder()
            .select("*")
            .from(getTableName());
            
        return databaseManager.getQueryExecutor()
            .executeQueryList(query, entityClass)
            .exceptionally(e -> {
                logger.error("Error finding all " + entityClass.getSimpleName(), e);
                return new ArrayList<>();
            });
    }
    
    protected abstract String getTableName();
}
```

### Service Layer Example
```java
public class UserService {
    private final UserRepository repository;
    private final TransactionManager transactionManager;
    
    public CompletableFuture<UserDTO> createUser(String username) {
        return transactionManager.inTransaction(() -> {
            // Validate username
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("Username cannot be empty");
            }
            
            // Create user DTO
            UserDTO user = new UserDTO();
            user.setUsername(username);
            user.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            
            // Save and return
            return repository.save(user);
        });
    }
    
    public CompletableFuture<List<UserDTO>> getActiveUsers() {
        QueryBuilder query = repository.createQuery()
            .select("*")
            .from("users")
            .where("is_active = ?", true)
            .orderBy("last_login", false);
            
        return repository.executeQuery(query);
    }
}
```

### Command Layer Integration
```java
public class UserCommand implements CommandExecutor {
    private final UserService userService;
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 1) {
            return false;
        }
        
        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 2) return false;
                createUser(sender, args[1]);
                return true;
            case "list":
                listUsers(sender);
                return true;
            default:
                return false;
        }
    }
    
    private void createUser(CommandSender sender, String username) {
        userService.createUser(username)
            .thenAccept(user -> {
                sender.sendMessage("User created: " + user.getUsername());
            })
            .exceptionally(e -> {
                sender.sendMessage("Error creating user: " + e.getMessage());
                return null;
            });
    }
    
    private void listUsers(CommandSender sender) {
        userService.getActiveUsers()
            .thenAccept(users -> {
                sender.sendMessage("Active users:");
                users.forEach(user -> 
                    sender.sendMessage("- " + user.getUsername()));
            })
            .exceptionally(e -> {
                sender.sendMessage("Error listing users: " + e.getMessage());
                return null;
            });
    }
}
```

## Integration Testing Example

```java
public class DatabaseIntegrationTest {
    private Plugin plugin;
    private DatabaseManager databaseManager;
    private UserRepository userRepository;
    
    @Before
    public void setup() {
        // Setup test database
        DatabaseConfig config = new DatabaseConfig();
        config.setType(DatabaseType.SQLITE);
        config.setDatabase(":memory:");
        
        databaseManager = new DatabaseManager(plugin, config);
        userRepository = new UserRepository(databaseManager);
    }
    
    @Test
    public void testUserCRUD() {
        // Create user
        UserDTO user = new UserDTO();
        user.setUsername("test_user");
        
        UserDTO savedUser = userRepository.save(user).join();
        assertNotNull(savedUser.getId());
        
        // Find user
        UserDTO foundUser = userRepository.findById(savedUser.getId()).join();
        assertEquals("test_user", foundUser.getUsername());
        
        // Update user
        foundUser.setUsername("updated_user");
        UserDTO updatedUser = userRepository.save(foundUser).join();
        assertEquals("updated_user", updatedUser.getUsername());
        
        // Delete user
        boolean deleted = userRepository.delete(updatedUser.getId()).join();
        assertTrue(deleted);
        
        // Verify deleted
        UserDTO shouldBeNull = userRepository.findById(updatedUser.getId()).join();
        assertNull(shouldBeNull);
    }
}
```

The examples above demonstrate a complete implementation of the database provider framework, showing how all components work together in a real application. The framework provides a solid foundation that can be extended and customized for specific plugin needs while maintaining clean separation of concerns and proper resource management.

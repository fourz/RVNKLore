# RVNKLore Database Setup and Health Check Restructuring Plan

*Last Updated: July 1, 2025*

This document outlines the steps to restructure and simplify the database initialization, validation, and health check processes in the RVNKLore plugin. The goal is to eliminate redundant schema validations, minimize log spam, and improve the overall database connectivity reliability.

## Current Issues

1. **Redundant Schema Validation**: Schema setup and validation is performed on every reconnection attempt, not just at startup.
2. **Excessive Table Verification**: Table validation is performed too frequently, leading to log spam.
3. **Aggressive Health Checks**: Health service performs checks too frequently with insufficient throttling.
4. **Blocking Operations**: Some database operations block the main thread unnecessarily.
5. **Dependency Injection**: Current initialization doesn't follow a clean two-phase initialization pattern.

## Architecture Goals

1. **Separation of Concerns**:
   - `DatabaseManager`: Central coordinator and repository factory
   - `DatabaseSetup`: Schema creation and validation only
   - `DatabaseHealthService`: Connection monitoring and recovery
   - `ConnectionProvider`: Database-specific connection handling

2. **Initialization Flow**:
   - Two-phase initialization for proper dependency injection
   - Schema validation only at startup or explicit reload
   - Lightweight reconnection process

3. **Health Check Improvements**:
   - Reduced frequency with better throttling
   - Simplified reconnection process
   - Improved error reporting

## Implementation Plan

### Phase 1: Refactor DatabaseSetup (Completed)

1. ✅ Update `DatabaseSetup` constructor to accept only minimal dependencies (plugin, connectionProvider)
2. ✅ Add `initialize(SchemaQueryBuilder, QueryExecutor)` method for second-phase setup
3. ✅ Add validation in all methods to prevent usage before initialization
4. ✅ Create lightweight `validateSchema()` method for connection validation
5. ✅ Add `isInitialized()` helper method

### Phase 2: Update DatabaseManager Constructor (Current Focus)

1. Fix the current constructor to properly create the connection provider before passing it to DatabaseSetup
2. Implement proper two-phase initialization for DatabaseSetup
3. Move schema validation to a single place at startup
4. Create a central connection provider factory method

```java
public DatabaseManager(RVNKLore plugin) {
    this.plugin = plugin;
    this.logger = LogManager.getInstance(plugin, "DatabaseManager");
    this.databaseConfig = plugin.getConfigManager().getDatabaseConfig();
    this.databaseType = databaseConfig.getType();
    
    // Step 1: Create connection provider first
    createConnectionProvider();
    
    // Step 2: Create DatabaseSetup with minimal dependencies
    this.databaseSetup = new DatabaseSetup(plugin, connectionProvider);
    
    // Step 3: Complete initialization
    initialize();
}

private void createConnectionProvider() {
    // Create the appropriate connection provider based on database type
    if (databaseType == DatabaseType.MYSQL) {
        connectionProvider = new MySQLConnectionProvider(plugin, databaseConfig);
    } else {
        connectionProvider = new SQLiteConnectionProvider(plugin, databaseConfig);
    }
    logger.debug("Connection provider created: " + connectionProvider.getClass().getSimpleName());
}
```

### Phase 3: Refactor Initialize Method

1. Split initialize() into smaller, focused methods
2. Complete DatabaseSetup initialization with remaining dependencies
3. Set up repositories and other components
4. Perform schema validation only at startup

```java
public void initialize() {
    logger.info("Initializing database manager...");
    
    // Step 1: Create query builders
    initializeQueryBuilders();
    
    // Step 2: Create query executor
    queryExecutor = new DefaultQueryExecutor(plugin, connectionProvider);
    
    // Step 3: Complete DatabaseSetup initialization
    databaseSetup.initialize(schemaQueryBuilder, queryExecutor);
    
    // Step 4: Initialize repositories
    initializeRepositories();
    
    // Step 5: Initialize database connection
    initializeConnection();
    
    // Step 6: Set up health service
    setupHealthService();
    
    // Step 7: Validate schema (only if not already validated)
    validateSchemaIfNeeded();
}
```

### Phase 4: Implement Lightweight Connection Validation

1. Create a lightweight `validateConnection()` method
2. Use DatabaseSetup's `validateSchema()` for initial validation only
3. Implement simple ping-style validation for normal operation

```java
public boolean validateConnection() {
    try {
        // For routine checks, just verify the connection is valid
        return connectionProvider.isValid();
    } catch (Exception e) {
        logger.warning("Connection validation failed: " + e.getMessage());
        // Try to reconnect
        return reconnect();
    }
}

private boolean reconnect() {
    logger.info("Attempting to reconnect to database...");
    try {
        // Close existing connection
        connectionProvider.close();
        
        // Create a new connection
        createConnectionProvider();
        
        // For SQLite, we need to initialize the connection explicitly
        if (databaseType == DatabaseType.SQLITE) {
            ((SQLiteConnectionProvider) connectionProvider).initializeConnection();
        }
        
        // Simple validation without schema checks
        if (connectionProvider.isValid()) {
            logger.info("Reconnection successful");
            return true;
        } else {
            logger.error("Reconnection failed - connection invalid");
            return false;
        }
    } catch (Exception e) {
        logger.error("Reconnection failed with error", e);
        return false;
    }
}
```

### Phase 5: Update DatabaseHealthService

1. Reduce check frequency (from every 30 seconds to every 2 minutes)
2. Add throttling to prevent log spam
3. Use the lightweight validation process
4. Improve error handling and reporting

```java
public class DatabaseHealthService {
    private static final long CHECK_INTERVAL_TICKS = 2400L; // 2 minutes (20 ticks per second)
    private static final long ERROR_THROTTLE_MS = 60000L; // 1 minute
    
    // Track last error time for throttling
    private long lastErrorLogTime = 0;
    
    public void checkDatabaseHealth() {
        try {
            if (!databaseManager.validateConnection()) {
                // Log errors with throttling
                long now = System.currentTimeMillis();
                if (now - lastErrorLogTime > ERROR_THROTTLE_MS) {
                    logger.warning("Database connection is not valid, attempting recovery...");
                    lastErrorLogTime = now;
                }
                
                // Try to reconnect (validateConnection already does this, but we'll be explicit)
                databaseManager.reconnect();
            }
        } catch (Exception e) {
            // Log with throttling
            long now = System.currentTimeMillis();
            if (now - lastErrorLogTime > ERROR_THROTTLE_MS) {
                logger.error("Error during database health check", e);
                lastErrorLogTime = now;
            }
        }
    }
}
```

### Phase 6: Implement Reload Support

1. Create a dedicated reload method for explicit schema revalidation
2. Ensure schema is only validated on startup and reload, not reconnect

```java
public void reload() {
    logger.info("Reloading database manager...");
    
    // Close existing resources
    shutdown();
    
    // Reinitialize
    createConnectionProvider();
    initialize();
    
    // Force schema validation on reload
    schemaValidated = false;
    validateSchemaIfNeeded();
    
    logger.info("Database manager reloaded successfully");
}
```

### Phase 7: Special SQLite Considerations

1. Implement SQLite-specific optimizations
2. Adjust health check frequency for SQLite (less frequent)
3. Add connection validation that's appropriate for SQLite

## Testing Strategy

1. **Startup Testing**: Verify proper initialization and schema validation
2. **Reconnect Testing**: Simulate connection failures and verify recovery
3. **Reload Testing**: Test explicit reloads with schema validation
4. **Long-Running Testing**: Verify health check behavior over extended periods
5. **Error Case Testing**: Validate proper handling of various error conditions

## Implementation Checklist

- [ ] Phase 1: Refactor DatabaseSetup ✅
- [ ] Phase 2: Update DatabaseManager Constructor
- [ ] Phase 3: Refactor Initialize Method
- [ ] Phase 4: Implement Lightweight Connection Validation
- [ ] Phase 5: Update DatabaseHealthService
- [ ] Phase 6: Implement Reload Support
- [ ] Phase 7: Special SQLite Considerations

## Expected Outcomes

1. **Reduced Log Spam**: Fewer redundant messages about schema validation
2. **Improved Performance**: Less overhead from redundant operations
3. **Better Reliability**: More robust connection handling and recovery
4. **Cleaner Code**: Improved separation of concerns and dependency management
5. **Simplified Maintenance**: Clearer initialization flow and error handling

## Conclusion

This restructuring will significantly improve the database handling in RVNKLore by eliminating redundant operations, improving error handling, and providing a more robust foundation for future enhancements. The changes maintain backward compatibility while introducing a cleaner architectural approach.

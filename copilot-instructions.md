# RVNKLore Copilot Instructions

This document outlines the coding standards and best practices for the RVNKLore plugin. These guidelines should be followed when modifying or creating code to maintain consistency throughout the codebase.

## General Directive

- **Do not create data migration methods unless explicitly asked. Assume there is no data in the database and no data needs to be migrated after schema changes.**

## Database Architecture Guidelines

### DatabaseManager as Central Hub

- **DatabaseManager serves as the single entry point for all database connection setup and management**
- **All data operations must flow through DatabaseManager - no direct repository access from domain or command classes**
- **DatabaseManager handles connection pooling, health monitoring, reconnection logic, and transaction management**
- **Implement async operations using CompletableFuture for all database interactions to avoid blocking the main thread**

### Repository Layer Strategy

- **Repository classes should delegate all connection-dependent operations to DatabaseManager**
- **Repositories are limited to table-specific business logic and DTO mapping**
- **Plan to refactor repositories into service classes that focus on domain logic rather than data access**
- **Deprecate direct SQL query construction within repositories - use DatabaseManager's centralized query execution**

### Query Builder Abstraction

- **Define common QueryBuilder interface for constructing SQL queries**
- **Implement MySQLQueryBuilder and SQLiteQueryBuilder to encapsulate database-specific syntax**
- **Use QueryBuilder implementations to isolate database dialect differences behind clean interfaces**
- **All query construction should use these builders for consistency and maintainability**

### Data Transfer Objects (DTOs)

- **Create DTOs for all major entities (LoreEntry, LoreSubmission, ItemProperties, etc.)**
- **Use DTOs as the standard data format between database and domain layers**
- **Implement conversion methods between DTOs and domain objects**
- **DTOs should be immutable where possible and include validation logic**

### Configuration Management

- **Load configuration values into dedicated DTOs for type safety and validation**
- **Access configuration settings exclusively through DTO getters, not raw config calls**
- **Create MySQLSettingsDTO, SQLiteSettingsDTO, and DatabaseSettingsDTO for database configurations**
- **Eliminate scattered config.getString() calls throughout the codebase**

## Commenting Guidelines

### JavaDoc Comments

#### Class Documentation

- Explain the class's purpose and responsibility in the system
- Note important design patterns or architectural decisions
- Focus on "why" over implementation details

```java
/**
 * Manages lore item creation and distribution with configurable properties.
 * Acts as the central registry for all custom items within the lore system.
 */
```

#### Method Documentation

- Describe purpose and behavior, not implementation
- Document parameters and return values
- Note exceptions that may be thrown
- Include examples for complex methods

```java
/**
 * Retrieves lore content based on provided entity type and identifier.
 * Handles fallback behavior when specific lore isn't available.
 *
 * @param entityType The type of entity to retrieve lore for
 * @param identifier Unique identifier within the entity type
 * @return The lore content or default text if none found
 * @throws IllegalArgumentException If entityType is null
 */
```

### Code Comments

- Comment on "why" not "what" - explain reasoning behind code
- Place comments above the code they describe
- Keep comments concise and meaningful
- Use TODO and FIXME sparingly and with clear descriptions
- Explain complex logic, business rules, or non-obvious decisions

## Message Formatting Standards

### Player-Facing Messages

Use these standardized message prefixes:

- `&c▶` for usage instructions and command help
- `&6⚙` for operations in progress
- `&a✓` for success messages
- `&c✖` for error messages
- `&e⚠` for warnings
- `&7   ` (three spaces after) for additional information or tips

### Console and Debug Messages

- Use the designated logging system for all console output
- **Do not use emojis or symbols in console messages**
- **Do not use color codes in console output**
- Create clear, concise messages that explain the context
- For errors, include actionable information to help troubleshoot
- Use appropriate log levels (INFO, WARNING, ERROR, DEBUG)

## Database Conventions

Follow the naming standardization guidelines in `docs/schema/doc-naming-standardization.md`:

- Use singular form for table names with consistent domain prefixes
- Follow standard column naming patterns:
  - Primary keys: `id`
  - Foreign keys: `entity_name_id`
  - Boolean fields: `is_` or `has_` prefix
  - Date/time fields: `_at` or `_date` suffix

## Code Structure Best Practices

### Command Implementation

- Implement appropriate interfaces and follow class hierarchy
- **Commands must use DatabaseManager exclusively for all data operations**
- **Implement async operation handling with proper error messaging**
- Follow consistent validation, execution, and feedback pattern:
  1. Permission check
  2. Argument validation
  3. Entity/target validation
  4. Async operation execution via DatabaseManager
  5. User feedback with progress indicators
  6. Next-step guidance

### Event Handling

- Register handlers properly in the plugin lifecycle
- Keep handlers focused and lightweight
- Consider performance implications for high-frequency events
- Clean up listeners when no longer needed

### Resource Management

- Properly initialize and clean up resources
- Use try-with-resources for closeable resources
- Unregister listeners and cancel tasks on plugin disable

## Performance Considerations

- **Use asynchronous tasks for all database operations and file I/O**
- Implement caching for frequently accessed data
- Batch database operations when possible
- Consider memory usage when implementing data structures
- Profile code for potential performance bottlenecks

## Development Workflow

### Building and Testing

To build and test the plugin, use one of the following methods:

- **Reload Server**
  - Use the `Reload Server` task to build, copy and reload the plugin without restarting the server. This is useful for quick testing of changes.

- **Restart the Server**:
  - Use the `Restart Server` task to build, copy and fully restart the server. This ensures a clean state and is recommended for testing major changes.

These tasks can be executed from the VS Code task runner or directly from the terminal using the provided PowerShell scripts at .vscode/*.ps1

### Copilot Usage Best Practices

- Accept Copilot suggestions only if they match project structure, naming, and standards.
- Refactor generated code to fit conventions and architecture.
- Reject code that violates schema, naming, or logging standards.
- Validate event registration and plugin lifecycle alignment.
- **Ensure all database code follows the DatabaseManager centralization pattern**
- **Verify that configuration access uses DTOs rather than direct config calls**

### Logging Manager Standard

- Use the persistent `LogManager` class for all info, warning, and error logging in plugin code.
- Always declare the property as `private final LogManager logger;` (or `private LogManager logger;` if not final).
- Initialize with `this.logger = LogManager.getInstance(plugin);` in constructors.
- Use `logger.info(message)`, `logger.warning(message)`, and `logger.error(message, exception)` for all logging.
- Do not use `System.out.println()`, direct logger calls, or custom logger fields for these log levels.
- Use the property name `logger` for all `LogManager` usages to ensure consistency across the codebase.
- Reserve the `Debug` class for debug-level or trace logging only.

**Example:**
```java
private final LogManager logger;

public MyClass(RVNKLore plugin) {
    this.logger = LogManager.getInstance(plugin);
}

public void doSomething() {
    logger.info("Something happened");
    logger.warning("A warning");
    logger.error("An error occurred", exception);
}
```

## Data Strategy Implementation Guidelines

### Database Interactions
- **Always use the DatabaseManager as the single entry point for setting up and managing connections.**
- **Perform all connection logic (initialization, reconnect, shutdown, and health checks) within DatabaseManager.**
- **Repository classes should own all table-specific logic; DatabaseManager should only provide high-level coordination and delegate to repositories for CRUD logic.**
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
- **Command Classes:** Refactor commands to invoke DatabaseManager for all data operations asynchronously.
  - *Example:*
    ```java
    // Before
    LoreEntry entry = loreRepository.getLoreEntryById(id);
    
    // After
    CompletableFuture<LoreEntry> futureEntry = databaseManager.getLoreEntry(id);
    futureEntry.thenAccept(entry -> { /* Process entry */ });
    ```
- **Configuration System:** Update ConfigManager to utilize DTOs (e.g., MySQLSettingsDTO) for settings retrieval.
  - *Example:*
    ```java
    // Before
    String host = config.getString("storage.mysql.host");
    
    // After
    MySQLSettingsDTO settings = configManager.getMySQLSettings();
    String host = settings.getHost();
    ```
- **Item and Lore Domain Classes:** Introduce DTO-based data handling in domain classes with conversion getters/setters.
  - *Example:*
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

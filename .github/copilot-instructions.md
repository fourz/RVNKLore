# RVNKLore Copilot Instructions

These guidelines should be followed when modifying or creating code to maintain consistency throughout the codebase using documentation files as references.

## General Directive

- **Do not create data migration methods unless explicitly asked. Assume there is no data in the database and no data needs to be migrated after schema changes.**

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

- Comment on "why" less "what" - explain reasoning behind code
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
- `&7␣␣␣` for additional information or tips (three spaces after)

### Console and Debug Messages

- Use the designated logging system for all console output
- **Do not use emojis or symbols in console messages**
- **Do not use color codes in console output**
- Create clear, concise messages that explain the context
- For errors, include actionable information to help troubleshoot
- Use appropriate log levels (INFO, WARNING, ERROR, DEBUG)

## Logging Manager Standard

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

## Database Architecture Guidelines

See the detailed documentation in [Database Architecture](../docs/rvnklore-database-architecture.md).

Key principles:

- DatabaseManager as central hub for connections
- Repository layer for table-specific operations
- Query builder abstraction for SQL dialect handling
- DTOs for clean data flow between layers
- Asynchronous operations for all database calls

## Code Structure Best Practices

### Command Implementation

See the detailed documentation in [Command Manager](../docs/rvnklore-commandmanager.md).

Key principles:

- Follow class hierarchy and command patterns
- Consistent validation, execution, and feedback flow
- Asynchronous database operations
- Clear user feedback and error handling

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

See the detailed documentation in [Performance Considerations](../docs/rvnklore-performance-consideration.md).

Key principles:

- Use asynchronous operations for I/O and database access
- Implement caching for frequently accessed data
- Batch operations when possible
- Monitor resource usage and performance metrics

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

## Documentation Reference

### Context Window Include Directive

- **Only include linked documentation files if relevant to current task or question.**
- **Limit included files to those that directly relate to the task at hand.** 
- **Search the web for additional information if needed especially for queries related to Minecraft, Spigot or Bukkit.**

### RVNKLore Project Information (project overview and status)

- [README](../README.md)
- [ROADMAP](../ROADMAP.md)

### Architecture & Design

- [Database Architecture](../docs/rvnklore-database-architecture.md)
- [Schema Documentation](../docs/schema/)
- [Command Manager](../docs/rvnklore-commandmanager.md)
- [Performance Considerations](../docs/rvnklore-performance-consideration.md)
- [Item Manager](../docs/rvnklore-itemmanager.md)
- [Lore Manager](../docs/rvnklore-loremanager.md)
- [Collection Manager](../docs/rvnklore-collectionmanager.md)
- [Enchant Manager](../docs/rvnklore-enchantmanager.md)
- [Model Data Manager](../docs/rvnklore-modeldatamanager.md)
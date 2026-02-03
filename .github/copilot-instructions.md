# RVNKLore Copilot Instructions

**Lore system plugin** — Player biographies, item generation, collection tracking.

---

## Quick Reference

**Tech**: Java 17+, Paper 1.20+, Maven, RVNKCore dependency
**Standards**: See `docs/standard/` in Ravenkraft-Dev

---

## Tool Discovery

### Live Server Testing
```
/rvnktest health              # Full health check
/rvnktest services            # List registered services (ILoreService, IItemService, etc.)
/rvnktest db                  # Database connectivity
/lore                         # Plugin commands
```

### MCP Server Management
`mcp_rvnkdev-minec_*` tools for console commands, file operations, server state.

### Claude Integration
- **Rules**: `.claude/rules/` — Import shared patterns
- **Skills**: `.claude/skills/` — Domain capabilities (ravenkraft-lore.md)
- **Agents**: `.claude/agents/` — Specialized workflows

---

## Archon Workflow

See [CLAUDE.md](../../CLAUDE.md) for complete task-driven development.

**Quick cycle**: `find_tasks()` → doing → research → implement → review → done

---

## Core Directives

- **Do not create migration methods** unless explicitly asked
- **Use DatabaseManager** as single entry point for all connections
- **Use ServiceRegistry** for RVNKCore integration
- **Use Repository pattern** with `I` prefix interfaces
- **Use DTOs** for all data transfer

---

## Registered Services

RVNKLore registers these with RVNKCore ServiceRegistry:
- `ILoreService` — Lore entry management
- `IItemService` — Custom item generation
- `ICollectionService` — Collection tracking
- `ISubmissionService` — Lore submissions
- `IPlayerService` — Player lore data

---

## Database Architecture

```java
// All operations through DatabaseManager
CompletableFuture<LoreEntry> future = databaseManager.getLoreEntry(id);
future.thenAccept(entry -> { /* process */ });

// Configuration via DTOs
MySQLSettingsDTO settings = configManager.getMySQLSettings();
```

**Query Builder**: Use `MySQLQueryBuilder`/`SQLiteQueryBuilder` for dialect abstraction.

---

## Message Formatting

| Type | Prefix |
|------|--------|
| Usage | `&c▶` |
| Progress | `&6⚙` |
| Success | `&a✓` |
| Error | `&c✖` |
| Warning | `&e⚠` |
| Tips | `&7   ` |

**Console**: No emojis, no colors — use LogManager.

---

## Logging Standard

```java
private final LogManager logger;

public MyClass(RVNKLore plugin) {
    this.logger = LogManager.getInstance(plugin, "MyClass");
}
```

---

## Schema Reference

See `docs/schema/` for naming conventions:
- Tables: singular form with domain prefix
- Primary keys: `id`
- Foreign keys: `entity_name_id`
- Booleans: `is_` or `has_` prefix
- Timestamps: `_at` or `_date` suffix

---

## Documentation References

- **Coding Standards**: `docs/standard/coding-standards.md`
- **Architecture**: `docs/architecture/shared-patterns.md`
- **RVNKCore Integration**: `docs/standard/rvnkcore-integration.md`

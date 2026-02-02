# RVNKLore Copilot Instructions

**Parent Hub**: See Ravenkraft-Dev CLAUDE.md for complete ecosystem standards.

## Tool Discovery

**Server Management**: `mcp_rvnkdev-minec_*` tools (console, files, state, db)
**Live Testing**: `/rvnktest [health|services|db|plugins|run all]`
**Agents**: Browse `.claude/agents/` for specialized workflows
**Skills**: Browse `.claude/skills/` for domain capabilities
**Rules Import**: Use `@import ../../.claude/rules/<rule>.md` for shared directives

## Archon Integration

**Board**: `e7d91a7e-8b3c-4f2a-9d1e-5c8b2a4f6e3d` (RVNKLore)
**Workflow**: `find_tasks()` → `manage_task("update", status="doing")` → implement → `status="done"`

## Plugin-Specific Standards

### General Directive
- **No data migration methods** unless explicitly requested. Assume empty database.

### Database Architecture
- `DatabaseManager` is the central hub for all connection management
- All data operations flow through DatabaseManager (no direct repository access from commands)
- Use async `CompletableFuture` for all database interactions
- Repositories handle table-specific logic and DTO mapping only

### Services Registered (via RVNKCore)
- `ILoreService`, `IItemService`, `ICollectionService`
- `ISubmissionService`, `IPlayerService`

### DTOs
- Create DTOs for all entities (LoreEntry, LoreSubmission, ItemProperties)
- DTOs are immutable with validation logic
- Use conversion methods between DTOs and domain objects

### Message Prefixes
- `&c▶` usage | `&6⚙` progress | `&a✓` success | `&c✖` error | `&e⚠` warning

### Logging
Use `LogManager.getInstance(plugin, "ClassName")` from RVNKCore.

## References

- **Schema**: `docs/standard/rvnklore-schema.md`
- **Database API**: `docs/standard/rvnklore-database-api.md`
- **Architecture Patterns**: `docs/architecture/shared-patterns.md`

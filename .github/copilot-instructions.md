# RVNKLore Copilot Instructions

**Lore system plugin** — Player biographies, item generation, discovery tracking, collection achievements, Dynmap integration.

---

## Quick Reference

**Tech**: Java 21, Paper 1.21.4, Maven, RVNKCore 1.3.5-alpha (provided)
**Version**: 1.0.14 | **Branch**: derek/dev
**Standards**: See `docs/standard/` in Ravenkraft-Dev
**Task board**: `gh issue list --repo fourz/Ravenkraft-Dev --label "board:rvnklore"`

---

## Tool Discovery

### Live Server Testing
```
/rvnktest health              # Full health check
/rvnktest services            # List registered services (6 expected)
/rvnktest db                  # Database connectivity
/lore                         # Plugin commands
```

### MCP Server Management
`mcp__rvnkdev-minec__*` tools for console commands, file operations, server state.

### Claude Integration
- **Rules**: `.claude/rules/` — Import shared patterns
- **Skills**: `.claude/skills/` — Domain capabilities
- **Agents**: `.claude/agents/` — Specialized workflows

---

## Task Management

**GitHub Issues (primary)**: `gh issue list --repo fourz/Ravenkraft-Dev --label "board:rvnklore" --json number,title,labels`

---

## Core Directives

- **Use DatabaseManager** as single entry point for all connections
- **Use ServiceRegistry** for RVNKCore integration (reflection-based)
- **Use Repository pattern** with `I` prefix interfaces
- **Use DTOs** for all data transfer
- **Do not create migration methods** unless explicitly asked
- **REST API does NOT re-register on reload** — server restart required

---

## Registered Services (6)

RVNKLore registers these with RVNKCore ServiceRegistry at startup:

| Interface | Implementation |
|-----------|---------------|
| `ILoreService` | `LoreManager` |
| `IItemService` | `ItemManager` |
| `ICollectionService` | `CollectionManager` |
| `ISubmissionService` | `SubmissionManager` |
| `IPlayerLoreService` | `PlayerManager` |
| `ILoreBookService` | `LoreBookManager` |

3 notification types registered with `PlayerPreferencesService`: `rvnklore.discovery`, `rvnklore.achievement`, `rvnklore.collection_completion`.

---

## Database Architecture

```
Primary:  MySQL (HikariCP pool)
Fallback: SQLite (automatic on failure)
Tracker:  FallbackTracker from RVNKCore
```

**Tables**: `lore_entry`, `lore_submission`, `lore_item`, `lore_metadata`, `collection`, `player_collection_progress`, `collection_reward`, `collection_item`, `player_collection_items`, `lore_location`, `lore_discovery`, `player_achievement`, `player_reward_claim`

Table name constants are in `DatabaseConnection.java`.

---

## REST API

Base: `/api/lore/*` — registered via `IServletRegistrationService`.

**Reload does NOT re-register endpoints — restart required.**

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/entries` | Paginated lore entries |
| GET | `/entries/{id}` | Single entry by UUID |
| GET | `/entries/type/{type}` | Entries by LoreType |
| GET | `/entries/search?q=` | Text search |
| POST | `/submit` | New submission |
| GET | `/player/{uuid}/collection` | Player collection progress |
| GET | `/collections` | All collections |
| GET | `/types` | Available lore types |
| GET | `/stats` | Statistics |
| GET | `/health` | Health check |

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

**Console**: No emojis, no colors — use `LogManager` from RVNKCore.

---

## Logging Standard

```java
private final LogManager logger;

public MyClass(RVNKLore plugin) {
    this.logger = LogManager.getInstance(plugin, "MyClass");
}
```

---

## External Integrations (Soft Dependencies)

Plugin runs fully without any of these:
- **Dynmap** — Lore location map markers
- **PlaceholderAPI** — `%rvnklore_*%` placeholders
- **Discord** — Collection completion webhooks
- **Citizens** — NPC collection vendors (stub)
- **GriefPrevention** — Claim-based protection
- **VotingPlugin** — Vote reward items
- **RVNKWorlds** — World lifecycle events

---

## Documentation References

- **CLAUDE.md**: Full architecture, class tree, DB schema, API reference
- **Coding Standards**: `docs/standard/coding-standards.md`
- **Architecture**: `docs/architecture/shared-patterns.md`
- **RVNKCore Integration**: `docs/standard/rvnkcore-integration.md`
- **Graph Memory**: `search_nodes("RVNKLore")`

# RVNKLore: AI Assistant Instructions

@import ../../.claude/rules/archon-workflow.md
@import ../../.claude/rules/java-plugin-build.md

---

## Project Overview

**RVNKLore** is a comprehensive lore and history plugin for Minecraft servers. It provides player lore tracking, custom item generation with enchantments and model data, thematic collections system, submission approval workflow, and database-backed storage for all lore entries including landmarks, cities, characters, items, events, quests, and special entities.

## Build Commands

```bash
# Build plugin JAR
mvn clean package

# Build without tests (faster)
mvn clean package -DskipTests

# Validate POM and dependencies
mvn validate

# Check dependency tree
mvn dependency:tree
```

**Output**: `target/RVNKLore-1.0-SNAPSHOT.jar`

**Current Status**: ✅ Build passing, deployed and tested on RVNK Dev server (Feb 2026)

## Remote Testing Workflow

Use `/rvnkdev-deploy` and `/rvnkdev-query` skills for remote server testing:

```bash
# Full deployment cycle (build locally first)
mvn clean package
/rvnkdev-deploy b2bc4d7e full

# Query console for errors
/rvnkdev-query b2bc4d7e errors

# Check plugin startup logs
/rvnkdev-query b2bc4d7e plugin RVNKLore

# Quick config iteration (no restart)
/rvnkdev-deploy b2bc4d7e reload-only
```

**Server IDs**:
- `b2bc4d7e` - SparkedHost test server
- `1eb313b1-40f7-4209-aa9d-352128214206` - Local MCSS dev server

## Local MCSS Development

Configure `.vscode/project.json` for local MCSS deployment:
```json
{
    "OutputFile": "..\\target\\RVNKLore-1.0-SNAPSHOT.jar",
    "DestinationPath": "F:\\Minecraft\\MCSS\\servers\\RVNK Dev\\plugins",
    "PluginFolder": "RVNKLore",
    "API": { "serverid": "...", "key": "...", "hostname": "localhost", "port": 25560 }
}
```

## Architecture

### Core Class Structure

```
org.fourz.RVNKLore
├── RVNKLore.java                # Main plugin class, lifecycle management, RVNKCore service registration
├── command/
│   ├── CommandManager.java      # Command registration
│   ├── LoreCommand.java         # Main /lore command dispatcher
│   ├── Lore*SubCommand.java     # Subcommand implementations (add, get, list, approve, etc.)
│   ├── output/
│   │   └── DisplayFactory.java  # Formatted output for commands
│   └── SubCommand.java          # Base interface for subcommands
├── config/
│   ├── ConfigManager.java       # Multi-file config (config.yml, database settings)
│   └── dto/                     # Configuration DTOs (DatabaseSettings, MySQL, SQLite)
├── data/
│   ├── DatabaseManager.java     # Database lifecycle management
│   ├── DatabaseHelper.java      # Database utility operations
│   ├── DatabaseConnection.java  # Connection abstraction
│   ├── DatabaseConnectionFactory.java
│   ├── DatabaseBackupService.java
│   ├── FallbackTracker.java     # Database fallback mechanism
│   ├── connection/
│   │   ├── IConnectionProvider.java
│   │   ├── MySQLConnection.java
│   │   └── SQLiteConnection.java
│   ├── dialect/                 # SQL dialect abstraction (MySQL, SQLite)
│   │   ├── SQLDialect.java
│   │   ├── MySQLDialect.java
│   │   └── SQLiteDialect.java
│   ├── query/                   # QueryBuilder pattern
│   │   ├── IQueryBuilder.java
│   │   ├── IQueryExecutor.java
│   │   ├── MySQLQueryBuilder.java
│   │   └── SQLiteQueryBuilder.java
│   ├── dto/                     # Data transfer objects
│   │   ├── LoreEntryDTO.java
│   │   ├── ItemPropertiesDTO.java
│   │   ├── LoreSubmissionDTO.java
│   │   └── NameChangeRecordDTO.java
│   └── Repository interfaces/implementations:
│       ├── ILoreEntryRepository.java
│       ├── LoreEntryRepository.java
│       ├── IItemRepository.java
│       ├── ItemRepository.java
│       ├── IPlayerRepository.java (in lore/player/)
│       └── PlayerRepository.java (in lore/player/)
├── lore/
│   ├── LoreManager.java         # Core lore orchestration (implements ILoreService)
│   ├── LoreEntry.java           # Lore entry model
│   ├── LoreType.java            # Enum: LANDMARK, CITY, PLAYER, ITEM, EVENT, etc.
│   ├── LoreSubmission.java      # Submission model for approval workflow
│   ├── LoreFinder.java          # Search and retrieval utilities
│   ├── ItemLoreInterface.java   # Deprecated interface for item lore
│   ├── QuestLoreHandler.java    # Quest-specific lore handling
│   ├── item/
│   │   ├── ItemManager.java     # Item orchestration (implements IItemService)
│   │   ├── ItemProperties.java  # Item property model
│   │   ├── ItemType.java        # Enum: LEGENDARY, ARTIFACT, SEASONAL, etc.
│   │   ├── ItemLoreInterface.java
│   │   ├── collection/          # Collection system
│   │   │   ├── CollectionManager.java (implements ICollectionService)
│   │   │   ├── ItemCollection.java
│   │   │   ├── CollectionTheme.java
│   │   │   └── CollectionRewards.java
│   │   ├── cosmetic/            # Cosmetic head system
│   │   │   ├── CosmeticsManager.java
│   │   │   ├── HeadCollection.java
│   │   │   ├── HeadRarity.java
│   │   │   ├── HeadType.java
│   │   │   └── HeadVariant.java
│   │   ├── custommodeldata/     # Custom model data management
│   │   │   ├── CustomModelDataManager.java
│   │   │   ├── CustomModelDataCategory.java
│   │   │   └── CustomModelDataRange.java
│   │   └── enchant/             # Enchantment system
│   │       ├── EnchantManager.java
│   │       ├── EnchantedItemGenerator.java
│   │       ├── EnchantmentProfile.java
│   │       ├── EnchantmentRules.java
│   │       ├── EnchantmentTemplate.java
│   │       └── EnchantmentTier.java
│   ├── player/
│   │   ├── PlayerManager.java   # Player lore operations (implements IPlayerService)
│   │   ├── IPlayerRepository.java
│   │   ├── PlayerRepository.java
│   │   └── NameChangeRecord.java
│   └── submission/
│       └── SubmissionManager.java (implements ISubmissionService)
├── handler/
│   ├── HandlerFactory.java      # Handler registration and lifecycle
│   ├── CityLoreHandler.java     # City-specific lore handling
│   ├── CommonHeadHandler.java   # Common head handling
│   └── sign/                    # Sign-based lore handlers
│       ├── HandlerSignBarterShops.java
│       ├── HandlerSignCity.java
│       └── HandlerSignLandmark.java
├── service/                     # RVNKCore service interfaces
│   ├── ILoreService.java        # Lore service interface
│   ├── IItemService.java        # Item service interface
│   ├── ICollectionService.java  # Collection service interface
│   ├── ISubmissionService.java  # Submission service interface
│   └── IPlayerService.java      # Player service interface
├── util/
│   ├── UtilityManager.java      # Utility orchestration
│   ├── DiagnosticUtil.java      # Diagnostic utilities
│   ├── ExceptionHandler.java    # Exception handling utilities
│   ├── HeadUtil.java            # Head item utilities
│   ├── NameGenerator.java       # Name generation for entities
│   ├── PerformanceMonitor.java  # Performance tracking
│   ├── TransactionManager.java  # Transaction management
│   ├── UuidUtil.java            # UUID utilities
│   └── ValidationUtil.java      # Validation utilities
└── exception/
    └── LoreException.java       # Custom exception types
```

### Key Patterns

**Manager Lifecycle**: All managers implement `initialize()` and `shutdown()`/`cleanup()` pattern
**Service Pattern**: Core managers implement service interfaces (ILoreService, IItemService, etc.) for RVNKCore integration
**Repository Pattern**: Data access through repository interfaces with DTO layer
**Database Abstraction**: Dialect-based SQL generation (MySQL/SQLite) with QueryBuilder pattern
**Subcommand Pattern**: LoreCommand dispatches to Lore*SubCommand implementations
**Handler Factory**: Dynamic handler registration for different lore types

### Service Registration (RVNKCore Integration)

RVNKLore uses reflection-based service registration to avoid hard dependencies on RVNKCore. The main plugin class registers five services with the RVNKCore ServiceRegistry:

```java
// Services registered (if RVNKCore available):
- ILoreService      → LoreManager
- IItemService      → ItemManager
- ICollectionService → CollectionManager
- ISubmissionService → SubmissionManager
- IPlayerService    → PlayerManager
```

Registration occurs in `onEnable()` via `registerWithRVNKCore()` and cleanup in `onDisable()` via `unregisterFromRVNKCore()`.

### Database System

```
Primary:   MySQL or SQLite (configurable)
Provider:  HikariCP connection pooling (planned/in migration)
Fallback:  FallbackTracker monitors consecutive failures
Dialects:  MySQLDialect, SQLiteDialect for vendor-specific SQL
```

Core tables include: lore_entries, lore_items, lore_locations, lore_characters, lore_quests, lore_events, collections, seasonal_items, item_values, player_achievements, and more.

### Lore Types

RVNKLore supports 11 distinct lore types defined in `LoreType` enum:
- `LANDMARK` - Notable locations in the world
- `CITY` - Settlements and cities
- `PLAYER` - Player character histories and achievements
- `FACTION` - Groups, factions, and organizations
- `ITEM` - Legendary or special items
- `HEAD` - Decorative head items with lore
- `EVENT` - Historical events
- `PATH` - Notable roads or pathways
- `QUEST` - Adventures and quests
- `ENCHANTMENT` - Special or legendary enchantments
- `SPECIAL_ENTITY` - Named or significant mobs

### Collection System

The collection system organizes items into thematic groups with support for:
- Multiple collection memberships per item
- Seasonal availability windows
- Themed sets (e.g., MICKY_HATS, LEGENDARY, QUEST_REWARDS)
- Rarity levels (COMMON → MYTHIC → UNIQUE → ARTIFACT)
- Item types (LEGENDARY, ARTIFACT, SEASONAL, EVENT, etc.)

## Command Formatting Standards

Use consistent message prefixes in command handlers:
- `&c▶` - Usage instructions
- `&6⚙` - Operations in progress
- `&a✓` - Success messages
- `&c✖` - Error messages
- `&e⚠` - Warnings
- `&7   ` - Additional tips

**Console/Debug**: No emojis, no color codes. Use `LogManager` class for all logging.

## Dependencies

| Dependency | Purpose |
|------------|---------|
| spigot-api 1.21.4-R0.1-SNAPSHOT | Bukkit API |
| snakeyaml 2.0 | YAML configuration |
| guava 32.1.3-jre | Google utilities |
| gson 2.8.9 | JSON serialization |
| worldedit-bukkit 7.3.0 | WorldEdit integration (provided) |
| rvnkcore 1.3.0-alpha | RVNKCore integration (provided) |

**Java Version**: 21 (compile target)

**Note**: RVNKCore uses `provided` scope - JAR must be in server plugins folder at runtime. The lib/rvnkcore-1.3.0-alpha.jar file is for IDE reference only.

## Documentation References

### Local Documentation
- [README.md](README.md) - Features, commands, configuration, API examples
- [docs/rvnklore-loremanager.md](docs/rvnklore-loremanager.md) - LoreManager documentation
- [docs/rvnklore-itemmanager.md](docs/rvnklore-itemmanager.md) - ItemManager documentation
- [docs/rvnklore-collectionmanager.md](docs/rvnklore-collectionmanager.md) - CollectionManager documentation
- [docs/rvnklore-enchantmanager.md](docs/rvnklore-enchantmanager.md) - EnchantManager documentation
- [docs/rvnklore-modeldatamanager.md](docs/rvnklore-modeldatamanager.md) - CustomModelDataManager documentation

### Archon Board Documents (RVNKLore-specific)
Documents on RVNKLore board (`a5856487-51f9-417f-965b-761f49f385d3`):
- **RVNKLore Database Schema Reference** - Authoritative schema for lore_entry, lore_submission, lore_item tables
- **RVNKLore Database Integration API Reference** - HikariCP patterns, DAO implementation, migration system
- **RVNKLore Database Architecture Refactor Plan** - QueryBuilder migration roadmap

### Parent Board Standards (Cross-cutting)
Documents on Ravenkraft Dev board (`4787f505-e92e-474d-ba54-f5ac7993ccfe`):
- [Coding Standards](../../docs/standard/coding-standards.md) - Java 17+ conventions
- [RVNKCore Integration](../../docs/standard/rvnkcore-integration.md) - ServiceRegistry usage patterns
- [Database Patterns](../../docs/standard/database-patterns.md) - Repository pattern, HikariCP

## Archon MCP Integration

**RVNKLore Board**: `a5856487-51f9-417f-965b-761f49f385d3`
**Parent Project (Ravenkraft Dev)**: `4787f505-e92e-474d-ba54-f5ac7993ccfe`
**RVNKCore Board**: `7785e125-4468-44e2-a86c-2fef668fce48`

Use parent project for shared RVNK standards, coding patterns, and documentation. Reference RVNKCore board for integration patterns (ServiceRegistry, Repository, DTO) and shared service interfaces.

Use Archon task management for development workflow:

```python
# Check for existing tasks on this board
find_tasks(project_id="a5856487-51f9-417f-965b-761f49f385d3")

# Start work
manage_task("update", task_id="...", status="doing")

# Search knowledge base for Paper/Bukkit patterns
rag_search_knowledge_base(query="Bukkit custom items")

# Complete task
manage_task("update", task_id="...", status="done")
```

### Current Status (Feb 2026)

**Build Status**: ✅ Passing (`mvn clean package -Dmaven.test.skip=true`)

**Completed Features**:
- Service interface layer (ILoreService, IItemService, etc.)
- RVNKCore ServiceRegistry integration via reflection
- Collection management system
- Custom model data manager
- Enchantment system
- Cosmetic head system
- Database abstraction with dialect support (HikariCP)
- QueryBuilder pattern implementation
- DTO layer for data transfer
- Async repository pattern (CompletableFuture)
- REST API endpoints at `/api/lore/*` (via RVNKCore RestAPIService)

**Pending Implementations**:
- Web interface integration
- PlaceholderAPI support
- NPC integration (Citizens)

## Development Checklist

Before committing changes:
1. `mvn clean package -Dmaven.test.skip=true` - Build succeeds
2. Test on local MCSS server or deploy to test server
3. Verify console output for errors: `/rvnkdev-query <id> errors`
4. Check plugin loads correctly: `/rvnkdev-query <id> plugin RVNKLore`
5. Validate RVNKCore service registration in logs
6. Test key commands: `/lore list`, `/lore search`, `/lore reload`, `/lore export json`
7. Verify database connectivity (SQLite/MySQL)

## Known Issues

1. **Short ID Format**: `/lore get` requires full UUID but list/search shows truncated IDs (bug-01)
2. **RVNKCore Dependency**: Must be present in server plugins folder at runtime (provided scope)

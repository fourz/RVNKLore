# RVNKLore: AI Assistant Instructions

@import ../../.claude/rules/archon-workflow.md
@import ../../.claude/rules/java-plugin-build.md

---

## Project Overview

**RVNKLore** is a comprehensive lore and history plugin for Bukkit/Spigot/Paper servers. It provides player lore tracking, custom item generation with enchantments and custom model data, a thematic collections system, lore discovery and achievement systems, a submission approval workflow, Dynmap marker integration, and database-backed storage for all lore entries covering landmarks, cities, players, factions, items, events, quests, enchantments, monuments, taverns, guilds, and shrines.

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

**Output**: `target/RVNKLore.jar` (versionless — `<finalName>` set in pom.xml)

**Current Status**: Active development — For plugin status and history, search Graph Memory: `search_nodes("RVNKLore")`

## Task Management

**GitHub Issues (primary)**: `gh issue list --repo fourz/Ravenkraft-Dev --label "board:rvnklore" --json number,title,labels`

**Status flow**: `open` → in progress (comment) → `closed`

## Remote Testing Workflow

Use `/rvnkdev-deploy` and `/rvnkdev-query` skills for remote server testing:

```bash
# Full deployment cycle (build locally first)
mvn clean package
/rvnkdev-deploy <server_id> full

# Query console for errors
/rvnkdev-query <server_id> errors

# Check plugin startup logs
/rvnkdev-query <server_id> plugin RVNKLore

# Quick config iteration (no restart)
/rvnkdev-deploy <server_id> reload-only
```

Use `mcp__ravencast-mcp__find_servers` to look up current server IDs.

## Architecture

### Core Class Structure

```
org.fourz.RVNKLore
├── RVNKLore.java                    # Main plugin class, lifecycle, RVNKCore registration
├── command/
│   ├── CommandManager.java          # Command registration
│   ├── LoreCommand.java             # Main /lore command dispatcher
│   ├── Lore*SubCommand.java         # Subcommand implementations (add, get, list, approve,
│   │                                #   book, browse, collection, debug, discover, dynmap,
│   │                                #   export, import, item, prefs, reload, search, seed)
│   ├── cosmetic/                    # Cosmetic subcommands (disabled)
│   ├── faction/                     # Faction-specific subcommands
│   │   ├── LoreFactionSubCommand.java
│   │   ├── LoreFactionAddTerritorySubCommand.java
│   │   └── LoreFactionRefreshSubCommand.java
│   ├── output/
│   │   └── DisplayFactory.java      # Formatted output for commands
│   ├── SubCommand.java              # Subcommand interface
│   └── TabCompletionUtil.java       # Tab completion helpers
├── config/
│   ├── ConfigManager.java           # Multi-file config (config.yml, database settings)
│   └── dto/                         # Configuration DTOs (DatabaseSettings, MySQL, SQLite)
├── data/
│   ├── DatabaseManager.java         # Database lifecycle facade, repository wiring
│   ├── DatabaseHelper.java          # Utility operations
│   ├── DatabaseConnection.java      # Abstract base (HikariCP pool, table name constants)
│   ├── DatabaseConnectionFactory.java
│   ├── DatabaseBackupService.java
│   ├── MySQLConnection.java         # HikariCP MySQL implementation
│   ├── SQLiteConnection.java        # SQLite fallback implementation
│   ├── connection/
│   │   └── IConnectionProvider.java
│   ├── dialect/                     # SQL dialect abstraction
│   │   ├── SQLDialect.java
│   │   ├── MySQLDialect.java
│   │   └── SQLiteDialect.java
│   ├── query/                       # QueryBuilder pattern
│   │   ├── IQueryBuilder.java
│   │   ├── IQueryExecutor.java
│   │   ├── MySQLQueryBuilder.java
│   │   └── SQLiteQueryBuilder.java
│   ├── dto/                         # Data Transfer Objects
│   │   ├── LoreEntryDTO.java
│   │   ├── ItemPropertiesDTO.java
│   │   ├── LoreSubmissionDTO.java
│   │   ├── NameChangeRecordDTO.java
│   │   └── LoreLocationDTO.java
│   ├── model/
│   │   ├── LoreLocation.java
│   │   └── CollectionReward.java
│   ├── repository/                  # Repository implementations
│   │   ├── IAchievementRepository.java / AchievementRepository.java
│   │   ├── ICollectionRewardRepository.java / CollectionRewardRepository.java
│   │   ├── IDiscoveryRepository.java / DiscoveryRepository.java
│   │   └── ILocationRepository.java / LocationRepository.java
│   ├── ILoreEntryRepository.java / LoreEntryRepository.java
│   └── IItemRepository.java / ItemRepository.java
├── lore/
│   ├── LoreManager.java             # Core lore orchestration (implements ILoreService)
│   ├── LoreEntry.java               # Lore entry model
│   ├── LoreType.java                # Enum: 15 types (GENERIC, PLAYER, CITY, LANDMARK,
│   │                                #   FACTION, PATH, ITEM, EVENT, QUEST, ENCHANTMENT,
│   │                                #   MONUMENT, HEAD, TAVERN, GUILD, SHRINE)
│   ├── LoreCategory.java
│   ├── LoreSubmission.java          # Submission model for approval workflow
│   ├── LoreFinder.java              # Search and retrieval utilities
│   ├── QuestLoreHandler.java
│   ├── item/
│   │   ├── ItemManager.java         # Item orchestration (implements IItemService)
│   │   ├── ItemProperties.java
│   │   ├── ItemType.java
│   │   ├── ItemLoreInterface.java
│   │   ├── book/
│   │   │   ├── LoreBookManager.java (implements ILoreBookService)
│   │   │   └── BookRarity.java
│   │   ├── collection/
│   │   │   ├── CollectionManager.java (implements ICollectionService)
│   │   │   ├── ItemCollection.java
│   │   │   ├── CollectionTheme.java
│   │   │   ├── CollectionRewards.java
│   │   │   └── event/              # Collection completion events
│   │   ├── cosmetic/
│   │   │   ├── CosmeticsManager.java
│   │   │   ├── HeadCollection.java
│   │   │   ├── HeadRarity.java
│   │   │   ├── HeadType.java
│   │   │   └── HeadVariant.java
│   │   ├── custommodeldata/
│   │   │   ├── CustomModelDataManager.java
│   │   │   ├── CustomModelDataCategory.java
│   │   │   └── CustomModelDataRange.java
│   │   └── enchant/
│   │       ├── EnchantManager.java
│   │       ├── EnchantedItemGenerator.java
│   │       ├── EnchantmentProfile.java
│   │       ├── EnchantmentRules.java
│   │       ├── EnchantmentTemplate.java
│   │       └── EnchantmentTier.java
│   ├── player/
│   │   ├── PlayerManager.java       (implements IPlayerLoreService)
│   │   ├── IPlayerRepository.java / PlayerRepository.java
│   │   └── NameChangeRecord.java
│   └── submission/
│       └── SubmissionManager.java   (implements ISubmissionService)
├── handler/
│   ├── HandlerFactory.java          # Handler registration and lifecycle
│   ├── LoreHandlerManager.java
│   ├── LoreHandler.java             # Base handler interface
│   ├── CityLoreHandler.java
│   ├── CommonHeadHandler.java
│   ├── DefaultLoreHandler.java
│   ├── EnchantedItemLoreHandler.java
│   ├── EnchantmentLoreHandler.java
│   ├── EventLoreHandler.java
│   ├── FactionLoreHandler.java
│   ├── GuildLoreHandler.java
│   ├── HeadLoreHandler.java
│   ├── ItemLoreHandler.java
│   ├── LandmarkLoreHandler.java
│   ├── MonumentLoreHandler.java
│   ├── PathLoreHandler.java
│   ├── PlayerLoreHandler.java
│   ├── ShrineLoreHandler.java
│   ├── TavernLoreHandler.java
│   └── sign/
│       ├── HandlerSignCity.java
│       ├── HandlerSignGuild.java
│       ├── HandlerSignLandmark.java
│       ├── HandlerSignMonument.java
│       ├── HandlerSignShrine.java
│       └── HandlerSignTavern.java
├── service/                         # RVNKCore service interfaces
│   ├── ILoreService.java
│   ├── IItemService.java
│   ├── ICollectionService.java
│   ├── ISubmissionService.java
│   ├── IPlayerLoreService.java
│   ├── ILoreBookService.java
│   └── item/                        # Additional item service sub-interfaces
├── api/
│   ├── LoreApiInitializer.java      # REST API registration with RVNKCore
│   └── controller/
│       └── LoreApiServlet.java      # Handles /api/lore/* endpoints
├── search/
│   ├── LoreSearchService.java
│   ├── SearchCriteria.java
│   ├── SearchResult.java
│   └── browse/                      # Browse/filter support
├── achievement/
│   ├── AchievementManager.java
│   ├── Achievement.java
│   ├── AchievementProgress.java
│   ├── AchievementReward.java
│   ├── AchievementType.java
│   ├── AchievementUnlockEvent.java
│   └── reward/                      # Achievement reward types
├── discovery/
│   ├── DiscoveryManager.java
│   ├── DiscoveryNotificationManager.java
│   ├── DiscoveryListener.java
│   ├── CartographyDiscoveryListener.java
│   ├── QuestDiscoveryListener.java
│   ├── DiscoveryTriggerType.java
│   └── LoreDiscoveryEvent.java
├── gui/
│   ├── GuiListener.java
│   ├── ItemBuilder.java
│   ├── MenuHolder.java
│   └── PaginatedMenu.java
├── integration/
│   ├── citizens.disabled/           # Citizens NPC (stub, soft dependency)
│   ├── discord/                     # Discord webhook (collection completions)
│   ├── dynmap/                      # Dynmap marker integration
│   ├── griefprevention/             # GriefPrevention claim integration
│   ├── placeholder/                 # PlaceholderAPI expansion
│   ├── preferences/                 # PlayerPreferencesService integration
│   ├── rvnkworlds/                  # RVNKWorlds world lifecycle listener
│   └── votingplugin/                # VotingPlugin rewards
├── util/
│   ├── UtilityManager.java
│   ├── DiagnosticUtil.java
│   ├── ExceptionHandler.java
│   ├── HeadUtil.java
│   ├── NameGenerator.java
│   ├── PerformanceMonitor.java
│   ├── TransactionManager.java
│   ├── UuidUtil.java
│   └── ValidationUtil.java
└── exception/
    └── LoreException.java
```

### Key Patterns

**Manager Lifecycle**: All managers implement `initialize()` and `shutdown()`/`cleanup()` pattern
**Service Pattern**: Core managers implement service interfaces registered with RVNKCore ServiceRegistry
**Repository Pattern**: Data access through repository interfaces with DTO layer
**Database Abstraction**: Dialect-based SQL (MySQL/SQLite) with HikariCP and QueryBuilder
**Subcommand Pattern**: LoreCommand dispatches to Lore*SubCommand implementations
**Handler Factory**: Dynamic handler registration for lore-type-specific processing
**Fallback Pattern**: MySQL primary with automatic SQLite fallback via FallbackTracker

### Service Registration (RVNKCore Integration)

RVNKLore uses reflection-based service registration to avoid hard dependencies on RVNKCore. The main plugin class registers six services with the RVNKCore ServiceRegistry:

```java
// Services registered (if RVNKCore available):
- ILoreService       → LoreManager
- IItemService       → ItemManager
- ICollectionService → CollectionManager
- ISubmissionService → SubmissionManager
- IPlayerLoreService → PlayerManager
- ILoreBookService   → LoreBookManager
```

Registration occurs in `onEnable()` via `registerWithRVNKCore()` and cleanup in `onDisable()` via `unregisterFromRVNKCore()`.

Additionally, 3 notification types are registered with `PlayerPreferencesService` under the `rvnklore` namespace: `discovery`, `achievement`, `collection_completion`.

### Database System

```
Primary:   MySQL (configurable, default SQLite)
Fallback:  SQLite (automatic on consecutive MySQL failures)
Pool:      HikariCP connection pooling (MySQL and SQLite)
Tracker:   FallbackTracker (from RVNKCore) monitors failure count and recovery
Dialects:  MySQLDialect, SQLiteDialect for vendor-specific SQL generation
```

**Database Tables** (defined as constants in `DatabaseConnection.java`):

| Constant | Table Name |
|----------|------------|
| `TABLE_LORE_ENTRY` | `lore_entry` |
| `TABLE_LORE_SUBMISSION` | `lore_submission` |
| `TABLE_LORE_ITEM` | `lore_item` |
| `TABLE_LORE_METADATA` | `lore_metadata` |
| `TABLE_COLLECTION` | `collection` |
| `TABLE_PLAYER_COLLECTION_PROGRESS` | `player_collection_progress` |
| `TABLE_COLLECTION_REWARD` | `collection_reward` |
| `TABLE_COLLECTION_ITEM` | `collection_item` |
| `TABLE_PLAYER_COLLECTION_ITEMS` | `player_collection_items` |
| `TABLE_LORE_LOCATION` | `lore_location` |
| `TABLE_LORE_DISCOVERY` | `lore_discovery` |
| `TABLE_PLAYER_ACHIEVEMENT` | `player_achievement` |
| `TABLE_PLAYER_REWARD_CLAIM` | `player_reward_claim` |

### REST API

Base path: `/api/lore/*` — registered via `IServletRegistrationService` at plugin startup.

**Important**: Reload does NOT re-register the REST API. A full server restart is required if the API registration state changes.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/lore/entries` | List entries (paginated, approved filter) |
| GET | `/api/lore/entries/{id}` | Get specific entry by UUID |
| GET | `/api/lore/entries/type/{type}` | Entries filtered by LoreType |
| GET | `/api/lore/entries/search?q=` | Search entries by query string |
| POST | `/api/lore/submit` | Submit new lore entry |
| GET | `/api/lore/player/{uuid}/collection` | Player collection progress |
| GET | `/api/lore/player/{uuid}` | Alias for `/player/{uuid}/collection` (same handler, same response) |
| GET | `/api/lore/collections` | List all collections |
| GET | `/api/lore/types` | List available lore types |
| GET | `/api/lore/stats` | Lore statistics |
| GET | `/api/lore/health` | Health check |

### Lore Types

15 types defined in `LoreType` enum:
`GENERIC`, `PLAYER`, `CITY`, `LANDMARK`, `FACTION`, `PATH`, `ITEM`, `EVENT`, `QUEST`, `ENCHANTMENT`, `MONUMENT`, `HEAD`, `TAVERN`, `GUILD`, `SHRINE`

### External Integrations (Soft Dependencies)

All integrations are optional — plugin runs fully without any of them:

| Integration | Class | Purpose |
|-------------|-------|---------|
| Dynmap | `DynmapIntegration` | Map markers for lore locations |
| Citizens | `CitizensIntegration` (stub, deferred) | NPC collection vendors — excluded from compilation via `citizens.disabled/` package; `TraitFactory` registration not yet wired; deferred until collection system stabilizes |
| Discord | `CollectionWebhookListener` | Collection completion webhooks |
| PlaceholderAPI | `RVNKLorePlaceholderExpansion` | `%rvnklore_*%` placeholders |
| VotingPlugin | `VotingPluginIntegration` | Vote reward items |
| GriefPrevention | `GriefPreventionIntegration` | Claim-based lore protection |
| RVNKWorlds | `WorldLifecycleListener` | World load/unload events |

### RVNKCore Services Consumed

- `IServletRegistrationService` — REST endpoint registration
- `PlayerPreferencesService` — notification type registration
- `PlayerLookup` — name/UUID resolution
- `LogManager` — structured logging
- `FallbackTracker` — database failure tracking

## Command Formatting Standards

Use consistent message prefixes in command handlers:
- `&c▶` - Usage instructions
- `&6⚙` - Operations in progress
- `&a✓` - Success messages
- `&c✖` - Error messages
- `&e⚠` - Warnings
- `&7   ` - Additional tips

**Console/Debug**: No emojis, no color codes. Use `LogManager` from RVNKCore for all logging.

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| spigot-api | 1.21.4-R0.1-SNAPSHOT | Bukkit API |
| rvnkcore | 1.3.5-alpha | Shared services, ServiceRegistry, LogManager (provided) |
| snakeyaml | 2.0 | YAML configuration |
| guava | 32.1.3-jre | Google utilities |
| gson | 2.8.9 | JSON serialization |
| worldedit-bukkit | 7.3.0 | WorldEdit integration (provided) |

**Java Version**: 21 (compile target)

**Note**: RVNKCore uses `provided` scope — JAR must be in server plugins folder at runtime. The `lib/rvnkcore-*.jar` file is for IDE reference only.

## Documentation References

### Local Documentation
- [README.md](README.md) - Features, commands, configuration, API examples
- [docs/rvnklore-loremanager.md](docs/rvnklore-loremanager.md) - LoreManager documentation
- [docs/rvnklore-itemmanager.md](docs/rvnklore-itemmanager.md) - ItemManager documentation
- [docs/rvnklore-collectionmanager.md](docs/rvnklore-collectionmanager.md) - CollectionManager documentation
- [docs/rvnklore-enchantmanager.md](docs/rvnklore-enchantmanager.md) - EnchantManager documentation
- **Graph Memory** — For plugin status and history: `search_nodes("RVNKLore")`

### Parent Board Standards (Cross-cutting)
Documents on Ravenkraft Dev board (`4787f505-e92e-474d-ba54-f5ac7993ccfe`):
- [Coding Standards](../../docs/standard/coding-standards.md) - Java 17+ conventions
- [RVNKCore Integration](../../docs/standard/rvnkcore-integration.md) - ServiceRegistry usage patterns
- [Database Patterns](../../docs/standard/database-patterns.md) - Repository pattern, HikariCP

## Development Checklist

Before committing changes:
1. `mvn clean package` - Build succeeds
2. Test on local MCSS server or deploy to test server
3. Verify console output for errors: `/rvnkdev-query <id> errors`
4. Check plugin loads correctly: `/rvnkdev-query <id> plugin RVNKLore`
5. Validate RVNKCore service registration in logs (6 services)
6. Test key commands: `/lore list`, `/lore search`, `/lore reload`
7. Verify database connectivity and fallback behavior if applicable

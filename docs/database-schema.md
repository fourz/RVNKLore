---
title: RVNKLore Database Schema Reference
category: schema
tags: [database, schema, rvnklore]
board: rvnklore
last_updated: 2026-05-09
source_of_truth: src/main/java/org/fourz/RVNKLore/data/DatabaseConnection.java
---

# RVNKLore Database Schema Reference

**Authoritative Reference** — Derived from `DatabaseConnection.java` `createTables()` method.

**Last Updated**: May 29, 2026
**Database Support**: SQLite (default), MySQL/MariaDB (configurable)
**Applies To**: RVNKLore plugin v1.0.25+

> **Note**: The older `docs/rvnklore-schema.md` is outdated. This document supersedes it.
> Always source schema truth from `DatabaseConnection.java`.

---

## Overview

RVNKLore uses a relational database schema organized into five groups:

| Group | Tables | Purpose |
|-------|--------|---------|
| Core Lore | `lore_entry`, `lore_submission`, `lore_metadata` | Base entries and versioned content |
| Item System | `lore_item`, `lore_item_rng_pool` | Custom Minecraft items and RNG drop pools |
| Quest Integration | `quest_item_presets` | Quest-specific item preset associations |
| Collection System | `collection`, `player_collection_progress`, `collection_reward`, `collection_item`, `player_collection_items` | Item collections and player tracking |
| Spatial | `lore_location` | Geographic coordinates for lore entries |
| Player Tracking | `lore_discovery`, `player_achievement`, `player_reward_claim` | Discovery events and achievement state |

All table names are configurable via a **table prefix** (`storage.<type>.tablePrefix` in `config.yml`). The prefix is prepended to all base table names at runtime. Constraint names also incorporate the prefix.

---

## Dialect Abstraction

All DDL is generated through a `SQLDialect` interface with two implementations:

| Dialect | Auto-Increment | Boolean Type | Timestamp w/ Default | Upsert Syntax |
|---------|---------------|--------------|----------------------|---------------|
| `SQLiteDialect` | `INTEGER PRIMARY KEY AUTOINCREMENT` | `BOOLEAN` | `TIMESTAMP DEFAULT CURRENT_TIMESTAMP` | `ON CONFLICT(...) DO UPDATE SET` |
| `MySQLDialect` | `INT AUTO_INCREMENT PRIMARY KEY` | `TINYINT(1)` | `TIMESTAMP DEFAULT CURRENT_TIMESTAMP` | `ON DUPLICATE KEY UPDATE` |

SQLite uses the `RETURNING` clause (SQLite 3.35+) to retrieve generated IDs. MySQL uses `Statement.RETURN_GENERATED_KEYS` with `getGeneratedKeys()`.

---

## Connection Pooling (HikariCP)

Both dialects use HikariCP for connection pooling. Key configuration values:

| Parameter | Config Key | Default |
|-----------|-----------|---------|
| Pool size | `storage.<type>.poolSize` | 10 |
| Connection timeout | `storage.<type>.connectionTimeout` | 30,000 ms |
| Idle timeout | `storage.<type>.idleTimeout` | 600,000 ms |
| Max lifetime | `storage.<type>.maxLifetime` | 1,800,000 ms |

**SQLite-specific optimizations** (enabled by default):
- `PRAGMA journal_mode = WAL` — Write-Ahead Logging for concurrent reads
- `PRAGMA synchronous = NORMAL` — Balanced durability vs. performance
- `PRAGMA foreign_keys = ON` — Enforces FK constraints (SQLite does not enforce by default)

**MySQL-specific optimizations**:
- Prepared statement caching (`cachePrepStmts=true`, cache size 250)
- `rewriteBatchedStatements=true`
- `useServerPrepStmts=true`

---

## Fallback Behavior

`DatabaseManager` uses `FallbackTracker` (from RVNKCore) to monitor MySQL connection health.

| Threshold | Config Key | Default |
|-----------|-----------|---------|
| Max failures before fallback | `database.fallback.maxFailuresBeforeFallback` | 3 |
| Recovery attempt interval | `database.fallback.recoveryTimeMinutes` | 5 minutes |

When the failure threshold is reached, the plugin switches to SQLite automatically. After the recovery interval elapses, the next database operation attempts to reconnect to MySQL. The `isInFallbackMode()` method and the `/api/lore/health` and `/api/lore/stats` endpoints expose fallback state.

---

## Table Definitions

### `lore_entry`

**Purpose**: Root entity for all lore content. Every piece of lore — regardless of type — starts with one row here.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | CHAR(36) | NO | — | UUID primary key |
| `entry_type` | VARCHAR(50) | NO | — | Maps to `LoreType` enum |
| `name` | VARCHAR(100) | NO | — | Display name |

**Constraints**:
- `uq_<prefix>lore_entry_name_type` — UNIQUE(`name`, `entry_type`)

**Indexes**: Primary key on `id`

**Foreign key target for**: `lore_submission`, `lore_item`, `lore_metadata`, `lore_location`, `lore_discovery`

**LoreType values** (16 types): `LANDMARK`, `CITY`, `PLAYER`, `FACTION`, `ITEM`, `HEAD`, `EVENT`, `PATH`, `QUEST`, `ENCHANTMENT`, `SPECIAL_ENTITY`, `GENERIC`, `MONUMENT`, `TAVERN`, `GUILD`, `SHRINE`

```sql
CREATE TABLE IF NOT EXISTS lore_entry (
    id CHAR(36) PRIMARY KEY,
    entry_type VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_lore_entry_name_type UNIQUE (name, entry_type)
);
```

---

### `lore_submission`

**Purpose**: Versioned lore content with approval workflow. One entry can have multiple submission versions; only one row should have `is_current_version = TRUE` per `entry_id`.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | INT/INTEGER | NO | auto | Auto-increment PK |
| `entry_id` | CHAR(36) | NO | — | FK → `lore_entry(id)` |
| `slug` | VARCHAR(150) | NO | — | URL-safe identifier, globally unique |
| `visibility` | VARCHAR(20) | NO | `'PUBLIC'` | `PUBLIC`, `STAFF_ONLY`, `HIDDEN` |
| `status` | VARCHAR(20) | NO | `'ACTIVE'` | `ACTIVE`, `ARCHIVED`, `DRAFT`, `PENDING_APPROVAL` |
| `submitter_uuid` | CHAR(36) | NO | — | Player UUID of submitter |
| `submission_date` | TIMESTAMP | NO | `CURRENT_TIMESTAMP` | Dialect-provided default |
| `approval_status` | VARCHAR(20) | NO | `'PENDING'` | Approval workflow state |
| `approved_by` | CHAR(36) | YES | NULL | Staff UUID who approved |
| `approved_at` | TIMESTAMP | YES | NULL | Approval timestamp |
| `view_count` | INTEGER | NO | `0` | Read counter |
| `last_viewed_at` | TIMESTAMP | YES | NULL | Last access time |
| `created_at` | TIMESTAMP | NO | `CURRENT_TIMESTAMP` | Dialect-provided default |
| `updated_at` | TIMESTAMP | YES | NULL | Last modification time (MySQL: `TIMESTAMP NULL`, SQLite: `TIMESTAMP`) |
| `content_version` | INTEGER | NO | `1` | Version number within entry |
| `is_current_version` | BOOLEAN/TINYINT(1) | NO | `FALSE` | Current version flag |
| `content` | TEXT | YES | NULL | JSON content blob |
| `rejection_reason` | VARCHAR(500) | YES | NULL | Staff-provided reason when approval is denied |

**Constraints**:
- `uq_<prefix>lore_submission_entry_version` — UNIQUE(`entry_id`, `content_version`)
- `uq_<prefix>lore_submission_slug` — UNIQUE(`slug`)
- `ck_<prefix>lore_submission_status` — CHECK(`status` IN `('ACTIVE', 'ARCHIVED', 'DRAFT', 'PENDING_APPROVAL')`)
- `ck_<prefix>lore_submission_visibility` — CHECK(`visibility` IN `('PUBLIC', 'STAFF_ONLY', 'HIDDEN')`)
- FK: `entry_id` → `lore_entry(id)` ON DELETE CASCADE

**Indexes**: `idx_<prefix>lore_submission_entry_id` on `entry_id`

---

### `lore_metadata`

**Purpose**: Key-value metadata pairs associated with a lore entry. Used for flexible per-entry attributes that do not fit the fixed schema.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `lore_id` | VARCHAR(36) | NO | — | FK → `lore_entry(id)` |
| `meta_key` | VARCHAR(64) | NO | — | Metadata key |
| `meta_value` | TEXT | YES | NULL | Metadata value |

**Constraints**:
- PRIMARY KEY (`lore_id`, `meta_key`)
- FK: `lore_id` → `lore_entry(id)` ON DELETE CASCADE

> **Note**: The older `rvnklore-schema.md` incorrectly referenced `lore_entries` (a legacy table no longer created) as the FK target. The actual FK is `lore_entry`.

---

### `lore_item`

**Purpose**: Custom Minecraft item definitions linked one-to-one with a lore entry.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | INT/INTEGER | NO | auto | Auto-increment PK |
| `name` | VARCHAR(64) | NO | — | Item display name |
| `short_uuid` | VARCHAR(12) | YES | NULL | Short UUID for in-game commands |
| `lore_entry_id` | CHAR(36) | NO | — | FK → `lore_entry(id)`, UNIQUE |
| `material` | VARCHAR(50) | NO | — | Bukkit `Material` name |
| `item_type` | VARCHAR(50) | NO | — | Maps to `ItemType` enum |
| `rarity` | VARCHAR(20) | NO | — | Rarity tier string |
| `is_obtainable` | BOOLEAN/TINYINT(1) | YES | `1` | Can players obtain this item |
| `custom_model_data` | INTEGER | YES | NULL | Resource pack CustomModelData value |
| `season_id` | INTEGER | YES | NULL | Seasonal association (if any) |
| `is_vote_reward` | BOOLEAN/TINYINT(1) | NO | `FALSE` | VotingPlugin reward item flag |
| `item_properties` | TEXT | YES | NULL | JSON: `ItemPropertiesDTO` |
| `drop_settings` | TEXT | YES | NULL | JSON: drop configuration |
| `created_by` | VARCHAR(64) | YES | NULL | Creator identifier |
| `nbt_data` | TEXT | YES | NULL | Serialized NBT data |
| `created_at` | TIMESTAMP | NO | `CURRENT_TIMESTAMP` | Dialect-provided default |
| `updated_at` | TIMESTAMP | NO | `CURRENT_TIMESTAMP` | Dialect-provided default |

**Constraints**:
- `uq_<prefix>lore_item_entry` — UNIQUE(`lore_entry_id`)
- FK: `lore_entry_id` → `lore_entry(id)` ON DELETE CASCADE

**Indexes**: `idx_<prefix>lore_item_entry_id` on `lore_entry_id`

---

### `lore_item_rng_pool`

**Purpose**: Weighted RNG drop pool entries. Each row associates a `lore_item` with a named pool and rarity tier, with a weight controlling relative probability. Used by `RngItemServiceImpl` to randomly select items for loot/reward events.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | INT | NO | auto | Auto-increment PK |
| `pool_id` | VARCHAR(100) | NO | — | Named pool identifier (e.g., `"dungeon_loot"`) |
| `lore_item_id` | INT | NO | — | FK → `lore_item(id)` |
| `rarity_tier` | VARCHAR(20) | NO | `'COMMON'` | Rarity filter; uppercased at query time |
| `weight` | INT | NO | `100` | Relative draw weight (higher = more likely) |
| `is_active` | TINYINT(1) | NO | `1` | Soft-delete / enable flag |
| `created_at` | TIMESTAMP | NO | `CURRENT_TIMESTAMP` | Creation time |

**Constraints**:
- `idx_<prefix>lore_item_rng_pool_pool_id` on `pool_id`
- `idx_<prefix>lore_item_rng_pool_item_id` on `lore_item_id`
- FK: `lore_item_id` → `lore_item(id)` (implied; enforced at application level)

**Selection algorithm** (`RngItemServiceImpl.roll()`): build list filtered by `pool_id` + optional `rarity_tier` + `is_active = 1`, sum weights, draw uniformly in `[0, totalWeight)`, walk list to find winner. Falls back to empty if pool is empty or DB unavailable.

---

### `quest_item_presets`

**Purpose**: Associates `lore_item` entries with specific quest IDs as preset/curated items. Used by the RVNKQuests integration to define which lore items are tied to a particular quest (e.g., reward items, key items, collectibles).

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | INT | NO | auto | Auto-increment PK |
| `quest_id` | VARCHAR(100) | NO | — | RVNKQuests quest identifier |
| `lore_item_id` | INT | NO | — | FK → `lore_item(id)` |
| `label` | VARCHAR(100) | YES | NULL | Human-readable role label (e.g., `"reward"`, `"key_item"`) |
| `notes` | TEXT | YES | NULL | Internal notes for quest designers |
| `created_at` | TIMESTAMP | NO | `CURRENT_TIMESTAMP` | Creation time |

**Constraints**:
- `idx_<prefix>quest_item_presets_quest_id` on `quest_id`
- `idx_<prefix>quest_item_presets_item_id` on `lore_item_id`

---

### `collection`

**Purpose**: Defines a named collection of lore items. Players complete collections by discovering all associated items.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | INT/INTEGER | NO | auto | Auto-increment PK |
| `collection_id` | TEXT | NO | — | String slug identifier |
| `name` | TEXT | NO | — | Display name |
| `description` | TEXT | YES | NULL | Collection description |
| `theme_id` | TEXT | YES | NULL | Theme association (e.g., season, event) |
| `is_active` | BOOLEAN/TINYINT(1) | YES | `1` | Active/enabled flag |
| `created_at` | INTEGER | NO | — | Unix epoch timestamp (no default) |

**Constraints**:
- MySQL: `uq_<prefix>collection_id` — UNIQUE(`collection_id(255)`)
- SQLite: `uq_<prefix>collection_id` — UNIQUE(`collection_id`)

> **Note**: `created_at` is stored as a Unix epoch `INTEGER`, unlike other tables which use `TIMESTAMP`. This is intentional for simpler timestamp math in the collection system, but creates a type inconsistency across the schema.

---

### `player_collection_progress`

**Purpose**: Tracks each player's progress toward completing each collection.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | INT/INTEGER | NO | auto | Auto-increment PK |
| `player_id` | TEXT | NO | — | Player UUID string |
| `collection_id` | TEXT | NO | — | Collection slug |
| `progress` | REAL | YES | `0.0` | Completion ratio (0.0–1.0) |
| `completed_at` | INTEGER | YES | NULL | Unix epoch timestamp when completed |
| `last_updated` | INTEGER | NO | — | Unix epoch of last progress update |

**Constraints**:
- MySQL: `uq_<prefix>player_collection` — UNIQUE(`player_id(36)`, `collection_id(255)`)
- SQLite: `uq_<prefix>player_collection` — UNIQUE(`player_id`, `collection_id`)

> **Gap**: No explicit index on `player_id` or `collection_id`. For servers with many players and collections, a query on `player_id` will perform a full table scan.

---

### `collection_reward`

**Purpose**: Defines rewards attached to a collection. A collection can have multiple rewards.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | INT/INTEGER | NO | auto | Auto-increment PK |
| `collection_id` | TEXT | NO | — | Collection slug identifier |
| `reward_type` | TEXT | NO | — | Maps to `RewardType` enum |
| `reward_data` | TEXT | YES | NULL | JSON: reward configuration |
| `is_claimed` | BOOLEAN/TINYINT(1) | YES | `0` | Global claimed flag (see note) |

> **Design note**: `is_claimed` here is a global flag, not per-player. Per-player claim tracking is handled by `player_reward_claim`. The `is_claimed` column on this table is of questionable utility since a reward can be claimed by multiple players.

> **Gap**: No index on `collection_id`. Lookups by collection require a full scan.

---

### `collection_item`

**Purpose**: Junction table linking items to collections with ordering information.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `collection_id` | INTEGER | NO | — | FK → `collection(id)` |
| `item_id` | INTEGER | NO | — | FK → `lore_item(id)` |
| `sequence_number` | INTEGER | YES | `0` | Display order within collection |
| `item_config` | TEXT | YES | NULL | JSON: per-collection item overrides |
| `entry_id` | CHAR(36) | YES | NULL | FK → `lore_entry(id)` — direct lore entry association (added via migration) |

**Constraints**:
- PRIMARY KEY (`collection_id`, `item_id`)
- FK: `collection_id` → `collection(id)` ON DELETE CASCADE
- FK: `item_id` → `lore_item(id)` ON DELETE CASCADE

> **Note**: `entry_id` is a nullable migration column. It is not yet used by any query and will be populated in a future phase when collection items are directly associated with lore entries rather than inferred through `lore_item`.

---

### `player_collection_items`

**Purpose**: Tracks which specific items within a collection each player has discovered.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | INT/INTEGER | NO | auto | Auto-increment PK |
| `player_uuid` | CHAR(36) | NO | — | Player UUID |
| `collection_id` | INTEGER | NO | — | FK → `collection(id)` |
| `item_id` | INTEGER | NO | — | FK → `lore_item(id)` |
| `discovered_at` | TIMESTAMP | NO | `CURRENT_TIMESTAMP` | Discovery timestamp |
| `entry_uuid` | CHAR(36) | YES | NULL | Lore entry UUID for direct association (added via migration) |

**Constraints**:
- `uq_<prefix>player_collection_item` — UNIQUE(`player_uuid`, `collection_id`, `item_id`)
- FK: `collection_id` → `collection(id)` ON DELETE CASCADE
- FK: `item_id` → `lore_item(id)` ON DELETE CASCADE

**Indexes**:
- `idx_<prefix>player_collection_items_player` on `player_uuid`
- `idx_<prefix>player_collection_items_collection` on `collection_id`

> **Note**: `entry_uuid` is a nullable migration column, not yet used by any query.

---

### `lore_location`

**Purpose**: Stores geographic coordinates for lore entries. An entry can have multiple locations (e.g., a path has a start and end point).

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | INT/INTEGER | NO | auto | Auto-increment PK |
| `entry_id` | CHAR(36) | NO | — | FK → `lore_entry(id)` |
| `world` | VARCHAR(64) | NO | — | Minecraft world name |
| `x` | DOUBLE | NO | — | X coordinate |
| `y` | DOUBLE | NO | — | Y coordinate |
| `z` | DOUBLE | NO | — | Z coordinate |
| `location_type` | VARCHAR(30) | YES | `'PRIMARY'` | `PRIMARY` or custom type string |
| `label` | VARCHAR(100) | YES | NULL | Human-readable label |
| `created_at` | TIMESTAMP | NO | `CURRENT_TIMESTAMP` | Dialect-provided default |

**Constraints**:
- FK: `entry_id` → `lore_entry(id)` ON DELETE CASCADE

**Indexes**:
- `idx_<prefix>lore_location_entry` on `entry_id`
- `idx_<prefix>lore_location_world` on `(world, x, z)` — spatial proximity queries

---

### `lore_discovery`

**Purpose**: Enriched discovery event log. Records when a player discovers a lore entry, where, and how.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | INT/INTEGER | NO | auto | Auto-increment PK |
| `player_uuid` | CHAR(36) | NO | — | Player who discovered |
| `entry_id` | CHAR(36) | NO | — | FK → `lore_entry(id)` |
| `trigger_type` | VARCHAR(30) | NO | — | Maps to `DiscoveryTriggerType` enum: `MANUAL`, `LOCATION`, `ITEM`, `COMMAND`, `CHEST`, `BOOK`, `CLAIM_ENTER` |
| `world` | VARCHAR(64) | YES | NULL | World where discovery occurred |
| `x` | DOUBLE | YES | NULL | X coordinate at discovery |
| `y` | DOUBLE | YES | NULL | Y coordinate at discovery |
| `z` | DOUBLE | YES | NULL | Z coordinate at discovery |
| `is_first_discovery` | BOOLEAN/TINYINT(1) | NO | `FALSE` | True if no other player had found this entry first |
| `discovered_at` | TIMESTAMP | NO | `CURRENT_TIMESTAMP` | Dialect-provided default |

**Constraints**:
- `uq_<prefix>lore_discovery_player_entry` — UNIQUE(`player_uuid`, `entry_id`) — a player can only discover an entry once
- FK: `entry_id` → `lore_entry(id)` ON DELETE CASCADE

**Indexes**:
- `idx_<prefix>lore_discovery_player` on `player_uuid`
- `idx_<prefix>lore_discovery_entry` on `entry_id`
- `idx_<prefix>lore_discovery_first` on `is_first_discovery`

---

### `player_achievement`

**Purpose**: Tracks progress and completion state for each achievement per player.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `player_uuid` | CHAR(36) | NO | — | Part of composite PK |
| `achievement_id` | VARCHAR(50) | NO | — | Part of composite PK; maps to `AchievementType` |
| `current_progress` | INTEGER | NO | `0` | Current progress counter |
| `target_progress` | INTEGER | NO | `1` | Required progress to complete |
| `completed` | BOOLEAN/TINYINT(1) | NO | `FALSE` | Completion flag |
| `rewards_claimed` | BOOLEAN/TINYINT(1) | NO | `FALSE` | Whether rewards have been issued |
| `started_at` | BIGINT | NO | — | Unix epoch milliseconds |
| `completed_at` | BIGINT | NO | `0` | Unix epoch milliseconds; `0` = not completed |

**Constraints**:
- PRIMARY KEY (`player_uuid`, `achievement_id`)

**Indexes**:
- `idx_<prefix>player_achievement_player` on `player_uuid`
- `idx_<prefix>player_achievement_completed` on `completed`

> **Note**: `started_at` and `completed_at` use BIGINT (milliseconds), unlike `lore_discovery` which uses TIMESTAMP. This inconsistency exists across the schema.

---

### `player_reward_claim`

**Purpose**: Per-player tracking of which collection rewards have been claimed.

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | INT/INTEGER | NO | auto | Auto-increment PK |
| `reward_id` | INTEGER | NO | — | FK → `collection_reward(id)` |
| `player_uuid` | CHAR(36) | NO | — | Player UUID |
| `claimed_at` | BIGINT | NO | — | Unix epoch milliseconds |

**Constraints**:
- FK: `reward_id` → `collection_reward(id)` ON DELETE CASCADE

**Indexes**:
- `idx_<prefix>reward_claim_unique` — UNIQUE(`reward_id`, `player_uuid`) — prevents duplicate claims
- `idx_<prefix>reward_claim_player` on `player_uuid`

---

## Entity Relationships

```
lore_entry (1) ────────────────< (N) lore_submission
    │                                   (versioned content, approval workflow)
    │
    ├─── (1) ────────────────── (0..1) lore_item
    │                                   (one-to-one via UNIQUE FK)
    │                                       │
    │                               ────────< (N) lore_item_rng_pool (pool membership, weighted)
    │                               ────────< (N) quest_item_presets (quest preset association)
    │
    ├─── (1) ────────────────< (N) lore_metadata
    │                                   (key-value pairs)
    │
    ├─── (1) ────────────────< (N) lore_location
    │                                   (spatial coordinates)
    │
    └─── (1) ────────────────< (N) lore_discovery
                                        (per-player discovery events)

collection (1) ──────────────< (N) player_collection_progress
    │                                   (per-player progress %)
    │
    ├─── (1) ──────────────< (N) collection_reward
    │                               │
    │                               └─── (1) ──< (N) player_reward_claim
    │                                           (per-player claim tracking)
    │
    └─── (N) ──< collection_item >── (N) lore_item
                 (many-to-many junction, ordered)
                     │
                     └─ also tracked per-player in player_collection_items

player_achievement
    (standalone; no FK to lore_entry — achievement triggers reference entry IDs
     in application logic, not enforced by DB constraint)
```

---

## Schema vs. Expected Table Audit

The following tables were listed as expected (from prior planning docs) but **do not exist** in the current codebase:

| Expected Table | Status | Replacement |
|----------------|--------|-------------|
| `lore_character` | Not implemented | `lore_entry` with `entry_type = 'PLAYER'` or `'FACTION'` |
| `lore_faction` | Not implemented | `lore_entry` with `entry_type = 'FACTION'` |
| `lore_event` | Not implemented | `lore_entry` with `entry_type = 'EVENT'` |
| `lore_path` | Not implemented | `lore_entry` with `entry_type = 'PATH'` |
| `lore_collection` | Not implemented | Renamed to `collection` |
| `lore_seasonal_item` | Not implemented | `lore_item.season_id` field |
| `lore_item_value` | Not implemented | `lore_item.item_properties` JSON |
| `player_achievements` | Not implemented | Renamed to `player_achievement` |
| `player_lore_discovery` | Not implemented | Renamed to `lore_discovery` |
| `player_name_history` | Not implemented | No equivalent found in schema |

---

## Timestamp Type Convention

Three timestamp storage types are used across the 15 tables. **New columns must use `TIMESTAMP` (dialect default)**. The `INTEGER` and `BIGINT` columns are legacy — do not compare them directly with `TIMESTAMP` columns without conversion.

| Type | Tables / Columns | Notes |
|------|-----------------|-------|
| `TIMESTAMP` (dialect default) | `lore_entry`, `lore_submission`, `lore_item`, `lore_location`, `lore_discovery.discovered_at`, `player_collection_items.discovered_at` | Standard — use for all new columns |
| `INTEGER` (Unix epoch seconds) | `collection.created_at`, `player_collection_progress.completed_at`, `player_collection_progress.last_updated` | Legacy — epoch seconds, no fractional precision |
| `BIGINT` (Unix epoch milliseconds) | `player_achievement.started_at`, `player_achievement.completed_at`, `player_reward_claim.claimed_at` | Legacy — millisecond precision |

**Conversion**: When joining across types, use `FROM_UNIXTIME(col)` (MySQL) or `datetime(col, 'unixepoch')` (SQLite) to convert INTEGER/BIGINT to TIMESTAMP for comparison.

---

## Schema Initialization

Collection tables (`collection`, `player_collection_progress`, `collection_reward`, `collection_item`, `player_collection_items`) are created via two paths:

1. **`createTables()`** — Normal startup path. Calls `setupCollectionSchema()`, then `runMigrations()`.
2. **`ensureCollectionTables()`** — Safety-net path. Called from `CollectionManager.loadCollectionsFromDatabase()` on every startup. Calls `setupCollectionSchema()` only (not `runMigrations()`). Idempotent via `CREATE TABLE IF NOT EXISTS`.

The `runMigrations()` method handles additive schema changes via `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` (silent no-op if column already exists). Current migrations:
- `collection_item.entry_id CHAR(36) NULL`
- `player_collection_items.entry_uuid CHAR(36) NULL`

---

## Known Schema Gaps

| Gap | Affected Table(s) | Severity | Status |
|-----|-------------------|----------|--------|
| Timestamp type inconsistency across tables | `collection`, `player_collection_progress`, `player_achievement`, `player_reward_claim` | Medium | Open — documented above; new columns use TIMESTAMP |
| `is_claimed` on `collection_reward` is global, not per-player | `collection_reward` | Medium | Open (design gap) |
| No `player_name_history` table for name change tracking | — | Low | Open |
| Missing index on `player_collection_progress.player_id` | `player_collection_progress` | Medium | **Fixed** (commit 4b03ff0) |
| Missing index on `collection_reward.collection_id` | `collection_reward` | Low | **Fixed** (commit 4b03ff0) |
| Collection tables not created on first startup (silent `createTableSafely` swallow) | All collection tables | High | **Fixed** v1.0.25 — `ensureCollectionTables()` safety net added; `saveCollection()` prefix bug fixed; `theme`/`theme_id` column name corrected throughout `ItemRepository` |

---

## Dev DB Table Naming Convention

On the RVNK Dev server, RVNKLore is configured with `storage.mysql.tablePrefix: lore_`. Because the base table names already start with `lore_` (e.g., `lore_entry`), the resulting table names are double-prefixed: **`lore_lore_entry`**, `lore_lore_item`, etc.

The `t("lore_entry")` helper in `DatabaseConnection` resolves consistently via the configured prefix, so the plugin operates correctly. This naming quirk is a consequence of the chosen prefix value — not a bug.

**Orphaned unprefixed tables** (`lore_entry`, `lore_item`, etc.) may exist in the dev DB from before the prefix was applied. They are no longer used and are safe to drop.

---

## Related Documentation

- **Old Schema Reference** (outdated): [rvnklore-schema.md](rvnklore-schema.md)
- **Database API Reference**: [rvnklore-database-api.md](rvnklore-database-api.md)
- **Database Patterns**: [../../docs/standard/database-patterns.md](../../docs/standard/database-patterns.md)
- **Source of Truth**: `src/main/java/org/fourz/RVNKLore/data/DatabaseConnection.java`

---

**Document Version**: 2.2.0
**Maintainer**: Ravenkraft Development Team

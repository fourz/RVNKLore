# RVNKLore Database Schema

**Authoritative Reference** - Single source of truth for RVNKLore database schema.

**Last Updated**: January 30, 2026
**Database Support**: SQLite (primary), MySQL (planned)
**Applies To**: RVNKLore plugin

---

## Overview

RVNKLore uses a relational database schema with the following table groups:

1. **Core Lore Schema** - Base lore entries and submissions
2. **Item System** - Custom items with properties and metadata
3. **Collection System** - Player progress tracking and rewards
4. **Legacy Tables** - Backward compatibility (deprecated)

---

## 1. Core Lore Schema

### lore_entry

Base table for all lore content. Each entry has a unique UUID.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | CHAR(36) | PRIMARY KEY | UUID identifier |
| `entry_type` | VARCHAR(50) | NOT NULL | Lore type (ITEM, LOCATION, CHARACTER, EVENT, QUEST) |
| `name` | VARCHAR(100) | NOT NULL | Display name |

**Constraints**:
- `uq_lore_entry_name_type`: UNIQUE(name, entry_type)

**Indexes**: Primary key on `id`

```sql
CREATE TABLE IF NOT EXISTS lore_entry (
    id CHAR(36) PRIMARY KEY,
    entry_type VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_lore_entry_name_type UNIQUE (name, entry_type)
);
```

---

### lore_submission

Versioned content with approval workflow. Supports multiple versions per entry.

| Column | Type | Constraints | Default | Description |
|--------|------|-------------|---------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | - | Auto-increment ID |
| `entry_id` | CHAR(36) | NOT NULL, FK | - | Reference to lore_entry |
| `slug` | VARCHAR(150) | NOT NULL, UNIQUE | - | URL-safe identifier |
| `visibility` | VARCHAR(20) | NOT NULL | 'PUBLIC' | PUBLIC, STAFF_ONLY, HIDDEN |
| `status` | VARCHAR(20) | NOT NULL | 'ACTIVE' | ACTIVE, ARCHIVED, DRAFT, PENDING_APPROVAL |
| `submitter_uuid` | CHAR(36) | NOT NULL | - | Player UUID who submitted |
| `submission_date` | TIMESTAMP | NOT NULL | CURRENT_TIMESTAMP | When submitted |
| `approval_status` | VARCHAR(20) | NOT NULL | 'PENDING' | Approval state |
| `approved_by` | CHAR(36) | - | NULL | Staff UUID who approved |
| `approved_at` | TIMESTAMP | - | NULL | When approved |
| `view_count` | INTEGER | NOT NULL | 0 | View counter |
| `last_viewed_at` | TIMESTAMP | - | NULL | Last view timestamp |
| `created_at` | TIMESTAMP | NOT NULL | CURRENT_TIMESTAMP | Creation time |
| `updated_at` | TIMESTAMP | - | NULL | Last update time |
| `content_version` | INTEGER | NOT NULL | 1 | Version number |
| `is_current_version` | BOOLEAN | NOT NULL | FALSE | Current version flag |
| `content` | TEXT | - | NULL | JSON content blob |

**Constraints**:
- `uq_lore_submission_entry_version`: UNIQUE(entry_id, content_version)
- `uq_lore_submission_slug`: UNIQUE(slug)
- `ck_lore_submission_status`: CHECK(status IN ('ACTIVE', 'ARCHIVED', 'DRAFT', 'PENDING_APPROVAL'))
- `ck_lore_submission_visibility`: CHECK(visibility IN ('PUBLIC', 'STAFF_ONLY', 'HIDDEN'))
- Foreign key to `lore_entry(id)` ON DELETE CASCADE

**Indexes**: `idx_lore_submission_entry_id` on `entry_id`

```sql
CREATE TABLE IF NOT EXISTS lore_submission (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entry_id CHAR(36) NOT NULL,
    slug VARCHAR(150) NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    submitter_uuid CHAR(36) NOT NULL,
    submission_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by CHAR(36),
    approved_at TIMESTAMP,
    view_count INTEGER NOT NULL DEFAULT 0,
    last_viewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    content_version INTEGER NOT NULL DEFAULT 1,
    is_current_version BOOLEAN NOT NULL DEFAULT FALSE,
    content TEXT,
    CONSTRAINT uq_lore_submission_entry_version UNIQUE (entry_id, content_version),
    CONSTRAINT uq_lore_submission_slug UNIQUE (slug),
    CONSTRAINT ck_lore_submission_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DRAFT', 'PENDING_APPROVAL')),
    CONSTRAINT ck_lore_submission_visibility CHECK (visibility IN ('PUBLIC', 'STAFF_ONLY', 'HIDDEN')),
    FOREIGN KEY (entry_id) REFERENCES lore_entry(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_lore_submission_entry_id ON lore_submission(entry_id);
```

---

## 2. Item System

### lore_item

Custom Minecraft items with properties, rarity, and metadata.

| Column | Type | Constraints | Default | Description |
|--------|------|-------------|---------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | - | Auto-increment ID |
| `name` | VARCHAR(64) | NOT NULL | - | Item display name |
| `short_uuid` | VARCHAR(12) | - | NULL | Short UUID for commands |
| `lore_entry_id` | CHAR(36) | NOT NULL, FK, UNIQUE | - | Reference to lore_entry |
| `material` | VARCHAR(50) | NOT NULL | - | Minecraft material type |
| `item_type` | VARCHAR(50) | NOT NULL | - | Item category |
| `rarity` | VARCHAR(20) | NOT NULL | - | Rarity tier |
| `is_obtainable` | BOOLEAN | - | 1 | Can players obtain this |
| `custom_model_data` | INTEGER | - | NULL | Resource pack model ID |
| `season_id` | INTEGER | - | NULL | Seasonal association |
| `is_vote_reward` | BOOLEAN | NOT NULL | FALSE | VotingPlugin reward flag |
| `item_properties` | TEXT | - | NULL | JSON properties blob |
| `drop_settings` | TEXT | - | NULL | JSON drop configuration |
| `created_by` | VARCHAR(64) | - | NULL | Creator identifier |
| `nbt_data` | TEXT | - | NULL | Serialized NBT data |
| `created_at` | TIMESTAMP | NOT NULL | CURRENT_TIMESTAMP | Creation time |
| `updated_at` | TIMESTAMP | - | CURRENT_TIMESTAMP | Last update time |

**Constraints**:
- `uq_lore_item_entry`: UNIQUE(lore_entry_id)
- Foreign key to `lore_entry(id)` ON DELETE CASCADE

**Indexes**: `idx_lore_item_entry_id` on `lore_entry_id`

```sql
CREATE TABLE IF NOT EXISTS lore_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(64) NOT NULL,
    short_uuid VARCHAR(12),
    lore_entry_id CHAR(36) NOT NULL,
    material VARCHAR(50) NOT NULL,
    item_type VARCHAR(50) NOT NULL,
    rarity VARCHAR(20) NOT NULL,
    is_obtainable BOOLEAN DEFAULT 1,
    custom_model_data INTEGER,
    season_id INTEGER,
    is_vote_reward BOOLEAN NOT NULL DEFAULT FALSE,
    item_properties TEXT,
    drop_settings TEXT,
    created_by VARCHAR(64),
    nbt_data TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_lore_item_entry UNIQUE (lore_entry_id),
    FOREIGN KEY (lore_entry_id) REFERENCES lore_entry(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_lore_item_entry_id ON lore_item(lore_entry_id);
```

---

## 3. Collection System

### collection

Groupings of items/lore for player collection tracking.

| Column | Type | Constraints | Default | Description |
|--------|------|-------------|---------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | - | Auto-increment ID |
| `collection_id` | TEXT | UNIQUE, NOT NULL | - | String identifier |
| `name` | TEXT | NOT NULL | - | Display name |
| `description` | TEXT | - | NULL | Collection description |
| `theme_id` | TEXT | - | NULL | Theme association |
| `is_active` | BOOLEAN | - | 1 | Active/enabled flag |
| `created_at` | INTEGER | NOT NULL | - | Unix timestamp |

```sql
CREATE TABLE IF NOT EXISTS collection (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    collection_id TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    theme_id TEXT,
    is_active BOOLEAN DEFAULT 1,
    created_at INTEGER NOT NULL
);
```

---

### player_collection_progress

Tracks player progress toward collection completion.

| Column | Type | Constraints | Default | Description |
|--------|------|-------------|---------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | - | Auto-increment ID |
| `player_id` | TEXT | NOT NULL | - | Player UUID |
| `collection_id` | TEXT | NOT NULL | - | Collection identifier |
| `progress` | REAL | - | 0.0 | Completion percentage |
| `completed_at` | INTEGER | - | NULL | Unix timestamp when completed |
| `last_updated` | INTEGER | NOT NULL | - | Last progress update |

**Constraints**: UNIQUE(player_id, collection_id)

```sql
CREATE TABLE IF NOT EXISTS player_collection_progress (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id TEXT NOT NULL,
    collection_id TEXT NOT NULL,
    progress REAL DEFAULT 0.0,
    completed_at INTEGER,
    last_updated INTEGER NOT NULL,
    UNIQUE(player_id, collection_id)
);
```

---

### collection_reward

Rewards for collection completion.

| Column | Type | Constraints | Default | Description |
|--------|------|-------------|---------|-------------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | - | Auto-increment ID |
| `collection_id` | TEXT | NOT NULL | - | Collection identifier |
| `reward_type` | TEXT | NOT NULL | - | Type of reward |
| `reward_data` | TEXT | - | NULL | JSON reward configuration |
| `is_claimed` | BOOLEAN | - | 0 | Claimed flag |

```sql
CREATE TABLE IF NOT EXISTS collection_reward (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    collection_id TEXT NOT NULL,
    reward_type TEXT NOT NULL,
    reward_data TEXT,
    is_claimed BOOLEAN DEFAULT 0
);
```

---

### collection_item

Junction table linking items to collections with ordering and configuration.

| Column | Type | Constraints | Default | Description |
|--------|------|-------------|---------|-------------|
| `collection_id` | INTEGER | NOT NULL, FK | - | Reference to collection(id) |
| `item_id` | INTEGER | NOT NULL, FK | - | Reference to lore_item(id) |
| `sequence_number` | INTEGER | - | 0 | Display order in collection |
| `item_config` | TEXT | - | NULL | JSON configuration overrides |

**Constraints**:

- PRIMARY KEY(collection_id, item_id)
- Foreign key to `collection(id)` ON DELETE CASCADE
- Foreign key to `lore_item(id)` ON DELETE CASCADE

```sql
CREATE TABLE IF NOT EXISTS collection_item (
    collection_id INTEGER NOT NULL,
    item_id INTEGER NOT NULL,
    sequence_number INTEGER DEFAULT 0,
    item_config TEXT,
    PRIMARY KEY (collection_id, item_id),
    FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES lore_item(id) ON DELETE CASCADE
);
```

---

## 4. Legacy Tables (Deprecated)

### lore_entries

**DEPRECATED** - Retained for backward compatibility during migration.

| Column | Type | Constraints | Default | Description |
|--------|------|-------------|---------|-------------|
| `id` | VARCHAR(36) | PRIMARY KEY | - | UUID identifier |
| `type` | VARCHAR(20) | NOT NULL | - | Lore type |
| `name` | TEXT | NOT NULL | - | Display name |
| `description` | TEXT | - | NULL | Description text |
| `nbt_data` | TEXT | - | NULL | Serialized NBT |
| `world` | VARCHAR(64) | - | NULL | World name |
| `x`, `y`, `z` | DOUBLE | - | NULL | Location coordinates |
| `submitted_by` | VARCHAR(36) | NOT NULL | - | Submitter UUID |
| `approved` | BOOLEAN | - | 0 | Approval status |
| `created_at` | TIMESTAMP | - | CURRENT_TIMESTAMP | Creation time |

```sql
CREATE TABLE IF NOT EXISTS lore_entries (
    id VARCHAR(36) PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    nbt_data TEXT,
    world VARCHAR(64),
    x DOUBLE,
    y DOUBLE,
    z DOUBLE,
    submitted_by VARCHAR(36) NOT NULL,
    approved BOOLEAN DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

### lore_metadata

Key-value metadata for legacy lore entries.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `lore_id` | VARCHAR(36) | NOT NULL | Reference to lore_entries |
| `meta_key` | VARCHAR(64) | NOT NULL | Metadata key |
| `meta_value` | TEXT | - | Metadata value |

**Constraints**: PRIMARY KEY(lore_id, meta_key), FK to lore_entries(id) ON DELETE CASCADE

```sql
CREATE TABLE IF NOT EXISTS lore_metadata (
    lore_id VARCHAR(36) NOT NULL,
    meta_key VARCHAR(64) NOT NULL,
    meta_value TEXT,
    PRIMARY KEY (lore_id, meta_key),
    FOREIGN KEY (lore_id) REFERENCES lore_entries(id) ON DELETE CASCADE
);
```

---

## Entity Relationships

```
lore_entry (1) ─────< (N) lore_submission
     │
     └── (1) ─────< (1) lore_item ──────┐
                                        │
collection (1) ─────< (N) player_collection_progress
     │                                  │
     ├── (1) ─────< (N) collection_reward
     │                                  │
     └── (N) ─────< collection_item >── (N) lore_item
            (many-to-many via junction table)

[LEGACY]
lore_entries (1) ─────< (N) lore_metadata
```

---

## Data Types Reference

| Java Type | SQLite Type | Notes |
|-----------|-------------|-------|
| UUID | CHAR(36) / TEXT | Store as string |
| String | VARCHAR(n) / TEXT | Use TEXT for large strings |
| int | INTEGER | |
| boolean | BOOLEAN / INTEGER | 0=false, 1=true |
| Instant | TIMESTAMP / INTEGER | Unix timestamp or ISO8601 |
| Map/JSON | TEXT | JSON string |

---

## Migration Notes

1. **From lore_entries to lore_entry**: The new schema separates entry metadata from content (lore_submission)
2. **Content versioning**: lore_submission supports multiple versions via content_version + is_current_version
3. **Approval workflow**: Managed via approval_status, approved_by, approved_at fields
4. **JSON storage**: item_properties, drop_settings, content use JSON TEXT columns

---

## Related Documentation

- **Database Patterns**: [database-patterns.md](database-patterns.md)
- **RVNKCore Integration**: [rvnkcore-integration.md](rvnkcore-integration.md)
- **Database API Reference**: [rvnklore-database-api.md](rvnklore-database-api.md)
- **Architecture Refactor Plan**: [../architecture/rvnklore-database-refactor.md](../architecture/rvnklore-database-refactor.md)

---

**Document Version**: 1.0.0
**Maintainer**: Ravenkraft Development Team

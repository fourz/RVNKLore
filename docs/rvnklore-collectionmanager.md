# RVNKLore CollectionManager

The `CollectionManager` is responsible for managing item collections in the RVNKLore plugin. It organizes items into thematic groups, tracks player progress, and handles collection rewards.

## Recent Changes (v1.0.25)

- Fixed collection tables not being created on first startup due to silent `createTableSafely()` failure during `createTables()`.
- Added `ensureCollectionTables()` public method on `DatabaseConnection` — called from `loadCollectionsFromDatabase()` as an idempotent safety net.
- Extracted all collection DDL into `setupCollectionSchema(Statement)` — shared between `createTables()` and `ensureCollectionTables()`.
- Fixed `saveCollection()` missing `t()` table prefix wrapper (would silently write to unprefixed table when a prefix is configured).
- Fixed `createCollection()`, `getCollectionDetails()`, `updateCollection()` column name: `theme` → `theme_id`.
- Fixed `updateCollection()` referencing non-existent `updated_at` column.

## Responsibilities

- Define and manage item collections (`LoreCollection`)
- Track player progress and completion
- Handle collection themes, metadata, and rewards
- Integrate with the plugin's logging and item systems
- Support seasonal and event-based collections
- Validate and persist collections to the database
- Provide filtered and paginated access to collections for commands

## Key Methods

- `getCollection(String)`: Retrieve a collection by ID
- `createCollection(String, String, String)`: Register a new collection with validation
- `saveCollection(LoreCollection)`: Persist a collection to the database via `ItemRepository`
- `getPlayerProgress(UUID, String)`: Track player progress
- `grantCollectionReward(UUID, String)`: Grant rewards for completion
- `getAllCollections()`: Retrieve all collections
- `getCollectionsByTheme(String)`: Filter collections by theme
- `reloadCollectionsFromDatabase()` / `loadCollectionsFromDatabase()`: Refresh in-memory collections from storage
- `shutdown()`: Cleanup

## Startup Flow

```
CollectionManager.initialize()
  └─ loadCollectionsFromDatabase()
       ├─ Check DatabaseManager connected
       ├─ DatabaseConnection.ensureCollectionTables()   ← safety net
       │     └─ setupCollectionSchema(stmt)              ← CREATE TABLE IF NOT EXISTS x5
       └─ ItemRepository.loadAllCollections()
```

`ensureCollectionTables()` uses `CREATE TABLE IF NOT EXISTS` for all five collection tables and is safe to call multiple times. It does **not** run migrations — `runMigrations()` is only called from the normal `createTables()` path at connection initialization.

## Database Columns — `collection` Table

| Column | Type | Notes |
|--------|------|-------|
| `id` | INT AUTO_INCREMENT | Primary key |
| `collection_id` | VARCHAR(255) | Unique slug |
| `name` | VARCHAR(255) | Display name |
| `description` | TEXT | Nullable |
| `theme_id` | VARCHAR(100) | Theme association — **not** `theme` |
| `is_active` | BOOLEAN | Default 1 |
| `created_at` | INTEGER | Unix epoch ms — set by application, no DB default |

## Example Usage

```java
CollectionManager collectionManager = itemManager.getCollectionManager();
LoreCollection collection = collectionManager.getCollection("winter_wonders");
collectionManager.saveCollection(collection);
```

## Design Notes

- Collections are extensible and support metadata and themes
- Rewards are managed via `CollectionRewards`
- All actions are logged via `LogManager`
- Database integration supports persistence and reload
- Validation ensures unique, well-formed collection IDs
- `ItemRepository` is instantiated on-demand in `CollectionManager` — it is not pre-initialized in `DatabaseManager.initializeDatabase()`

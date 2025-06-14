# Plan: Implement `lore_submission` Table Integration in LoreManager

## Overview
This plan outlines how to integrate the `lore_submission` table into the `LoreManager` so that:
- Every new lore entry—regardless of type—gets a corresponding submission record for versioning.
- `ITEM`-type lore is linked via foreign keys to `lore_item`.
- The plugin’s ItemRepository is updated to register new items with the correct `lore_entry_id`.

**Note:** Data migration is not required for this implementation, as the database will be reset as part of this edit. All schema changes can assume a clean state.

---

## 1. Database Schema Preparation
1. Ensure the following tables exist:
   - `lore_entry` (base table)
   - `lore_submission` (holds versions; FK → `lore_entry.id`)
   - Specialized tables (e.g. `lore_item`) each with `lore_entry_id` FK → `lore_entry.id`
2. Verify foreign-key constraints:
   - `lore_submission.entry_id` → `lore_entry.id`
   - `lore_item.lore_entry_id` → `lore_entry.id`
3. Confirm indices on `entry_id` for fast lookups and joins.

---

## 2. Update LoreManager to Use `lore_submission`

### 2.1 Insert Base Entry
- In `LoreManager.addLoreEntry(LoreType type, String name, String description)`:
  1. Insert into `lore_entry`, retrieve generated `entryId`.

### 2.2 Insert Specialized Record
- If `type == ITEM`:
  1. Insert into `lore_item(lore_entry_id, item_properties…)` using `entryId` as FK.
- For other types, insert into their own table similarly.

### 2.3 Create Initial Submission Version
- Insert into `lore_submission(entry_id, version_number, content, author_id, is_current_version)`
  - Set `version_number = 1`
  - `is_current_version = TRUE`
  - Store `description`, `visibility`, etc.

### 2.4 Enforce Foreign-Key Relationships
- Use single transaction for:
  1. `lore_entry` → 2. `lore_item` (if ITEM) → 3. `lore_submission`
- Rollback on any failure to maintain referential integrity.

### 2.5 Register `ITEM` in ItemRepository
- After successful insert into `lore_item`:
  1. Call `itemManager.registerLoreItem(entryId, itemMeta…)`
  2. Pass `lore_entry_id` so the repository stores the link.
- Ensure `itemManager.getAllItemsWithProperties()` can return items keyed by their `lore_entry_id`.

---

## 3. Retrieval & Listing Changes
- Update `getLoreEntries…` and listing commands to:
  - Join `lore_entry` → `lore_submission` (filter `is_current_version = TRUE`)
  - For ITEM types, further join to `lore_item` for item-specific fields.
- Ensure admin commands can fetch unapproved submissions by joining `lore_submission.status`.

---

## 4. Approval & Versioning Workflow
- On approve:
  1. In `LoreManager.approveLoreEntry(UUID entryId)` update `lore_submission.is_approved` on current version.
  2. Set `is_current_version = FALSE` on old version and insert a new version if content changed.
- Maintain history in `lore_submission`.

---

## 5. Implementation Steps
1. **Schema Setup**  
   - Create/alter tables with the FK constraints above. (No migration needed; database will be reset.)
2. **Code Refactoring**  
   - Refactor `LoreManager.addLoreEntry()` per 2.1–2.5.
   - Wrap all inserts in a single transaction.
3. **ItemRepository Integration**  
   - Extend `ItemManager.registerLoreItem(UUID entryId, ItemProperties props)`.
4. **Listing & Retrieval**  
   - Update DAO methods to fetch current submissions and join item data.
5. **Testing**  
   - Write unit tests for:
     - New entry creation (all types).
     - Submission versioning.
     - ITEM lore registration and lookup.
     - Foreign key constraint enforcement.
6. **Documentation**  
   - Update `docs/schema/db-schema-data-relationships.md` with new relationship lines.
   - Document usage in `LoreManager` JavaDocs.

---

## 6. Developer Notes
- **Why submissions?** Enables rollback, audit, and multi-version support.
- **Transaction safety:** Critical for maintaining FK integrity.
- **Item registration:** Keeps in-game item repository in sync with lore database.

---
**Summary:**  
This plan ensures every lore entry is versioned in `lore_submission`, ITEM types are FK-linked to `lore_item`, and the ItemRepository is notified with the `lore_entry_id` for seamless integration.

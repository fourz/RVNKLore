# RVNKLore Developer Guide

Reference for plugin developers integrating with or extending RVNKLore.

---

## Item Identity — `rvnklore:lore_entry_id` PDC Key

### Overview

The `rvnklore:lore_entry_id` PersistentData key is the canonical contract for associating a Minecraft ItemStack with a lore entry. It is a **public, stable contract** — do not rename or remove it without a major version bump.

### Key Specification

| Field | Value |
|-------|-------|
| **Namespace** | `rvnklore` (plugin namespace) |
| **Key** | `lore_entry_id` |
| **Full key** | `rvnklore:lore_entry_id` |
| **Value type** | `PersistentDataType.STRING` |
| **Value format** | Full UUID string of the lore entry (e.g. `"75925e64-a37b-3184-91e6-dd47d1d4c282"`) |

### Writers (where the key is SET)

| Class | Command/Event | Notes |
|-------|--------------|-------|
| `LoreItemTagSubCommand` | `/lore item tag` | Tags held item with entry UUID |
| `LoreBookManager` | Book generation | Lore books written by the book system |
| `VotingPluginIntegration` | Vote reward items | Reward items stamped on vote trigger |

### Readers (where the key is CONSUMED)

| Class | Purpose |
|-------|---------|
| `DiscoveryListener` | Triggers lore discovery when a stamped item is held near a location |
| `CollectionManager` | PDC scan for `/lore collection claim` — matches items to collection entries |
| `LoreItemTagSubCommand` | Reads key to prevent double-tagging |

### Usage Example

```java
// Write
NamespacedKey key = new NamespacedKey(plugin, "lore_entry_id");
meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, entryUuid.toString());

// Read
NamespacedKey key = new NamespacedKey(plugin, "lore_entry_id");
PersistentDataContainer pdc = meta.getPersistentDataContainer();
if (pdc.has(key, PersistentDataType.STRING)) {
    String entryId = pdc.get(key, PersistentDataType.STRING);
    UUID entryUuid = UUID.fromString(entryId);
}
```

### Stability Contract

- The key name `lore_entry_id` is frozen. Changing it breaks all stamped items on existing servers.
- The value is always a full UUID string — never a short ID or name.
- Items can carry only one lore entry stamp (one key slot).
- Items with this key are recognized across all subsystems (discovery, collections, voting) without additional configuration.

---

## Approval State Model

### LoreEntry approval fields

`LoreEntry` carries two approval-related fields kept in sync:

| Field | Type | Source | Purpose |
|-------|------|--------|---------|
| `approvalStatus` | `String` | `lore_submission.approval_status` | Authoritative — PENDING / APPROVED / REJECTED |
| `approved` | `boolean` | Derived | Convenience — `true` only when APPROVED |

**Always write via `setApprovalStatus(String)`** — it updates both fields atomically. Direct `setApproved(boolean)` is only used during object construction.

### Approval flow

```
Submit → PENDING → approve → APPROVED
                → reject  → REJECTED (stores rejection_reason in lore_submission)
                → soft-delete → status=ARCHIVED, visibility=HIDDEN (does not change approval_status)
```

### Status vs ApprovalStatus

These are independent columns:

| Column | Values | Meaning |
|--------|--------|---------|
| `lore_submission.approval_status` | PENDING / APPROVED / REJECTED | Content review state |
| `lore_submission.status` | ACTIVE / ARCHIVED / DRAFT / PENDING_APPROVAL | Lifecycle state |
| `lore_submission.visibility` | PUBLIC / STAFF_ONLY / HIDDEN | Display visibility |

Soft-delete sets `status=ARCHIVED` and `visibility=HIDDEN` without touching `approval_status`.

---

## Command Permission Reference

| Permission | Default | Description |
|------------|---------|-------------|
| `rvnklore.use` | true | Baseline read access |
| `rvnklore.add` | true | Submit new entries |
| `rvnklore.get` | true | View entries |
| `rvnklore.list` | true | List entries |
| `rvnklore.search` | true | Search entries |
| `rvnklore.browse` | true | GUI browser |
| `rvnklore.discover` | true | Self-targeting discover |
| `rvnklore.share` | true | Share button in browse GUI |
| `rvnklore.admin` | op | All admin commands |
| `rvnklore.admin.delete` | op | Delete entries |
| `rvnklore.command.edit` | op | Edit entry fields |
| `rvnklore.command.reject` | op | Reject pending entries |
| `rvnklore.approve.own` | op | Auto-approve own submissions |
| `rvnklore.*` | op | All permissions |

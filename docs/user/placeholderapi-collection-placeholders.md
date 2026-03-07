---
title: PlaceholderAPI Collection Placeholders
description: Collection system integration with PlaceholderAPI for dynamic scoreboards and signs
author: RVNKLore
updated: 2026-02-14
---

# PlaceholderAPI Collection Placeholders

RVNKLore integrates with PlaceholderAPI to provide dynamic collection progress placeholders for use in scoreboards, signs, chat, and plugins.

## Requirements

- PlaceholderAPI plugin installed on server
- RVNKLore v1.0.0 or later
- Collection system initialized with `/lore collection list`

## Collection Placeholders

All collection placeholders use the format `%rvnklore_<placeholder>%` and are case-insensitive.

### Player Collection Progress

#### Overall Completion

**`%rvnklore_collection_completed_count%`**

Returns the total number of collections completed by a player.

```
Example output: 5
```

**Usage:**
- Scoreboard: `scoreboard players set <player> collections %rvnklore_collection_completed_count%`
- Chat: `/tellraw @s {"text":"Collections completed: %rvnklore_collection_completed_count%"}`
- Signs: Use PlaceholderAPI to auto-update sign lines

---

### Specific Collection Progress

Use collection names (lowercase, spaces as underscores) to query individual collections.

#### Progress Percentage

**`%rvnklore_collection_<name>_progress%`**

Returns the completion percentage for a specific collection.

**Parameters:**
- `<name>` - Collection ID or name (lowercase, replace spaces with underscores)

**Example output:** `50.0%`, `100.0%`, `0.0%`

**Usage:**

```yaml
# Dynamic scoreboard
/scoreboard objectives add cities dummy "Cities Collection"
/scoreboard players set <player> cities %rvnklore_collection_cities_progress%

# Chat message
/tellraw @s {"text":"Progress: ","extra":[{"text":"%rvnklore_collection_cities_progress%","color":"gold"}]}

# Sign update (PlaceholderAPI handles auto-updates every 60s)
# Line 1: Cities Collection
# Line 2: Progress:
# Line 3: %rvnklore_collection_cities_progress%
```

---

#### Items Collected Count

**`%rvnklore_collection_<name>_items%`**

Returns the number of items collected in a specific collection.

**Example output:** `5`, `0`, `8`

**Usage:**

```yaml
# Show items collected
/tellraw @s {"text":"Items collected: %rvnklore_collection_landmarks_items%/10"}

# Conditional display (requires plugin to parse placeholder)
# "Landmarks (5/10 items)"
```

---

#### Total Items in Collection

**`%rvnklore_collection_<name>_total%`**

Returns the total number of items in a specific collection (for all players).

**Example output:** `10`, `25`, `3`

**Usage:**

```yaml
# Display total in collection
/tellraw @s {"text":"Total items in collection: %rvnklore_collection_artifacts_total%"}

# Fraction format (requires joining with items count)
# "Artifacts: 3/25"
```

---

#### Missing Items Count

**`%rvnklore_collection_<name>_missing%`**

Returns the number of items not yet collected in a specific collection.

**Example output:** `5`, `0`, `12`

**Usage:**

```yaml
# Show items still needed
/tellraw @s {"text":"Still need to find: %rvnklore_collection_ancient_cities_missing%"}

# Display as fraction using both items and missing
# "Progress: 2 of 10 (still need 8 more)"
```

---

#### Completion Status (Boolean)

Use the progress placeholder with a plugin that can parse numeric comparisons:

```yaml
# Via plugin command (Lua, custom parser, etc.)
# If %rvnklore_collection_cities_progress% >= 100, show checkmark
# If %rvnklore_collection_cities_progress% < 100, show incomplete
```

## Configuration Examples

### Scoreboard Setup

```yaml
# Create scoreboards for each collection type

# Cities Collection Objective
/scoreboard objectives add cities_collected dummy "Cities"
/scoreboard players set @a cities_collected %rvnklore_collection_cities_items%
/scoreboard players set @a cities_total %rvnklore_collection_cities_total%

# Landmarks Collection Objective
/scoreboard objectives add landmarks_collected dummy "Landmarks"
/scoreboard players set @a landmarks_collected %rvnklore_collection_landmarks_items%

# Display on sidebar
/scoreboard objectives setdisplay sidebar cities_collected
```

### Chat Notification

Notify players of their collection progress:

```yaml
# Command block / repeating timer
/execute as @a run tellraw @s {"text":"Collection Progress: ","extra":[
  {"text":"Cities %rvnklore_collection_cities_progress%","color":"gold"},
  {"text":" • "},
  {"text":"Landmarks %rvnklore_collection_landmarks_progress%","color":"green"}
]}
```

### Sign Display (Auto-Updating)

PlaceholderAPI automatically updates signs every 60 seconds if registered:

```
[PlaceholderAPI]
Collection Progress
Cities: %rvnklore_collection_cities_progress%
Status: %rvnklore_collection_completed_count%/5
```

### Tab List Header/Footer

Use with TAB plugin (requires TAB PlaceholderAPI support):

```yaml
Header: "RVNKLore Collection Tracker"
Footer: "Collections: %rvnklore_collection_completed_count% | Cities: %rvnklore_collection_cities_items%/%rvnklore_collection_cities_total%"
```

---

## Troubleshooting

### Placeholder Not Updating

**Problem:** Placeholder shows `%rvnklore_collection_cities_progress%` instead of a value

**Solution:**
1. Verify PlaceholderAPI is installed: `/papi version`
2. Reload expansion: `/papi ecloud download RVNKLore && /papi reload`
3. Verify RVNKLore is loaded: `/lore debug`
4. Check collection name matches exactly (case-insensitive but underscores for spaces)

### Collection Name Mismatch

**Problem:** Placeholder returns `0%` or `0` but collection exists

**Solution:**
1. List all collections: `/lore collection list`
2. Use exact collection ID/name in placeholder
3. Replace spaces with underscores: `Ancient Cities` → `ancient_cities`
4. Example: `%rvnklore_collection_ancient_cities_progress%`

### Performance Issues

**Problem:** Server lag when updating many placeholders

**Solution:**
1. PlaceholderAPI caches values for 5 seconds per placeholder
2. Limit number of scoreboards/signs using collection placeholders
3. Use completed_count placeholder sparingly (iterates all collections)
4. Check PlaceholderAPI performance: `/papi debug`

---

## Advanced Usage

### Conditional Displays with Lua Scripts

If using a plugin that supports Lua scripting (e.g., Skript with PlaceholderAPI addon):

```lua
-- Show collection tier based on progress
local progress = getPlaceholder("rvnklore_collection_cities_progress")
if progress >= 100 then
    return "✓ Collections Mastered"
elseif progress >= 75 then
    return "◐ Almost Complete"
elseif progress >= 50 then
    return "◑ Half Complete"
elseif progress >= 25 then
    return "◒ Started"
else
    return "○ Not Started"
end
```

### Custom Collections Plugin Integration

If a plugin needs collection data programmatically:

```java
// Via PlaceholderAPI
String progress = papiSetPlaceholders(player, "%rvnklore_collection_cities_progress%");
int collected = Integer.parseInt(papiSetPlaceholders(player, "%rvnklore_collection_cities_items%"));
```

---

## Collection Placeholder Reference Table

| Placeholder | Returns | Example |
|-------------|---------|---------|
| `%rvnklore_collection_completed_count%` | Total completed collections | `3` |
| `%rvnklore_collection_<name>_progress%` | Completion percentage | `75.0%` |
| `%rvnklore_collection_<name>_items%` | Items collected | `15` |
| `%rvnklore_collection_<name>_total%` | Total items in collection | `20` |
| `%rvnklore_collection_<name>_missing%` | Items not yet collected | `5` |

---

## See Also

- [Collection System Guide](../collections.md)
- [PlaceholderAPI Documentation](https://github.com/PlaceholderAPI/PlaceholderAPI)
- [RVNKLore Commands](../commands/collection.md)


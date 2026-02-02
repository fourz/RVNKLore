# RVNKLore Import/Export System

## Overview

The Import/Export System provides bulk data management capabilities for RVNKLore entries, enabling content creation, backup, and migration workflows.

## Features

### Export Capabilities
- **Multiple Formats**: JSON (primary) and YAML (secondary)
- **Filtering Options**: Export all entries, by type, or single entry by ID
- **Metadata Inclusion**: Author, timestamps, and custom metadata preserved
- **Automatic Timestamping**: Export files include creation timestamp
- **Location**: `plugins/RVNKLore/exports/`

### Import Capabilities
- **Format Support**: JSON and YAML files
- **Validation**: Entry validation during import
- **Duplicate Detection**: Automatically skips entries with existing IDs
- **Preview Mode**: Dry-run validation without database changes
- **Error Reporting**: Detailed success/failure/warning summaries
- **Location**: `plugins/RVNKLore/imports/`

## Commands

### Export Command
```
/lore export [format] [type]
```

**Examples:**
```bash
# Export all entries to JSON (default)
/lore export

# Export all entries to YAML
/lore export yaml

# Export only landmarks to JSON
/lore export json landmark

# Export only cities to YAML
/lore export yaml city
```

**Available Types:**
- `landmark` - Notable locations
- `city` - Settlements and cities
- `player` - Player character lore
- `faction` - Groups and organizations
- `item` - Special items
- `head` - Decorative heads/hats
- `event` - Historical events
- `path` - Roads and pathways
- `quest` - Adventures and quests
- `enchantment` - Special enchantments
- `generic` - Unspecified lore

**Permissions:**
- `rvnklore.admin` or OP status required

**Output:**
- Files saved to `plugins/RVNKLore/exports/`
- Filename format: `lore_export_{type}_{timestamp}.{format}`
- Example: `lore_export_all_2026-02-01_14-30-45.json`

### Import Command
```
/lore import <filename> [--preview]
```

**Examples:**
```bash
# Import entries from JSON file
/lore import lore_export_all_2026-02-01.json

# Preview import without making changes
/lore import backup.yaml --preview

# Import from YAML file
/lore import city_entries.yaml
```

**Permissions:**
- `rvnklore.admin` or OP status required

**Process:**
1. Place import file in `plugins/RVNKLore/imports/`
2. Run import command
3. Review results summary
4. Check console logs for detailed error information

**Import Result Summary:**
```
Total: X | Success: Y | Skipped: Z | Failed: W
```

- **Success**: Entries successfully imported
- **Skipped**: Duplicate IDs (already exist in database)
- **Failed**: Validation errors or import failures

## File Format Specifications

### JSON Format
```json
{
  "exported_at": "2026-02-01T14:30:45",
  "plugin_version": "1.0-SNAPSHOT",
  "entry_count": 3,
  "entries": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Ancient Tower",
      "description": "A mysterious tower from ages past",
      "type": "LANDMARK",
      "nbt_data": "",
      "location": {
        "world": "world",
        "x": 100.5,
        "y": 64.0,
        "z": -200.5
      },
      "submitted_by": "PlayerName",
      "approved": true,
      "created_at": "2026-01-15 10:30:00.0",
      "metadata": {
        "region": "northern_wastes",
        "discovery_date": "2025-12-01"
      }
    }
  ]
}
```

### YAML Format
```yaml
exported_at: '2026-02-01T14:30:45'
plugin_version: 1.0-SNAPSHOT
entry_count: 3
entries:
- id: 550e8400-e29b-41d4-a716-446655440000
  name: Ancient Tower
  description: A mysterious tower from ages past
  type: LANDMARK
  nbt_data: ''
  location:
    world: world
    x: 100.5
    y: 64.0
    z: -200.5
  submitted_by: PlayerName
  approved: true
  created_at: '2026-01-15 10:30:00.0'
  metadata:
    region: northern_wastes
    discovery_date: '2025-12-01'
```

## Required Fields

All lore entries must include:
- `id` - Unique identifier (UUID format)
- `name` - Entry name
- `description` - Entry description
- `type` - Lore type (enum value)

## Optional Fields

- `nbt_data` - NBT data for items/heads
- `location` - World coordinates (required for landmarks, cities, paths)
- `submitted_by` - Author username
- `approved` - Approval status (boolean)
- `created_at` - Creation timestamp
- `metadata` - Custom key-value metadata

## Validation Rules

### Entry Validation
1. Required fields must be present and non-empty
2. Type must be valid LoreType enum value
3. Type-specific validation:
   - **Landmarks/Cities/Paths**: Must have valid location
   - **Heads**: Must have NBT data

### Import Validation
1. File must exist in imports directory
2. File format must be .json, .yaml, or .yml
3. File structure must match expected format
4. All entries undergo validation before import
5. Duplicate IDs are automatically skipped

## Workflow Examples

### Backup Workflow
```bash
# Export all entries to JSON
/lore export json

# Copy export file to safe location
# File: plugins/RVNKLore/exports/lore_export_all_2026-02-01_14-30-45.json
```

### Migration Workflow
```bash
# Export from source server
/lore export json

# Copy export file to target server
# Place in plugins/RVNKLore/imports/

# Preview import on target server
/lore import lore_export_all_2026-02-01.json --preview

# Perform actual import
/lore import lore_export_all_2026-02-01.json
```

### Content Creation Workflow
```bash
# Create YAML file with new entries
# Place in plugins/RVNKLore/imports/new_content.yaml

# Preview to validate
/lore import new_content.yaml --preview

# Import after validation
/lore import new_content.yaml
```

### Type-Specific Export
```bash
# Export only cities for wiki documentation
/lore export yaml city

# Export landmarks for map integration
/lore export json landmark
```

## Best Practices

1. **Always use preview mode** for new import files
2. **Backup before bulk imports** using export command
3. **Use JSON for automated processes** (better machine readability)
4. **Use YAML for manual editing** (better human readability)
5. **Include metadata** for tracking and documentation
6. **Validate world names** before importing location-based lore
7. **Check console logs** for detailed error information

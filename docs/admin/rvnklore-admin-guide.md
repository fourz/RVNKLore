# RVNKLore Admin Guide

**Audience**: Server Administrators
**Version**: 1.0.0
**Last Updated**: February 1, 2026

## Overview

This guide covers server administration, configuration, permissions, and management of the RVNKLore plugin.

## Installation

1. Download RVNKLore.jar and place in `plugins/` folder
2. Ensure RVNKCore.jar is present in `plugins/` folder (required dependency)
3. Restart server
4. Configure `plugins/RVNKLore/config.yml`
5. Reload plugin: `/lore reload` or restart server

## Configuration

### Main Configuration (config.yml)

```yaml
general:
  logLevel: INFO  # OFF, SEVERE, WARNING, INFO, DEBUG, FINE

storage:
  type: sqlite    # sqlite or mysql
  mysql:
    host: localhost
    port: 3306
    username: root
    password: ''
    useSSL: false
    database: rvnklore
  sqlite:
    database: data.db
```

**Log Levels:**
- `OFF` - No logging (not recommended)
- `SEVERE` - Critical errors only
- `WARNING` - Errors and warnings
- `INFO` - General information (recommended for production)
- `DEBUG` - Detailed debugging information
- `FINE` - Very verbose debugging

### Database Configuration

**SQLite (Default):**
- File-based database stored in `plugins/RVNKLore/data.db`
- No additional setup required
- Recommended for single servers

**MySQL:**
- Recommended for multi-server networks or high traffic
- Create database before enabling: `CREATE DATABASE rvnklore;`
- Configure connection settings in config.yml
- Tables are created automatically on first startup

**Switching Databases:**
1. Export existing data: `/lore export`
2. Change `storage.type` in config.yml
3. Reload plugin: `/lore reload`
4. Import data if needed

## Permissions

### Admin Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `rvnklore.*` | Full access to all commands | op |
| `rvnklore.admin` | Access to administrative commands | op |
| `rvnklore.admin.item.give` | Give any lore item via `/lore item give` | op |

### Player Commands

| Permission | Description | Default |
|------------|-------------|---------|
| `rvnklore.use` | Basic lore viewing commands | true |
| `rvnklore.browse` | Access to `/lore browse` GUI | true |
| `rvnklore.command.add` | Add new lore entries | false |
| `rvnklore.command.get` | View lore entries | true |
| `rvnklore.command.list` | List lore entries | true |
| `rvnklore.command.getitem` | Get lore items | op |
| `rvnklore.command.collection` | View and claim collection progress | true |

### Approval System

| Permission | Description | Default |
|------------|-------------|---------|
| `rvnklore.command.approve` | Approve pending lore entries | op |
| `rvnklore.approve.own` | Auto-approve own submissions | op |

### Advanced Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `rvnklore.register.city` | Register new cities | op |
| `rvnklore.sign.landmark` | Create landmark signs | op |
| `rvnklore.notable` | Mark player for automatic lore generation | op |
| `rvnklore.achievement` | View achievements | true |
| `rvnklore.achievement.grant` | Grant achievements to players | op |
| `rvnklore.achievement.revoke` | Revoke achievements from players | op |
| `rvnklore.achievement.seehidden` | View hidden achievements | op |

## Admin Commands

### Lore Management

**Approve pending entries:**
```
/lore approve <id>
```
Review and approve player-submitted lore entries.

**Export lore data:**
```
/lore export [type]
```
Export lore entries to JSON files in `plugins/RVNKLore/exports/`.
- Without type: exports all entries
- With type: exports specific category (e.g., `/lore export LANDMARK`)

**Reload configuration:**
```
/lore reload
```
Reloads config.yml without restarting the server. Note: database connection changes require full restart.

### Item Management

**Give item to player:**
```
/lore item give <player> <item_name>
```
Give any lore item to a player. The item must exist in the lore database.

**View item information:**
```
/lore item info <uuid>
```
View detailed information about a lore item by its UUID (full or 8-character short ID).

**List all items:**
```
/lore item list [page]
```
List all lore items in the database with pagination.

### Book Management

**Give book to player:**
```
/lore book give <player> <entry_id> [rarity]
```
Create a lore book from an entry and give it to a player.
- `entry_id` - UUID of lore entry
- `rarity` - Optional: common, uncommon, rare, epic, legendary

**List available book entries:**
```
/lore book list [page] [type]
```
List all lore entries that can be converted to books.

### Collection Management

**Add collection:**
```
/lore collection add <collection_id> <name> <description>
```
Create a new collection.

**List collections:**
```
/lore collection list
```
View all defined collections.

### Achievement Management

**Grant achievement to player:**
```
/lore achievement grant <player> <achievement_id>
```
Manually award an achievement to a player.

**Revoke achievement from player:**
```
/lore achievement revoke <player> <achievement_id>
```
Remove an achievement from a player.

**View player progress:**
```
/lore achievement progress [player]
```
View detailed achievement progress for a specific player.

### Debug Commands

**Enable debug logging:**
```
/lore debug
```
Access diagnostic and debugging tools (permission: `rvnklore.admin`).

## Staff Workflow: Approving Lore

1. Player submits lore: `/lore add LANDMARK "Old Tower" A crumbling stone tower...`
2. Entry is created with `approved: false` status
3. Admin reviews pending entries (currently manual database check)
4. Admin approves: `/lore approve <entry_id>`
5. Entry becomes visible to all players

**Auto-Approval:**
- Grant `rvnklore.approve.own` to trusted players
- Their submissions bypass approval workflow

## Managing Collections

Collections organize themed sets of items for players to collect.

**Creating a Collection:**
1. Define collection: `/lore collection add winter_weapons "Winter Arsenal" "Legendary ice weapons"`
2. Add items to collection (currently via database or API)
3. Players can view and track progress: `/lore collection view winter_weapons`

**Collection Rewards:**
- Configured in database or via API
- Players claim rewards at 100% completion: `/lore collection claim <id>`

## Achievement System

Achievements track player engagement with lore content.

**Achievement Types:**
- Lore discovery (e.g., "Discover 10 landmarks")
- Collection completion (e.g., "Complete Winter Arsenal collection")
- Quest completion
- Exploration milestones

**Hidden Achievements:**
- Set `hidden: true` in database
- Not visible until player earns them
- Requires `rvnklore.achievement.seehidden` to view

## Database Schema

RVNKLore uses a comprehensive relational database with the following core tables:

**Core Entities:**
- `lore_entries` - Main lore entry storage
- `lore_items` - Custom items with properties and enchantments
- `lore_locations` - Significant places with coordinates
- `lore_characters` - NPCs, historical figures, player characters
- `lore_quests` - Quest definitions and progression
- `lore_events` - Historical events
- `special_entities` - Named mobs and creatures
- `server_happenings` - Server events and roleplay activities

**Collection System:**
- `collections` - Collection definitions
- `seasonal_items` - Time-limited item availability
- `item_values` - Economic tracking

**Engagement:**
- `player_achievements` - Player achievement records
- `community_voting` - Player feedback on lore
- `trading_records` - Item trading history

## Troubleshooting

### Plugin Won't Load

**Symptoms:** Plugin shows as red in `/plugins` or doesn't load
**Solutions:**
1. Check RVNKCore is installed and loaded first
2. Verify Java version (requires Java 21+)
3. Check server logs for specific error messages
4. Ensure plugin file is not corrupted (re-download if needed)

### Database Connection Errors

**MySQL Connection Failed:**
1. Verify MySQL service is running
2. Check connection credentials in config.yml
3. Ensure database exists: `CREATE DATABASE rvnklore;`
4. Verify user has permissions: `GRANT ALL ON rvnklore.* TO 'user'@'localhost';`
5. Test connection with external tool (e.g., MySQL Workbench)

**SQLite Errors:**
1. Check file permissions on `plugins/RVNKLore/`
2. Ensure disk space is available
3. Verify database file is not locked by another process

### Commands Not Working

**Permission Issues:**
1. Check permission nodes in your permissions plugin
2. Verify `rvnklore.use` for basic commands
3. Grant `rvnklore.admin` for admin commands

**Console Shows Errors:**
1. Enable debug logging: set `logLevel: DEBUG` in config.yml
2. Run `/lore debug` for diagnostic information
3. Check for plugin conflicts
4. Verify RVNKCore compatibility

### Performance Issues

**Slow Database Queries:**
1. Switch to MySQL for better performance on large datasets
2. Ensure database server has adequate resources
3. Check for database table fragmentation
4. Review slow query logs

**High Memory Usage:**
1. Limit collection sizes
2. Reduce debug logging level
3. Optimize database queries
4. Consider pagination for large result sets

## Data Backup

**Export Lore Data:**
```
/lore export
```
Creates JSON backup in `plugins/RVNKLore/exports/`

**Database Backup:**
- SQLite: Copy `plugins/RVNKLore/data.db`
- MySQL: Use `mysqldump` for database backup

**Recommended Backup Schedule:**
- Daily automated database backups
- Weekly exports via `/lore export`
- Before major updates or migrations

## API Integration

RVNKLore provides API access for other plugins:

```java
// Get RVNKLore instance
RVNKLore rvnkLore = (RVNKLore) Bukkit.getPluginManager().getPlugin("RVNKLore");

// Access services via RVNKCore ServiceRegistry
ServiceRegistry registry = RVNKCore.getServiceRegistry();
ILoreService loreService = registry.getService(ILoreService.class);
IItemService itemService = registry.getService(IItemService.class);
ICollectionService collectionService = registry.getService(ICollectionService.class);

// Create lore entry programmatically
LoreEntry entry = new LoreEntry();
entry.setType(LoreType.EVENT);
entry.setName("Battle of the North");
entry.setDescription("A legendary battle...");
entry.setApproved(true);
loreService.addLoreEntry(entry);
```

See [README.md](../../README.md) for complete API examples.

## Command Reference (Complete)

| Command | Permission | Description |
|---------|------------|-------------|
| `/lore` | `rvnklore.use` | Show available commands |
| `/lore list [type]` | `rvnklore.command.list` | List lore entries |
| `/lore get <name>` | `rvnklore.command.get` | View specific entry |
| `/lore add <type> <name> <desc>` | `rvnklore.command.add` | Add new lore entry |
| `/lore search <keyword>` | `rvnklore.command.list` | Search for lore |
| `/lore approve <id>` | `rvnklore.command.approve` | Approve pending entry |
| `/lore export [type]` | `rvnklore.admin` | Export lore to JSON |
| `/lore reload` | `rvnklore.admin` | Reload configuration |
| `/lore debug` | `rvnklore.admin` | Access debug tools |
| `/lore browse [type]` | `rvnklore.browse` | Open GUI browser |
| `/lore collection list` | `rvnklore.command.collection` | List collections |
| `/lore collection view <id>` | `rvnklore.command.collection` | View collection details |
| `/lore collection claim <id>` | `rvnklore.command.collection` | Claim rewards |
| `/lore collection add <id> <name> <desc>` | `rvnklore.admin` | Create collection |
| `/lore item give <player> <item>` | `rvnklore.admin.item.give` | Give item to player |
| `/lore item info <uuid>` | `rvnklore.admin` | View item details |
| `/lore item list [page]` | `rvnklore.admin` | List all items |
| `/lore book give <player> <entry_id> [rarity]` | `rvnklore.admin` | Give lore book |
| `/lore book list [page] [type]` | `rvnklore.use` | List available books |
| `/lore achievement list [page]` | `rvnklore.achievement` | List achievements |
| `/lore achievement progress [player]` | `rvnklore.achievement` | View progress |
| `/lore achievement grant <player> <id>` | `rvnklore.achievement.grant` | Grant achievement |
| `/lore achievement revoke <player> <id>` | `rvnklore.achievement.revoke` | Revoke achievement |

## Best Practices

**Security:**
- Never grant `rvnklore.admin` to untrusted players
- Regularly review and approve player submissions
- Back up database before major changes
- Use MySQL with SSL for production environments

**Performance:**
- Use MySQL for servers with 50+ players
- Enable query caching in MySQL
- Monitor database size and optimize tables
- Set appropriate log levels (INFO for production)

**User Experience:**
- Create collections for seasonal events
- Grant achievements for player milestones
- Approve lore submissions within 24-48 hours
- Use lore books as quest rewards

**Content Management:**
- Maintain consistent naming conventions
- Document custom lore types and categories
- Create style guidelines for submissions
- Regularly export data for backup

## Support

**Bug Reports:**
- Check server logs for error messages
- Enable debug logging for detailed information
- Document reproduction steps
- Report to plugin developers

**Feature Requests:**
- Submit via GitHub or server forum
- Provide use case and examples
- Check roadmap for planned features

## Related Documentation

- [Player Guide](../user/rvnklore-player-guide.md) - For players
- [README.md](../../README.md) - Full feature documentation
- [Coding Standards](../../../../docs/standard/coding-standards.md) - For developers
- [RVNKCore Integration](../../../../docs/standard/rvnkcore-integration.md) - Service integration

---

**Version**: RVNKLore 1.0.0
**Dependencies**: RVNKCore 1.3.0-alpha, Java 21+
**Compatibility**: Minecraft 1.17+, Paper/Spigot

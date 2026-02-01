# RVNKLore Player Guide

**Audience**: Players
**Version**: 1.0.0
**Last Updated**: February 1, 2026

## Overview

RVNKLore is a comprehensive lore and history system that brings the stories, landmarks, characters, and events of your server to life. Discover hidden histories, collect unique items, and track your achievements as you explore the world.

## Getting Started

All lore commands begin with `/lore`. Type `/lore` to see available commands based on your permissions.

## Core Features

### 1. Discovering Lore Entries

Lore entries document the history and significance of places, people, items, and events in the world.

**View available lore:**
```
/lore list [type]
```

**Search for specific lore:**
```
/lore search <keyword>
```

**View a specific entry:**
```
/lore get <name>
```

**Lore Types:**
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

### 2. Browse GUI

The interactive GUI browser makes it easy to explore lore by category.

**Open the main browser:**
```
/lore browse
```

**Open a specific category:**
```
/lore browse <type>
```
Example: `/lore browse landmark` - View all landmarks

**Browse all entries:**
```
/lore browse all
```

**Features:**
- Click entries to view detailed information
- Paginated browsing for large collections
- Color-coded by lore type
- Filter by category

### 3. Collection System

Collections are thematic groups of items you can collect. Complete collections to earn rewards!

**View all collections:**
```
/lore collection list
```

**View collection details:**
```
/lore collection view <collection_id>
```
Shows:
- Collection name and description
- Your completion progress (percentage)
- All items in the collection
- Which items you own (✓) and which you need (✖)
- Rarity levels for each item
- Collection rewards

**Check your progress:**
```
/lore collection progress
```
Shows completion percentage for all collections.

**Claim collection rewards:**
```
/lore collection claim <collection_id>
```
You must complete 100% of a collection to claim rewards.

**Collection Themes:**
- Seasonal collections (limited-time availability)
- Legendary items
- Quest rewards
- Themed sets (e.g., MickeyHats)

### 4. Achievement System

Track your lore-related accomplishments and earn points.

**List all achievements:**
```
/lore achievement list [page]
```

**Check your progress:**
```
/lore achievement progress
```
Shows:
- Total achievement points earned
- Completed achievements
- In-progress achievements with completion percentages

**Achievement Types:**
- Lore discovery achievements
- Collection completion achievements
- Quest completion achievements
- Exploration achievements

### 5. Lore Books

Physical in-game books containing lore entries that you can read and share.

**List available books:**
```
/lore book list [page]
```

Books can be:
- Given to you by staff
- Found in the world
- Earned as quest rewards
- Claimed from collections

**Book Rarities:**
- Common
- Uncommon
- Rare
- Epic
- Legendary

## Adding Your Own Lore

You can contribute to the server's history by submitting lore entries (if permitted by server staff).

**Add a lore entry:**
```
/lore add <type> <name> <description>
```

Examples:
```
/lore add LANDMARK "Ancient Oak" This massive oak tree has stood since the founding of the server.
/lore add PLAYER "Sir Gallant" A brave knight who defended the realm from the darkness.
/lore add ITEM "Frost Edge" An ancient blade radiating cold energy.
```

**Note:** Depending on server settings, your submission may require staff approval before appearing in the lore database.

## Tips and Tricks

**Quick Discovery:**
- Use `/lore browse` for the fastest way to explore lore
- Press Tab while typing commands for auto-completion
- Use `/lore search` if you know part of a name

**Collection Hunting:**
- Check `/lore collection progress` regularly
- Look for seasonal collections during special events
- Some rare items require special permissions or event participation

**Achievement Progress:**
- Hidden achievements won't show until you unlock them
- Achievement points contribute to server rankings (if enabled)
- Some achievements have progress tracking (e.g., "Discover 10 landmarks")

**Lore Books:**
- Books can be placed in item frames for decoration
- Share interesting books with other players
- Some books contain hints for quests or hidden locations

## Permissions

Most players have access to these basic commands:
- `/lore list` - View available lore entries
- `/lore get` - View specific entries
- `/lore search` - Search for lore
- `/lore browse` - Open GUI browser
- `/lore collection` - View collections
- `/lore achievement` - View achievements

Additional permissions may be granted for:
- Adding new lore entries
- Auto-approving your own submissions
- Creating landmark signs

## Common Questions

**Q: Why can't I see some lore entries?**
A: Some entries may be hidden until discovered, require special permissions, or be pending approval.

**Q: How do I get items for collections?**
A: Collection items come from gameplay, events, quests, voting rewards, and sometimes staff gifts.

**Q: Can I edit my lore submission?**
A: Contact server staff to modify existing lore entries. Editing directly is not supported.

**Q: What do achievement points do?**
A: Achievement points track your lore engagement. Server staff may use them for rankings or rewards.

**Q: Are seasonal collections available year-round?**
A: No, seasonal collections have limited availability windows (e.g., Winter collection during December-January).

## Getting Help

- Use `/lore` to see all available commands
- Contact server staff for lore-related questions
- Check the wiki or server website for additional guides
- Report bugs or issues to administrators

## Quick Reference

| Command | Description |
|---------|-------------|
| `/lore` | Show all available commands |
| `/lore list [type]` | List lore entries |
| `/lore get <name>` | View specific entry |
| `/lore search <keyword>` | Search for lore |
| `/lore browse [type]` | Open GUI browser |
| `/lore collection list` | View all collections |
| `/lore collection view <id>` | View collection details |
| `/lore collection progress` | Check your progress |
| `/lore collection claim <id>` | Claim collection rewards |
| `/lore achievement list` | List achievements |
| `/lore achievement progress` | Check your achievements |
| `/lore book list` | List available books |
| `/lore add <type> <name> <desc>` | Submit new lore entry |

---

**Related Documentation:**
- [Admin Guide](../admin/rvnklore-admin-guide.md) - For server administrators
- [README.md](../../README.md) - Full feature documentation

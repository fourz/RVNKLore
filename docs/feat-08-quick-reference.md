# feat-08 Quick Reference Guide

## Search Command Syntax

```bash
/lore search <query> [--type TYPE[,TYPE2...]] [--discovered|--undiscovered] [--limit N] [--page N]
```

## Examples

### Basic Search
```bash
/lore search dragon
# Finds all entries with "dragon" in name or description
```

### Type Filtering
```bash
# Single type
/lore search sword --type ITEM

# Multiple types (comma-separated)
/lore search tower --type LANDMARK,CITY

# All entries of a type (no query)
/lore search --type QUEST
```

### Discovery Status
```bash
# Show only approved entries
/lore search castle --discovered

# Show only pending entries
/lore search --undiscovered
```

### Pagination
```bash
# Show 20 results per page
/lore search event --limit 20

# Go to page 2
/lore search event --page 2

# Combine limit and page
/lore search quest --limit 15 --page 3
```

### Combined Filters
```bash
/lore search ancient --type ITEM,LANDMARK --discovered --limit 10
# Finds approved ITEM or LANDMARK entries containing "ancient", max 10 results
```

## Available Lore Types

- `GENERIC` - Generic/unspecified lore
- `PLAYER` - Player character lore
- `CITY` - City/settlement lore
- `LANDMARK` - Notable landmark
- `FACTION` - Faction or group
- `PATH` - Path or road
- `ITEM` - Crafted or special item
- `EVENT` - Historical event
- `QUEST` - Quest or mission
- `ENCHANTMENT` - Enchantment on an item
- `HEAD` - Decorative head or hat item

## Search Result Indicators

### Relevance Stars
- `***` - Exact match (score 100+)
- `**` - High relevance (score 75-99)
- `*` - Moderate relevance (score 50-74)
- (none) - Lower relevance (score 25-49)

### Status Badges
- `[TYPE]` - Lore entry type (ITEM, LANDMARK, etc.)
- `[PENDING]` - Entry not yet approved
- No badge = Approved/discovered entry

## Permissions

- `rvnklore.search` - Use search command
- `rvnklore.user` - General user permissions (includes search)

## Tab Completion

The search command supports tab completion for:
- `--type` - Suggests flag name
- `--limit` - Suggests flag name
- `--page` - Suggests flag name
- `--discovered` - Suggests flag name
- `--undiscovered` - Suggests flag name
- Lore type names after `--type`
- Common limit values (10, 20, 50)
- Page numbers (1-5)
- Lore entry names based on partial input

## Scoring Algorithm

Search results are scored for relevance:

| Match Type | Score | Example |
|------------|-------|---------|
| Exact name match | 100 | Query "Dragon Blade", Entry name "Dragon Blade" |
| Exact ID match | 100 | Query "a1b2c3d4", Entry ID "a1b2c3d4" |
| Name starts with | 75 | Query "drag", Entry name "Dragon Blade" |
| Description starts with | 60 | Query "ancient", Description "Ancient sword..." |
| Name contains | 50 | Query "sword", Entry name "Legendary Sword" |
| Description contains | 25 | Query "magic", Description "...with magic powers" |

Results are sorted by:
1. Score (highest first)
2. Name (alphabetically)

## API Usage (For Developers)

### LoreSearchService

```java
// Get service instance
LoreSearchService searchService = new LoreSearchService(plugin);

// Build search criteria
SearchCriteria criteria = new SearchCriteria.Builder()
    .query("dragon")
    .addTypeFilter(LoreType.ITEM)
    .discovered(true)
    .page(1, 10)
    .build();

// Execute search
List<SearchResult> results = searchService.search(criteria);

// Get total count (without pagination)
int totalMatches = searchService.countMatches(criteria);

// Quick name search (for autocomplete)
List<String> names = searchService.searchNames("drag", 5);

// Search by type only
List<LoreEntry> items = searchService.searchByType(LoreType.ITEM);
```

### SearchCriteria Builder

```java
SearchCriteria criteria = new SearchCriteria.Builder()
    .query("sword")                    // Search query
    .addTypeFilter(LoreType.ITEM)      // Single type filter
    .typeFilters(Set.of(               // Multiple type filters
        LoreType.ITEM,
        LoreType.QUEST
    ))
    .discovered(true)                  // Filter by approval status
    .limit(20)                         // Results per page
    .offset(40)                        // Result offset
    .page(3, 20)                       // Page 3 with 20 per page
    .sortByRelevance(true)             // Sort by score (default)
    .build();
```

### SearchResult

```java
for (SearchResult result : results) {
    LoreEntry entry = result.getEntry();
    double score = result.getScore();
    SearchResult.MatchType matchType = result.getMatchType();
    String indicator = result.getScoreIndicator(); // "***", "**", "*", or ""

    System.out.println(indicator + " " + entry.getName() + " (" + score + ")");
}
```

## Console Usage

The search command is console-compatible:

```bash
# From server console (no color codes)
lore search dragon --type ITEM

# Output format:
[RVNKLore] === Search Results for "dragon" (type: ITEM) ===
[RVNKLore] Found 3 result(s) - Page 1/1
[RVNKLore]
[RVNKLore] 1. *** [ITEM] 1a2b3c4d Dragon Blade by PlayerName
[RVNKLore] 2. ** [ITEM] 5e6f7g8h Dragonscale Armor by AdminName
[RVNKLore] 3. [ITEM] 9i0j1k2l Dragon Egg
```

## Performance Notes

### Current Implementation
- **Method**: In-memory filtering and scoring
- **Time Complexity**: O(n) where n = total lore entries
- **Best For**: Databases with < 10,000 entries
- **Typical Response**: < 50ms for 1,000 entries

### Future Optimization (Phase 2+)
When database grows beyond 10,000 entries:
- Add database indexes on name/description columns
- Implement full-text search (MySQL FULLTEXT or PostgreSQL)
- Move filtering to SQL WHERE clauses
- Use database LIMIT/OFFSET for pagination
- Expected response: < 100ms for 100,000+ entries

## Troubleshooting

### "No results found"
- Check spelling of query
- Try removing type filter: `/lore search query` (without --type)
- Try broader search: `/lore search part` instead of `/lore search particle`

### "Invalid type"
- Type names are case-sensitive: use `ITEM` not `item`
- Multiple types must be comma-separated: `ITEM,LANDMARK` not `ITEM LANDMARK`
- See list of valid types above

### "Invalid page number"
- Page numbers start at 1, not 0
- Cannot specify negative pages
- Page beyond total pages shows last page

### "Invalid limit number"
- Limit must be between 1 and 50
- Values outside range are clamped automatically

## Tips

1. **Empty query with filters** lists all entries matching filters:
   ```bash
   /lore search --type ITEM --discovered
   # Shows all approved items
   ```

2. **Use quotes for multi-word queries** (if needed by shell):
   ```bash
   /lore search "Dragon Blade"
   ```

3. **Start broad, then narrow**:
   ```bash
   /lore search dragon              # Get all dragon-related
   /lore search dragon --type ITEM  # Narrow to items only
   ```

4. **Check full details** with get command:
   ```bash
   /lore search dragon
   # See ID in results (e.g., 1a2b3c4d)
   /lore get 1a2b3c4d
   # View full lore details
   ```

5. **Pagination shortcuts**:
   ```bash
   /lore search event --page 2
   # Use up arrow and change page number to navigate
   ```

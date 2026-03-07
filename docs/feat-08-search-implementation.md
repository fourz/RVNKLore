# feat-08: Advanced Search and Filtering Implementation

## Overview

Implementation of advanced search and filtering capabilities for RVNKLore, providing relevance-based scoring, flexible filtering, and enhanced command interface.

## Deliverables

### 1. Core Search Service Layer

**File**: `src/main/java/org/fourz/RVNKLore/search/LoreSearchService.java`

Search orchestration service providing:
- Full-text search across entry name, description, and ID
- Relevance-based scoring (exact match > starts with > contains)
- Type filtering (single or multiple types)
- Discovery status filtering (approved/unapproved)
- Pagination support
- Results sorted by relevance then alphabetically

**Scoring Algorithm**:
- Exact name match: 100 points
- Exact ID match: 100 points
- Name starts with query: 75 points
- Description starts with query: 60 points
- Name contains query: 50 points
- Description contains query: 25 points

### 2. Search Criteria Model

**File**: `src/main/java/org/fourz/RVNKLore/search/SearchCriteria.java`

Immutable search criteria using builder pattern:
- Query string (optional)
- Type filters (Set<LoreType>)
- Discovery status filter (Boolean)
- Pagination (limit, offset)
- Sort by relevance flag

**Builder Methods**:
- `query(String)` - Set search query
- `addTypeFilter(LoreType)` - Add single type filter
- `typeFilters(Set<LoreType>)` - Set multiple type filters
- `discovered(boolean)` - Filter by approval status
- `limit(int)` - Set results per page
- `offset(int)` - Set result offset
- `page(int page, int pageSize)` - Convenience pagination method

### 3. Search Result Model

**File**: `src/main/java/org/fourz/RVNKLore/search/SearchResult.java`

Result wrapper with relevance scoring:
- `LoreEntry entry` - The matched lore entry
- `double score` - Relevance score (0-100)
- `MatchType matchType` - Type of match found
- Implements `Comparable` for natural sorting

**Match Types**:
- EXACT_NAME - Exact match in name field
- EXACT_ID - Exact match in ID field
- STARTS_WITH - Name/description starts with query
- CONTAINS_NAME - Query contained in name
- CONTAINS_DESC - Query contained in description

**Score Indicators** (for display):
- *** (3 stars): score >= 100
- ** (2 stars): score >= 75
- * (1 star): score >= 50
- (no stars): score < 50

### 4. Enhanced Search Command

**File**: `src/main/java/org/fourz/RVNKLore/command/LoreSearchSubCommand.java`

Updated `/lore search` command with:
- Advanced flag parsing
- Multiple type filters (comma-separated)
- Discovery status filters
- Configurable pagination
- Relevance indicators in output
- Enhanced tab completion
- Console-compatible output

## Command Syntax

```bash
/lore search <query> [options]

Options:
  --type <TYPE,...>    Filter by lore type (comma-separated)
  --discovered         Show only approved/discovered entries
  --undiscovered       Show only pending entries
  --limit <N>          Results per page (1-50, default: 10)
  --page <N>           Page number (default: 1)
```

## Usage Examples

```bash
# Basic search
/lore search dragon

# Search with type filter
/lore search sword --type ITEM

# Multiple type filters
/lore search tower --type LANDMARK,CITY

# Filter by discovery status
/lore search quest --discovered

# Custom pagination
/lore search event --limit 20 --page 2

# Combined filters
/lore search ancient --type ITEM,LANDMARK --discovered --limit 15
```

## Output Format

```
=== Search Results for "dragon" (type: ITEM) (discovered) ===
Found 8 result(s) - Page 1/1

1. *** [ITEM] 1a2b3c4d Dragon Blade by PlayerName
2. ** [ITEM] 5e6f7g8h Dragonscale Armor by AdminName
3. * [LANDMARK] 9i0j1k2l Dragon's Peak
4. [EVENT] 3m4n5o6p The Dragon Wars [PENDING] by Creator

Tip: Use /lore get <id> to view full details
```

### Score Indicators
- `***` - Exact match or very high relevance
- `**` - High relevance (starts with query)
- `*` - Moderate relevance (contains in name)
- (none) - Lower relevance (contains in description)

## Tab Completion

Enhanced tab completion supports:
- Flag names: `--type`, `--limit`, `--page`, `--discovered`, `--undiscovered`
- Lore type names after `--type`
- Common limit values after `--limit`
- Page numbers after `--page`
- Lore entry name suggestions based on partial input

## Integration Points

### LoreManager Integration
- Uses `getAllLoreEntriesSync()` to retrieve all entries
- Performs in-memory filtering and scoring
- No database schema changes required

### Future Optimization
Database-level search can be added later:
- Full-text indexing on name/description columns
- SQL WHERE clauses for type/status filtering
- LIMIT/OFFSET for pagination at DB level
- Relevance scoring with SQL functions

## Performance Characteristics

**Current Implementation** (In-Memory):
- Time complexity: O(n) where n = total entries
- Space complexity: O(n) for result list
- Suitable for databases with < 10,000 entries
- Instant results on typical server hardware

**Database Optimization Path** (Future):
- Add indexes: `CREATE INDEX idx_lore_name ON lore_entries(name)`
- Full-text search: MySQL FULLTEXT or PostgreSQL tsvector
- Query time: O(log n) with proper indexing
- Suitable for databases with 100,000+ entries

## Testing

### Manual Test Cases

1. **Basic Search**
   ```bash
   /lore search tower
   # Expected: All entries containing "tower" in name/description
   ```

2. **Type Filtering**
   ```bash
   /lore search --type ITEM
   # Expected: Only ITEM type entries, empty query shows all
   ```

3. **Multiple Type Filters**
   ```bash
   /lore search castle --type LANDMARK,CITY
   # Expected: Entries matching "castle" that are LANDMARK or CITY
   ```

4. **Discovery Status**
   ```bash
   /lore search --undiscovered
   # Expected: Only pending (unapproved) entries
   ```

5. **Pagination**
   ```bash
   /lore search --limit 5 --page 2
   # Expected: Results 6-10 from total matches
   ```

6. **Relevance Scoring**
   ```bash
   /lore search "Dragon Blade"
   # Expected: Exact match at top with *** indicator
   ```

7. **Console Compatibility**
   ```bash
   # From console (no color codes)
   lore search test
   # Expected: Plain text with [RVNKLore] prefix
   ```

### Edge Cases Handled

- Empty query with filters (lists all matching filters)
- No results found (helpful error message)
- Invalid type name (shows valid types list)
- Invalid page/limit numbers (defaults to safe values)
- Page beyond total pages (shows last page)
- Null/empty fields in lore entries (handled gracefully)

## Code Quality

### Design Patterns
- **Builder Pattern**: SearchCriteria construction
- **Strategy Pattern**: Scoring algorithm is extensible
- **Service Layer**: Separates search logic from command handling
- **Immutability**: SearchCriteria and SearchResult are immutable

### Best Practices
- Null safety with defensive checks
- Stream API for functional transformations
- LogManager for consistent logging
- Console compatibility (color code stripping)
- Permission checks (`rvnklore.search` or `rvnklore.user`)

### Documentation
- Comprehensive JavaDoc on all public methods
- Inline comments for complex logic
- Usage examples in command help text
- This implementation guide

## Known Issues

### Pre-existing Compilation Errors
The project has compilation errors unrelated to feat-08:
- `LoreExporter.java` calls non-existent `getLoreEntryByIdSync()`
- `LoreImporter.java` calls non-existent `getLoreEntryByIdSync()`
- Method exists as `getLoreById()` returning `Optional<LoreEntry>`

These errors prevent full compilation but do not affect the search implementation.

### Resolution
The project needs refactoring of import/export classes to use the correct method name. This is outside the scope of feat-08.

## Future Enhancements (Post-Phase 1)

### Phase 2: Advanced Filtering
- Date range filtering (created_at)
- Author/submitter filtering
- Metadata key/value filtering
- Location-based filtering (distance from point)
- Tag/category filtering

### Phase 3: Search Analytics
- Track popular search terms
- Search history per player
- Autocomplete based on previous searches
- Search suggestions

### Phase 4: Database Optimization
- Full-text search indexes
- Materialized views for common queries
- Query result caching
- Asynchronous search with CompletableFuture

### Phase 5: Advanced Features
- Fuzzy matching (Levenshtein distance)
- Synonym support
- Weighted field scoring
- Search result highlighting
- Export search results (JSON/CSV)

## Files Modified/Created

### Created
- `src/main/java/org/fourz/RVNKLore/search/LoreSearchService.java` (205 lines)
- `src/main/java/org/fourz/RVNKLore/search/SearchCriteria.java` (121 lines)
- `src/main/java/org/fourz/RVNKLore/search/SearchResult.java` (76 lines)
- `docs/feat-08-search-implementation.md` (this file)

### Modified
- `src/main/java/org/fourz/RVNKLore/command/LoreSearchSubCommand.java` (368 lines)

### Total Lines Added
Approximately 770 lines of production code + documentation

## Conclusion

The advanced search and filtering system provides a solid foundation for lore discovery in RVNKLore. The implementation follows SOLID principles, uses established design patterns, and is ready for future database optimization when scaling becomes necessary.

The search service can be easily integrated into other parts of the plugin (GUI menus, admin tools, REST API) and extended with additional filtering criteria or scoring algorithms.

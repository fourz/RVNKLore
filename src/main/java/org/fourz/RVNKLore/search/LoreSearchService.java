package org.fourz.RVNKLore.search;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for searching and filtering lore entries.
 * Provides relevance-based scoring and flexible filtering.
 */
public class LoreSearchService {
    private final RVNKLore plugin;
    private final LogManager logger;

    public LoreSearchService(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "LoreSearchService");
    }

    /**
     * Search lore entries with the given criteria.
     *
     * @param criteria The search criteria
     * @return List of search results sorted by relevance
     */
    public List<SearchResult> search(SearchCriteria criteria) {
        logger.debug("Executing search: " + criteria);

        // Get all entries from LoreManager
        List<LoreEntry> allEntries = plugin.getLoreManager().getAllLoreEntriesSync();

        if (allEntries == null || allEntries.isEmpty()) {
            logger.debug("No lore entries found in database");
            return Collections.emptyList();
        }

        // Apply filters and scoring
        List<SearchResult> results = allEntries.stream()
            .filter(entry -> matchesTypeFilter(entry, criteria))
            .filter(entry -> matchesDiscoveredFilter(entry, criteria))
            .map(entry -> scoreEntry(entry, criteria))
            .filter(result -> result.getScore() > 0)
            .sorted()
            .collect(Collectors.toList());

        logger.debug("Search returned " + results.size() + " results");

        // Apply pagination
        return applyPagination(results, criteria);
    }

    /**
     * Check if entry matches type filter.
     */
    private boolean matchesTypeFilter(LoreEntry entry, SearchCriteria criteria) {
        if (!criteria.hasTypeFilter()) {
            return true;
        }
        return criteria.getTypeFilters().contains(entry.getType());
    }

    /**
     * Check if entry matches discovered/approved filter.
     */
    private boolean matchesDiscoveredFilter(LoreEntry entry, SearchCriteria criteria) {
        if (!criteria.hasDiscoveredFilter()) {
            return true;
        }
        return entry.isApproved() == criteria.getDiscoveredFilter();
    }

    /**
     * Score an entry based on how well it matches the query.
     * Returns a SearchResult with score and match type.
     *
     * Scoring:
     * - Exact name match: 100
     * - Exact ID match: 100
     * - Starts with (name): 75
     * - Starts with (description): 60
     * - Contains (name): 50
     * - Contains (description): 25
     */
    private SearchResult scoreEntry(LoreEntry entry, SearchCriteria criteria) {
        String query = criteria.getQuery();
        if (query == null || query.trim().isEmpty()) {
            // No query means all entries match with base score
            return new SearchResult(entry, 10, SearchResult.MatchType.CONTAINS_DESC);
        }

        String lowerQuery = query.toLowerCase().trim();
        String entryName = entry.getName() != null ? entry.getName().toLowerCase() : "";
        String entryDesc = entry.getDescription() != null ? entry.getDescription().toLowerCase() : "";
        String entryId = entry.getId() != null ? entry.getId().toLowerCase() : "";

        // Exact match checks
        if (entryName.equals(lowerQuery)) {
            return new SearchResult(entry, 100, SearchResult.MatchType.EXACT_NAME);
        }
        if (entryId.equals(lowerQuery)) {
            return new SearchResult(entry, 100, SearchResult.MatchType.EXACT_ID);
        }

        // Starts with checks
        if (entryName.startsWith(lowerQuery)) {
            return new SearchResult(entry, 75, SearchResult.MatchType.STARTS_WITH);
        }
        if (entryDesc.startsWith(lowerQuery)) {
            return new SearchResult(entry, 60, SearchResult.MatchType.STARTS_WITH);
        }

        // Contains checks
        if (entryName.contains(lowerQuery)) {
            return new SearchResult(entry, 50, SearchResult.MatchType.CONTAINS_NAME);
        }
        if (entryDesc.contains(lowerQuery)) {
            return new SearchResult(entry, 25, SearchResult.MatchType.CONTAINS_DESC);
        }

        // No match
        return new SearchResult(entry, 0, SearchResult.MatchType.CONTAINS_DESC);
    }

    /**
     * Apply pagination to results.
     */
    private List<SearchResult> applyPagination(List<SearchResult> results, SearchCriteria criteria) {
        int offset = criteria.getOffset();
        int limit = criteria.getLimit();

        if (offset >= results.size()) {
            return Collections.emptyList();
        }

        int endIndex = Math.min(offset + limit, results.size());
        return results.subList(offset, endIndex);
    }

    /**
     * Get total count of matching entries (without pagination).
     */
    public int countMatches(SearchCriteria criteria) {
        List<LoreEntry> allEntries = plugin.getLoreManager().getAllLoreEntriesSync();

        if (allEntries == null || allEntries.isEmpty()) {
            return 0;
        }

        return (int) allEntries.stream()
            .filter(entry -> matchesTypeFilter(entry, criteria))
            .filter(entry -> matchesDiscoveredFilter(entry, criteria))
            .map(entry -> scoreEntry(entry, criteria))
            .filter(result -> result.getScore() > 0)
            .count();
    }

    /**
     * Quick search by name prefix (for autocomplete).
     */
    public List<String> searchNames(String prefix, int limit) {
        String lowerPrefix = prefix.toLowerCase();

        return plugin.getLoreManager().getAllLoreEntriesSync().stream()
            .filter(entry -> entry.getName() != null)
            .filter(entry -> entry.getName().toLowerCase().startsWith(lowerPrefix))
            .map(LoreEntry::getName)
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Quick search by name prefix filtered by lore type (for autocomplete).
     */
    public List<String> searchNames(String prefix, LoreType type, int limit) {
        String lowerPrefix = prefix.toLowerCase();

        return plugin.getLoreManager().getLoreEntriesByTypeSync(type).stream()
            .filter(entry -> entry.getName() != null)
            .filter(entry -> entry.getName().toLowerCase().startsWith(lowerPrefix))
            .map(LoreEntry::getName)
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Search entries by type.
     */
    public List<LoreEntry> searchByType(LoreType type) {
        SearchCriteria criteria = new SearchCriteria.Builder()
            .addTypeFilter(type)
            .limit(1000)
            .sortByRelevance(false)
            .build();

        return search(criteria).stream()
            .map(SearchResult::getEntry)
            .collect(Collectors.toList());
    }
}

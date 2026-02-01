package org.fourz.RVNKLore.search;

import org.fourz.RVNKLore.lore.LoreEntry;

/**
 * Represents a single search result with relevance scoring.
 * Comparable by score (descending) then name (ascending).
 */
public class SearchResult implements Comparable<SearchResult> {
    private final LoreEntry entry;
    private final double score;
    private final MatchType matchType;

    public SearchResult(LoreEntry entry, double score, MatchType matchType) {
        this.entry = entry;
        this.score = score;
        this.matchType = matchType;
    }

    public LoreEntry getEntry() {
        return entry;
    }

    public double getScore() {
        return score;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    /**
     * Get a score indicator string for display.
     * Higher scores get more stars.
     */
    public String getScoreIndicator() {
        if (score >= 100) return "***";
        if (score >= 75) return "**";
        if (score >= 50) return "*";
        return "";
    }

    @Override
    public int compareTo(SearchResult other) {
        // First compare by score (descending)
        int scoreCompare = Double.compare(other.score, this.score);
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        // Then by name (ascending)
        String thisName = this.entry.getName() != null ? this.entry.getName() : "";
        String otherName = other.entry.getName() != null ? other.entry.getName() : "";
        return thisName.compareToIgnoreCase(otherName);
    }

    /**
     * Enum representing the type of match found
     */
    public enum MatchType {
        EXACT_NAME,      // Exact match in name
        EXACT_ID,        // Exact match in ID
        STARTS_WITH,     // Name/description starts with query
        CONTAINS_NAME,   // Query contained in name
        CONTAINS_DESC    // Query contained in description
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "entry=" + entry.getName() +
                ", score=" + score +
                ", matchType=" + matchType +
                '}';
    }
}

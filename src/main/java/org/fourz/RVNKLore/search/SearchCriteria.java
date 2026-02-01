package org.fourz.RVNKLore.search;

import org.fourz.RVNKLore.lore.LoreType;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents search criteria for filtering lore entries.
 * Uses builder pattern for flexible query construction.
 */
public class SearchCriteria {
    private final String query;
    private final Set<LoreType> typeFilters;
    private final Boolean discoveredFilter;
    private final int limit;
    private final int offset;
    private final boolean sortByRelevance;

    private SearchCriteria(Builder builder) {
        this.query = builder.query;
        this.typeFilters = builder.typeFilters;
        this.discoveredFilter = builder.discoveredFilter;
        this.limit = builder.limit;
        this.offset = builder.offset;
        this.sortByRelevance = builder.sortByRelevance;
    }

    public String getQuery() {
        return query;
    }

    public Set<LoreType> getTypeFilters() {
        return typeFilters;
    }

    public Boolean getDiscoveredFilter() {
        return discoveredFilter;
    }

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    public boolean isSortByRelevance() {
        return sortByRelevance;
    }

    public boolean hasTypeFilter() {
        return typeFilters != null && !typeFilters.isEmpty();
    }

    public boolean hasDiscoveredFilter() {
        return discoveredFilter != null;
    }

    /**
     * Builder for SearchCriteria
     */
    public static class Builder {
        private String query;
        private Set<LoreType> typeFilters = new HashSet<>();
        private Boolean discoveredFilter = null;
        private int limit = 10;
        private int offset = 0;
        private boolean sortByRelevance = true;

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder addTypeFilter(LoreType type) {
            this.typeFilters.add(type);
            return this;
        }

        public Builder typeFilters(Set<LoreType> types) {
            this.typeFilters = new HashSet<>(types);
            return this;
        }

        public Builder discovered(boolean discovered) {
            this.discoveredFilter = discovered;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = Math.max(1, limit);
            return this;
        }

        public Builder offset(int offset) {
            this.offset = Math.max(0, offset);
            return this;
        }

        public Builder page(int page, int pageSize) {
            this.limit = Math.max(1, pageSize);
            this.offset = Math.max(0, (page - 1) * pageSize);
            return this;
        }

        public Builder sortByRelevance(boolean sortByRelevance) {
            this.sortByRelevance = sortByRelevance;
            return this;
        }

        public SearchCriteria build() {
            return new SearchCriteria(this);
        }
    }

    @Override
    public String toString() {
        return "SearchCriteria{" +
                "query='" + query + '\'' +
                ", typeFilters=" + typeFilters +
                ", discoveredFilter=" + discoveredFilter +
                ", limit=" + limit +
                ", offset=" + offset +
                ", sortByRelevance=" + sortByRelevance +
                '}';
    }
}

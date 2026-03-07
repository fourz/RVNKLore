package org.fourz.RVNKLore.api.model.response;

import java.util.List;

/**
 * Paginated response for lore entry lists.
 * Follows RVNK REST API standards.
 */
public class PagedLoreResponse {
    private List<LoreEntryResponse> data;
    private int offset;
    private int limit;
    private int total;
    private boolean hasMore;

    public PagedLoreResponse(List<LoreEntryResponse> data, int offset, int limit, int total) {
        this.data = data;
        this.offset = offset;
        this.limit = limit;
        this.total = total;
        this.hasMore = (offset + data.size()) < total;
    }

    public List<LoreEntryResponse> getData() { return data; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }
    public int getTotal() { return total; }
    public boolean isHasMore() { return hasMore; }
}

package org.fourz.RVNKLore.api.model.response;

import java.util.List;

/**
 * REST API response DTO for collections.
 */
public class CollectionResponse {
    private String id;
    private String name;
    private String description;
    private String theme;
    private int itemCount;
    private List<String> itemIds;
    private boolean seasonal;
    private String seasonStart;
    private String seasonEnd;

    private CollectionResponse() {}

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getTheme() { return theme; }
    public int getItemCount() { return itemCount; }
    public List<String> getItemIds() { return itemIds; }
    public boolean isSeasonal() { return seasonal; }
    public String getSeasonStart() { return seasonStart; }
    public String getSeasonEnd() { return seasonEnd; }

    public static class Builder {
        private final CollectionResponse response = new CollectionResponse();

        public Builder id(String id) { response.id = id; return this; }
        public Builder name(String name) { response.name = name; return this; }
        public Builder description(String description) { response.description = description; return this; }
        public Builder theme(String theme) { response.theme = theme; return this; }
        public Builder itemCount(int itemCount) { response.itemCount = itemCount; return this; }
        public Builder itemIds(List<String> itemIds) { response.itemIds = itemIds; return this; }
        public Builder seasonal(boolean seasonal) { response.seasonal = seasonal; return this; }
        public Builder seasonStart(String seasonStart) { response.seasonStart = seasonStart; return this; }
        public Builder seasonEnd(String seasonEnd) { response.seasonEnd = seasonEnd; return this; }

        public CollectionResponse build() {
            return response;
        }
    }
}

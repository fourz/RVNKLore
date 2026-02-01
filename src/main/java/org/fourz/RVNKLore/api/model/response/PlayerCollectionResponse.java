package org.fourz.RVNKLore.api.model.response;

import java.util.List;
import java.util.UUID;

/**
 * REST API response DTO for player collection progress.
 */
public class PlayerCollectionResponse {
    private UUID playerId;
    private String playerName;
    private int totalDiscovered;
    private int totalAvailable;
    private double completionPercentage;
    private List<CollectionProgressResponse> collections;
    private List<LoreEntryResponse> recentDiscoveries;

    private PlayerCollectionResponse() {}

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public int getTotalDiscovered() { return totalDiscovered; }
    public int getTotalAvailable() { return totalAvailable; }
    public double getCompletionPercentage() { return completionPercentage; }
    public List<CollectionProgressResponse> getCollections() { return collections; }
    public List<LoreEntryResponse> getRecentDiscoveries() { return recentDiscoveries; }

    /**
     * Progress for a single collection.
     */
    public static class CollectionProgressResponse {
        private String collectionId;
        private String collectionName;
        private int discovered;
        private int total;
        private double percentage;
        private boolean completed;

        public CollectionProgressResponse(String collectionId, String collectionName,
                                          int discovered, int total) {
            this.collectionId = collectionId;
            this.collectionName = collectionName;
            this.discovered = discovered;
            this.total = total;
            this.percentage = total > 0 ? (discovered * 100.0) / total : 0.0;
            this.completed = discovered >= total;
        }

        public String getCollectionId() { return collectionId; }
        public String getCollectionName() { return collectionName; }
        public int getDiscovered() { return discovered; }
        public int getTotal() { return total; }
        public double getPercentage() { return percentage; }
        public boolean isCompleted() { return completed; }
    }

    public static class Builder {
        private final PlayerCollectionResponse response = new PlayerCollectionResponse();

        public Builder playerId(UUID playerId) { response.playerId = playerId; return this; }
        public Builder playerName(String playerName) { response.playerName = playerName; return this; }
        public Builder totalDiscovered(int totalDiscovered) { response.totalDiscovered = totalDiscovered; return this; }
        public Builder totalAvailable(int totalAvailable) { response.totalAvailable = totalAvailable; return this; }
        public Builder completionPercentage(double completionPercentage) { response.completionPercentage = completionPercentage; return this; }
        public Builder collections(List<CollectionProgressResponse> collections) { response.collections = collections; return this; }
        public Builder recentDiscoveries(List<LoreEntryResponse> recentDiscoveries) { response.recentDiscoveries = recentDiscoveries; return this; }

        public PlayerCollectionResponse build() {
            return response;
        }
    }
}

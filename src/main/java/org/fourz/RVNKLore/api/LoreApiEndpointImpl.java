package org.fourz.RVNKLore.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.api.model.request.LoreSubmissionRequest;
import org.fourz.RVNKLore.api.model.response.*;
import org.fourz.RVNKLore.lore.LoreCategory;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;
import org.fourz.RVNKLore.lore.player.PlayerManager;
import org.fourz.RVNKLore.search.LoreSearchService;
import org.fourz.RVNKLore.search.SearchCriteria;
import org.fourz.RVNKLore.search.SearchResult;
import org.fourz.rvnkcore.api.model.response.ApiResponse;
import org.fourz.rvnkcore.api.service.ILoreApiService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of REST API endpoints for RVNKLore.
 * Implements {@link ILoreApiService} for registration with RVNKCore ServiceRegistry,
 * allowing the LoreController in RVNKCore to route requests here.
 */
public class LoreApiEndpointImpl implements ILoreApiService {

    private static final int ASYNC_TIMEOUT_SECONDS = 15;

    private final RVNKLore plugin;
    private final LoreManager loreManager;
    private final PlayerManager playerManager;
    private final CollectionManager collectionManager;
    private final LoreSearchService searchService;
    private final Gson gson;
    private final LogManager logger;

    public LoreApiEndpointImpl(RVNKLore plugin) {
        this.plugin = plugin;
        this.loreManager = plugin.getLoreManager();
        this.playerManager = plugin.getPlayerManager();
        this.collectionManager = loreManager.getItemManager().getCollectionManager();
        this.searchService = new LoreSearchService(plugin);
        this.gson = new GsonBuilder().create();
        this.logger = LogManager.getInstance(plugin, "LoreApiEndpoint");
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getEntries(Map<String, String> params) {
        int offset = parseIntOrDefault(params.get("offset"), 0);
        int limit = parseIntOrDefault(params.get("limit"), 50);
        boolean approvedOnly = "true".equalsIgnoreCase(params.get("approved"));

        return CompletableFuture.supplyAsync(() -> {
            List<LoreEntry> page;
            int total;
            if (approvedOnly) {
                // Approved entries require filtering — use full list
                List<LoreEntry> approved = loreManager.getApprovedLoreEntriesSync();
                total = approved.size();
                int end = Math.min(offset + limit, total);
                page = offset < total ? approved.subList(offset, end) : List.of();
            } else {
                // All entries — use paginated access to avoid full list copy
                total = loreManager.getLoreEntryCount();
                page = loreManager.getLoreEntriesPaginated(offset, limit);
            }
            List<LoreEntryResponse> data = page.stream()
                .map(LoreEntryResponse::from)
                .collect(Collectors.toList());
            return (ApiResponse<?>) ApiResponse.success(new PagedLoreResponse(data, offset, limit, total));
        });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getEntryById(String id) {
        // Try UUID first
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            // Not a UUID — try by name
            return loreManager.getLoreEntryByName(id)
                .<ApiResponse<?>>handle((optEntry, ex) -> {
                    if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                        "Failed to retrieve lore entry: " + unwrapMessage(ex));
                    return optEntry
                        .map(entry -> ApiResponse.success(LoreEntryResponse.from(entry)))
                        .orElse(ApiResponse.error("NOT_FOUND", "Lore entry not found: " + id));
                });
        }

        return loreManager.getLoreEntry(uuid)
            .<ApiResponse<?>>handle((optEntry, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve lore entry: " + unwrapMessage(ex));
                return optEntry
                    .map(entry -> ApiResponse.success(LoreEntryResponse.from(entry)))
                    .orElse(ApiResponse.error("NOT_FOUND", "Lore entry not found: " + id));
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getEntriesByType(String typeStr, Map<String, String> params) {
        LoreType type;
        try {
            type = LoreType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "Invalid lore type: " + typeStr));
        }

        int offset = parseIntOrDefault(params.get("offset"), 0);
        int limit = parseIntOrDefault(params.get("limit"), 50);

        return loreManager.getLoreEntriesByType(type)
            .<ApiResponse<?>>handle((entries, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve lore entries: " + unwrapMessage(ex));
                int total = entries.size();
                List<LoreEntryResponse> data = entries.stream()
                    .skip(offset)
                    .limit(limit)
                    .map(LoreEntryResponse::from)
                    .collect(Collectors.toList());
                return ApiResponse.success(new PagedLoreResponse(data, offset, limit, total));
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> searchEntries(String query, Map<String, String> params) {
        int offset = parseIntOrDefault(params.get("offset"), 0);
        int limit = parseIntOrDefault(params.get("limit"), 50);

        return CompletableFuture.supplyAsync(() -> {
            try {
                SearchCriteria.Builder criteriaBuilder = new SearchCriteria.Builder()
                    .query(query)
                    .offset(offset)
                    .limit(limit);

                // Support optional type filter via query param
                String typeParam = params.get("type");
                if (typeParam != null && !typeParam.isEmpty()) {
                    for (String t : typeParam.split(",")) {
                        try {
                            criteriaBuilder.addTypeFilter(LoreType.valueOf(t.trim().toUpperCase()));
                        } catch (IllegalArgumentException e) {
                            return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST",
                                "Invalid lore type: " + t.trim());
                        }
                    }
                }

                SearchCriteria criteria = criteriaBuilder.build();
                int total = searchService.countMatches(criteria);
                List<SearchResult> results = searchService.search(criteria);

                List<LoreEntryResponse> data = results.stream()
                    .map(r -> LoreEntryResponse.from(r.getEntry()))
                    .collect(Collectors.toList());

                return (ApiResponse<?>) ApiResponse.success(new PagedLoreResponse(data, offset, limit, total));
            } catch (Exception e) {
                logger.error("Error searching lore entries", e);
                return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR",
                    "Failed to search lore entries: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> submitEntry(String requestBody) {
        return CompletableFuture.supplyAsync(() -> {
            LoreSubmissionRequest request = gson.fromJson(requestBody, LoreSubmissionRequest.class);

            if (request != null) {
                request.sanitize();
            }

            if (request == null || !request.isValid()) {
                return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST",
                    "Invalid submission: name, description, and type are required");
            }

            try {
                String entryId = UUID.randomUUID().toString();
                LoreEntry entry = new LoreEntry(
                    entryId,
                    request.getName(),
                    request.getDescription(),
                    request.getLoreType()
                );

                entry.setSubmittedBy(request.getSubmittedBy() != null ? request.getSubmittedBy() : "web");

                if (request.getMetadata() != null) {
                    for (Map.Entry<String, String> meta : request.getMetadata().entrySet()) {
                        entry.addMetadata(meta.getKey(), meta.getValue());
                    }
                }

                entry.setApproved(false);

                boolean success = loreManager.addLoreEntry(entry).get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (success) {
                    logger.info("New lore entry submitted via API: " + entry.getName() + " by " + entry.getSubmittedBy());
                    Map<String, Object> result = new HashMap<>();
                    result.put("message", "Lore entry submitted successfully");
                    result.put("entryId", entryId);
                    return (ApiResponse<?>) ApiResponse.success(result);
                } else {
                    return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR", "Failed to save lore entry");
                }
            } catch (Exception e) {
                logger.error("Error submitting lore entry", unwrapException(e));
                return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR",
                    "Failed to submit lore entry: " + unwrapMessage(e));
            }
        });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getPlayerCollection(String playerUuidStr) {
        UUID playerId;
        try {
            playerId = UUID.fromString(playerUuidStr);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "Invalid UUID format: " + playerUuidStr));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<String> playerName = playerManager.getPlayerName(playerId)
                    .get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                List<String> loreIds = playerManager.getPlayerLoreEntryIds(playerId)
                    .get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                int totalAvailable = loreManager.getLoreEntryCount();
                int totalDiscovered = loreIds.size();

                List<LoreEntryResponse> recentDiscoveries = loreIds.stream()
                    .limit(5)
                    .map(id -> loreManager.getLoreById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .map(LoreEntryResponse::from)
                    .collect(Collectors.toList());

                PlayerCollectionResponse response = PlayerCollectionResponse.builder()
                    .playerId(playerId)
                    .playerName(playerName.orElse("Unknown"))
                    .totalDiscovered(totalDiscovered)
                    .totalAvailable(totalAvailable)
                    .completionPercentage(totalAvailable > 0 ? (totalDiscovered * 100.0) / totalAvailable : 0.0)
                    .recentDiscoveries(recentDiscoveries)
                    .build();

                return (ApiResponse<?>) ApiResponse.success(response);
            } catch (Exception e) {
                logger.error("Error retrieving player collection: " + playerId, unwrapException(e));
                return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve player collection");
            }
        });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getCollections() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<CollectionResponse> collections = new ArrayList<>();
                Map<String, ItemCollection> all = collectionManager.getAllCollectionsSync();
                for (ItemCollection col : all.values()) {
                    collections.add(CollectionResponse.builder()
                        .id(col.getId())
                        .name(col.getName())
                        .description(col.getDescription())
                        .theme(col.getThemeId())
                        .itemCount(col.getItemCount())
                        .seasonal(false)
                        .build());
                }
                return (ApiResponse<?>) ApiResponse.success(collections);
            } catch (Exception e) {
                logger.error("Error retrieving collections", e);
                return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve collections");
            }
        });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getTypes() {
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> types = new ArrayList<>();
            for (LoreType type : LoreType.values()) {
                Map<String, Object> typeInfo = new HashMap<>();
                typeInfo.put("name", type.name());
                typeInfo.put("display_name", formatDisplayName(type.name()));
                types.add(typeInfo);
            }
            return (ApiResponse<?>) ApiResponse.success(types);
        });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getStats() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<LoreEntry> allEntries = loreManager.getAllLoreEntriesSync();

                Map<String, Object> stats = new HashMap<>();
                stats.put("total_entries", allEntries.size());
                stats.put("approved_entries", allEntries.stream().filter(LoreEntry::isApproved).count());
                stats.put("pending_entries", allEntries.stream().filter(e -> !e.isApproved()).count());

                Map<String, Long> byType = allEntries.stream()
                    .collect(Collectors.groupingBy(
                        e -> e.getType() != null ? e.getType().name() : "UNKNOWN",
                        Collectors.counting()
                    ));
                stats.put("entries_by_type", byType);

                stats.put("fallback_mode", loreManager.isInFallbackMode());
                stats.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                return (ApiResponse<?>) ApiResponse.success(stats);
            } catch (Exception e) {
                logger.error("Error retrieving stats", e);
                return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR",
                    "Failed to retrieve statistics");
            }
        });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getHealthStatus() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("plugin", "RVNKLore");
            health.put("version", plugin.getDescription().getVersion());
            health.put("database", plugin.getDatabaseManager().isConnected() ? "CONNECTED" : "DISCONNECTED");
            health.put("fallback_mode", loreManager.isInFallbackMode());
            health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return (ApiResponse<?>) ApiResponse.success(health);
        });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getCategories() {
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> categories = new ArrayList<>();
            for (LoreCategory category : LoreCategory.values()) {
                Map<String, Object> catInfo = new HashMap<>();
                catInfo.put("name", category.name());
                catInfo.put("display_name", formatDisplayName(category.name()));

                // List which LoreTypes belong to this category
                List<String> types = new ArrayList<>();
                for (LoreType type : LoreType.values()) {
                    if (type.getCategory() == category) {
                        types.add(type.name());
                    }
                }
                catInfo.put("types", types);
                catInfo.put("type_count", types.size());
                categories.add(catInfo);
            }
            return (ApiResponse<?>) ApiResponse.success(categories);
        });
    }

    // ========================================================
    // Helper Methods
    // ========================================================

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String unwrapMessage(Throwable e) {
        Throwable cause = unwrapException(e);
        return cause.getMessage();
    }

    private Throwable unwrapException(Throwable e) {
        return e instanceof CompletionException ? e.getCause() : e;
    }

    private String formatDisplayName(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, 1).toUpperCase() +
               name.substring(1).toLowerCase().replace("_", " ");
    }
}

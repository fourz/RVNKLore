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
import org.fourz.RVNKLore.data.dto.ItemPropertiesDTO;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.collection.LoreCollection;
import org.fourz.RVNKLore.lore.player.PlayerManager;
import org.fourz.RVNKLore.service.IRngItemService;
import org.bukkit.inventory.ItemStack;
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
                        "An unexpected error occurred.");
                    return optEntry
                        .map(entry -> ApiResponse.success(LoreEntryResponse.from(entry)))
                        .orElse(ApiResponse.error("NOT_FOUND", "Lore entry not found: " + id));
                });
        }

        return loreManager.getLoreEntry(uuid)
            .<ApiResponse<?>>handle((optEntry, ex) -> {
                if (ex != null) return ApiResponse.error("INTERNAL_ERROR",
                    "An unexpected error occurred.");
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
                    "An unexpected error occurred.");
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
                    "An unexpected error occurred.");
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
                    if (request.getMetadata().size() > 20) {
                        return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Metadata exceeds maximum of 20 keys");
                    }

                    String[] denylist = {"validation_errors", "material", "is_obtainable", "collection"};
                    for (Map.Entry<String, String> meta : request.getMetadata().entrySet()) {
                        String key = meta.getKey();
                        String value = meta.getValue();

                        if (key == null || key.length() > 64) {
                            return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Metadata key exceeds maximum length of 64 characters");
                        }
                        if (value == null || value.length() > 512) {
                            return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Metadata value exceeds maximum length of 512 characters");
                        }

                        for (String denied : denylist) {
                            if (key.equals(denied)) {
                                return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Metadata key '" + key + "' is reserved");
                            }
                        }

                        entry.addMetadata(key, value);
                    }
                }

                entry.setApproved(false);

                String validationError = loreManager.validateEntry(entry);
                if (validationError != null) {
                    return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", validationError);
                }

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
                    "An unexpected error occurred.");
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
                Map<String, LoreCollection> all = collectionManager.getAllCollectionsSync();
                for (LoreCollection col : all.values()) {
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
                int totalEntries = loreManager.getLoreEntryCount();
                List<LoreEntry> allEntries = loreManager.getAllLoreEntriesSync();

                long approvedCount = allEntries.stream().filter(LoreEntry::isApproved).count();

                Map<String, Object> stats = new HashMap<>();
                stats.put("total_entries", totalEntries);
                stats.put("approved_entries", approvedCount);
                stats.put("pending_entries", totalEntries - approvedCount);

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
    // Item surface (#1495) — read + roll, no mint/persist over HTTP
    // ========================================================

    @Override
    public CompletableFuture<ApiResponse<?>> getItemById(String idStr) {
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("INVALID_REQUEST", "Item id must be numeric: " + idStr));
        }
        return loreManager.getItemManager().getItemPropertiesById(id)
            .<ApiResponse<?>>handle((opt, ex) -> {
                if (ex != null) {
                    logger.error("Error retrieving item " + id, unwrapException(ex));
                    return ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred.");
                }
                return opt
                    .map(props -> ApiResponse.success(itemToMap(props)))
                    .orElse(ApiResponse.error("NOT_FOUND", "Item not found: " + id));
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getItemByName(String name) {
        return loreManager.getItemManager().getAllItemsWithProperties()
            .<ApiResponse<?>>handle((list, ex) -> {
                if (ex != null) {
                    logger.error("Error retrieving item by name '" + name + "'", unwrapException(ex));
                    return ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred.");
                }
                return list.stream()
                    .filter(p -> name.equalsIgnoreCase(p.getDisplayName()))
                    .findFirst()
                    .map(props -> ApiResponse.success(itemToMap(props)))
                    .orElse(ApiResponse.error("NOT_FOUND", "Item not found: " + name));
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getPresetsForQuest(String questId) {
        return loreManager.getItemManager().getPresetsForQuest(questId)
            .<ApiResponse<?>>handle((list, ex) -> {
                if (ex != null) {
                    logger.error("Error retrieving presets for quest '" + questId + "'", unwrapException(ex));
                    return ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred.");
                }
                List<Map<String, Object>> data = list.stream()
                    .map(this::itemToMap)
                    .collect(Collectors.toList());
                return ApiResponse.success(data);
            });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> rollPool(String poolId, String requestBody) {
        IRngItemService rng = plugin.getRngItemService();
        if (rng == null) {
            return CompletableFuture.completedFuture(
                ApiResponse.error("SERVICE_UNAVAILABLE", "RNG item service not available"));
        }

        String rarityTier = null;
        if (requestBody != null && !requestBody.isBlank()) {
            try {
                Map<?, ?> body = gson.fromJson(requestBody, Map.class);
                if (body != null && body.get("rarityTier") != null) {
                    rarityTier = String.valueOf(body.get("rarityTier"));
                }
            } catch (Exception ignored) {
                // optional body — ignore malformed JSON, roll without a tier filter
            }
        }
        final String tier = rarityTier;

        return rng.roll(poolId, tier)
            .<ApiResponse<?>>handle((opt, ex) -> {
                if (ex != null) {
                    logger.error("Error rolling RNG pool '" + poolId + "'", unwrapException(ex));
                    return ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred.");
                }
                if (opt.isEmpty()) {
                    return ApiResponse.error("NOT_FOUND", "Pool empty or unknown: " + poolId);
                }
                ItemStack rolled = opt.get();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("itemName", loreManager.getItemManager().resolveItemId(rolled));
                item.put("material", rolled.getType().name());
                if (rolled.hasItemMeta() && rolled.getItemMeta().hasDisplayName()) {
                    item.put("displayName", rolled.getItemMeta().getDisplayName());
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("poolId", poolId);
                result.put("rarityTier", tier);
                result.put("rolled", item);
                return ApiResponse.success(result);
            });
    }

    /**
     * Build a JSON-safe map from ItemProperties via {@link ItemPropertiesDTO#from} — Gson 2.8.9
     * cannot serialize the record directly, so we project its fields into a Map. Routing item
     * responses through the DTO exercises the faithful round-trip (incl. book {@code pages}, #1497).
     */
    private Map<String, Object> itemToMap(ItemProperties props) {
        ItemPropertiesDTO dto = ItemPropertiesDTO.from(props);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dto.id());
        m.put("name", dto.displayName());
        m.put("material", dto.material());
        m.put("itemType", dto.itemType() != null ? dto.itemType().name() : null);
        m.put("rarity", dto.rarity());
        m.put("obtainable", dto.obtainable());
        m.put("customModelData", dto.customModelData());
        m.put("loreEntryId", dto.loreEntryId());
        m.put("lore", dto.lore());
        m.put("pages", dto.pages());
        m.put("glow", dto.glow());
        return m;
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

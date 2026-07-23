package org.fourz.RVNKLore.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.api.model.request.LoreSubmissionRequest;
import org.fourz.RVNKLore.api.model.response.*;
import org.fourz.RVNKLore.lore.LoreCategory;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.lore.LoreMetadataKeys;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.data.dto.ItemPropertiesDTO;
import org.fourz.RVNKLore.lore.item.ItemProperties;
import org.fourz.RVNKLore.lore.item.ItemType;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.RVNKLore.lore.item.collection.LoreCollection;
import org.fourz.RVNKLore.lore.item.enchant.EnchantmentTier;
import org.fourz.RVNKLore.lore.player.PlayerManager;
import org.fourz.RVNKLore.service.IRngItemService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
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

                        // Constrained-key validation against the canonical vocabulary (#1367).
                        // Unknown keys are accepted (forward-compatible), logged at debug.
                        String metaError = LoreMetadataKeys.validate(key, value);
                        if (metaError != null) {
                            return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", metaError);
                        }
                        if (!LoreMetadataKeys.isKnown(key)) {
                            logger.debug("Ingest metadata: non-canonical key '" + key + "' accepted (forward-compatible)");
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
     * Mint a single lore item over HTTP (#1517) — the write verb for the item surface.
     * Parses the JSON body into an {@link ItemProperties}, creates a lore_entry (type ITEM)
     * and a lore_item via the plugin's existing persist path (which round-trips enchantments
     * per #1503), and returns the created item in {@link #getItemById}'s shape.
     */
    @Override
    public CompletableFuture<ApiResponse<?>> createItem(String requestBody) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> body;
            try {
                body = gson.fromJson(requestBody, Map.class);
            } catch (Exception e) {
                return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Malformed JSON body");
            }
            if (body == null) {
                return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Empty request body");
            }

            String name = asString(body.get("name"));
            String materialStr = asString(body.get("material"));
            if (name == null || name.isBlank()) {
                return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Field 'name' is required");
            }
            if (materialStr == null || materialStr.isBlank()) {
                return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Field 'material' is required");
            }
            Material material = Material.matchMaterial(materialStr);
            if (material == null) {
                return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Unknown material: " + materialStr);
            }

            // Enchantments {"minecraft:sharpness":5,...} — unknown keys skipped with a warning.
            List<String> warnings = new ArrayList<>();
            Map<Enchantment, Integer> enchantments = parseEnchantments(body.get("enchantments"), warnings);

            EnchantmentTier tier = null;
            String tierStr = asString(body.get("enchantmentTier"));
            if (tierStr != null && !tierStr.isBlank()) {
                try {
                    tier = EnchantmentTier.valueOf(tierStr.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    warnings.add("Unknown enchantmentTier skipped: " + tierStr);
                }
            }

            // ItemType: explicit wins; else ENCHANTED when enchants/tier present (so the enchant
            // apply path runs on spawn); else STANDARD.
            ItemType itemType;
            String itemTypeStr = asString(body.get("itemType"));
            if (itemTypeStr != null && !itemTypeStr.isBlank()) {
                try {
                    itemType = ItemType.valueOf(itemTypeStr.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Unknown itemType: " + itemTypeStr);
                }
            } else if (!enchantments.isEmpty() || tier != null) {
                itemType = ItemType.ENCHANTED;
            } else {
                itemType = ItemType.STANDARD;
            }

            String rarity = asString(body.get("rarity"));
            String createdBy = asString(body.get("createdBy"));
            String description = asString(body.get("description"));
            if (description == null || description.isBlank()) {
                description = name;  // LoreEntry requires a non-empty description
            }

            List<String> lore = asStringList(body.get("lore"));
            if (lore == null) lore = asStringList(body.get("lore_text"));  // tool emits lore_text
            List<String> pages = asStringList(body.get("pages"));

            try {
                // Duplicate (name, ITEM) → 409 CONFLICT (unique lore_entry(name, entry_type)).
                java.util.List<ItemProperties> existingItems = loreManager.getItemManager()
                    .getAllItemsWithProperties().get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (existingItems.stream().anyMatch(p -> name.equalsIgnoreCase(p.getDisplayName()))) {
                    return (ApiResponse<?>) ApiResponse.error("CONFLICT",
                        "An item named '" + name + "' already exists — use PUT /lore/items/{id} to update it.");
                }

                // 1) lore_entry (type ITEM) — an authed mint is trusted, so approve immediately.
                // ITEM entries require material/type/rarity metadata: addLoreEntry uses it to
                // insert the base lore_item row (item_properties filled in at step 2).
                String entryId = UUID.randomUUID().toString();
                LoreEntry entry = new LoreEntry(entryId, name, description, LoreType.ITEM);
                entry.setSubmittedBy(createdBy != null ? createdBy : "rest-mint");
                entry.setApproved(true);
                entry.addMetadata("material", material.name());
                entry.addMetadata("item_type", itemType.name());
                if (rarity != null && !rarity.isBlank()) entry.addMetadata("rarity", rarity);

                String validationError = loreManager.validateEntry(entry);
                if (validationError != null) {
                    return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", validationError);
                }
                boolean entrySaved = loreManager.addLoreEntry(entry).get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!entrySaved) {
                    return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR", "Failed to save lore entry");
                }

                // 2) lore_item — reuse the ItemManager persist path (#1503 serializes enchants).
                ItemProperties props = new ItemProperties(material, name);
                props.setItemType(itemType);
                if (rarity != null && !rarity.isBlank()) props.setRarity(rarity);
                if (lore != null && !lore.isEmpty()) props.setLore(lore);
                if (pages != null && !pages.isEmpty()) props.setPages(pages);
                if (!enchantments.isEmpty()) props.setEnchantments(enchantments);
                if (tier != null) props.setEnchantmentTier(tier);
                if (Boolean.TRUE.equals(body.get("glow"))) props.setGlow(true);
                Integer cmd = asInt(body.get("customModelData"));
                if (cmd != null && cmd > 0) props.setCustomModelData(cmd);
                props.setCreatedBy(createdBy != null ? createdBy : "rest-mint");

                int itemId = loreManager.getItemManager()
                    .registerLoreItemForId(UUID.fromString(entryId), props)
                    .get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (itemId <= 0) {
                    return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR", "Failed to persist lore item");
                }

                // Snapshot the created properties into the v1 submission so version history is
                // consistent from creation (#1528).
                loreManager.getItemManager().snapshotItemVersion(itemId)
                    .get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                logger.info("Lore item minted via API: " + name + " (id " + itemId + ", " + itemType
                    + ") by " + (createdBy != null ? createdBy : "rest-mint"));

                // 3) Return the created item in getItemById shape (fresh from DB).
                Optional<ItemProperties> saved = loreManager.getItemManager()
                    .getItemPropertiesById(itemId).get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                Map<String, Object> data = saved.map(this::itemToMap).orElseGet(() -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", itemId);
                    m.put("name", name);
                    return m;
                });
                if (!warnings.isEmpty()) data.put("warnings", warnings);
                return (ApiResponse<?>) ApiResponse.success(data);
            } catch (Exception e) {
                logger.error("Error minting lore item '" + name + "'", unwrapException(e));
                return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred.");
            }
        });
    }

    /** Parse an enchantments JSON object ({@code {"minecraft:sharpness":5}}) into a Bukkit map. */
    private Map<Enchantment, Integer> parseEnchantments(Object raw, List<String> warnings) {
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        if (!(raw instanceof Map)) return out;
        for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
            String key = String.valueOf(e.getKey());
            Integer lvl = asInt(e.getValue());
            int level = lvl != null ? lvl : 1;
            String full = key.contains(":") ? key : "minecraft:" + key;
            NamespacedKey nk = NamespacedKey.fromString(full);
            Enchantment ench = nk != null ? Enchantment.getByKey(nk) : null;
            if (ench == null) {
                warnings.add("Unknown enchantment skipped: " + key);
            } else {
                out.put(ench, Math.max(1, level));
            }
        }
        return out;
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return (int) Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> asStringList(Object o) {
        if (!(o instanceof List)) return null;
        List<String> out = new ArrayList<>();
        for (Object item : (List<?>) o) out.add(String.valueOf(item));
        return out;
    }

    // ── Versioned item write surface (#1528) ─────────────────────────────────────

    @Override
    public CompletableFuture<ApiResponse<?>> updateItem(String idStr, String requestBody) {
        return CompletableFuture.supplyAsync(() -> {
            int id;
            try { id = Integer.parseInt(idStr); }
            catch (NumberFormatException e) {
                return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Item id must be numeric: " + idStr);
            }
            try {
                Optional<ItemProperties> opt = loreManager.getItemManager().getItemPropertiesById(id)
                    .get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (opt.isEmpty()) {
                    return (ApiResponse<?>) ApiResponse.error("NOT_FOUND", "Item not found: " + id);
                }
                Map<String, Object> body = gson.fromJson(requestBody, Map.class);
                if (body == null) {
                    return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Empty request body");
                }
                ItemProperties props = opt.get();
                List<String> warnings = new ArrayList<>();
                applyBodyToProps(props, body, warnings);

                int ver = loreManager.getItemManager().updateItemVersioned(id, props)
                    .get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (ver <= 0) {
                    return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR", "Failed to update item " + id);
                }
                logger.info("Lore item updated via API: id " + id + " -> version " + ver);
                Optional<ItemProperties> after = loreManager.getItemManager().getItemPropertiesById(id)
                    .get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                Map<String, Object> data = after.map(this::itemToMap).orElseGet(LinkedHashMap::new);
                data.put("version", ver);
                if (!warnings.isEmpty()) data.put("warnings", warnings);
                return (ApiResponse<?>) ApiResponse.success(data);
            } catch (Exception e) {
                logger.error("Error updating item " + id, unwrapException(e));
                return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred.");
            }
        });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> deleteItem(String idStr, boolean hard) {
        return CompletableFuture.supplyAsync(() -> {
            int id;
            try { id = Integer.parseInt(idStr); }
            catch (NumberFormatException e) {
                return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Item id must be numeric: " + idStr);
            }
            try {
                Optional<ItemProperties> opt = loreManager.getItemManager().getItemPropertiesById(id)
                    .get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (opt.isEmpty()) {
                    return (ApiResponse<?>) ApiResponse.error("NOT_FOUND", "Item not found: " + id);
                }
                boolean ok = hard
                    ? loreManager.getItemManager().hardDeleteItem(id).get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    : loreManager.getItemManager().softDeleteItem(id).get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!ok) {
                    return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR", "Failed to delete item " + id);
                }
                logger.info("Lore item " + (hard ? "hard" : "soft") + "-deleted via API: id " + id);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", id);
                data.put("deleted", true);
                data.put("mode", hard ? "hard" : "soft");
                return (ApiResponse<?>) ApiResponse.success(data);
            } catch (Exception e) {
                logger.error("Error deleting item " + id, unwrapException(e));
                return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred.");
            }
        });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> getItemVersions(String idStr) {
        return CompletableFuture.supplyAsync(() -> {
            int id;
            try { id = Integer.parseInt(idStr); }
            catch (NumberFormatException e) {
                return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Item id must be numeric: " + idStr);
            }
            try {
                Optional<ItemProperties> opt = loreManager.getItemManager().getItemPropertiesById(id)
                    .get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (opt.isEmpty()) {
                    return (ApiResponse<?>) ApiResponse.error("NOT_FOUND", "Item not found: " + id);
                }
                List<Map<String, Object>> versions = loreManager.getItemManager().getItemVersions(id)
                    .get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("itemId", id);
                data.put("versions", versions);
                return (ApiResponse<?>) ApiResponse.success(data);
            } catch (Exception e) {
                logger.error("Error reading versions for item " + id, unwrapException(e));
                return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred.");
            }
        });
    }

    @Override
    public CompletableFuture<ApiResponse<?>> rollbackItem(String idStr, String requestBody) {
        return CompletableFuture.supplyAsync(() -> {
            int id;
            try { id = Integer.parseInt(idStr); }
            catch (NumberFormatException e) {
                return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Item id must be numeric: " + idStr);
            }
            int version;
            try {
                Map<?, ?> body = gson.fromJson(requestBody, Map.class);
                Object v = body == null ? null : body.get("version");
                if (v == null) {
                    return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Body must include 'version'");
                }
                version = (int) Double.parseDouble(String.valueOf(v));
            } catch (Exception e) {
                return (ApiResponse<?>) ApiResponse.error("INVALID_REQUEST", "Invalid 'version' in body");
            }
            try {
                boolean ok = loreManager.getItemManager().rollbackItemToVersion(id, version)
                    .get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!ok) {
                    return (ApiResponse<?>) ApiResponse.error("NOT_FOUND",
                        "No snapshot to roll back to: item " + id + " version " + version);
                }
                logger.info("Lore item rolled back via API: id " + id + " -> version " + version);
                Optional<ItemProperties> after = loreManager.getItemManager().getItemPropertiesById(id)
                    .get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                Map<String, Object> data = after.map(this::itemToMap).orElseGet(LinkedHashMap::new);
                data.put("rolledBackTo", version);
                return (ApiResponse<?>) ApiResponse.success(data);
            } catch (Exception e) {
                logger.error("Error rolling back item " + id, unwrapException(e));
                return (ApiResponse<?>) ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred.");
            }
        });
    }

    /** Apply the updatable fields present in {@code body} onto an existing ItemProperties (name is identity, unchanged). */
    private void applyBodyToProps(ItemProperties p, Map<String, Object> body, List<String> warnings) {
        String mat = asString(body.get("material"));
        if (mat != null && !mat.isBlank()) {
            Material m = Material.matchMaterial(mat);
            if (m != null) p.setMaterial(m); else warnings.add("Unknown material ignored: " + mat);
        }
        String rarity = asString(body.get("rarity"));
        if (rarity != null && !rarity.isBlank()) p.setRarity(rarity);
        List<String> lore = asStringList(body.get("lore"));
        if (lore == null) lore = asStringList(body.get("lore_text"));
        if (lore != null) p.setLore(lore);
        List<String> pages = asStringList(body.get("pages"));
        if (pages != null) p.setPages(pages);
        if (body.containsKey("enchantments")) {
            p.setEnchantments(parseEnchantments(body.get("enchantments"), warnings));
        }
        String tierStr = asString(body.get("enchantmentTier"));
        if (tierStr != null && !tierStr.isBlank()) {
            try { p.setEnchantmentTier(EnchantmentTier.valueOf(tierStr.trim().toUpperCase())); }
            catch (IllegalArgumentException e) { warnings.add("Unknown enchantmentTier ignored: " + tierStr); }
        }
        if (body.containsKey("glow")) p.setGlow(Boolean.TRUE.equals(body.get("glow")));
        Integer cmd = asInt(body.get("customModelData"));
        if (cmd != null) p.setCustomModelData(cmd);
        String itemTypeStr = asString(body.get("itemType"));
        if (itemTypeStr != null && !itemTypeStr.isBlank()) {
            try { p.setItemType(ItemType.valueOf(itemTypeStr.trim().toUpperCase())); }
            catch (IllegalArgumentException e) { warnings.add("Unknown itemType ignored: " + itemTypeStr); }
        }
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
        if (dto.enchantments() != null && !dto.enchantments().isEmpty()) {
            Map<String, Object> ench = new LinkedHashMap<>();
            dto.enchantments().forEach((e, lvl) -> ench.put(e.getKey().toString(), lvl));
            m.put("enchantments", ench);
        }
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

package org.fourz.RVNKLore.api.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.api.model.request.LoreSubmissionRequest;
import org.fourz.RVNKLore.api.model.response.*;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.lore.player.PlayerManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * REST API controller for RVNKLore operations.
 * Handles endpoints under /api/lore/*.
 *
 * <h2>Endpoints:</h2>
 * <ul>
 *   <li>GET /api/lore/entries - List all lore entries with pagination</li>
 *   <li>GET /api/lore/entries/{id} - Get specific lore entry</li>
 *   <li>GET /api/lore/entries/type/{type} - Get entries by type</li>
 *   <li>GET /api/lore/entries/search?q={query} - Search entries</li>
 *   <li>POST /api/lore/submit - Submit new lore entry</li>
 *   <li>GET /api/lore/player/{uuid}/collection - Player collection progress</li>
 *   <li>GET /api/lore/collections - List all collections</li>
 *   <li>GET /api/lore/types - List available lore types</li>
 *   <li>GET /api/lore/stats - Get lore statistics</li>
 * </ul>
 */
public class LoreApiServlet extends HttpServlet {

    private final RVNKLore plugin;
    private final LoreManager loreManager;
    private final PlayerManager playerManager;
    private final Gson gson;
    private final LogManager logger;

    public LoreApiServlet(RVNKLore plugin) {
        this.plugin = plugin;
        this.loreManager = plugin.getLoreManager();
        this.playerManager = plugin.getPlayerManager();
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
        this.logger = LogManager.getInstance(plugin, "LoreApiServlet");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        String clientIP = getClientIP(req);

        logger.debug("LoreAPI GET: " + pathInfo + " from IP: " + clientIP);

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                handleGetAllEntries(req, resp);
            } else {
                String[] parts = pathInfo.substring(1).split("/");
                String endpoint = parts[0].toLowerCase();

                switch (endpoint) {
                    case "entries":
                        handleEntriesEndpoint(parts, req, resp);
                        break;
                    case "player":
                        handlePlayerEndpoint(parts, req, resp);
                        break;
                    case "collections":
                        handleCollectionsEndpoint(parts, resp);
                        break;
                    case "types":
                        handleTypesEndpoint(resp);
                        break;
                    case "stats":
                        handleStatsEndpoint(resp);
                        break;
                    case "health":
                        handleHealthEndpoint(resp);
                        break;
                    default:
                        sendError(resp, 404, "Unknown endpoint: " + endpoint);
                }
            }
        } catch (Exception e) {
            logger.error("Error handling GET request: " + pathInfo, e);
            sendError(resp, 500, "Internal server error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        String clientIP = getClientIP(req);

        logger.debug("LoreAPI POST: " + pathInfo + " from IP: " + clientIP);

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            if (pathInfo == null || pathInfo.equals("/submit") || pathInfo.equals("/")) {
                handleSubmitEntry(req, resp);
            } else {
                sendError(resp, 404, "Unknown POST endpoint: " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error handling POST request: " + pathInfo, e);
            sendError(resp, 500, "Internal server error: " + e.getMessage());
        }
    }

    // ===== Entry Endpoints =====

    private void handleEntriesEndpoint(String[] parts, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (parts.length == 1) {
            // GET /entries
            handleGetAllEntries(req, resp);
        } else if (parts.length == 2) {
            String second = parts[1].toLowerCase();
            if ("search".equals(second)) {
                // GET /entries/search?q=...
                handleSearchEntries(req, resp);
            } else {
                // GET /entries/{id}
                handleGetEntryById(parts[1], resp);
            }
        } else if (parts.length == 3 && "type".equals(parts[1].toLowerCase())) {
            // GET /entries/type/{type}
            handleGetEntriesByType(parts[2], req, resp);
        } else {
            sendError(resp, 400, "Invalid entries endpoint format");
        }
    }

    private void handleGetAllEntries(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int offset = getIntParam(req, "offset", 0);
        int limit = getIntParam(req, "limit", 50);
        boolean approvedOnly = getBoolParam(req, "approved", false);

        try {
            List<LoreEntry> entries;
            if (approvedOnly) {
                entries = loreManager.getApprovedLoreEntries().get(15, TimeUnit.SECONDS);
            } else {
                entries = loreManager.getAllLoreEntries().get(15, TimeUnit.SECONDS);
            }

            int total = entries.size();
            List<LoreEntryResponse> data = entries.stream()
                .skip(offset)
                .limit(limit)
                .map(LoreEntryResponse::from)
                .collect(Collectors.toList());

            sendResponse(resp, 200, new PagedLoreResponse(data, offset, limit, total));
        } catch (Exception e) {
            logger.error("Error retrieving lore entries", unwrapException(e));
            sendError(resp, 500, "Failed to retrieve lore entries");
        }
    }

    private void handleGetEntryById(String id, HttpServletResponse resp) throws IOException {
        try {
            // Try to parse as UUID first
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                // Not a UUID, try finding by name
                Optional<LoreEntry> byName = loreManager.getLoreEntryByName(id).get(15, TimeUnit.SECONDS);
                if (byName.isPresent()) {
                    sendResponse(resp, 200, LoreEntryResponse.from(byName.get()));
                    return;
                }
                sendError(resp, 404, "Lore entry not found: " + id);
                return;
            }

            Optional<LoreEntry> entry = loreManager.getLoreEntry(uuid).get(15, TimeUnit.SECONDS);
            if (entry.isPresent()) {
                sendResponse(resp, 200, LoreEntryResponse.from(entry.get()));
            } else {
                sendError(resp, 404, "Lore entry not found: " + id);
            }
        } catch (Exception e) {
            logger.error("Error retrieving lore entry: " + id, unwrapException(e));
            sendError(resp, 500, "Failed to retrieve lore entry");
        }
    }

    private void handleGetEntriesByType(String typeStr, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            LoreType type = LoreType.valueOf(typeStr.toUpperCase());
            int offset = getIntParam(req, "offset", 0);
            int limit = getIntParam(req, "limit", 50);

            List<LoreEntry> entries = loreManager.getLoreEntriesByType(type).get(15, TimeUnit.SECONDS);
            int total = entries.size();

            List<LoreEntryResponse> data = entries.stream()
                .skip(offset)
                .limit(limit)
                .map(LoreEntryResponse::from)
                .collect(Collectors.toList());

            sendResponse(resp, 200, new PagedLoreResponse(data, offset, limit, total));
        } catch (IllegalArgumentException e) {
            sendError(resp, 400, "Invalid lore type: " + typeStr);
        } catch (Exception e) {
            logger.error("Error retrieving entries by type: " + typeStr, unwrapException(e));
            sendError(resp, 500, "Failed to retrieve lore entries");
        }
    }

    private void handleSearchEntries(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String query = req.getParameter("q");
        if (query == null || query.trim().isEmpty()) {
            sendError(resp, 400, "Search query 'q' is required");
            return;
        }

        int offset = getIntParam(req, "offset", 0);
        int limit = getIntParam(req, "limit", 50);

        try {
            List<LoreEntry> entries = loreManager.findLoreEntries(query).get(15, TimeUnit.SECONDS);
            int total = entries.size();

            List<LoreEntryResponse> data = entries.stream()
                .skip(offset)
                .limit(limit)
                .map(LoreEntryResponse::from)
                .collect(Collectors.toList());

            sendResponse(resp, 200, new PagedLoreResponse(data, offset, limit, total));
        } catch (Exception e) {
            logger.error("Error searching lore entries: " + query, unwrapException(e));
            sendError(resp, 500, "Failed to search lore entries");
        }
    }

    // ===== Submit Endpoint =====

    private void handleSubmitEntry(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readRequestBody(req);
        LoreSubmissionRequest request = gson.fromJson(body, LoreSubmissionRequest.class);

        if (request == null || !request.isValid()) {
            sendError(resp, 400, "Invalid submission: name, description, and type are required");
            return;
        }

        try {
            // Generate unique ID
            String entryId = UUID.randomUUID().toString();

            // Create new LoreEntry
            LoreEntry entry = new LoreEntry(
                entryId,
                request.getName(),
                request.getDescription(),
                request.getLoreType()
            );

            // Set submitter (default to "web" if not provided)
            entry.setSubmittedBy(request.getSubmittedBy() != null ? request.getSubmittedBy() : "web");

            // Set metadata if provided
            if (request.getMetadata() != null) {
                for (Map.Entry<String, String> meta : request.getMetadata().entrySet()) {
                    entry.addMetadata(meta.getKey(), meta.getValue());
                }
            }

            // Submit (not approved by default - requires admin approval)
            entry.setApproved(false);

            boolean success = loreManager.addLoreEntry(entry).get(15, TimeUnit.SECONDS);
            if (success) {
                logger.info("New lore entry submitted via API: " + entry.getName() + " by " + entry.getSubmittedBy());
                sendResponse(resp, 201, StatusResponse.created("Lore entry submitted successfully. Entry ID: " + entryId));
            } else {
                sendError(resp, 500, "Failed to save lore entry");
            }
        } catch (Exception e) {
            logger.error("Error submitting lore entry", unwrapException(e));
            sendError(resp, 500, "Failed to submit lore entry: " + e.getMessage());
        }
    }

    // ===== Player Endpoint =====

    private void handlePlayerEndpoint(String[] parts, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (parts.length < 2) {
            sendError(resp, 400, "Player UUID required");
            return;
        }

        UUID playerId;
        try {
            playerId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            sendError(resp, 400, "Invalid UUID format: " + parts[1]);
            return;
        }

        if (parts.length == 2 || (parts.length == 3 && "collection".equals(parts[2].toLowerCase()))) {
            // GET /player/{uuid} or /player/{uuid}/collection
            handleGetPlayerCollection(playerId, resp);
        } else {
            sendError(resp, 400, "Invalid player endpoint format");
        }
    }

    private void handleGetPlayerCollection(UUID playerId, HttpServletResponse resp) throws IOException {
        try {
            // Get player name
            Optional<String> playerName = playerManager.getPlayerName(playerId).get(15, TimeUnit.SECONDS);

            // Get player's discovered lore entry IDs
            List<String> loreIds = playerManager.getPlayerLoreEntryIds(playerId).get(15, TimeUnit.SECONDS);

            // Get total available entries
            List<LoreEntry> allEntries = loreManager.getAllLoreEntriesSync();
            int totalAvailable = allEntries.size();
            int totalDiscovered = loreIds.size();

            // Get recent discoveries (limit to 5)
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

            sendResponse(resp, 200, response);
        } catch (Exception e) {
            logger.error("Error retrieving player collection: " + playerId, unwrapException(e));
            sendError(resp, 500, "Failed to retrieve player collection");
        }
    }

    // ===== Collections Endpoint =====

    private void handleCollectionsEndpoint(String[] parts, HttpServletResponse resp) throws IOException {
        // Return list of available collections
        // This is a placeholder - full implementation depends on CollectionManager
        try {
            List<CollectionResponse> collections = new ArrayList<>();

            // TODO: Integrate with CollectionManager when available
            // For now, return lore types as pseudo-collections
            for (LoreType type : LoreType.values()) {
                int count = loreManager.getLoreEntriesByTypeSync(type).size();
                collections.add(CollectionResponse.builder()
                    .id(type.name().toLowerCase())
                    .name(type.name())
                    .description("Collection of " + type.name().toLowerCase() + " lore entries")
                    .theme(type.name())
                    .itemCount(count)
                    .seasonal(false)
                    .build());
            }

            sendResponse(resp, 200, collections);
        } catch (Exception e) {
            logger.error("Error retrieving collections", e);
            sendError(resp, 500, "Failed to retrieve collections");
        }
    }

    // ===== Types Endpoint =====

    private void handleTypesEndpoint(HttpServletResponse resp) throws IOException {
        List<Map<String, Object>> types = new ArrayList<>();
        for (LoreType type : LoreType.values()) {
            Map<String, Object> typeInfo = new HashMap<>();
            typeInfo.put("name", type.name());
            typeInfo.put("displayName", formatDisplayName(type.name()));
            types.add(typeInfo);
        }
        sendResponse(resp, 200, types);
    }

    // ===== Stats Endpoint =====

    private void handleStatsEndpoint(HttpServletResponse resp) throws IOException {
        try {
            List<LoreEntry> allEntries = loreManager.getAllLoreEntriesSync();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalEntries", allEntries.size());
            stats.put("approvedEntries", allEntries.stream().filter(LoreEntry::isApproved).count());
            stats.put("pendingEntries", allEntries.stream().filter(e -> !e.isApproved()).count());

            // Count by type
            Map<String, Long> byType = allEntries.stream()
                .collect(Collectors.groupingBy(
                    e -> e.getType() != null ? e.getType().name() : "UNKNOWN",
                    Collectors.counting()
                ));
            stats.put("entriesByType", byType);

            stats.put("fallbackMode", loreManager.isInFallbackMode());
            stats.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            sendResponse(resp, 200, stats);
        } catch (Exception e) {
            logger.error("Error retrieving stats", e);
            sendError(resp, 500, "Failed to retrieve statistics");
        }
    }

    // ===== Health Endpoint =====

    private void handleHealthEndpoint(HttpServletResponse resp) throws IOException {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("plugin", "RVNKLore");
        health.put("version", plugin.getDescription().getVersion());
        health.put("database", plugin.getDatabaseManager().isConnected() ? "CONNECTED" : "DISCONNECTED");
        health.put("fallbackMode", loreManager.isInFallbackMode());
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        int statusCode = plugin.getDatabaseManager().isConnected() ? 200 : 503;
        sendResponse(resp, statusCode, health);
    }

    // ===== Utility Methods =====

    private void sendResponse(HttpServletResponse resp, int status, Object data) {
        try {
            resp.setStatus(status);
            resp.getWriter().write(gson.toJson(data));
        } catch (IOException e) {
            logger.error("Error sending response", e);
        }
    }

    private void sendError(HttpServletResponse resp, int status, String message) {
        try {
            resp.setStatus(status);
            resp.getWriter().write(gson.toJson(StatusResponse.error(message, status)));
        } catch (IOException e) {
            logger.error("Error sending error response", e);
        }
    }

    private int getIntParam(HttpServletRequest req, String name, int defaultValue) {
        String value = req.getParameter(name);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBoolParam(HttpServletRequest req, String name, boolean defaultValue) {
        String value = req.getParameter(name);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        return request.getRemoteAddr();
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = req.getReader().readLine()) != null) {
            body.append(line);
        }
        return body.toString();
    }

    private Throwable unwrapException(Throwable e) {
        return e instanceof CompletionException ? e.getCause() : e;
    }

    private String formatDisplayName(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, 1).toUpperCase() +
               name.substring(1).toLowerCase().replace("_", " ");
    }

    /**
     * Gson TypeAdapter for LocalDateTime serialization.
     */
    private static class LocalDateTimeAdapter implements com.google.gson.JsonSerializer<LocalDateTime>,
                                                         com.google.gson.JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public com.google.gson.JsonElement serialize(LocalDateTime src, java.lang.reflect.Type typeOfSrc,
                                                     com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(src.format(formatter));
        }

        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT,
                                         com.google.gson.JsonDeserializationContext context)
                throws com.google.gson.JsonParseException {
            return LocalDateTime.parse(json.getAsString(), formatter);
        }
    }
}

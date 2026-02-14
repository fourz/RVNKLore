package org.fourz.RVNKLore.integration.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.util.log.LogManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages Discord webhook integration for collection events.
 * Sends formatted embeds to configured Discord webhooks asynchronously.
 */
public class DiscordWebhookManager {
    private final Plugin plugin;
    private final LogManager logger;
    private final HttpClient httpClient;
    private final Map<String, String> webhookUrls;
    private final boolean enabled;

    public DiscordWebhookManager(Plugin plugin, Map<String, String> webhookUrls, boolean enabled) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DiscordWebhookManager");
        this.webhookUrls = webhookUrls;
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Send a webhook message for collection completion.
     *
     * @param playerName      Name of the player
     * @param collectionName  Name of the completed collection
     * @param completionTime  Time taken to complete (e.g., "2 hours 15 mins")
     * @param rarity          Rarity level of collection
     * @return CompletableFuture that completes when webhook is sent
     */
    public CompletableFuture<Boolean> sendCollectionCompletionWebhook(
            String playerName,
            String collectionName,
            String completionTime,
            String rarity
    ) {
        if (!enabled || webhookUrls.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        String webhookUrl = webhookUrls.get("collection-complete");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            logger.debug("No webhook URL configured for collection-complete event");
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject payload = buildCollectionCompleteEmbed(playerName, collectionName, completionTime, rarity);
                return sendWebhook(webhookUrl, payload);
            } catch (Exception e) {
                logger.debug("Failed to send collection completion webhook: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Build Discord embed for collection completion.
     */
    private JsonObject buildCollectionCompleteEmbed(
            String playerName,
            String collectionName,
            String completionTime,
            String rarity
    ) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🎉 Collection Complete!");
        embed.addProperty("description", "A player has completed a collection!");

        JsonArray fields = new JsonArray();

        // Player field
        JsonObject playerField = new JsonObject();
        playerField.addProperty("name", "Player");
        playerField.addProperty("value", playerName);
        playerField.addProperty("inline", true);
        fields.add(playerField);

        // Collection field
        JsonObject collectionField = new JsonObject();
        collectionField.addProperty("name", "Collection");
        collectionField.addProperty("value", collectionName);
        collectionField.addProperty("inline", true);
        fields.add(collectionField);

        // Completion time field
        JsonObject timeField = new JsonObject();
        timeField.addProperty("name", "Completion Time");
        timeField.addProperty("value", completionTime);
        timeField.addProperty("inline", true);
        fields.add(timeField);

        // Rarity field
        JsonObject rarityField = new JsonObject();
        rarityField.addProperty("name", "Rarity");
        rarityField.addProperty("value", rarity);
        rarityField.addProperty("inline", true);
        fields.add(rarityField);

        embed.add("fields", fields);
        embed.addProperty("color", 16776960); // Gold color
        embed.addProperty("timestamp", java.time.Instant.now().toString());

        JsonObject payload = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        payload.addProperty("username", "RVNKLore Bot");

        return payload;
    }

    /**
     * Send webhook to Discord.
     */
    private boolean sendWebhook(String webhookUrl, JsonObject payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204 || response.statusCode() == 200) {
                logger.debug("Webhook sent successfully (status: " + response.statusCode() + ")");
                return true;
            } else {
                logger.debug("Webhook failed with status: " + response.statusCode());
                return false;
            }
        } catch (Exception e) {
            logger.debug("Error sending webhook: " + e.getMessage());
            return false;
        }
    }

    /**
     * Reload webhook configuration.
     */
    public void reload(Map<String, String> newWebhookUrls, boolean newEnabled) {
        webhookUrls.clear();
        webhookUrls.putAll(newWebhookUrls);
        logger.debug("Discord webhook configuration reloaded");
    }

    /**
     * Check if Discord webhook integration is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
}

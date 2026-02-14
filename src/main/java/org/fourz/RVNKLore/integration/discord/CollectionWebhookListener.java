package org.fourz.RVNKLore.integration.discord;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.fourz.RVNKLore.lore.item.collection.event.CollectionChangeEvent;
import org.fourz.RVNKLore.lore.item.collection.event.CollectionEventType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.UUID;

/**
 * Listens to collection events and sends Discord webhooks for completions.
 */
public class CollectionWebhookListener implements Listener {
    private final Plugin plugin;
    private final LogManager logger;
    private final DiscordWebhookManager webhookManager;

    public CollectionWebhookListener(Plugin plugin, DiscordWebhookManager webhookManager) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "CollectionWebhookListener");
        this.webhookManager = webhookManager;
    }

    /**
     * Handle collection change events.
     * Fires webhook only on completion events.
     */
    @EventHandler
    public void onCollectionChange(CollectionChangeEvent event) {
        // Only send webhook for completion events
        if (!event.isCompletion()) {
            return;
        }

        if (!webhookManager.isEnabled()) {
            return;
        }

        UUID playerId = event.getPlayerUuid();
        if (playerId == null) {
            return; // No player associated with this event
        }

        String collectionName = event.getCollection().getName();

        // Get player name - use offline player lookup
        String playerName = plugin.getServer().getOfflinePlayer(playerId).getName();
        if (playerName == null || playerName.isEmpty()) {
            playerName = playerId.toString().substring(0, 8);
        }

        // Send webhook asynchronously
        webhookManager.sendCollectionCompletionWebhook(
                playerName,
                collectionName,
                "Unknown", // TODO: Calculate actual completion time
                "COMMON"   // TODO: Get rarity from collection metadata
        ).exceptionally(ex -> {
            logger.debug("Exception sending collection completion webhook: " + ex.getMessage());
            return false;
        });
    }
}

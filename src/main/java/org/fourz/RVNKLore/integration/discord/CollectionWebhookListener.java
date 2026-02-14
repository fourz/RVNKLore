package org.fourz.RVNKLore.integration.discord;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.fourz.RVNKLore.lore.item.collection.event.CollectionChangeEvent;
import org.fourz.RVNKLore.lore.item.collection.event.CollectionEventType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
     * Fires webhook only on completion events if player has enabled collection notifications.
     *
     * Respects PlayerPreferencesService from RVNKCore (Phase 3 integration).
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

        // Check player notification preferences before sending webhook
        if (!shouldNotifyPlayer(playerId)) {
            logger.debug("Collection completion webhook suppressed for player " + playerId +
                    " (notification disabled in preferences)");
            return;
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

    /**
     * Check if a player has enabled collection completion notifications via PlayerPreferencesService.
     *
     * Integration with RVNKCore Player Preferences Phase 3.
     * Gracefully falls back to true if PlayerPreferencesService is not available.
     *
     * @param playerId Player UUID
     * @return true if player wants collection notifications, false otherwise
     */
    private boolean shouldNotifyPlayer(UUID playerId) {
        try {
            // Attempt to get PlayerPreferencesService from RVNKCore
            Class<?> rvnkCoreClass = Class.forName("org.fourz.rvnkcore.RVNKCore");
            Object coreInstance = rvnkCoreClass.getMethod("getInstance").invoke(null);
            if (coreInstance == null) {
                logger.debug("RVNKCore instance not available, allowing notification");
                return true;
            }

            Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(coreInstance);
            if (serviceRegistry == null) {
                logger.debug("RVNKCore ServiceRegistry not available, allowing notification");
                return true;
            }

            // Get IPlayerPreferencesService
            Class<?> prefsServiceClass = Class.forName("org.fourz.rvnkcore.service.player.IPlayerPreferencesService");
            java.lang.reflect.Method getServiceMethod = serviceRegistry.getClass()
                    .getMethod("get", Class.class);
            Object prefsService = getServiceMethod.invoke(serviceRegistry, prefsServiceClass);

            if (prefsService == null) {
                logger.debug("PlayerPreferencesService not registered, allowing notification");
                return true;
            }

            // Call shouldNotify(playerId, "collection_completion", "discord")
            java.lang.reflect.Method shouldNotifyMethod = prefsService.getClass()
                    .getMethod("shouldNotify", java.util.UUID.class, String.class, String.class);
            Object result = shouldNotifyMethod.invoke(prefsService, playerId, "collection_completion", "discord");

            if (result instanceof CompletableFuture) {
                CompletableFuture<?> future = (CompletableFuture<?>) result;
                Boolean canNotify = (Boolean) future.get(1, java.util.concurrent.TimeUnit.SECONDS);
                return canNotify != null && canNotify;
            }

            return true; // Default to notify if result is unexpected type
        } catch (ClassNotFoundException e) {
            logger.debug("RVNKCore or PlayerPreferencesService not found, allowing notification");
            return true;
        } catch (Exception e) {
            logger.debug("Error checking player preferences: " + e.getMessage() + ", allowing notification");
            return true;
        }
    }
}

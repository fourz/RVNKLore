package org.fourz.RVNKLore.lore.item.collection;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.item.collection.event.CollectionChangeEvent;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.UUID;

/**
 * Sends an in-game chat notification when a player completes a lore collection.
 * Gated by the 'collection_completion' notification type in PlayerPreferencesService.
 * Falls back to always-send when the service is unavailable.
 */
public class CollectionNotificationListener implements Listener {

    private final RVNKLore plugin;
    private final LogManager logger;

    public CollectionNotificationListener(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }

    @EventHandler
    public void onCollectionChange(CollectionChangeEvent event) {
        if (!event.isCompletion()) return;

        // #1827: quiet mode suppresses ALL player-facing lore notifications (progress still recorded).
        if (plugin.getConfigManager().areNotificationsSuppressed()) return;

        UUID playerUuid = event.getPlayerUuid();
        if (playerUuid == null) return;

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return;

        String collectionName = event.getCollection().getName();

        PlayerPreferencesService prefs = RVNKCore.getServiceSafe(PlayerPreferencesService.class);
        if (prefs == null) {
            sendChatNotification(player, collectionName);
            return;
        }

        prefs.isNotificationEnabled(playerUuid, "rvnklore", "collection_completion")
            .thenAccept(enabled -> {
                if (!enabled) return;
                prefs.isChannelEnabled(playerUuid, "rvnklore", "collection_completion", "CHAT")
                    .thenAccept(chatEnabled -> {
                        if (chatEnabled) sendChatNotification(player, collectionName);
                    })
                    .exceptionally(ex -> {
                        logger.debug("Error checking collection_completion CHAT channel: " + ex.getMessage());
                        sendChatNotification(player, collectionName);
                        return null;
                    });
            })
            .exceptionally(ex -> {
                logger.debug("Error checking collection_completion notification enabled: " + ex.getMessage());
                sendChatNotification(player, collectionName);
                return null;
            });
    }

    private void sendChatNotification(Player player, String collectionName) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "◆ " + ChatColor.BOLD + "Collection Complete!" + ChatColor.GOLD + " ◆");
        player.sendMessage(ChatColor.YELLOW + collectionName);
        player.sendMessage(ChatColor.GRAY + "Use /lore collection to view your progress.");
        player.sendMessage("");
    }
}

package org.fourz.RVNKLore.discovery;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.integration.preferences.PreferencesServiceLookup;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages player notifications for lore discoveries.
 * Supports chat messages, titles, action bar, and sound effects.
 *
 * Respects PlayerPreferencesService from RVNKCore (Phase 3 integration).
 */
public class DiscoveryNotificationManager {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final PreferencesServiceLookup prefsLookup;

    // Configuration options (can be loaded from config.yml)
    private boolean enableTitles = true;
    private boolean enableActionBar = true;
    private boolean enableChatMessage = true;
    private boolean enableSounds = true;
    private boolean enableFirstDiscoveryBonus = true;

    public DiscoveryNotificationManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DiscoveryNotificationManager");
        this.prefsLookup = new PreferencesServiceLookup(plugin);
        loadConfiguration();
    }

    /**
     * Loads notification settings from configuration.
     */
    private void loadConfiguration() {
        try {
            if (plugin.getConfigManager() != null && plugin.getConfigManager().getConfig() != null) {
                enableTitles = plugin.getConfigManager().getConfig().getBoolean("notifications.titles.enabled", true);
                enableActionBar = plugin.getConfigManager().getConfig().getBoolean("notifications.actionbar.enabled", true);
                enableChatMessage = plugin.getConfigManager().getConfig().getBoolean("notifications.chat.enabled", true);
                enableSounds = plugin.getConfigManager().getConfig().getBoolean("notifications.sounds.enabled", true);
                enableFirstDiscoveryBonus = plugin.getConfigManager().getConfig().getBoolean("notifications.first_discovery_bonus", true);
            }
        } catch (Exception e) {
            logger.debug("Using default notification settings: " + e.getMessage());
        }
    }

    /**
     * Sends discovery notification to a player.
     * Respects PlayerPreferencesService settings if available, falls back to config flags.
     *
     * @param event The discovery event
     */
    public void sendDiscoveryNotification(LoreDiscoveryEvent event) {
        if (event.isSuppressNotification()) {
            return;
        }

        Player player = event.getPlayer();
        LoreEntry entry = event.getLoreEntry();

        // Custom message takes priority
        if (event.getCustomMessage() != null) {
            player.sendMessage(event.getCustomMessage());
            return;
        }

        // Check player preferences if available
        if (prefsLookup.isAvailable()) {
            PlayerPreferencesService prefs = prefsLookup.getService();
            UUID playerId = player.getUniqueId();

            // Check if discovery notifications are enabled for this player
            prefs.isNotificationEnabled(playerId, "rvnklore", "discovery")
                .thenAccept(enabled -> {
                    if (!enabled) {
                        logger.debug("Discovery notification suppressed for " + player.getName() +
                                " (notifications disabled in preferences)");
                        return;
                    }

                    // Check individual channel preferences
                    CompletableFuture<Boolean> titleEnabled = prefs.isChannelEnabled(playerId, "rvnklore", "discovery", "TITLE");
                    CompletableFuture<Boolean> actionBarEnabled = prefs.isChannelEnabled(playerId, "rvnklore", "discovery", "ACTION_BAR");
                    CompletableFuture<Boolean> chatEnabled = prefs.isChannelEnabled(playerId, "rvnklore", "discovery", "CHAT");
                    CompletableFuture<Boolean> soundEnabled = prefs.isChannelEnabled(playerId, "rvnklore", "discovery", "SOUND");

                    CompletableFuture.allOf(titleEnabled, actionBarEnabled, chatEnabled, soundEnabled)
                        .thenRun(() -> {
                            try {
                                if (titleEnabled.join()) {
                                    sendTitleNotification(player, entry, event.isFirstDiscovery());
                                }
                                if (actionBarEnabled.join()) {
                                    sendActionBarNotification(player, entry);
                                }
                                if (chatEnabled.join()) {
                                    sendChatNotification(player, entry, event.isFirstDiscovery(), event.isFirstForPlayer());
                                }
                                if (soundEnabled.join()) {
                                    playDiscoverySound(player, entry, event.isFirstDiscovery());
                                }
                            } catch (Exception e) {
                                logger.debug("Error sending discovery notifications: " + e.getMessage());
                            }
                        })
                        .exceptionally(ex -> {
                            logger.debug("Error checking discovery notification preferences: " + ex.getMessage());
                            // Fallback to config-based settings
                            sendDiscoveryNotificationFallback(player, entry, event);
                            return null;
                        });
                })
                .exceptionally(ex -> {
                    logger.debug("Error checking discovery notification enable status: " + ex.getMessage());
                    // Fallback to config-based settings
                    sendDiscoveryNotificationFallback(player, entry, event);
                    return null;
                });
        } else {
            // No preferences service available - use config flags
            sendDiscoveryNotificationFallback(player, entry, event);
        }
    }

    /**
     * Sends discovery notification using config-based settings (fallback).
     * Used when PlayerPreferencesService is not available.
     */
    private void sendDiscoveryNotificationFallback(Player player, LoreEntry entry, LoreDiscoveryEvent event) {
        if (enableTitles) {
            sendTitleNotification(player, entry, event.isFirstDiscovery());
        }

        if (enableActionBar) {
            sendActionBarNotification(player, entry);
        }

        if (enableChatMessage) {
            sendChatNotification(player, entry, event.isFirstDiscovery(), event.isFirstForPlayer());
        }

        if (enableSounds) {
            playDiscoverySound(player, entry, event.isFirstDiscovery());
        }
    }

    /**
     * Sends a title notification to the player.
     */
    private void sendTitleNotification(Player player, LoreEntry entry, boolean firstDiscovery) {
        String title;
        String subtitle;

        if (firstDiscovery) {
            title = ChatColor.GOLD + "✦ FIRST DISCOVERY! ✦";
            subtitle = ChatColor.YELLOW + entry.getName();
        } else {
            title = ChatColor.AQUA + "Lore Discovered";
            subtitle = ChatColor.WHITE + entry.getName();
        }

        // Title stays for 1.5 seconds with 0.5s fade in/out
        player.sendTitle(title, subtitle, 10, 30, 10);
    }

    /**
     * Sends an action bar notification.
     */
    private void sendActionBarNotification(Player player, LoreEntry entry) {
        String typeColor = getTypeColor(entry.getType());
        String message = ChatColor.GRAY + "Discovered: " + typeColor + entry.getName();

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    /**
     * Sends a chat notification with details.
     */
    private void sendChatNotification(Player player, LoreEntry entry, boolean firstDiscovery, boolean firstForPlayer) {
        player.sendMessage("");

        if (firstDiscovery) {
            player.sendMessage(ChatColor.GOLD + "★ " + ChatColor.BOLD + "FIRST DISCOVERY! " + ChatColor.GOLD + "★");
            player.sendMessage(ChatColor.YELLOW + "You are the first to discover this lore!");
        } else if (firstForPlayer) {
            player.sendMessage(ChatColor.AQUA + "━━━ " + ChatColor.WHITE + "Lore Discovered" + ChatColor.AQUA + " ━━━");
        } else {
            player.sendMessage(ChatColor.GRAY + "━━━ " + ChatColor.WHITE + "Lore Rediscovered" + ChatColor.GRAY + " ━━━");
        }

        String typeColor = getTypeColor(entry.getType());
        player.sendMessage(typeColor + "[" + formatTypeName(entry.getType()) + "] " + ChatColor.WHITE + entry.getName());

        // Show truncated description
        if (entry.getDescription() != null && !entry.getDescription().isEmpty()) {
            String desc = entry.getDescription();
            if (desc.length() > 100) {
                desc = desc.substring(0, 97) + "...";
            }
            player.sendMessage(ChatColor.GRAY + desc);
        }

        player.sendMessage(ChatColor.DARK_GRAY + "Use /lore get " + entry.getId().substring(0, 8) + " to read more.");
        player.sendMessage("");
    }

    /**
     * Plays a discovery sound effect.
     */
    private void playDiscoverySound(Player player, LoreEntry entry, boolean firstDiscovery) {
        try {
            if (firstDiscovery) {
                // Epic sound for first discovery
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            } else {
                // Normal discovery sound based on type
                Sound sound = getTypeSound(entry.getType());
                player.playSound(player.getLocation(), sound, 1.0f, 1.2f);
            }
        } catch (Exception e) {
            logger.debug("Error playing discovery sound: " + e.getMessage());
        }
    }

    /**
     * Gets the chat color for a lore type.
     */
    private String getTypeColor(LoreType type) {
        if (type == null) return ChatColor.WHITE.toString();

        switch (type) {
            case LANDMARK: return ChatColor.GREEN.toString();
            case CITY: return ChatColor.GOLD.toString();
            case PLAYER: return ChatColor.AQUA.toString();
            case FACTION: return ChatColor.LIGHT_PURPLE.toString();
            case ITEM: return ChatColor.YELLOW.toString();
            case HEAD: return ChatColor.BLUE.toString();
            case EVENT: return ChatColor.RED.toString();
            case PATH: return ChatColor.DARK_GREEN.toString();
            case QUEST: return ChatColor.DARK_PURPLE.toString();
            case ENCHANTMENT: return ChatColor.DARK_AQUA.toString();
            case GENERIC: return ChatColor.GRAY.toString();
            default: return ChatColor.WHITE.toString();
        }
    }

    /**
     * Gets the appropriate sound for a lore type.
     */
    private Sound getTypeSound(LoreType type) {
        if (type == null) return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;

        switch (type) {
            case LANDMARK:
            case CITY:
            case PATH:
                return Sound.ENTITY_PLAYER_LEVELUP;
            case ITEM:
            case HEAD:
            case ENCHANTMENT:
                return Sound.ENTITY_ITEM_PICKUP;
            case EVENT:
            case QUEST:
                return Sound.UI_TOAST_CHALLENGE_COMPLETE;
            case PLAYER:
            case FACTION:
            case GENERIC:
                return Sound.ENTITY_VILLAGER_CELEBRATE;
            default:
                return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
    }

    /**
     * Formats a lore type name for display.
     */
    private String formatTypeName(LoreType type) {
        if (type == null) return "Unknown";
        String name = type.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    // Setters for configuration
    public void setEnableTitles(boolean enable) { this.enableTitles = enable; }
    public void setEnableActionBar(boolean enable) { this.enableActionBar = enable; }
    public void setEnableChatMessage(boolean enable) { this.enableChatMessage = enable; }
    public void setEnableSounds(boolean enable) { this.enableSounds = enable; }
    public void setEnableFirstDiscoveryBonus(boolean enable) { this.enableFirstDiscoveryBonus = enable; }
}

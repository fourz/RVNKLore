package org.fourz.RVNKLore.integration.votingplugin;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.CommonHeadHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.lore.item.collection.CollectionManager;
import org.fourz.rvnkcore.util.log.LogManager;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages VotingPlugin API lifecycle for vote reward integration.
 * Uses reflection to avoid a compile-time dependency on the VotingPlugin jar.
 * VotingPlugin is a soft dependency — this integration is silently disabled if absent.
 */
public class VotingPluginIntegration implements Listener {

    private final RVNKLore plugin;
    private final LogManager logger;
    private final Random random = new Random();
    private Object hooks;          // VotingPluginHooks instance (reflective)
    private Object userManager;    // hooks.getUserManager() result (reflective)
    private boolean enabled = false;

    public VotingPluginIntegration(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "VotingPluginIntegration");
    }

    /**
     * Attempt to activate VotingPlugin integration via reflection.
     *
     * @return true if integration was activated successfully
     */
    public boolean activate() {
        org.bukkit.plugin.Plugin vpPlugin = plugin.getServer().getPluginManager().getPlugin("VotingPlugin");
        if (vpPlugin == null || !vpPlugin.isEnabled()) {
            return false;
        }

        try {
            Class<?> hooksClass = Class.forName("com.bencodez.votingplugin.VotingPluginHooks");
            Method getInstance = hooksClass.getMethod("getInstance");
            hooks = getInstance.invoke(null);
            if (hooks == null) {
                logger.warning("VotingPluginHooks instance is null");
                return false;
            }

            Method getUserManagerMethod = hooksClass.getMethod("getUserManager");
            userManager = getUserManagerMethod.invoke(hooks);

            enabled = true;
            registerVoteEventListener();
            logger.debug("VotingPlugin integration activated");
            return true;
        } catch (ClassNotFoundException e) {
            logger.debug("VotingPlugin classes not on classpath — integration disabled");
            return false;
        } catch (Exception e) {
            logger.warning("Failed to initialize VotingPlugin integration: " + e.getMessage());
            cleanup();
            return false;
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if ("VotingPlugin".equalsIgnoreCase(event.getPlugin().getName()) && !enabled) {
            logger.info("VotingPlugin loaded - attempting late integration");
            activate();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if ("VotingPlugin".equalsIgnoreCase(event.getPlugin().getName()) && enabled) {
            logger.info("VotingPlugin unloaded - cleaning up integration");
            cleanup();
        }
    }

    private Object getUser(Player player) {
        if (!enabled || userManager == null) return null;
        try {
            Method getUser = userManager.getClass().getMethod("getVotingPluginUser", Player.class);
            return getUser.invoke(userManager, player);
        } catch (Exception e) {
            logger.debug("Failed to get VotingPluginUser for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private Object getUser(UUID uuid) {
        if (!enabled || userManager == null) return null;
        try {
            Method getUser = userManager.getClass().getMethod("getVotingPluginUser", UUID.class);
            return getUser.invoke(userManager, uuid);
        } catch (Exception e) {
            logger.debug("Failed to get VotingPluginUser for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get the vote points for a player.
     *
     * @return The player's vote points, or 0 if unavailable
     */
    public int getVotePoints(Player player) {
        Object user = getUser(player);
        if (user == null) return 0;
        try {
            Method getPoints = user.getClass().getMethod("getPoints");
            Object result = getPoints.invoke(user);
            return result instanceof Number ? ((Number) result).intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Add vote points to a player.
     */
    public void addVotePoints(Player player, int points) {
        Object user = getUser(player);
        if (user == null) return;
        try {
            Method addPoints = user.getClass().getMethod("addPoints", int.class);
            addPoints.invoke(user, points);
        } catch (Exception e) {
            logger.debug("Failed to add vote points for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Remove vote points from a player.
     */
    public void removeVotePoints(Player player, int points) {
        Object user = getUser(player);
        if (user == null) return;
        try {
            Method removePoints = user.getClass().getMethod("removePoints", int.class);
            removePoints.invoke(user, points);
        } catch (Exception e) {
            logger.debug("Failed to remove vote points for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Dynamically register a listener for VotingPlugin's VotingPluginEvent.
     * Uses reflection + registerEvent so no compile-time dependency is needed.
     */
    @SuppressWarnings("unchecked")
    private void registerVoteEventListener() {
        if (!plugin.getConfig().getBoolean("voting.headsEnabled", true)) {
            logger.debug("Vote head rewards disabled in config");
            return;
        }
        try {
            Class<? extends Event> voteEventClass = (Class<? extends Event>)
                Class.forName("com.bencodez.votingplugin.events.VotingPluginEvent");

            org.bukkit.plugin.EventExecutor executor = (listener, event) -> {
                if (voteEventClass.isInstance(event)) {
                    handleVoteEvent(event);
                }
            };

            plugin.getServer().getPluginManager().registerEvent(
                voteEventClass, this, EventPriority.NORMAL, executor, plugin
            );
            logger.debug("Registered VotingPlugin vote event listener");
        } catch (ClassNotFoundException e) {
            logger.debug("VotingPluginEvent class not found — vote head rewards disabled");
        } catch (Exception e) {
            logger.warning("Failed to register vote event listener: " + e.getMessage());
        }
    }

    /**
     * Award a random approved HEAD lore entry to the voter.
     */
    private void handleVoteEvent(Event event) {
        if (!plugin.getConfig().getBoolean("voting.headsEnabled", true)) return;

        try {
            // Extract the player name from the event via reflection
            Method getPlayerNameMethod = event.getClass().getMethod("getPlayerName");
            String playerName = (String) getPlayerNameMethod.invoke(event);
            if (playerName == null) return;

            Player player = plugin.getServer().getPlayer(playerName);
            if (player == null || !player.isOnline()) return;

            List<LoreEntry> headEntries = plugin.getLoreManager().getAllLoreEntriesSync().stream()
                .filter(e -> e.isApproved() && e.getType() == LoreType.HEAD)
                .collect(Collectors.toList());

            if (headEntries.isEmpty()) {
                logger.debug("No approved HEAD entries for vote reward");
                return;
            }

            LoreEntry chosen = headEntries.get(random.nextInt(headEntries.size()));

            // Build item and stamp PDC entry ID
            CommonHeadHandler handler = new CommonHeadHandler(plugin);
            ItemStack item = handler.createLoreItem(chosen);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                NamespacedKey key = new NamespacedKey(plugin, "lore_entry_id");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, chosen.getId());
                item.setItemMeta(meta);
            }

            player.getInventory().addItem(item);

            // Record in player_collection_items
            CollectionManager cmgr = plugin.getLoreManager().getItemManager().getCollectionManager();
            try {
                UUID entryUuid = UUID.fromString(chosen.getId());
                cmgr.recordEntryCollectedSync(player.getUniqueId(), "vote_rewards", entryUuid);
            } catch (IllegalArgumentException ignored) {
                // Entry ID not a valid UUID — skip recording
            }

            player.sendMessage("§aVote reward: §e" + chosen.getName());
            logger.debug("Vote reward HEAD '" + chosen.getName() + "' given to " + player.getName());
        } catch (Exception e) {
            logger.warning("Error handling vote event: " + e.getMessage());
        }
    }

    public void cleanup() {
        hooks = null;
        userManager = null;
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

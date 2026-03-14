package org.fourz.RVNKLore.integration.votingplugin;

import com.bencodez.votingplugin.VotingPluginHooks;
import com.bencodez.votingplugin.user.VotingPluginUser;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Optional;
import java.util.UUID;

/**
 * Manages VotingPlugin API lifecycle for vote reward integration.
 * Listens for VotingPlugin enable/disable events to safely initialize/cleanup.
 */
public class VotingPluginIntegration implements Listener {

    private final RVNKLore plugin;
    private final LogManager logger;
    private VotingPluginHooks hooks;
    private boolean enabled = false;

    public VotingPluginIntegration(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "VotingPluginIntegration");
    }

    /**
     * Attempt to activate VotingPlugin integration.
     * Called during plugin enable or when VotingPlugin is detected.
     *
     * @return true if integration was activated successfully
     */
    public boolean activate() {
        org.bukkit.plugin.Plugin vpPlugin = plugin.getServer().getPluginManager().getPlugin("VotingPlugin");
        if (vpPlugin == null || !vpPlugin.isEnabled()) {
            return false;
        }

        try {
            hooks = VotingPluginHooks.getInstance();
            if (hooks == null) {
                logger.warning("VotingPluginHooks instance is null");
                return false;
            }

            enabled = true;
            logger.debug("VotingPlugin integration activated");
            return true;
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

    /**
     * Get a VotingPluginUser wrapper for the given player.
     *
     * @param player The player to look up
     * @return Optional containing the VotingPluginUser, or empty if unavailable
     */
    public Optional<VotingPluginUser> getUser(Player player) {
        if (!enabled || hooks == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(hooks.getUserManager().getVotingPluginUser(player));
        } catch (Exception e) {
            logger.debug("Failed to get VotingPluginUser for " + player.getName() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get a VotingPluginUser wrapper for the given UUID.
     *
     * @param uuid The player UUID to look up
     * @return Optional containing the VotingPluginUser, or empty if unavailable
     */
    public Optional<VotingPluginUser> getUser(UUID uuid) {
        if (!enabled || hooks == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(hooks.getUserManager().getVotingPluginUser(uuid));
        } catch (Exception e) {
            logger.debug("Failed to get VotingPluginUser for " + uuid + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public void cleanup() {
        hooks = null;
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public VotingPluginHooks getHooks() {
        return hooks;
    }
}

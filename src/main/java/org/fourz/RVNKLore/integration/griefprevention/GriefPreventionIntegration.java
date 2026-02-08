package org.fourz.RVNKLore.integration.griefprevention;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.Optional;
import java.util.UUID;

/**
 * Manages GriefPrevention API lifecycle for claim integration.
 * Listens for GriefPrevention enable/disable events to safely initialize/cleanup.
 */
public class GriefPreventionIntegration implements Listener {

    private final RVNKLore plugin;
    private final LogManager logger;
    private GriefPrevention gpInstance;
    private DataStore dataStore;
    private boolean enabled = false;

    public GriefPreventionIntegration(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "GriefPreventionIntegration");
    }

    /**
     * Attempt to activate GriefPrevention integration.
     * Called during plugin enable or when GriefPrevention is detected.
     *
     * @return true if integration was activated successfully
     */
    public boolean activate() {
        org.bukkit.plugin.Plugin gpPlugin = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
        if (gpPlugin == null || !gpPlugin.isEnabled()) {
            return false;
        }

        try {
            gpInstance = GriefPrevention.instance;
            if (gpInstance == null) {
                logger.warning("GriefPrevention instance is null");
                return false;
            }

            dataStore = gpInstance.dataStore;
            if (dataStore == null) {
                logger.warning("GriefPrevention dataStore is null");
                cleanup();
                return false;
            }

            enabled = true;
            logger.info("GriefPrevention integration enabled");
            return true;
        } catch (Exception e) {
            logger.warning("Failed to initialize GriefPrevention integration: " + e.getMessage());
            cleanup();
            return false;
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if ("GriefPrevention".equalsIgnoreCase(event.getPlugin().getName()) && !enabled) {
            logger.info("GriefPrevention loaded - attempting late integration");
            activate();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if ("GriefPrevention".equalsIgnoreCase(event.getPlugin().getName()) && enabled) {
            logger.info("GriefPrevention unloaded - cleaning up integration");
            cleanup();
        }
    }

    /**
     * Get the claim at a specific location.
     *
     * @param location The location to check
     * @return Optional containing the Claim, or empty if no claim or unavailable
     */
    public Optional<Claim> getClaimAt(Location location) {
        if (!enabled || dataStore == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(dataStore.getClaimAt(location, true, null));
        } catch (Exception e) {
            logger.debug("Failed to get claim at " + location + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get the owner UUID of the claim at a specific location.
     *
     * @param location The location to check
     * @return Optional containing the owner UUID, or empty if no claim or unavailable
     */
    public Optional<UUID> getClaimOwner(Location location) {
        return getClaimAt(location).map(Claim::getOwnerID);
    }

    /**
     * Check if a location is inside a claim.
     *
     * @param location The location to check
     * @return true if the location is within a claim
     */
    public boolean isInClaim(Location location) {
        return getClaimAt(location).isPresent();
    }

    /**
     * Get the total number of claims on the server.
     * Useful for diagnostics and status reporting.
     *
     * @return The number of claims, or 0 if unavailable
     */
    public int getClaimCount() {
        if (!enabled || dataStore == null) {
            return 0;
        }
        try {
            return dataStore.getClaims().size();
        } catch (Exception e) {
            logger.debug("Failed to get claim count: " + e.getMessage());
            return 0;
        }
    }

    public void cleanup() {
        gpInstance = null;
        dataStore = null;
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public GriefPrevention getGpInstance() {
        return gpInstance;
    }

    public DataStore getDataStore() {
        return dataStore;
    }
}

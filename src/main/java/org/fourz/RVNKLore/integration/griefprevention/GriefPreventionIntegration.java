package org.fourz.RVNKLore.integration.griefprevention;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.discovery.DiscoveryManager;
import org.fourz.RVNKLore.discovery.DiscoveryTriggerType;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreManager;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages GriefPrevention API lifecycle for claim integration.
 * Listens for GriefPrevention enable/disable events to safely initialize/cleanup.
 * Also implements claim-enter discovery via PlayerMoveEvent boundary detection.
 */
public class GriefPreventionIntegration implements Listener {

    private final RVNKLore plugin;
    private final LogManager logger;
    private GriefPrevention gpInstance;
    private DataStore dataStore;
    private boolean enabled = false;

    // Track previous claim for each player to detect claim transitions
    private final Map<UUID, Long> playerPreviousClaimIds = new ConcurrentHashMap<>();

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
            logger.debug("GriefPrevention integration activated");
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
     * Listen for player movement to detect claim entries and trigger CLAIM_ENTER discovery.
     * Compares the claim at the new location with the claim at the previous location.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled || dataStore == null) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        try {
            Location to = event.getTo();
            Location from = event.getFrom();

            // Only check on block changes, not sub-block movement
            if (to == null || (to.getBlockX() == from.getBlockX() &&
                               to.getBlockY() == from.getBlockY() &&
                               to.getBlockZ() == from.getBlockZ())) {
                return;
            }

            Claim previousClaim = getClaimAt(from).orElse(null);
            Claim currentClaim = getClaimAt(to).orElse(null);

            Long previousClaimId = previousClaim != null ? previousClaim.getID() : null;
            Long currentClaimId = currentClaim != null ? currentClaim.getID() : null;

            // If claim changed, check for discovery
            if ((previousClaimId == null && currentClaimId != null) ||
                (previousClaimId != null && !previousClaimId.equals(currentClaimId))) {
                // Player entered a new claim (or left a claim)
                if (currentClaimId != null) {
                    triggerClaimEnterDiscovery(player, currentClaim);
                }
            }
        } catch (Exception e) {
            logger.debug("Error checking claim entry for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Trigger discovery for a player entering a claim that has associated FACTION lore.
     */
    private void triggerClaimEnterDiscovery(Player player, Claim claim) {
        LoreManager loreManager = plugin.getLoreManager();
        DiscoveryManager discoveryManager = plugin.getDiscoveryManager();

        if (loreManager == null || discoveryManager == null) {
            return;
        }

        try {
            // Get all FACTION lore entries and check which one matches this claim
            java.util.List<LoreEntry> allEntries = loreManager.getAllLoreEntriesSync();

            for (LoreEntry entry : allEntries) {
                // Only match FACTION types (only faction entries can have claim_id)
                if (entry.getType() != LoreType.FACTION) {
                    continue;
                }

                // Check if entry has claim_id metadata matching current claim
                if (entry.hasMetadata("claim_id")) {
                    try {
                        long entryClaimId = Long.parseLong(entry.getMetadata("claim_id"));
                        if (entryClaimId == claim.getID()) {
                            // Match found — trigger discovery
                            discoveryManager.triggerDiscovery(
                                player,
                                entry,
                                DiscoveryTriggerType.CLAIM_ENTER,
                                player.getLocation()
                            );
                            return; // Only trigger once per claim
                        }
                    } catch (NumberFormatException e) {
                        logger.debug("Invalid claim_id for entry " + entry.getId() + ": " + entry.getMetadata("claim_id"));
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error triggering claim enter discovery: " + e.getMessage());
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

    /**
     * Check if a player owns or manages a claim.
     *
     * @param player The player to check
     * @param claim The claim to check against
     * @return true if the player is the owner or a manager of the claim
     */
    public boolean ownsOrManagesClaim(Player player, Claim claim) {
        if (claim == null || player == null) {
            return false;
        }
        try {
            UUID ownerUUID = claim.getOwnerID();
            if (ownerUUID != null && ownerUUID.equals(player.getUniqueId())) {
                return true;
            }
            // Check manager list
            ArrayList<String> managers = new ArrayList<>();
            claim.getPermissions(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), managers);
            return managers.contains(player.getUniqueId().toString());
        } catch (Exception e) {
            logger.debug("Failed to check claim ownership: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get a claim by its ID.
     *
     * @param claimId The numeric ID of the claim
     * @return Optional containing the Claim, or empty if not found or unavailable
     */
    public Optional<Claim> getClaimById(long claimId) {
        if (!enabled || dataStore == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(dataStore.getClaim(claimId));
        } catch (Exception e) {
            logger.debug("Failed to get claim #" + claimId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public void cleanup() {
        gpInstance = null;
        dataStore = null;
        playerPreviousClaimIds.clear();
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

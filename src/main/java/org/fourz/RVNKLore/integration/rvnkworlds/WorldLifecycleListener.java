package org.fourz.RVNKLore.integration.rvnkworlds;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.EventHandler;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.repository.LocationRepository;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.List;

/**
 * Listens to RVNKWorlds custom world lifecycle events via reflection.
 * Soft dependency -- compiles and runs without RVNKWorlds present.
 *
 * <p>Events handled:</p>
 * <ul>
 *   <li>WorldPostCreateEvent  - log new world for lore awareness</li>
 *   <li>WorldPreDeleteEvent   - warn about lore entries in doomed world</li>
 *   <li>WorldPostDeleteEvent  - log deletion for audit trail</li>
 * </ul>
 */
public class WorldLifecycleListener implements Listener {

    private final RVNKLore plugin;
    private final LogManager logger;
    private boolean enabled = false;

    public WorldLifecycleListener(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "WorldLifecycleListener");
    }

    /**
     * Attempt to activate RVNKWorlds event integration.
     * Uses reflection to load event classes and register handlers dynamically.
     *
     * @return true if integration was activated
     */
    @SuppressWarnings("unchecked")
    public boolean activate() {
        org.bukkit.plugin.Plugin rvnkWorlds = plugin.getServer().getPluginManager().getPlugin("RVNKWorlds");
        if (rvnkWorlds == null || !rvnkWorlds.isEnabled()) {
            return false;
        }

        try {
            Class<? extends Event> postCreateClass =
                    (Class<? extends Event>) Class.forName("org.fourz.RVNKWorlds.event.world.WorldPostCreateEvent");
            Class<? extends Event> preDeleteClass =
                    (Class<? extends Event>) Class.forName("org.fourz.RVNKWorlds.event.world.WorldPreDeleteEvent");
            Class<? extends Event> postDeleteClass =
                    (Class<? extends Event>) Class.forName("org.fourz.RVNKWorlds.event.world.WorldPostDeleteEvent");

            var pm = plugin.getServer().getPluginManager();

            pm.registerEvent(postCreateClass, this, EventPriority.NORMAL,
                    (listener, event) -> handleWorldPostCreate(event), plugin);

            pm.registerEvent(preDeleteClass, this, EventPriority.MONITOR,
                    (listener, event) -> handleWorldPreDelete(event), plugin);

            pm.registerEvent(postDeleteClass, this, EventPriority.NORMAL,
                    (listener, event) -> handleWorldPostDelete(event), plugin);

            enabled = true;
            logger.info("RVNKWorlds event integration enabled");
            return true;
        } catch (ClassNotFoundException e) {
            logger.debug("RVNKWorlds event classes not found - world lifecycle integration disabled");
            return false;
        } catch (Exception e) {
            logger.warning("Failed to register RVNKWorlds event listeners: " + e.getMessage());
            return false;
        }
    }

    // -- Bukkit listeners for late plugin load/unload --

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if ("RVNKWorlds".equalsIgnoreCase(event.getPlugin().getName()) && !enabled) {
            logger.info("RVNKWorlds loaded - attempting late integration");
            activate();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if ("RVNKWorlds".equalsIgnoreCase(event.getPlugin().getName()) && enabled) {
            logger.info("RVNKWorlds unloaded - cleaning up integration");
            cleanup();
        }
    }

    // -- Event handlers (reflection-safe, no direct imports of RVNKWorlds classes) --

    private void handleWorldPostCreate(Event event) {
        try {
            String worldName = (String) event.getClass().getMethod("getWorldName").invoke(event);
            logger.info("New world created: " + worldName + " - now eligible for lore entries");

            // Notify online ops
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("rvnklore.admin"))
                    .forEach(p -> p.sendMessage(
                            "\u00a7e[RVNKLore] \u00a77New world '\u00a7f" + worldName +
                            "\u00a77' is now available for lore entries."));
        } catch (Exception e) {
            logger.warning("Failed to handle WorldPostCreate: " + e.getMessage());
        }
    }

    private void handleWorldPreDelete(Event event) {
        try {
            // MONITOR priority -- only log, never cancel
            boolean cancelled = (boolean) event.getClass().getMethod("isCancelled").invoke(event);
            if (cancelled) return;

            String worldName = (String) event.getClass().getMethod("getWorldName").invoke(event);

            // Count lore locations in the doomed world (async, but join since we need the count now)
            LocationRepository locationRepo = plugin.getDatabaseManager().getLocationRepository();
            int locationCount = locationRepo.countByWorld(worldName).join();

            // Also count cached entries that reference this world
            List<LoreEntry> worldEntries = plugin.getLoreManager().getLoreFinder().findLoreInWorld(worldName);

            if (locationCount > 0 || !worldEntries.isEmpty()) {
                int entryCount = Math.max(locationCount, worldEntries.size());
                logger.warning("World '" + worldName + "' is being deleted with " +
                        entryCount + " lore location(s) - entries will lose spatial data");

                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("rvnklore.admin"))
                        .forEach(p -> p.sendMessage(
                                "\u00a7c[RVNKLore] \u00a77World '\u00a7f" + worldName +
                                "\u00a77' being deleted contains \u00a7c" + entryCount +
                                "\u00a77 lore location(s)."));
            } else {
                logger.info("World '" + worldName + "' pending deletion - no lore locations affected");
            }
        } catch (Exception e) {
            logger.warning("Failed to handle WorldPreDelete: " + e.getMessage());
        }
    }

    private void handleWorldPostDelete(Event event) {
        try {
            String worldName = (String) event.getClass().getMethod("getWorldName").invoke(event);
            boolean filesDeleted = (boolean) event.getClass().getMethod("wereFilesDeleted").invoke(event);
            logger.info("World deleted: " + worldName + " (files removed: " + filesDeleted + ")");

            // Note: lore entries themselves are preserved (they have value as historical records).
            // Only the location references become orphaned -- the world column still stores
            // the name for archival purposes. A future cleanup command could purge these.
        } catch (Exception e) {
            logger.warning("Failed to handle WorldPostDelete: " + e.getMessage());
        }
    }

    public void cleanup() {
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

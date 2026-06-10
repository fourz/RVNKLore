package org.fourz.RVNKLore.lore.post;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LorePostProcessor;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Creates or updates a Dynmap marker for the lore entry when Dynmap is available.
 * Failures are logged at debug level and never trigger a rollback.
 */
public class DynmapPostProcessor implements LorePostProcessor {

    private final RVNKLore plugin;
    private final LogManager logger;

    public DynmapPostProcessor(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DynmapPostProcessor");
    }

    @Override
    public boolean appliesTo(LoreEntry entry) {
        return plugin.isDynmapAvailable();
    }

    @Override
    public boolean process(LoreEntry entry) {
        try {
            plugin.getDynmapIntegration().getMarkerManager().createOrUpdateMarker(entry);
        } catch (Exception e) {
            logger.debug("Failed to create Dynmap marker: " + e.getMessage());
        }
        return true;
    }
}

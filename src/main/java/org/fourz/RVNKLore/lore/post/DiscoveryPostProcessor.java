package org.fourz.RVNKLore.lore.post;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LorePostProcessor;

/**
 * Refreshes the proximity discovery cache when a location-bearing entry is added,
 * so the new entry is discoverable immediately without a server restart.
 */
public class DiscoveryPostProcessor implements LorePostProcessor {

    private final RVNKLore plugin;

    public DiscoveryPostProcessor(RVNKLore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean appliesTo(LoreEntry entry) {
        return entry.getLocation() != null && plugin.getDiscoveryManager() != null;
    }

    @Override
    public boolean process(LoreEntry entry) {
        plugin.getDiscoveryManager().refreshLocationCache();
        return true;
    }
}

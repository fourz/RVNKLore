package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreType;

/**
 * Handler for tavern lore entries.
 * Taverns are inns, pubs, and social gathering places.
 */
public class TavernLoreHandler extends AbstractLocationLoreHandler {

    public TavernLoreHandler(RVNKLore plugin) {
        super(plugin, Material.BARREL, ChatColor.DARK_AQUA,
                "Established by:", "Location:", "Tavern", LoreType.TAVERN);
    }
}

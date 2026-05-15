package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreType;

/**
 * Handler for monument lore entries.
 * Monuments are memorial markers, statues, or commemorative structures.
 */
public class MonumentLoreHandler extends AbstractLocationLoreHandler {

    public MonumentLoreHandler(RVNKLore plugin) {
        super(plugin, Material.CHISELED_STONE_BRICKS, ChatColor.GOLD,
                "Erected by:", "Location:", "Monument", LoreType.MONUMENT);
    }
}

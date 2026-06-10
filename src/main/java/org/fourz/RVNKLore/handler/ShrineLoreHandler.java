package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreType;

/**
 * Handler for shrine lore entries.
 * Shrines are temples, altars, and places of worship or mystical significance.
 */
public class ShrineLoreHandler extends AbstractLocationLoreHandler {

    public ShrineLoreHandler(RVNKLore plugin) {
        super(plugin, Material.ENCHANTING_TABLE, ChatColor.DARK_PURPLE,
                "Consecrated by:", "Location:", "Shrine", LoreType.SHRINE);
    }
}

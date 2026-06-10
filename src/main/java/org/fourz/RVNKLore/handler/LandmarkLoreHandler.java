package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreType;

/**
 * Handler for landmark lore entries.
 */
public class LandmarkLoreHandler extends AbstractLocationLoreHandler {

    public LandmarkLoreHandler(RVNKLore plugin) {
        super(plugin, Material.BEACON, ChatColor.LIGHT_PURPLE,
                "Discovered by:", "Location:", "Landmark", LoreType.LANDMARK);
    }
}

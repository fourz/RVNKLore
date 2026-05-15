package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreType;

/**
 * Handler for guild lore entries.
 * Guilds are professional organizations, halls, and player factions.
 */
public class GuildLoreHandler extends AbstractLocationLoreHandler {

    public GuildLoreHandler(RVNKLore plugin) {
        super(plugin, Material.SHIELD, ChatColor.DARK_GREEN,
                "Founded by:", "Location:", "Guild", LoreType.GUILD);
    }
}

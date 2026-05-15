package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for path/road lore entries.
 *
 * <p>Extends {@link AbstractLocationLoreHandler} and overrides:
 * <ul>
 *   <li>{@link #validateEntry} — uses simple per-field debug logging rather than the
 *       validation-errors metadata approach used by other location types.</li>
 *   <li>{@link #createLoreItem} / {@link #displayLore} — adds an optional
 *       {@code destination} metadata line and uses {@code "Starting Point:"} as the
 *       location label.</li>
 * </ul>
 */
public class PathLoreHandler extends AbstractLocationLoreHandler {

    public PathLoreHandler(RVNKLore plugin) {
        super(plugin, Material.MAP, ChatColor.GOLD,
                "Paved by:", "Starting Point:", "Path/Road", LoreType.PATH);
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        if (entry.getName() == null || entry.getName().isEmpty()) {
            logger.debug("Path lore validation failed: Name is required");
            return false;
        }

        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            logger.debug("Path lore validation failed: Description is required");
            return false;
        }

        if (entry.getLocation() == null) {
            logger.debug("Path lore validation failed: Starting location is required");
            return false;
        }

        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + entry.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.GOLD + "Path/Road");

            if (entry.getSubmittedBy() != null) {
                lore.add(ChatColor.GRAY + "Paved by: " + ChatColor.YELLOW + entry.getSubmittedBy());
            }

            if (entry.getMetadata("destination") != null) {
                lore.add(ChatColor.GRAY + "Destination: " + ChatColor.WHITE + entry.getMetadata("destination"));
            }

            lore.add("");
            appendDescriptionLines(lore, entry);

            if (entry.getLocation() != null) {
                lore.add("");
                lore.add(formatLocationLine(entry, "Starting Point:"));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    @Override
    public void displayLore(LoreEntry entry, Player player) {
        player.sendMessage(ChatColor.GOLD + "==== " + entry.getName() + " ====");
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.GOLD + "Path/Road");

        if (entry.getSubmittedBy() != null) {
            player.sendMessage(ChatColor.GRAY + "Paved by: " + ChatColor.YELLOW + entry.getSubmittedBy());
        }

        if (entry.getMetadata("destination") != null) {
            player.sendMessage(ChatColor.GRAY + "Destination: " + ChatColor.WHITE + entry.getMetadata("destination"));
        }

        player.sendMessage("");

        for (String descLine : getDescriptionLines(entry)) {
            player.sendMessage(descLine);
        }

        if (entry.getLocation() != null) {
            player.sendMessage("");
            player.sendMessage(formatLocationLine(entry, "Starting Point:"));
        }
    }
}

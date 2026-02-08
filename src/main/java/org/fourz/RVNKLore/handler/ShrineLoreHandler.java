package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for shrine lore entries.
 * Shrines are temples, altars, and places of worship or mystical significance.
 */
public class ShrineLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final LogManager logger;

    public ShrineLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ShrineLoreHandler");
    }

    @Override
    public void initialize() {
        logger.info("Initializing shrine lore handler");
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        List<String> validationErrors = new ArrayList<>();

        if (entry.getName() == null || entry.getName().isEmpty()) {
            validationErrors.add("Name is required");
        }

        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            validationErrors.add("Description is required");
        } else if (entry.getDescription().length() < 10) {
            validationErrors.add("Description too short");
        }

        if (entry.getLocation() == null) {
            validationErrors.add("Location is required");
        }

        if (!validationErrors.isEmpty()) {
            logger.warning("Shrine validation failed: " + String.join(", ", validationErrors));
            return false;
        }

        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_PURPLE + entry.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.DARK_PURPLE + "Shrine");

            if (entry.getSubmittedBy() != null) {
                lore.add(ChatColor.GRAY + "Consecrated by: " + ChatColor.YELLOW + entry.getSubmittedBy());
            }

            lore.add("");

            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }

            if (entry.getLocation() != null) {
                lore.add("");
                lore.add(ChatColor.GRAY + "Location: " +
                        ChatColor.WHITE + entry.getLocation().getWorld().getName() + " at " +
                        (int) entry.getLocation().getX() + ", " +
                        (int) entry.getLocation().getY() + ", " +
                        (int) entry.getLocation().getZ());
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    @Override
    public void displayLore(LoreEntry entry, Player player) {
        player.sendMessage(ChatColor.DARK_PURPLE + "==== " + entry.getName() + " ====");
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.DARK_PURPLE + "Shrine");

        if (entry.getSubmittedBy() != null) {
            player.sendMessage(ChatColor.GRAY + "Consecrated by: " + ChatColor.YELLOW + entry.getSubmittedBy());
        }

        player.sendMessage("");

        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            player.sendMessage(ChatColor.WHITE + line);
        }

        if (entry.getLocation() != null) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Location: " +
                    ChatColor.WHITE + entry.getLocation().getWorld().getName() + " at " +
                    (int) entry.getLocation().getX() + ", " +
                    (int) entry.getLocation().getY() + ", " +
                    (int) entry.getLocation().getZ());
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.SHRINE;
    }
}

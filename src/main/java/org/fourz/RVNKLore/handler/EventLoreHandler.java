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
 * Handler for EVENT lore type entries.
 * Events are historical occurrences, battles, festivals, or notable happenings.
 */
public class EventLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final LogManager logger;

    public EventLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "EventLoreHandler");
    }

    @Override
    public void initialize() {
        logger.info("Initializing event lore handler");
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
            validationErrors.add("Description too short (min 10 chars)");
        }

        if (!validationErrors.isEmpty()) {
            entry.addMetadata("validation_errors", String.join(";", validationErrors));
            logger.warning("Event validation failed: " + String.join(", ", validationErrors));
            return false;
        }

        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + entry.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.RED + "Event");

            if (entry.getSubmittedBy() != null) {
                lore.add(ChatColor.GRAY + "Chronicled by: " + ChatColor.YELLOW + entry.getSubmittedBy());
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
        player.sendMessage(ChatColor.RED + "==== " + entry.getName() + " ====");
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.RED + "Event");

        if (entry.getSubmittedBy() != null) {
            player.sendMessage(ChatColor.GRAY + "Chronicled by: " + ChatColor.YELLOW + entry.getSubmittedBy());
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
        return LoreType.EVENT;
    }
}

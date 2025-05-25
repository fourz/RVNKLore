package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for path/road lore entries
 */
public class PathLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final LogManager logger;
    
    public PathLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "PathLoreHandler");
    }

    @Override
    public void initialize() {
        logger.debug("Initializing path lore handler");
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
            
            // Add creator if available
            if (entry.getSubmittedBy() != null) {
                lore.add(ChatColor.GRAY + "Paved by: " + ChatColor.YELLOW + entry.getSubmittedBy());
            }
            
            // Get destination from metadata if available
            if (entry.getMetadata("destination") != null) {
                lore.add(ChatColor.GRAY + "Destination: " + ChatColor.WHITE + entry.getMetadata("destination"));
            }
            
            lore.add("");
            
            // Split description into lines for better readability
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }
            
            // Add starting location
            if (entry.getLocation() != null) {
                lore.add("");
                lore.add(ChatColor.GRAY + "Starting Point: " + 
                        ChatColor.WHITE + entry.getLocation().getWorld().getName() + " at " + 
                        (int)entry.getLocation().getX() + ", " + 
                        (int)entry.getLocation().getY() + ", " + 
                        (int)entry.getLocation().getZ());
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
        
        // Add creator if available
        if (entry.getSubmittedBy() != null) {
            player.sendMessage(ChatColor.GRAY + "Paved by: " + ChatColor.YELLOW + entry.getSubmittedBy());
        }
        
        // Get destination from metadata if available
        if (entry.getMetadata("destination") != null) {
            player.sendMessage(ChatColor.GRAY + "Destination: " + ChatColor.WHITE + entry.getMetadata("destination"));
        }
        
        player.sendMessage("");
        
        // Display description
        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            player.sendMessage(ChatColor.WHITE + line);
        }
        
        // Add starting location
        if (entry.getLocation() != null) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Starting Point: " + 
                    ChatColor.WHITE + entry.getLocation().getWorld().getName() + " at " + 
                    (int)entry.getLocation().getX() + ", " + 
                    (int)entry.getLocation().getY() + ", " + 
                    (int)entry.getLocation().getZ());
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.PATH;
    }
}

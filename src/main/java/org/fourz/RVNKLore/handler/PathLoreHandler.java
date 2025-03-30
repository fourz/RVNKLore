package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.util.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Handler for path and route lore entries
 */
public class PathLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final Debug debug;
    
    public PathLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "PathLoreHandler", Level.FINE);
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        if (entry.getName() == null || entry.getName().isEmpty()) {
            debug.debug("Path lore validation failed: Name is required");
            return false;
        }
        
        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            debug.debug("Path lore validation failed: Description is required");
            return false;
        }
        
        if (entry.getLocation() == null) {
            debug.debug("Path lore validation failed: Starting location is required for paths");
            return false;
        }
        
        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.GREEN + "Path/Route");
            
            // Location info
            if (entry.getLocation() != null) {
                lore.add(ChatColor.GRAY + "Starting Point: " + 
                        ChatColor.YELLOW + entry.getLocation().getWorld().getName() + " at " + 
                        (int)entry.getLocation().getX() + ", " + 
                        (int)entry.getLocation().getY() + ", " + 
                        (int)entry.getLocation().getZ());
            }
            
            // Split description into lines for better readability
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }
            
            lore.add("");
            lore.add(ChatColor.GRAY + "Documented by: " + ChatColor.YELLOW + entry.getSubmittedBy());
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    @Override
    public void displayLore(LoreEntry entry, Player player) {
        player.sendMessage(ChatColor.GOLD + "=== " + entry.getName() + " ===");
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.GREEN + "Path/Route");
        
        if (entry.getLocation() != null) {
            player.sendMessage(ChatColor.GRAY + "Starting Point: " + 
                    ChatColor.YELLOW + entry.getLocation().getWorld().getName() + " at " + 
                    (int)entry.getLocation().getX() + ", " + 
                    (int)entry.getLocation().getY() + ", " + 
                    (int)entry.getLocation().getZ());
        }
        
        player.sendMessage("");
        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            player.sendMessage(ChatColor.WHITE + line);
        }
        
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Documented by: " + ChatColor.YELLOW + entry.getSubmittedBy());
    }
}

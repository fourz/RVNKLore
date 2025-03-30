package org.fourz.RVNKLore.lore;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.util.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Handler for landmark lore entries
 */
public class LandmarkLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final Debug debug;
    
    public LandmarkLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "LandmarkLoreHandler", Level.FINE);
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        if (entry.getName() == null || entry.getName().isEmpty()) {
            debug.debug("Landmark lore validation failed: Name is required");
            return false;
        }
        
        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            debug.debug("Landmark lore validation failed: Description is required");
            return false;
        }
        
        if (entry.getLocation() == null) {
            debug.debug("Landmark lore validation failed: Location is required for landmarks");
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
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + "Landmark");
            
            // Location info
            if (entry.getLocation() != null) {
                lore.add(ChatColor.GRAY + "Location: " + 
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
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + "Landmark");
        
        if (entry.getLocation() != null) {
            player.sendMessage(ChatColor.GRAY + "Location: " + 
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

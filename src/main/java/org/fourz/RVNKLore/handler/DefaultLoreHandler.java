package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.util.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Default implementation of LoreHandler for generic lore types
 */
public class DefaultLoreHandler implements LoreHandler {
    protected final RVNKLore plugin;
    protected final Debug debug;
    
    public DefaultLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "DefaultLoreHandler", Level.FINE);
    }
    
    @Override
    public void initialize() {
        debug.debug("Initializing default lore handler");
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        // Basic validation common to all lore entries
        if (entry.getName() == null || entry.getName().isEmpty()) {
            debug.debug("Lore validation failed: Name is required");
            return false;
        }
        
        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            debug.debug("Lore validation failed: Description is required");
            return false;
        }
        
        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        // Default representation is a book
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + entry.getType().toString());
            
            if (entry.getSubmittedBy() != null) {
                lore.add(ChatColor.GRAY + "Documented by: " + ChatColor.WHITE + entry.getSubmittedBy());
            }
            
            lore.add("");
            
            // Split description into lines for better readability
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }
            
            if (entry.getLocation() != null) {
                lore.add("");
                lore.add(ChatColor.GRAY + "Location: " + 
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
        player.sendMessage(ChatColor.YELLOW + "==== " + entry.getName() + " ====");
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + entry.getType().toString());
        
        if (entry.getSubmittedBy() != null) {
            player.sendMessage(ChatColor.GRAY + "Documented by: " + ChatColor.WHITE + entry.getSubmittedBy());
        }
        
        player.sendMessage("");
        
        // Display description
        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            player.sendMessage(ChatColor.WHITE + line);
        }
        
        if (entry.getLocation() != null) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Location: " + 
                    ChatColor.WHITE + entry.getLocation().getWorld().getName() + " at " + 
                    (int)entry.getLocation().getX() + ", " + 
                    (int)entry.getLocation().getY() + ", " + 
                    (int)entry.getLocation().getZ());
        }
    }
    
    @Override
    public LoreType getHandlerType() {
        return LoreType.GENERIC;
    }

    /**
     * Safely get metadata from entry with error handling
     */
    protected String getMetadataSafe(LoreEntry entry, String key) {
        try {
            if (entry == null) {
                debug.warning("Attempted to get metadata from null entry: " + key);
                return null;
            }
            
            return entry.getMetadata(key);
        } catch (Exception e) {
            debug.error("Error retrieving metadata " + key, e);
            return null;
        }
    }

    /**
     * Safely parse a long from metadata with error handling
     */
    protected Long getMetadataLong(LoreEntry entry, String key) {
        try {
            String value = getMetadataSafe(entry, key);
            if (value == null || value.isEmpty()) {
                return null;
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            debug.debug("Failed to parse " + key + " as long: " + getMetadataSafe(entry, key));
            return null;
        } catch (Exception e) {
            debug.error("Error parsing " + key + " as long", e);
            return null;
        }
    }
}

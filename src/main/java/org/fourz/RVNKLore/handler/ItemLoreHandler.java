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
 * Handler for special item lore entries
 */
public class ItemLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final Debug debug;
    
    public ItemLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "ItemLoreHandler", Level.FINE);
    }

    @Override
    public void initialize() {
        debug.debug("Initializing item lore handler");
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        if (entry.getName() == null || entry.getName().isEmpty()) {
            debug.debug("Item lore validation failed: Name is required");
            return false;
        }
        
        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            debug.debug("Item lore validation failed: Description is required");
            return false;
        }
        
        // Items should have a material type defined
        if (entry.getMetadata("material") == null || entry.getMetadata("material").isEmpty()) {
            debug.debug("Item lore validation warning: Material not specified");
            // Not a hard failure, but a warning
        }
        
        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        // Try to use the specified material, or default to NETHER_STAR
        Material material = Material.NETHER_STAR;
        if (entry.getMetadata("material") != null) {
            try {
                material = Material.valueOf(entry.getMetadata("material").toUpperCase());
            } catch (IllegalArgumentException e) {
                debug.debug("Invalid material specified for item: " + entry.getName());
            }
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + "Legendary Item");
            
            // Add creator if available
            if (entry.getSubmittedBy() != null) {
                lore.add(ChatColor.GRAY + "Crafted by: " + ChatColor.YELLOW + entry.getSubmittedBy());
            }
            
            lore.add("");
            
            // Split description into lines for better readability
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }
            
            // Add properties if available
            if (entry.getMetadata("properties") != null) {
                lore.add("");
                lore.add(ChatColor.GRAY + "Properties:");
                String[] properties = entry.getMetadata("properties").split(";");
                for (String property : properties) {
                    lore.add(ChatColor.GRAY + "- " + ChatColor.WHITE + property);
                }
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    @Override
    public void displayLore(LoreEntry entry, Player player) {
        player.sendMessage(ChatColor.AQUA + "==== " + entry.getName() + " ====");
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.AQUA + "Legendary Item");
        
        // Add creator if available
        if (entry.getSubmittedBy() != null) {
            player.sendMessage(ChatColor.GRAY + "Crafted by: " + ChatColor.YELLOW + entry.getSubmittedBy());
        }
        
        player.sendMessage("");
        
        // Display description
        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            player.sendMessage(ChatColor.WHITE + line);
        }
        
        // Add properties if available
        if (entry.getMetadata("properties") != null) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Properties:");
            String[] properties = entry.getMetadata("properties").split(";");
            for (String property : properties) {
                player.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + property);
            }
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.ITEM;
    }
}

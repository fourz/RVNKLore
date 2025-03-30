package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.util.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Handler for decorative head lore entries
 */
public class HeadLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final Debug debug;
    
    public HeadLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "HeadLoreHandler", Level.FINE);
    }

    @Override
    public void initialize() {
        debug.debug("Initializing head lore handler");
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        if (entry.getName() == null || entry.getName().isEmpty()) {
            debug.debug("Head lore validation failed: Name is required");
            return false;
        }
        
        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            debug.debug("Head lore validation failed: Description is required");
            return false;
        }
        
        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + entry.getName());
            
            // Set owner if available
            if (entry.getMetadata("head_owner") != null) {
                try {
                    meta.setOwner(entry.getMetadata("head_owner"));
                } catch (Exception e) {
                    debug.debug("Could not set skull owner: " + entry.getMetadata("head_owner"));
                }
            }
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.GOLD + "Decorative Head");
            
            // Add creator if available
            if (entry.getSubmittedBy() != null) {
                lore.add(ChatColor.GRAY + "Created by: " + ChatColor.YELLOW + entry.getSubmittedBy());
            }
            
            lore.add("");
            
            // Split description into lines for better readability
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    @Override
    public void displayLore(LoreEntry entry, Player player) {
        player.sendMessage(ChatColor.GOLD + "==== " + entry.getName() + " ====");
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.GOLD + "Decorative Head");
        
        // Add creator if available
        if (entry.getSubmittedBy() != null) {
            player.sendMessage(ChatColor.GRAY + "Created by: " + ChatColor.YELLOW + entry.getSubmittedBy());
        }
        
        player.sendMessage("");
        
        // Display description
        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            player.sendMessage(ChatColor.WHITE + line);
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.HEAD;
    }
}

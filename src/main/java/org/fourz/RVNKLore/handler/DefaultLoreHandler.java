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
 * Default handler for lore entries that don't have a specialized handler
 */
public class DefaultLoreHandler implements LoreHandler {
    protected final RVNKLore plugin;
    // Debug instance is protected to allow child handlers to use it
    protected final Debug debug;

    public DefaultLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "DefaultLoreHandler", Level.FINE);
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        // Name and description are the minimum requirements for any lore type
        if (entry.getName() == null || entry.getName().trim().isEmpty()) {
            debug.debug("Validation failed: name is empty");
            return false;
        }
        
        if (entry.getDescription() == null || entry.getDescription().trim().isEmpty()) {
            debug.debug("Validation failed: description is empty");
            return false;
        }
        
        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        // Books are used as the default display item since they represent written knowledge
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + entry.getType().name());
            
            // Break description into 40-character chunks for readability
            String desc = entry.getDescription();
            if (desc.length() > 40) {
                for (int i = 0; i < desc.length(); i += 40) {
                    int end = Math.min(i + 40, desc.length());
                    lore.add(ChatColor.WHITE + desc.substring(i, end));
                }
            } else {
                lore.add(ChatColor.WHITE + desc);
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    @Override
    public void displayLore(LoreEntry entry, Player player) {
        player.sendMessage(ChatColor.GOLD + "========== " + entry.getName() + " ==========");
        player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + entry.getType().name());
        player.sendMessage(ChatColor.YELLOW + "Description: " + ChatColor.WHITE + entry.getDescription());
        
        if (entry.getLocation() != null) {
            player.sendMessage(ChatColor.YELLOW + "Location: " + ChatColor.WHITE + 
                String.format("%.1f, %.1f, %.1f in %s", 
                    entry.getLocation().getX(),
                    entry.getLocation().getY(),
                    entry.getLocation().getZ(),
                    entry.getLocation().getWorld().getName()));
        }
        
        if (entry.getSubmittedBy() != null && !entry.getSubmittedBy().isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Submitted by: " + ChatColor.WHITE + entry.getSubmittedBy());
        }
    }
}

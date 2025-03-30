package org.fourz.RVNKLore.lore;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.util.Debug;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Handler for custom player head lore entries
 */
public class HeadLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final Debug debug;
    
    public HeadLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "HeadLoreHandler", Level.FINE);
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        if (entry.getName() == null || entry.getName().isEmpty()) {
            debug.debug("Head lore validation failed: Name is required");
            return false;
        }
        
        if (entry.getNbtData() == null || entry.getNbtData().isEmpty()) {
            debug.debug("Head lore validation failed: NBT data is required for heads");
            return false;
        }
        
        // Check if NBT data is valid JSON
        try {
            JSONParser parser = new JSONParser();
            parser.parse(entry.getNbtData());
        } catch (ParseException e) {
            debug.debug("Head lore validation failed: Invalid NBT JSON data");
            return false;
        }
        
        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + "Custom Head");
            
            // Split description into lines for better readability
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }
            
            lore.add("");
            lore.add(ChatColor.GRAY + "Creator: " + ChatColor.YELLOW + entry.getSubmittedBy());
            
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        
        // Note: In a full implementation, we would apply NBT data to the head
        // using a library like NBTAPI
        
        return head;
    }

    @Override
    public void displayLore(LoreEntry entry, Player player) {
        player.sendMessage(ChatColor.GOLD + "=== " + entry.getName() + " ===");
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + "Custom Head");
        
        // Split description into lines for better readability
        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            player.sendMessage(ChatColor.WHITE + line);
        }
        
        player.sendMessage(ChatColor.GRAY + "Creator: " + ChatColor.YELLOW + entry.getSubmittedBy());
        
        if (entry.getLocation() != null) {
            player.sendMessage(ChatColor.GRAY + "Location: " + 
                    ChatColor.YELLOW + entry.getLocation().getWorld().getName() + " at " + 
                    (int)entry.getLocation().getX() + ", " + 
                    (int)entry.getLocation().getY() + ", " + 
                    (int)entry.getLocation().getZ());
        }
    }
}

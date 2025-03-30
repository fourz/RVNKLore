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
 * Handler for enchantment lore entries
 */
public class EnchantmentLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final Debug debug;
    
    public EnchantmentLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "EnchantmentLoreHandler", Level.FINE);
    }

    @Override
    public void initialize() {
        debug.debug("Initializing enchantment lore handler");
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        if (entry.getName() == null || entry.getName().isEmpty()) {
            debug.debug("Enchantment lore validation failed: Name is required");
            return false;
        }
        
        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            debug.debug("Enchantment lore validation failed: Description is required");
            return false;
        }
        
        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.LIGHT_PURPLE + "Enchantment");
            
            // Add creator if available
            if (entry.getSubmittedBy() != null) {
                lore.add(ChatColor.GRAY + "Discovered by: " + ChatColor.YELLOW + entry.getSubmittedBy());
            }
            
            lore.add("");
            
            // Split description into lines for better readability
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }
            
            // Add item type if available
            if (entry.getMetadata("item_type") != null) {
                lore.add("");
                lore.add(ChatColor.GRAY + "Item Type: " + ChatColor.WHITE + 
                         entry.getMetadata("item_type").replace("_", " ").toLowerCase());
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    @Override
    public void displayLore(LoreEntry entry, Player player) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "==== " + entry.getName() + " ====");
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.LIGHT_PURPLE + "Enchantment");
        
        // Add creator if available
        if (entry.getSubmittedBy() != null) {
            player.sendMessage(ChatColor.GRAY + "Discovered by: " + ChatColor.YELLOW + entry.getSubmittedBy());
        }
        
        player.sendMessage("");
        
        // Display description
        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            player.sendMessage(ChatColor.WHITE + line);
        }
        
        // Add item type if available
        if (entry.getMetadata("item_type") != null) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Item Type: " + ChatColor.WHITE + 
                     entry.getMetadata("item_type").replace("_", " ").toLowerCase());
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.ENCHANTMENT;
    }
}

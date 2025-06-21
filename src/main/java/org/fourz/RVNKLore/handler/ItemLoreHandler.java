package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for special item lore entries
 */
public class ItemLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    
    public ItemLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ItemLoreHandler");
        this.databaseManager = plugin.getDatabaseManager();
    }

    @Override
    public void initialize() {
        logger.info("Initializing item lore handler");
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        if (entry.getName() == null || entry.getName().isEmpty()) {
            logger.warning("Item lore validation failed: Name is required");
            return false;
        }
        
        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            entry.setDescription("");   
        }
        
        if (entry.getMetadata("material") == null || entry.getMetadata("material").isEmpty()) {
            logger.warning("Item lore validation warning: Material not specified");
        }
        
        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        Material material = Material.NETHER_STAR;
        if (entry.getMetadata("material") != null) {
            try {
                material = Material.valueOf(entry.getMetadata("material").toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid material specified for item: " + entry.getName());
            }
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + "Legendary Item");
            
            if (entry.getSubmittedBy() != null) {
                lore.add(ChatColor.GRAY + "Crafted by: " + ChatColor.YELLOW + entry.getSubmittedBy());
            }
            
            lore.add("");
            
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }
            
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
        
        if (entry.getSubmittedBy() != null) {
            player.sendMessage(ChatColor.GRAY + "Crafted by: " + ChatColor.YELLOW + entry.getSubmittedBy());
        }
        
        player.sendMessage("");
        
        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            player.sendMessage(ChatColor.WHITE + line);
        }
        
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

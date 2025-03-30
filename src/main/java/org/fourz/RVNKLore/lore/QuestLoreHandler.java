package org.fourz.RVNKLore.lore;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.handler.LoreHandler;
import org.fourz.RVNKLore.util.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Handler for quest-related lore entries
 */
public class QuestLoreHandler extends DefaultLoreHandler implements LoreHandler {
    
    public QuestLoreHandler(RVNKLore plugin) {
        // Inherit debug setup from DefaultLoreHandler
        super(plugin);
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        // Quest entries must pass base validation and be of QUEST type
        if (!super.validateEntry(entry)) {
            return false;
        }
        
        if (entry.getType() != LoreType.QUEST) {
            debug.debug("Entry type mismatch: expected QUEST, got " + entry.getType());
            return false;
        }
        
        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        // Maps represent quests better than books visually
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // Quest titles are gold to stand out
            meta.setDisplayName(ChatColor.GOLD + "Quest: " + entry.getName());
            
            // Break description into 30-character chunks for readability on maps
            List<String> lore = new ArrayList<>();
            String desc = entry.getDescription();
            if (desc.length() > 30) {
                for (int i = 0; i < desc.length(); i += 30) {
                    int end = Math.min(i + 30, desc.length());
                    lore.add(ChatColor.WHITE + desc.substring(i, end));
                }
            } else {
                lore.add(ChatColor.WHITE + desc);
            }
            
            lore.add(ChatColor.GRAY + "ID: " + entry.getId());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    @Override
    public void displayLore(LoreEntry entry, Player player) {
        player.sendMessage(ChatColor.GOLD + "========== Quest: " + entry.getName() + " ==========");
        player.sendMessage(ChatColor.YELLOW + "Description: ");
        
        // Format description nicely
        String desc = entry.getDescription();
        if (desc.length() > 50) {
            for (int i = 0; i < desc.length(); i += 50) {
                int end = Math.min(i + 50, desc.length());
                player.sendMessage(ChatColor.WHITE + desc.substring(i, end));
            }
        } else {
            player.sendMessage(ChatColor.WHITE + desc);
        }
        
        if (entry.getLocation() != null) {
            player.sendMessage(ChatColor.YELLOW + "Location: " + ChatColor.WHITE + 
                String.format("%.1f, %.1f, %.1f in %s", 
                    entry.getLocation().getX(),
                    entry.getLocation().getY(),
                    entry.getLocation().getZ(),
                    entry.getLocation().getWorld().getName()));
        }
        
        if (entry.getSubmittedBy() != null && !entry.getSubmittedBy().isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Added by: " + ChatColor.WHITE + entry.getSubmittedBy());
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.QUEST;
    }
}

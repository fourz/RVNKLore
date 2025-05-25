package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

/**
 * Handler for faction/group lore entries
 */
public class FactionLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final LogManager logger;
    
    public FactionLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "FactionLoreHandler");
    }

    @Override
    public void initialize() {
        logger.debug("Initializing faction lore handler");
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        List<String> validationErrors = new ArrayList<>();
        
        if (entry.getName() == null || entry.getName().isEmpty()) {
            validationErrors.add("Name is required");
        } else if (entry.getName().length() < 3 || entry.getName().length() > 32) {
            validationErrors.add("Faction name must be between 3-32 characters");
        }
        
        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            validationErrors.add("Description is required");
        } else if (entry.getDescription().length() < 20) {
            validationErrors.add("Description too short (min 20 characters)");
        }
        
        // Factions should list at least one member
        if (entry.getMetadata("members") == null || entry.getMetadata("members").isEmpty()) {
            validationErrors.add("At least one member must be specified");
        }
        
        if (!validationErrors.isEmpty()) {
            logger.debug("Faction validation failed: " + String.join(", ", validationErrors));
            return false;
        }
        
        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.SHIELD);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.RED + "Faction");
            
            // Format founding date if available
            if (entry.getMetadata("founding_date") != null) {
                try {
                    long foundingTimestamp = Long.parseLong(entry.getMetadata("founding_date"));
                    Date foundingDate = new Date(foundingTimestamp);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    lore.add(ChatColor.GRAY + "Founded: " + ChatColor.WHITE + sdf.format(foundingDate));
                } catch (NumberFormatException e) {
                    logger.debug("Could not parse founding date for faction: " + entry.getName());
                }
            }
            
            // Add founder if available
            if (entry.getSubmittedBy() != null) {
                lore.add(ChatColor.GRAY + "Leader: " + ChatColor.YELLOW + entry.getSubmittedBy());
            }
            
            // Add members if available
            if (entry.getMetadata("members") != null) {
                lore.add(ChatColor.GRAY + "Members: " + ChatColor.WHITE + entry.getMetadata("members"));
            }
            
            lore.add("");
            
            // Split description into lines
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }
            
            // Add headquarters location if available
            if (entry.getLocation() != null) {
                lore.add("");
                lore.add(ChatColor.GRAY + "Headquarters: " + 
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
        player.sendMessage(ChatColor.RED + "==== " + entry.getName() + " ====");
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.RED + "Faction");
        
        // Format founding date if available
        if (entry.getMetadata("founding_date") != null) {
            try {
                long foundingTimestamp = Long.parseLong(entry.getMetadata("founding_date"));
                Date foundingDate = new Date(foundingTimestamp);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                player.sendMessage(ChatColor.GRAY + "Founded: " + ChatColor.WHITE + sdf.format(foundingDate));
            } catch (NumberFormatException e) {
                logger.debug("Could not parse founding date");
            }
        }
        
        // Add founder if available
        if (entry.getSubmittedBy() != null) {
            player.sendMessage(ChatColor.GRAY + "Leader: " + ChatColor.YELLOW + entry.getSubmittedBy());
        }
        
        // Add members if available
        if (entry.getMetadata("members") != null) {
            player.sendMessage(ChatColor.GRAY + "Members: " + ChatColor.WHITE + entry.getMetadata("members"));
        }
        
        player.sendMessage("");
        
        // Display description
        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            player.sendMessage(ChatColor.WHITE + line);
        }
        
        // Add headquarters location if available
        if (entry.getLocation() != null) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Headquarters: " + 
                    ChatColor.WHITE + entry.getLocation().getWorld().getName() + " at " + 
                    (int)entry.getLocation().getX() + ", " + 
                    (int)entry.getLocation().getY() + ", " + 
                    (int)entry.getLocation().getZ());
        }
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.FACTION;
    }
}

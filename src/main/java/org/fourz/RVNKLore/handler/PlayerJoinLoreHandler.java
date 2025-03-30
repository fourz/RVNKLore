package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.util.Debug;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

/**
 * Handler for player join events to create lore entries
 */
public class PlayerJoinLoreHandler extends DefaultLoreHandler {
    
    public PlayerJoinLoreHandler(RVNKLore plugin) {
        super(plugin);
    }
    
    @Override
    public void initialize() {
        debug.debug("Initializing player join lore handler");
    }

    /**
     * Handle player join events
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Only create entry for first-time joins
        if (!player.hasPlayedBefore()) {
            createNewPlayerJoinLoreEntry(player);
        }
    }
    
    /**
     * Create a lore entry for a new player
     */
    private void createNewPlayerJoinLoreEntry(Player player) {
        debug.debug("Creating join lore entry for new player: " + player.getName());
        
        LoreEntry entry = new LoreEntry();
        entry.setType(LoreType.PLAYER);
        entry.setName(player.getName() + "'s First Arrival");
        
        // Format date using SimpleDateFormat for consistent display
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String dateString = dateFormat.format(new Date());
        
        entry.setDescription(player.getName() + " first set foot in our world on " + dateString + ".");
        entry.setLocation(player.getLocation());
        entry.setSubmittedBy("Server");
        
        // Add essential metadata
        entry.addMetadata("player_uuid", player.getUniqueId().toString());
        entry.addMetadata("first_join_date", System.currentTimeMillis() + "");
        
        // Location coordinates as metadata
        entry.addMetadata("join_location", String.format("%s,%d,%d,%d", 
            player.getLocation().getWorld().getName(),
            (int)player.getLocation().getX(),
            (int)player.getLocation().getY(),
            (int)player.getLocation().getZ()));
        
        // Auto-approve server-generated entries
        entry.setApproved(true);
        plugin.getLoreManager().addLoreEntry(entry);
        
        // Notify the player
        player.sendMessage(ChatColor.GOLD + "Your arrival has been recorded in the annals of history!");
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + "First Arrival");
            
            // Get player name from entry name
            String playerName = entry.getName().replace("'s First Arrival", "");
            lore.add(ChatColor.GRAY + "Player: " + ChatColor.WHITE + playerName);
            
            // Format join date if available
            String joinDate = formatJoinDate(entry);
            if (joinDate != null) {
                lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + joinDate);
            }
            
            // Add description
            lore.add("");
            lore.add(ChatColor.WHITE + entry.getDescription());
            
            // Add location if available
            if (entry.getLocation() != null) {
                lore.add("");
                lore.add(ChatColor.GRAY + "Location: " + ChatColor.WHITE + 
                    String.format("%s (%d, %d, %d)", 
                        entry.getLocation().getWorld().getName(),
                        (int)entry.getLocation().getX(),
                        (int)entry.getLocation().getY(),
                        (int)entry.getLocation().getZ()));
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Format join date from metadata
     */
    private String formatJoinDate(LoreEntry entry) {
        try {
            String dateValue = entry.getMetadata("first_join_date");
            if (dateValue != null && !dateValue.isEmpty()) {
                long timestamp = Long.parseLong(dateValue);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                return sdf.format(new Date(timestamp));
            }
        } catch (Exception e) {
            debug.debug("Error formatting join date: " + e.getMessage());
        }
        return null;
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.PLAYER;
    }
}

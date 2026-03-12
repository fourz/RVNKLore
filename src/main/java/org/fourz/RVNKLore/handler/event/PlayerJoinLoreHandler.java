package org.fourz.RVNKLore.handler.event;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Handler for player join events to create lore entries
 */
public class PlayerJoinLoreHandler extends DefaultLoreHandler {
    private final LogManager logger;
    
    public PlayerJoinLoreHandler(RVNKLore plugin) {
        super(plugin);
        this.logger = LogManager.getInstance(plugin, "PlayerJoinLoreHandler");
    }
      @Override
    public void initialize() {
        logger.debug("Initializing player join event handler - this is the primary handler for PlayerJoinEvent");
    }    /**
     * Handle player join events - only logs for first joins and name changes
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        try {
            // Only log debug information for the actual handling
            // Use the centralized PlayerManager for all player join processing
            boolean processed = plugin.getPlayerManager().processPlayerJoin(player);
            
            // Only log if an action was taken (first join or name change)
            if (processed) {
                logger.debug("Player join event created lore entry for: " + player.getName());
            }
        } catch (Exception e) {
            logger.error("Error processing player join event: " + player.getName(), e);
        }
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + "First Arrival");
            
            // Get player name from metadata instead of entry name
            String playerName = entry.getMetadata("player_name");
            if (playerName != null) {
                lore.add(ChatColor.GRAY + "Player: " + ChatColor.WHITE + playerName);
            }
            
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
            logger.debug("Error formatting join date: " + e.getMessage());
        }
        return null;
    }    @Override
    public LoreType getHandlerType() {
        return LoreType.PLAYER;
    }
}

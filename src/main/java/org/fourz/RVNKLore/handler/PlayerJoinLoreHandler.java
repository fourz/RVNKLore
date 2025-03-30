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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Handler for player join events to create lore entries
 */
public class PlayerJoinLoreHandler extends DefaultLoreHandler {
    
    private Debug debug; // Add explicit field declaration
    
    public PlayerJoinLoreHandler(RVNKLore plugin) {
        super(plugin);
        this.debug = Debug.createDebugger(plugin, "PlayerJoinLoreHandler", Level.FINE);
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
        } else {
            updatePlayerJoinLoreEntry(player);
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
        entry.setDescription("On " + java.time.LocalDate.now().toString() + ", " + 
                             player.getName() + " first set foot in our world.");
        entry.setLocation(player.getLocation());
        entry.setSubmittedBy("Server");
        
        // Add metadata
        entry.addMetadata("player_uuid", player.getUniqueId().toString());
        entry.addMetadata("first_join_date", System.currentTimeMillis() + "");
        entry.addMetadata("join_location_x", player.getLocation().getX() + "");
        entry.addMetadata("join_location_y", player.getLocation().getY() + "");
        entry.addMetadata("join_location_z", player.getLocation().getZ() + "");
        entry.addMetadata("join_location_world", player.getLocation().getWorld().getName());
        
        // Auto-approve server-generated entries
        entry.setApproved(true);
        plugin.getLoreManager().addLoreEntry(entry);
        
        // Notify the player
        player.sendMessage(ChatColor.GOLD + "Your arrival has been recorded in the annals of history!");
    }
    
    /**
     * Update a player's join lore for returning players
     */
    private void updatePlayerJoinLoreEntry(Player player) {
        debug.debug("Player rejoined: " + player.getName());
        
        // For frequently returning players, we don't need to spam the lore database
        // Maybe update an existing entry or add a new one only for long absences
        // This is just a placeholder for future implementation
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
            
            // Get join date if available
            if (entry.hasMetadata("first_join_date")) {
                try {
                    long joinTimestamp = Long.parseLong(entry.getMetadata("first_join_date"));
                    java.util.Date joinDate = new java.util.Date(joinTimestamp);
                    lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + 
                             new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(joinDate));
                } catch (NumberFormatException e) {
                    debug.debug("Could not parse join date");
                }
            }
            
            // Split description into lines
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }
            
            if (entry.getLocation() != null) {
                lore.add(ChatColor.GRAY + "Location: " + 
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
    public LoreType getHandlerType() {
        return LoreType.PLAYER; // Was incorrectly returning PLAYER
    }
}

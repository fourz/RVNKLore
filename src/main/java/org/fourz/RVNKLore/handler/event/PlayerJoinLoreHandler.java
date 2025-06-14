package org.fourz.RVNKLore.handler.event;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
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
        logger.debug("Initializing player join lore handler");
    }    /**
     * Handle player join events
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerUuid = player.getUniqueId().toString();
        String playerName = player.getName();

        try {
            // Get all player lore entries
            List<LoreEntry> playerEntries = plugin.getLoreManager().getLoreEntriesByType(LoreType.PLAYER);
            LoreEntry existingEntry = null;
            String storedPlayerName = null;
            
            // Check if this player already has a lore entry by UUID
            for (LoreEntry entry : playerEntries) {
                if (playerUuid.equals(entry.getMetadata("player_uuid"))) {
                    existingEntry = entry;
                    storedPlayerName = entry.getMetadata("player_name");
                    break;
                }
            }

            if (existingEntry == null && !player.hasPlayedBefore()) {
                // No existing entry and this is a first-time join
                createNewPlayerJoinLoreEntry(player);
            } else if (existingEntry != null && storedPlayerName != null && !playerName.equals(storedPlayerName)) {
                // UUID matches but name has changed
                handlePlayerNameChangeLore(player, storedPlayerName);
            } else {
                // Player exists with the same name or has played before, no action needed
                logger.debug("No new lore entry needed for: " + playerName);
            }
        } catch (Exception e) {
            logger.error("Error processing player join event for: " + playerName, e);
        }
    }

    /**
     * Handles lore creation when a player changes their name.
     *
     * @param player The player who changed their name
     * @param oldName The previous name stored in the lore entry
     */    private void handlePlayerNameChangeLore(Player player, String oldName) {
        logger.debug("Detected player name change: " + oldName + " -> " + player.getName());
        
        try {
            // Create a name change lore entry with guaranteed unique name
            String uniqueName = "NameChange_" + player.getUniqueId().toString() + "_" + System.currentTimeMillis();
            
            LoreEntry entry = new LoreEntry();
            entry.setType(LoreType.PLAYER);
            entry.setName(uniqueName);
            
            // Format date using SimpleDateFormat for consistent display
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            String dateString = dateFormat.format(new Date());
            
            entry.setDescription("The adventurer known as " + oldName + " shall henceforth be known as " + 
                               player.getName() + ".\nName changed on " + dateString + ".");
            entry.setLocation(player.getLocation());
            entry.setSubmittedBy("Server");
            
            // Add essential metadata
            entry.addMetadata("player_uuid", player.getUniqueId().toString());
            entry.addMetadata("player_name", player.getName());
            entry.addMetadata("previous_name", oldName);
            entry.addMetadata("name_change_date", System.currentTimeMillis() + "");
            entry.addMetadata("entry_type", "name_change");
            
            // Auto-approve server-generated entries
            entry.setApproved(true);
            boolean success = plugin.getLoreManager().addLoreEntry(entry);
            
            if (success) {
                // Notify the player
                player.sendMessage(ChatColor.GOLD + "Your name change has been recorded in the annals of history!");
                logger.info("Name change lore entry created for: " + player.getName());
            } else {
                logger.warning("Failed to create name change lore entry for: " + player.getName());
            }
        } catch (Exception e) {
            logger.error("Error creating name change lore entry: " + e.getMessage(), e);
        }
    }/**
     * Create a lore entry for a new player
     */    private void createNewPlayerJoinLoreEntry(Player player) {
        logger.debug("Creating join lore entry for new player: " + player.getName());
        
        try {
            // Create a guaranteed unique entry name using UUID
            String uniqueName = "FirstArrival_" + player.getUniqueId().toString();
            
            LoreEntry entry = new LoreEntry();
            entry.setType(LoreType.PLAYER);
            entry.setName(uniqueName);
            
            // Format date using SimpleDateFormat for consistent display
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String dateString = dateFormat.format(new Date());
            
            entry.setDescription(player.getName() + " first set foot in our world on " + dateString + ".\n" +
                               "Welcome to a new adventurer in our realm!");
            entry.setLocation(player.getLocation());
            entry.setSubmittedBy("Server");
            
            // Add essential metadata
            entry.addMetadata("player_uuid", player.getUniqueId().toString());
            entry.addMetadata("player_name", player.getName());
            entry.addMetadata("first_join_date", System.currentTimeMillis() + "");
            entry.addMetadata("entry_type", "first_arrival");
            
            // Location coordinates as metadata
            entry.addMetadata("join_location", String.format("%s,%d,%d,%d", 
                player.getLocation().getWorld().getName(),
                (int)player.getLocation().getX(),
                (int)player.getLocation().getY(),
                (int)player.getLocation().getZ()));
            
            // Auto-approve server-generated entries
            entry.setApproved(true);
            boolean success = plugin.getLoreManager().addLoreEntry(entry);
            
            if (success) {
                // Notify the player
                player.sendMessage(ChatColor.GOLD + "Your arrival has been recorded in the annals of history!");
                logger.info("First arrival lore entry created for: " + player.getName());
            } else {
                logger.warning("Failed to create first arrival lore entry for: " + player.getName());
            }
        } catch (Exception e) {
            logger.error("Error creating first arrival lore entry", e);
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
            logger.debug("Error formatting join date: " + e.getMessage());
        }
        return null;
    }

    @Override
    public LoreType getHandlerType() {
        return LoreType.PLAYER;
    }
}

package org.fourz.RVNKLore.lore.player;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Manager for player-related lore operations
 * 
 * This class centralizes player lore management including:
 * - First join lore creation
 * - Player name change detection and recording
 * - Player lore entry lookup and retrieval
 */
public class PlayerManager {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final PlayerRepository playerRepository;
    
    public PlayerManager(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "PlayerManager");
        this.playerRepository = new PlayerRepository(plugin, plugin.getDatabaseManager().getDatabaseConnection());
    }
    
    /**
     * Initialize the player manager
     */
    public void initialize() {
        logger.info("Initializing PlayerManager");
    }
    
    /**
     * Check if a player already has a lore entry
     * 
     * @param playerUuid The UUID of the player to check
     * @return true if the player has a lore entry, false otherwise
     */
    public boolean playerExists(UUID playerUuid) {
        return playerRepository.playerExists(playerUuid);
    }
    
    /**
     * Get the player's current name stored in the lore system
     * 
     * @param playerUuid The UUID of the player
     * @return The player's name, or null if not found
     */
    public String getStoredPlayerName(UUID playerUuid) {
        return playerRepository.getStoredPlayerName(playerUuid);
    }
    
    /**
     * Process a player join event
     * - Creates a first join entry if the player is new
     * - Checks for and records name changes if the player exists
     * 
     * @param player The player who joined
     * @return true if any action was taken, false otherwise
     */
    public boolean processPlayerJoin(Player player) {
        UUID playerUuid = player.getUniqueId();
        String currentName = player.getName();
        
        try {
            if (!playerExists(playerUuid)) {
                // New player, create first join entry
                return createPlayerLoreEntry(player);
            } else {
                // Existing player, check for name change
                String storedName = getStoredPlayerName(playerUuid);
                if (storedName != null && !storedName.equals(currentName)) {
                    return createNameChangeLoreEntry(player, storedName);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing player join: " + player.getName(), e);
        }
        
        return false;
    }
    
    /**
     * Process a new player joining for the first time
     * 
     * @param player The player who joined
     * @return true if the entry was created, false otherwise
     */
    public boolean processFirstTimeJoin(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        if (!playerExists(playerUuid)) {
            return createFirstJoinLoreEntry(player);
        }
        
        return false;
    }
    
    /**
     * Create a lore entry for a player's character
     * 
     * @param player The player to create an entry for
     * @return true if the entry was created successfully, false otherwise
     */
    public boolean createPlayerLoreEntry(Player player) {
        logger.info("Creating player lore entry for: " + player.getName());
        
        try {
            // Create a guaranteed unique entry name with UUID
            String uniqueName = "Player_" + player.getUniqueId().toString();
            
            LoreEntry entry = new LoreEntry();
            entry.setType(LoreType.PLAYER);
            entry.setName(uniqueName);
            entry.setDescription("A player who joined the realm on " + 
                               java.time.LocalDate.now().toString());
            entry.setLocation(player.getLocation());
            entry.setSubmittedBy("Server");
            
            // Add metadata
            entry.addMetadata("player_uuid", player.getUniqueId().toString());
            entry.addMetadata("player_name", player.getName());
            entry.addMetadata("first_join_date", System.currentTimeMillis() + "");
            entry.addMetadata("entry_type", "player_character");
            
            // Save to database - automatically approved since this is server-generated
            entry.setApproved(true);
            boolean success = plugin.getLoreManager().addLoreEntry(entry);
            
            if (success) {
                logger.info("Player lore entry created for: " + player.getName());
            } else {
                logger.warning("Failed to create player lore entry for: " + player.getName());
            }
            
            return success;
        } catch (Exception e) {
            logger.error("Error creating player lore entry", e);
            return false;
        }
    }
    
    /**
     * Create a lore entry for a player's first join
     * 
     * @param player The player who joined for the first time
     * @return true if the entry was created successfully, false otherwise
     */
    public boolean createFirstJoinLoreEntry(Player player) {
        logger.info("Creating first join lore entry for: " + player.getName());
        
        try {
            // Create a guaranteed unique entry name with UUID
            String uniqueName = "FirstJoin_" + player.getUniqueId().toString();
            
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
            entry.addMetadata("entry_type", "first_join");
            
            // Location coordinates as metadata
            entry.addMetadata("join_location", formatLocation(player.getLocation()));
            
            // Auto-approve server-generated entries
            entry.setApproved(true);
            boolean success = plugin.getLoreManager().addLoreEntry(entry);
            
            if (success) {
                // Notify the player
                player.sendMessage(ChatColor.GOLD + "Your arrival has been recorded in the annals of history!");
                logger.info("First join lore entry created for: " + player.getName());
            } else {
                logger.warning("Failed to create first join lore entry for: " + player.getName());
            }
            
            return success;
        } catch (Exception e) {
            logger.error("Error creating first join lore entry", e);
            return false;
        }
    }
    
    /**
     * Create a lore entry for a player name change
     * 
     * @param player The player who changed their name
     * @param oldName The previous name of the player
     * @return true if the entry was created successfully, false otherwise
     */
    public boolean createNameChangeLoreEntry(Player player, String oldName) {
        logger.info("Creating name change lore entry: " + oldName + " → " + player.getName());
        
        try {
            // Create a guaranteed unique entry name with UUID and timestamp
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
                logger.info("Name change lore entry created: " + oldName + " → " + player.getName());
            } else {
                logger.warning("Failed to create name change lore entry: " + oldName + " → " + player.getName());
            }
            
            return success;
        } catch (Exception e) {
            logger.error("Error creating name change lore entry", e);
            return false;
        }
    }
    
    /**
     * Get the list of name changes for a player
     * 
     * @param playerUuid The UUID of the player
     * @return List of name change records, empty if none found
     */
    public List<NameChangeRecord> getNameChangeHistory(UUID playerUuid) {
        return playerRepository.getNameChangeHistory(playerUuid);
    }
    
    /**
     * Format a location as a string
     * 
     * @param location The location to format
     * @return Formatted location string
     */
    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        
        return String.format("%s,%d,%d,%d", 
            location.getWorld().getName(),
            (int)location.getX(),
            (int)location.getY(),
            (int)location.getZ());
    }
}

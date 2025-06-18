package org.fourz.RVNKLore.lore.player;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.dto.NameChangeRecordDTO;
import org.fourz.RVNKLore.data.dto.PlayerDTO;
import org.fourz.RVNKLore.debug.LogManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manager for player-related lore operations
 * 
 * This class centralizes player lore management including:
 * - First join lore creation
 * - Player name change detection and recording
 * - Player lore entry lookup and retrieval
 */
public class PlayerManager {
    private final LogManager logger;
    private final PlayerRepository playerRepository;
    
    public PlayerManager(RVNKLore plugin) {
        this.logger = LogManager.getInstance(plugin, "PlayerManager");
        this.playerRepository = new PlayerRepository(plugin, plugin.getDatabaseManager());
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
     * @return CompletableFuture that completes with true if the player has a lore entry
     */
    public CompletableFuture<Boolean> playerExists(UUID playerUuid) {
        return playerRepository.playerExists(playerUuid);
    }
    
    /**
     * Get the player's current name stored in the lore system
     * 
     * @param playerUuid The UUID of the player
     * @return CompletableFuture containing the player's name, or null if not found
     */
    public CompletableFuture<String> getStoredPlayerName(UUID playerUuid) {
        return playerRepository.getStoredPlayerName(playerUuid);
    }
    
    /**
     * Process a player join event
     * - Creates a first join entry if the player is new
     * - Checks for and records name changes if the player exists
     * 
     * @param player The player who joined
     * @return CompletableFuture that completes with true if any action was taken
     */
    public CompletableFuture<Boolean> processPlayerJoin(Player player) {
        UUID playerUuid = player.getUniqueId();
        String currentName = player.getName();
        
        return playerExists(playerUuid)
            .thenCompose(exists -> {
                if (!exists) {
                    // New player, create first join entry
                    return createPlayerLoreEntry(player);
                } else {
                    // Existing player, check for name change
                    return getStoredPlayerName(playerUuid)
                        .thenCompose(storedName -> {
                            if (storedName != null && !storedName.equals(currentName)) {
                                return createNameChangeLoreEntry(player, storedName);
                            }
                            return CompletableFuture.completedFuture(false);
                        });
                }
            })
            .exceptionally(e -> {
                logger.error("Error processing player join: " + player.getName(), e);
                return false;
            });
    }
    
    /**
     * Process a new player joining for the first time
     * 
     * @param player The player who joined
     * @return CompletableFuture that completes with true if the entry was created
     */
    public CompletableFuture<Boolean> processFirstTimeJoin(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        return playerExists(playerUuid)
            .thenCompose(exists -> {
                if (!exists) {
                    return createFirstJoinLoreEntry(player);
                }
                return CompletableFuture.completedFuture(false);
            });
    }
    
    /**
     * Create a lore entry for a player's character
     * 
     * @param player The player to create an entry for
     * @return CompletableFuture that completes with true if successful
     */
    public CompletableFuture<Boolean> createPlayerLoreEntry(Player player) {
        logger.info("Creating player lore entry for: " + player.getName());
        
        try {
            PlayerDTO dto = new PlayerDTO();
            dto.setPlayerUuid(player.getUniqueId());
            dto.setPlayerName(player.getName());
            dto.setFirstJoinDate(new Date());
            dto.setLocation(formatLocation(player.getLocation()));
            dto.getMetadata().put("entry_type", "player_character");
            dto.getMetadata().put("description", "A player who joined the realm on " + 
                                java.time.LocalDate.now().toString());
            dto.setApproved(true);
            
            return playerRepository.savePlayer(dto)
                .thenApply(id -> {
                    if (id != null) {
                        logger.info("Player lore entry created for: " + player.getName());
                        return true;
                    } else {
                        logger.warning("Failed to create player lore entry for: " + player.getName());
                        return false;
                    }
                })
                .exceptionally(e -> {
                    logger.error("Error creating player lore entry", e);
                    return false;
                });
        } catch (Exception e) {
            logger.error("Error creating player lore entry", e);
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * Create a lore entry for a player's first join
     * 
     * @param player The player who joined for the first time
     * @return CompletableFuture that completes with true if successful
     */
    public CompletableFuture<Boolean> createFirstJoinLoreEntry(Player player) {
        logger.info("Creating first join lore entry for: " + player.getName());
        
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String dateString = dateFormat.format(new Date());
            
            PlayerDTO dto = new PlayerDTO();
            dto.setPlayerUuid(player.getUniqueId());
            dto.setPlayerName(player.getName());
            dto.setFirstJoinDate(new Date());
            dto.setLocation(formatLocation(player.getLocation()));
            dto.getMetadata().put("entry_type", "first_join");
            dto.getMetadata().put("description", player.getName() + " first set foot in our world on " + dateString + ".\n" +
                                              "Welcome to a new adventurer in our realm!");
            dto.getMetadata().put("join_location", formatLocation(player.getLocation()));
            dto.setApproved(true);
            
            return playerRepository.savePlayer(dto)
                .thenApply(id -> {
                    if (id != null) {
                        player.sendMessage(ChatColor.GOLD + "Your arrival has been recorded in the annals of history!");
                        logger.info("First join lore entry created for: " + player.getName());
                        return true;
                    } else {
                        logger.warning("Failed to create first join lore entry for: " + player.getName());
                        return false;
                    }
                })
                .exceptionally(e -> {
                    logger.error("Error creating first join lore entry", e);
                    return false;
                });
        } catch (Exception e) {
            logger.error("Error creating first join lore entry", e);
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * Create a lore entry for a player name change
     * 
     * @param player The player who changed their name
     * @param oldName The previous name of the player
     * @return CompletableFuture that completes with true if successful
     */
    public CompletableFuture<Boolean> createNameChangeLoreEntry(Player player, String oldName) {
        if (oldName == null || oldName.equals(player.getName())) {
            return CompletableFuture.completedFuture(false);
        }

        PlayerDTO dto = new PlayerDTO();
        dto.setPlayerUuid(player.getUniqueId());
        dto.setPlayerName(player.getName());
        dto.setLocation(formatLocation(player.getLocation()));
        dto.getMetadata().put("entry_type", "name_change");
        dto.getMetadata().put("old_name", oldName);
        dto.getMetadata().put("new_name", player.getName());
        dto.getMetadata().put("description", "The adventurer known as " + oldName + 
                                          " shall henceforth be known as " + player.getName());
        dto.setApproved(true);
        
        return playerRepository.savePlayer(dto)
            .thenApply(id -> {
                if (id != null) {
                    logger.info("Name change lore entry created for " + oldName + " -> " + player.getName());
                    return true;
                } else {
                    logger.warning("Failed to create name change lore entry for " + oldName + " -> " + player.getName());
                    return false;
                }
            })
            .exceptionally(e -> {
                logger.error("Error creating name change lore entry", e);
                return false;
            });
    }
    
    /**
     * Get the list of name changes for a player
     * 
     * @param playerUuid The UUID of the player
     * @return CompletableFuture containing list of name change records
     */
    public CompletableFuture<List<NameChangeRecordDTO>> getNameChangeHistory(UUID playerUuid) {
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

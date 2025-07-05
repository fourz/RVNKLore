package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for player character lore entries
 */
public class PlayerLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseManager databaseManager;
    
    public PlayerLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "PlayerLoreHandler");
        this.databaseManager = plugin.getDatabaseManager();
    }
    
    @Override
    public void initialize() {
        logger.info("Initializing player lore handler with event listener");
    }
    
    /**
     * Handle player join events to create/update player lore
     */    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        try {
            // Use the centralized PlayerManager instead of duplicate logic
            plugin.getPlayerManager().processPlayerJoin(player);
        } catch (Exception e) {
            logger.error("Error in player join handler", e);
        }
    }/**
     * Handles lore creation when a player changes their name.
     *
     * @param player The player who changed their name
     * @param oldName The previous name stored in the lore entry
     */
    private void handlePlayerNameChangeLore(Player player, String oldName) {
        logger.info("Detected player name change: " + oldName + " -> " + player.getName());
        try {
            String uniqueName = "NameChange_" + player.getUniqueId().toString().substring(0, 8) + "_" + System.currentTimeMillis();
            LoreEntryDTO dto = new LoreEntryDTO();
            dto.setEntryType(LoreType.PLAYER.name());
            dto.setName(uniqueName);
            dto.setDescription(oldName + " is now known as " + player.getName() + ".\nName changed on " + java.time.LocalDate.now());
            dto.setWorld(player.getWorld().getName());
            dto.setX(player.getLocation().getX());
            dto.setY(player.getLocation().getY());
            dto.setZ(player.getLocation().getZ());
            dto.setSubmittedBy("Server");
            dto.setApproved(true);
            java.util.Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("player_uuid", player.getUniqueId().toString());
            metadata.put("player_name", player.getName());
            metadata.put("previous_name", oldName);
            metadata.put("name_change_date", String.valueOf(System.currentTimeMillis()));
            dto.setMetadata(metadata);
            databaseManager.getLoreEntryRepository().saveLoreEntry(dto).thenAccept(id -> {
                if (id > 0) {
                    logger.info("Player name change lore entry created for: " + player.getName());
                    player.sendMessage(ChatColor.GOLD + "Your name change has been recorded in the annals of history!");
                } else {
                    logger.warning("Failed to create player name change lore entry for: " + player.getName());
                }
            }).exceptionally(e -> {
                logger.error("Error creating player name change lore entry", e);
                return null;
            });
        } catch (Exception e) {
            logger.error("Error creating player name change lore entry", e);
        }
    }/**
     * Create a new lore entry for a player
     */
    private void createPlayerLoreEntry(Player player) {
        logger.info("Creating new player lore entry for: " + player.getName());
        try {
            String uniqueName = player.getName() + "_" + player.getUniqueId().toString().substring(0, 8);
            LoreEntryDTO dto = new LoreEntryDTO();
            dto.setEntryType(LoreType.PLAYER.name());
            dto.setName(uniqueName);
            dto.setDescription("A player who joined the realm on " + java.time.LocalDate.now());
            dto.setWorld(player.getWorld().getName());
            dto.setX(player.getLocation().getX());
            dto.setY(player.getLocation().getY());
            dto.setZ(player.getLocation().getZ());
            dto.setSubmittedBy("Server");
            dto.setApproved(true);
            java.util.Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("player_uuid", player.getUniqueId().toString());
            metadata.put("player_name", player.getName());
            metadata.put("first_join_date", String.valueOf(System.currentTimeMillis()));
            dto.setMetadata(metadata);
            databaseManager.getLoreEntryRepository().saveLoreEntry(dto).thenAccept(id -> {
                if (id > 0) {
                    logger.info("Player lore entry created for: " + player.getName());
                } else {
                    logger.warning("Failed to create player lore entry for: " + player.getName());
                }
            }).exceptionally(e -> {
                logger.error("Error creating player lore entry", e);
                return null;
            });
        } catch (Exception e) {
            logger.error("Error creating player lore entry", e);
        }
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        if (entry.getName() == null || entry.getName().isEmpty()) {
            logger.warning("Player lore validation failed: Name is required");
            return false;
        }
        
        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            logger.warning("Player lore validation failed: Description is required");
            return false;
        }
        
        return true;
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + entry.getName());
            
            // Remove deprecated setOwner(String) and any duplicate logic
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.GREEN + "Player Character");
            
            // Split description into lines for better readability
            String[] descLines = entry.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add(ChatColor.WHITE + line);
            }
            
            lore.add("");
            lore.add(ChatColor.GRAY + "Biography by: " + ChatColor.YELLOW + entry.getSubmittedBy());
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    @Override
    public void displayLore(LoreEntry entry, Player player) {
        player.sendMessage(ChatColor.GOLD + "=== " + entry.getName() + " ===");
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.GREEN + "Player Character");
        
        player.sendMessage("");
        String[] descLines = entry.getDescription().split("\\n");
        for (String line : descLines) {
            player.sendMessage(ChatColor.WHITE + line);
        }
        
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Biography by: " + ChatColor.YELLOW + entry.getSubmittedBy());
    }
}

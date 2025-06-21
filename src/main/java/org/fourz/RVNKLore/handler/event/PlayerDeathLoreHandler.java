package org.fourz.RVNKLore.handler.event;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.data.DatabaseManager;
import org.fourz.RVNKLore.data.dto.LoreEntryDTO;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Handler for creating lore entries when players die
 */
public class PlayerDeathLoreHandler extends DefaultLoreHandler {
    private final DatabaseManager databaseManager;
    
    public PlayerDeathLoreHandler(RVNKLore plugin) {
        super(plugin);
        this.databaseManager = plugin.getDatabaseManager();
    }
    
    @Override
    public void initialize() {
        logger.debug("Initializing player death lore handler");
    }

    /**
     * Listen for player death events and create lore entries
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        try {
            Player player = event.getEntity();
            
            if (shouldCreateDeathLoreEntry(player)) {
                createDeathLoreEntry(player, event.getDeathMessage());
            }
        } catch (Exception e) {
            logger.error("Error processing player death event", e);
        }
    }
    
    /**
     * Determine if this death should be recorded as lore
     */
    private boolean shouldCreateDeathLoreEntry(Player player) {
        try {
            if (player.hasPermission("rvnklore.notable")) {
                return true;
            }

            // Check for death by another player
            if (player.getKiller() != null) {
                return true;
            }

            // Check for death in special locations
            if (player.getLocation().getY() < 0 || player.getLocation().getY() > 200) {
                return true;
            }

            // Check for death with valuable items
            return player.getInventory().all(Material.DIAMOND).size() > 0 ||
                   player.getInventory().all(Material.NETHERITE_INGOT).size() > 0;

        } catch (Exception e) {
            logger.error("Error checking death significance", e);
            return false;
        }
    }
    
    /**
     * Create a lore entry for a player death
     */
    private void createDeathLoreEntry(Player player, String deathMessage) {
        logger.debug("Creating death lore entry for: " + player.getName());
        
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        String dateString = formatter.format(new Date());
        
        LoreEntry entry = new LoreEntry();
        entry.setType(LoreType.EVENT);
        entry.setName("Death of " + player.getName());
        
        // Create a descriptive death entry
        String description = "On " + dateString + ", " + player.getName() + " met their demise.";
        if (deathMessage != null) {
            description += "\n" + deathMessage;
        }
        entry.setDescription(description);
        
        entry.setLocation(player.getLocation());
        entry.setSubmittedBy("Server");
        
        // Add metadata
        entry.setMetadata("player_uuid", player.getUniqueId().toString());
        entry.setMetadata("death_date", String.valueOf(System.currentTimeMillis()));
        entry.setMetadata("death_message", deathMessage != null ? deathMessage : "");
        
        // Save to database - might need admin approval
        entry.setApproved(false);
        
        // Save asynchronously using DatabaseManager
        CompletableFuture.runAsync(() -> {
            try {
                LoreEntryDTO dto = entry.toDTO();
                databaseManager.getLoreEntryRepository()
                    .addLoreEntry(dto)
                    .thenAccept(success -> {
                        if (!success) {
                            logger.warning("Failed to save death lore entry for: " + player.getName());
                        } else {
                            logger.debug("Death lore entry created for: " + player.getName());
                        }
                    })
                    .exceptionally(e -> {
                        logger.error("Error saving death lore entry", e);
                        return null;
                    });
            } catch (Exception e) {
                logger.error("Error preparing death lore entry for save", e);
            }
        });
    }

    @Override
    public ItemStack createLoreItem(LoreEntry entry) {
        ItemStack item = new ItemStack(Material.BONE);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + entry.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.RED + "Notable Death");
            
            // Death date if available
            String deathDateStr = entry.getMetadata("death_date");
            if (deathDateStr != null) {
                try {
                    long deathTimestamp = Long.parseLong(deathDateStr);
                    Date deathDate = new Date(deathTimestamp);
                    lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + 
                             new SimpleDateFormat("yyyy-MM-dd").format(deathDate));
                } catch (NumberFormatException e) {
                    logger.debug("Could not parse death date");
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
        return LoreType.PLAYER;
    }
}

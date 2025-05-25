package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.Debug;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Handler for player character lore entries
 */
public class PlayerLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final Debug debug;
    
    public PlayerLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.debug = Debug.createDebugger(plugin, "PlayerLoreHandler", plugin.getConfigManager().getLogLevel());
    }
    
    @Override
    public void initialize() {
        debug.debug("Initializing player lore handler with event listener");
    }
    
    /**
     * Handle player join events to create/update player lore
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if this player already has a lore entry
        List<LoreEntry> playerEntries = plugin.getLoreManager().getLoreEntriesByType(LoreType.PLAYER);
        String playerUuid = player.getUniqueId().toString();
        
        boolean hasLoreEntry = false;
        for (LoreEntry entry : playerEntries) {
            if (entry.getMetadata("player_uuid") != null && 
                entry.getMetadata("player_uuid").equals(playerUuid)) {
                hasLoreEntry = true;
                break;
            }
        }
        
        // If no entry exists, create one for this player
        if (!hasLoreEntry) {
            createPlayerLoreEntry(player);
        }
    }
    
    /**
     * Create a new lore entry for a player
     */
    private void createPlayerLoreEntry(Player player) {
        debug.debug("Creating new player lore entry for: " + player.getName());
        
        LoreEntry entry = new LoreEntry();
        entry.setType(LoreType.PLAYER);
        entry.setName(player.getName());
        entry.setDescription("A player who joined the realm on " + 
                             java.time.LocalDate.now().toString());
        entry.setLocation(player.getLocation());
        entry.setSubmittedBy("Server");
        
        // Add metadata
        entry.addMetadata("player_uuid", player.getUniqueId().toString());
        entry.addMetadata("first_join_date", System.currentTimeMillis() + "");
        
        // Save to database - automatically approved since this is server-generated
        entry.setApproved(true);
        plugin.getLoreManager().addLoreEntry(entry);
        
        debug.debug("Player lore entry created for: " + player.getName());
    }

    @Override
    public boolean validateEntry(LoreEntry entry) {
        if (entry.getName() == null || entry.getName().isEmpty()) {
            debug.debug("Player lore validation failed: Name is required");
            return false;
        }
        
        if (entry.getDescription() == null || entry.getDescription().isEmpty()) {
            debug.debug("Player lore validation failed: Description is required");
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
            
            // Try to set the skull owner to the name of the player being documented
            try {
                meta.setOwner(entry.getName());
            } catch (Exception e) {
                debug.debug("Could not set skull owner to " + entry.getName());
            }
            
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

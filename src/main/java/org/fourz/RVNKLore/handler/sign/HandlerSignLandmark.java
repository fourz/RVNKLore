package org.fourz.RVNKLore.handler.sign;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.SignChangeEvent;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.handler.DefaultLoreHandler;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.fourz.RVNKLore.debug.LogManager;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Handler for creating landmarks via sign creation
 * When a player places a sign with "[Landmark]" on the first line,
 * a new landmark lore entry is created.
 */
public class HandlerSignLandmark extends DefaultLoreHandler {
    private static final String LANDMARK_SIGN_HEADER = "[Landmark]";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final LogManager logger;
    
    public HandlerSignLandmark(RVNKLore plugin) {
        super(plugin);
        this.logger = LogManager.getInstance(plugin, "HandlerSignLandmark");
    }
    
    @Override
    public void initialize() {
        logger.debug("Initializing sign landmark handler");
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        // Check if first line contains the landmark identifier
        if (!event.getLine(0).equalsIgnoreCase(LANDMARK_SIGN_HEADER)) {
            return;
        }
        
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        // Check if player has permission to create landmark signs
        if (!player.hasPermission("rvnklore.sign.landmark")) {
            logger.debug(player.getName() + " tried to create a landmark sign but lacks permission");
            event.setLine(0, ChatColor.RED + "[Landmark]");
            player.sendMessage(ChatColor.RED + "You don't have permission to create landmark signs.");
            return;
        }
        
        // Format the sign display
        event.setLine(0, ChatColor.DARK_BLUE + "[" + ChatColor.BLUE + "Landmark" + ChatColor.DARK_BLUE + "]");
        
        // Get landmark name from line 2, or use "Unnamed Landmark" if empty
        String landmarkName = event.getLine(1);
        if (landmarkName == null || landmarkName.trim().isEmpty()) {
            landmarkName = "Unnamed Landmark";
            event.setLine(1, landmarkName);
        }
        
        // Get description from lines 3 and 4, combine if both exist
        String description = "";
        if (event.getLine(2) != null && !event.getLine(2).trim().isEmpty()) {
            description = event.getLine(2);
            
            if (event.getLine(3) != null && !event.getLine(3).trim().isEmpty()) {
                description += " " + event.getLine(3);
            }
        } else {
            description = "Landmark created by " + player.getName();
        }
        
        // Create the landmark lore entry
        createLandmarkEntry(player, landmarkName, description, block);
    }
    
    /**
     * Create a landmark lore entry from a sign
     */
    private void createLandmarkEntry(Player player, String name, String description, Block block) {
        // Create a new lore entry using the factory pattern
        LoreEntry entry = LoreEntry.createLocationLore(name, description, LoreType.LANDMARK, block.getLocation(), player);
        
        // Add metadata to track origin and additional information
        String currentTime = dateFormat.format(new Date());
        entry.addMetadata("created_at", currentTime);
        entry.addMetadata("player_uuid", player.getUniqueId().toString());
        entry.addMetadata("player_name", player.getName());
        entry.addMetadata("world", block.getWorld().getName());
        entry.addMetadata("x", String.valueOf(block.getX()));
        entry.addMetadata("y", String.valueOf(block.getY()));
        entry.addMetadata("z", String.valueOf(block.getZ()));
        entry.addMetadata("source", "sign");
        
        // Use ConfigManager instead of direct getConfig() access for consistency
        boolean autoApprove = plugin.getConfigManager().getConfig().getBoolean("landmarks.signs.auto_approve", false);
        entry.setApproved(autoApprove || player.hasPermission("rvnklore.approve.own"));
        
        // Save the entry
        boolean success = plugin.getLoreManager().addLoreEntry(entry);
        
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Landmark '" + name + "' has been " + 
                (entry.isApproved() ? "created" : "submitted for approval") + ".");
            logger.debug("Created landmark via sign: " + name + " by " + player.getName());
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create landmark. Please try again or contact an admin.");
            logger.warning("Failed to create landmark via sign: " + name + " by " + player.getName());
        }
    }
    
    @Override
    public LoreType getHandlerType() {
        return LoreType.LANDMARK;
    }
}

package org.fourz.RVNKLore.handler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for player character lore entries.
 * <p>
 * <b>Note:</b> This class does <u>not</u> register for or handle any player events.
 * All player join and name change event handling is performed by {@link PlayerJoinLoreHandler},
 * which delegates to PlayerManager for all player lore operations.
 * <p>
 * This handler is only used for lore entry display, validation, and item creation.
 */
public class PlayerLoreHandler implements LoreHandler {
    private final RVNKLore plugin;
    private final LogManager logger;
    
    public PlayerLoreHandler(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "PlayerLoreHandler");
    }
      @Override
    public void initialize() {
        logger.info("Initializing player lore handler");
    }
      /**
     * This method is deprecated and will be removed in a future version.
     * Use PlayerManager's processPlayerJoin method directly.
     * 
     * @deprecated Use {@link org.fourz.RVNKLore.lore.player.PlayerManager#processPlayerJoin(Player)} instead
     */    
    public void processPlayerJoin(Player player) {
        logger.warning("PlayerLoreHandler.processPlayerJoin is deprecated. Use PlayerManager directly.");
        // No-op - do not process player join events in this handler
    }    /**
     * Handles lore creation when a player changes their name.
     * 
     * @deprecated This method is deprecated in favor of using {@link org.fourz.RVNKLore.lore.player.PlayerManager#createNameChangeLoreEntry(Player, String)}
     * @param player The player who changed their name
     * @param oldName The previous name stored in the lore entry
     */
    private void handlePlayerNameChangeLore(Player player, String oldName) {
        logger.warning("PlayerLoreHandler.handlePlayerNameChangeLore is deprecated. Use PlayerManager directly.");
        // No-op - delegate to PlayerManager instead
    }    /**
     * Create a new lore entry for a player
     * 
     * @deprecated This method is deprecated in favor of using {@link org.fourz.RVNKLore.lore.player.PlayerManager#createPlayerLoreEntry(Player)}
     */
    private void createPlayerLoreEntry(Player player) {
        logger.warning("PlayerLoreHandler.createPlayerLoreEntry is deprecated. Use PlayerManager directly.");
        // No-op - delegate to PlayerManager instead
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
    
    @Override
    public LoreType getHandlerType() {
        return LoreType.PLAYER;
    }
}

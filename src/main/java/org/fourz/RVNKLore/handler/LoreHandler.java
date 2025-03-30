package org.fourz.RVNKLore.handler;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;

/**
 * Interface for lore handlers
 * Handlers are responsible for validating and rendering lore entries
 */
public interface LoreHandler extends Listener {
    
    /**
     * Initialize the handler
     */
    void initialize();
    
    /**
     * Validate a lore entry before it's added to the database
     * 
     * @param entry The lore entry to validate
     * @return True if the entry is valid, false otherwise
     */
    boolean validateEntry(LoreEntry entry);
    
    /**
     * Create an item representing this lore entry
     * 
     * @param entry The lore entry
     * @return An ItemStack representing this lore
     */
    ItemStack createLoreItem(LoreEntry entry);
    
    /**
     * Display the lore to a player
     * 
     * @param entry The lore entry to display
     * @param player The player to display the lore to
     */
    default void displayLore(LoreEntry entry, Player player) {
        // Default implementation - can be overridden
        player.sendMessage("=== " + entry.getName() + " ===");
        player.sendMessage(entry.getDescription());
    }
    
    /**
     * Get the type of lore this handler manages
     * 
     * @return The lore type
     */
    default LoreType getHandlerType() {
        return LoreType.GENERIC;
    }
}

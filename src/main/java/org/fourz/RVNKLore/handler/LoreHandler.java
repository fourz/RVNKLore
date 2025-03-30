package org.fourz.RVNKLore.handler;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.fourz.RVNKLore.lore.LoreEntry;

/**
 * Interface for lore type handlers
 */
public interface LoreHandler {
    
    /**
     * Validates a lore entry
     * 
     * @param entry The lore entry to validate
     * @return True if the entry is valid, false otherwise
     */
    boolean validateEntry(LoreEntry entry);
    
    /**
     * Creates an item representation of a lore entry
     * 
     * @param entry The lore entry to represent
     * @return An ItemStack representing the lore entry
     */
    ItemStack createLoreItem(LoreEntry entry);
    
    /**
     * Displays lore information to a player
     * 
     * @param entry The lore entry to display
     * @param player The player to display the lore to
     */
    void displayLore(LoreEntry entry, Player player);
    
    /**
     * Gets the type of lore this handler is responsible for
     * 
     * @return The name of the lore type this handler manages
     */
    default String getHandlerType() {
        return getClass().getSimpleName().replace("LoreHandler", "").toUpperCase();
    }
}

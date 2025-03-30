package org.fourz.RVNKLore.lore;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
}

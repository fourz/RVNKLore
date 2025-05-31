package org.fourz.RVNKLore.lore.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Interface for in-game item lore editing and display.
 * Provides methods for adding, editing, and formatting lore on items.
 *
 * This is a stub for future implementation of the in-game item lore interface.
 */
public interface ItemLoreInterface {
    /**
     * Opens the lore editor UI for the player and item.
     * @param player The player editing the item
     * @param item The item to edit
     */
    void openLoreEditor(Player player, ItemStack item);

    /**
     * Applies lore to the given item.
     * @param item The item to modify
     * @param lore The lore lines to set
     * @return The updated ItemStack
     */
    ItemStack setLore(ItemStack item, java.util.List<String> lore);

    /**
     * Gets the current lore from the item.
     * @param item The item to inspect
     * @return The lore lines, or empty list if none
     */
    java.util.List<String> getLore(ItemStack item);
}

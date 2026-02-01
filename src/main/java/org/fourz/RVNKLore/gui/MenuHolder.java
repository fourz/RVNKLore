package org.fourz.RVNKLore.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base holder for custom GUI menus.
 * Implements InventoryHolder to allow identification of custom inventories.
 */
public abstract class MenuHolder implements InventoryHolder {

    protected Inventory inventory;
    protected final Player viewer;
    protected final Map<Integer, Consumer<InventoryClickEvent>> clickHandlers;
    protected MenuHolder parent;

    /**
     * Create a new menu holder.
     *
     * @param viewer The player viewing the menu
     * @param title The menu title
     * @param size The inventory size (multiple of 9, max 54)
     */
    public MenuHolder(Player viewer, String title, int size) {
        this.viewer = viewer;
        this.clickHandlers = new HashMap<>();
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Get the player viewing this menu.
     */
    public Player getViewer() {
        return viewer;
    }

    /**
     * Set the parent menu for back navigation.
     */
    public void setParent(MenuHolder parent) {
        this.parent = parent;
    }

    /**
     * Get the parent menu.
     */
    public MenuHolder getParent() {
        return parent;
    }

    /**
     * Set a click handler for a specific slot.
     *
     * @param slot The slot number
     * @param handler The click handler
     */
    public void setClickHandler(int slot, Consumer<InventoryClickEvent> handler) {
        clickHandlers.put(slot, handler);
    }

    /**
     * Handle a click event.
     *
     * @param event The click event
     */
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true); // Cancel by default

        int slot = event.getRawSlot();
        Consumer<InventoryClickEvent> handler = clickHandlers.get(slot);

        if (handler != null) {
            handler.accept(event);
        }
    }

    /**
     * Open this menu for the viewer.
     */
    public void open() {
        build();
        viewer.openInventory(inventory);
    }

    /**
     * Close the menu.
     */
    public void close() {
        viewer.closeInventory();
    }

    /**
     * Go back to parent menu.
     */
    public void goBack() {
        if (parent != null) {
            parent.open();
        } else {
            close();
        }
    }

    /**
     * Build the menu contents. Called before opening.
     */
    protected abstract void build();

    /**
     * Called when the menu is closed.
     */
    public void onClose() {
        // Override in subclasses if needed
    }
}

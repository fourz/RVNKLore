package org.fourz.RVNKLore.integration.citizens;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.lore.item.collection.ItemCollection;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GUI for browsing and purchasing items from a collection vendor.
 * Displays all items in a collection with pricing information.
 */
public class CollectionShopGUI implements Listener {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int CLOSE_BUTTON_SLOT = 49;

    private final RVNKLore plugin;
    private final Player player;
    private final ItemCollection collection;
    private final CollectionVendorTrait vendor;
    private final LogManager logger;

    private Inventory inventory;
    private int currentPage = 0;
    private List<ItemStack> availableItems;

    public CollectionShopGUI(RVNKLore plugin, Player player, ItemCollection collection, CollectionVendorTrait vendor) {
        this.plugin = plugin;
        this.player = player;
        this.collection = collection;
        this.vendor = vendor;
        this.logger = LogManager.getInstance(plugin, "CollectionShopGUI");
        this.availableItems = new ArrayList<>(collection.getItems());
    }

    /**
     * Open the shop GUI for the player.
     */
    public void open() {
        createInventory();
        player.openInventory(inventory);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Create the inventory GUI for the current page.
     */
    private void createInventory() {
        String title = collection.getName() + " §8(§7" + (currentPage + 1) + "§8)";
        inventory = Bukkit.createInventory(null, 54, title.length() > 32 ? title.substring(0, 32) : title);

        // Fill with available items from this collection
        int itemIndex = currentPage * ITEMS_PER_PAGE;
        int slotIndex = 0;

        while (slotIndex < ITEMS_PER_PAGE && itemIndex < availableItems.size()) {
            ItemStack item = availableItems.get(itemIndex);
            if (item != null) {
                ItemStack displayItem = item.clone();
                displayItem.setAmount(1);

                // Add price information to lore
                ItemMeta meta = displayItem.getItemMeta();
                if (meta == null) {
                    meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());
                }

                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                double price = calculatePrice(item);
                lore.add("§7Price: §6" + String.format("%.2f", price) + " coins");

                meta.setLore(lore);
                displayItem.setItemMeta(meta);

                inventory.setItem(slotIndex, displayItem);
            }

            itemIndex++;
            slotIndex++;
        }

        // Add navigation buttons
        addNavigationButtons();
    }

    /**
     * Add pagination and close buttons to the inventory.
     */
    private void addNavigationButtons() {
        // Previous page button (slot 47)
        if (currentPage > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta meta = prevButton.getItemMeta();
            meta.setDisplayName("§6← Previous");
            prevButton.setItemMeta(meta);
            inventory.setItem(47, prevButton);
        }

        // Next page button (slot 51)
        if ((currentPage + 1) * ITEMS_PER_PAGE < availableItems.size()) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta meta = nextButton.getItemMeta();
            meta.setDisplayName("§6Next →");
            nextButton.setItemMeta(meta);
            inventory.setItem(51, nextButton);
        }

        // Close button (slot 49)
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta meta = closeButton.getItemMeta();
        meta.setDisplayName("§cClose");
        closeButton.setItemMeta(meta);
        inventory.setItem(CLOSE_BUTTON_SLOT, closeButton);
    }

    /**
     * Calculate the price for an item based on vendor pricing.
     * TODO: Integrate with item value system from RVNKLore.
     */
    private double calculatePrice(ItemStack item) {
        // Base price: 100 coins per item (placeholder)
        double basePrice = 100.0;

        // Apply vendor multiplier
        return basePrice * vendor.getPriceMultiplier();
    }

    /**
     * Handle inventory clicks.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() != inventory) {
            return;
        }

        event.setCancelled(true);

        Player clickPlayer = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // Navigation buttons
        if (slot == 47) {
            // Previous page
            if (currentPage > 0) {
                currentPage--;
                createInventory();
                clickPlayer.openInventory(inventory);
            }
            return;
        }

        if (slot == 51) {
            // Next page
            if ((currentPage + 1) * ITEMS_PER_PAGE < availableItems.size()) {
                currentPage++;
                createInventory();
                clickPlayer.openInventory(inventory);
            }
            return;
        }

        if (slot == CLOSE_BUTTON_SLOT) {
            // Close shop
            clickPlayer.closeInventory();
            return;
        }

        // Item purchase
        if (slot >= 0 && slot < 45) {
            ItemStack clickedItem = inventory.getItem(slot);
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                purchaseItem(clickPlayer, clickedItem);
            }
        }
    }

    /**
     * Process item purchase.
     */
    private void purchaseItem(Player purchaser, ItemStack item) {
        double price = calculatePrice(item);

        // TODO: Check player currency/economy
        // For now, just add item and log

        ItemStack purchaseItem = item.clone();
        if (!vendor.purchaseItem(purchaser, purchaseItem, price)) {
            purchaser.sendMessage("§cCould not complete purchase.");
        }
    }

    /**
     * Handle inventory close to clean up the listener.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() != inventory) {
            return;
        }

        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
        logger.debug("Collection shop GUI closed for player: " + event.getPlayer().getName());
    }
}

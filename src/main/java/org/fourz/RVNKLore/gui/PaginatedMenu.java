package org.fourz.RVNKLore.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * A paginated menu that supports navigating through items.
 *
 * @param <T> The type of items being displayed
 */
public abstract class PaginatedMenu<T> extends MenuHolder {

    protected List<T> items;
    protected int page;
    protected int itemsPerPage;

    // Slot layout (for 54-slot inventory)
    protected static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    protected static final int PREV_PAGE_SLOT = 45;
    protected static final int PAGE_INFO_SLOT = 49;
    protected static final int NEXT_PAGE_SLOT = 53;
    protected static final int BACK_SLOT = 48;
    protected static final int CLOSE_SLOT = 50;

    /**
     * Create a paginated menu.
     *
     * @param viewer The player viewing the menu
     * @param title The menu title
     * @param items The items to display
     */
    public PaginatedMenu(Player viewer, String title, List<T> items) {
        super(viewer, title, 54);
        this.items = items;
        this.page = 1;
        this.itemsPerPage = ITEM_SLOTS.length;
    }

    @Override
    protected void build() {
        inventory.clear();
        clickHandlers.clear();

        // Fill border
        fillBorder();

        // Add items for current page
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, items.size());

        for (int i = 0; i < itemsPerPage && startIndex + i < items.size(); i++) {
            int slot = ITEM_SLOTS[i];
            T item = items.get(startIndex + i);
            ItemStack display = createItemDisplay(item);
            inventory.setItem(slot, display);

            // Set click handler
            final int itemIndex = startIndex + i;
            setClickHandler(slot, event -> onItemClick(event, items.get(itemIndex)));
        }

        // Add navigation
        addNavigation();
    }

    /**
     * Fill the border with filler items.
     */
    protected void fillBorder() {
        ItemStack filler = ItemBuilder.filler();
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, filler);
            inventory.setItem(45 + i, filler);
        }
        inventory.setItem(9, filler);
        inventory.setItem(17, filler);
        inventory.setItem(18, filler);
        inventory.setItem(26, filler);
        inventory.setItem(27, filler);
        inventory.setItem(35, filler);
        inventory.setItem(36, filler);
        inventory.setItem(44, filler);
    }

    /**
     * Add navigation buttons.
     */
    protected void addNavigation() {
        int totalPages = getTotalPages();

        // Previous page
        if (page > 1) {
            inventory.setItem(PREV_PAGE_SLOT, ItemBuilder.prevPage());
            setClickHandler(PREV_PAGE_SLOT, event -> {
                page--;
                build();
            });
        }

        // Page info
        inventory.setItem(PAGE_INFO_SLOT, ItemBuilder.pageInfo(page, totalPages));

        // Next page
        if (page < totalPages) {
            inventory.setItem(NEXT_PAGE_SLOT, ItemBuilder.nextPage());
            setClickHandler(NEXT_PAGE_SLOT, event -> {
                page++;
                build();
            });
        }

        // Back button
        if (parent != null) {
            inventory.setItem(BACK_SLOT, ItemBuilder.backButton());
            setClickHandler(BACK_SLOT, event -> goBack());
        }

        // Close button
        inventory.setItem(CLOSE_SLOT, ItemBuilder.closeButton());
        setClickHandler(CLOSE_SLOT, event -> close());
    }

    /**
     * Get total number of pages.
     */
    protected int getTotalPages() {
        if (items.isEmpty()) return 1;
        return (int) Math.ceil((double) items.size() / itemsPerPage);
    }

    /**
     * Get the current page.
     */
    public int getPage() {
        return page;
    }

    /**
     * Set the current page.
     */
    public void setPage(int page) {
        this.page = Math.max(1, Math.min(page, getTotalPages()));
    }

    /**
     * Create the display item for an entry.
     *
     * @param item The item to create a display for
     * @return The ItemStack to display
     */
    protected abstract ItemStack createItemDisplay(T item);

    /**
     * Handle a click on an item.
     *
     * @param event The click event
     * @param item The item that was clicked
     */
    protected abstract void onItemClick(InventoryClickEvent event, T item);
}

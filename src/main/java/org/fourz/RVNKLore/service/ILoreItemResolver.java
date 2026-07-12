package org.fourz.RVNKLore.service;

import org.bukkit.inventory.ItemStack;

/**
 * Resolves RVNKLore item identity from an ItemStack via PDC tag lookup.
 *
 * <p>Registered with RVNKCore ServiceRegistry by RVNKLore at startup.
 * Consume via soft-dep reflection (see {@code LoreItemResolverBridge} in RVNKQuests).</p>
 */
public interface ILoreItemResolver {

    /**
     * Returns the lore item name stored in this item's PDC, or null if absent or not a lore item.
     *
     * <p>Reads the {@code lore_item_name} persistent data container tag set by RVNKLore
     * when creating lore items. Tag is present on items created via {@link
     * org.fourz.RVNKLore.lore.item.ItemManager}.</p>
     *
     * @param item The item to inspect; may be null
     * @return The item identifier (e.g. {@code "heralds_mandate"}), or null
     */
    String getBookId(ItemStack item);
}

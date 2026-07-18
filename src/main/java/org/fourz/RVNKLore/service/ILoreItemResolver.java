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
     * Returns the lore item identifier stored in this item's PDC, or null if absent
     * or not a lore item.
     *
     * <p>Reads the {@code lore_item_name} persistent data container tag set by RVNKLore
     * when creating lore items. Tag is present on items created via {@link
     * org.fourz.RVNKLore.lore.item.ItemManager} — this covers <b>all</b> lore items,
     * not just books.</p>
     *
     * @param item The item to inspect; may be null
     * @return The item identifier (e.g. {@code "heralds_mandate"}), or null
     */
    String resolveItemId(ItemStack item);

    /**
     * @deprecated Misnomer — this never resolved book identity specifically; it returns
     *             any lore item's id. Use {@link #resolveItemId(ItemStack)}. Retained as a
     *             delegating alias for soft-dep reflection consumers that look up
     *             {@code getBookId} by name (#1498).
     */
    @Deprecated
    default String getBookId(ItemStack item) {
        return resolveItemId(item);
    }
}

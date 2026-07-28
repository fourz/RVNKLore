package org.fourz.RVNKLore.lore.item;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reverse of {@link ItemManager#createLoreItemInternal} — reads an in-hand
 * {@link ItemStack} into an {@link ItemProperties} so it can be published to the lore
 * catalog by the {@code [Forge]} feature.
 *
 * <p>Extracts material, display name, lore, custom model data, enchantments and (for
 * written books) pages. Item type is {@link ItemType#ENCHANTED} when enchantments are
 * present, else {@link ItemType#STANDARD} — matching the REST mint rule in
 * {@code LoreApiEndpointImpl.createItem}.</p>
 */
public final class ItemPropertiesExtractor {

    private ItemPropertiesExtractor() {}

    /**
     * Build an {@link ItemProperties} snapshot from a held item.
     *
     * @param held the item to read (must be non-null)
     * @return populated properties; display name is null when the item has none
     */
    public static ItemProperties from(ItemStack held) {
        Material material = held.getType();
        ItemMeta meta = held.getItemMeta();

        String displayName = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : null;
        ItemProperties props = new ItemProperties(material, displayName);

        boolean hasEnchants = false;
        if (meta != null) {
            if (meta.hasLore() && meta.getLore() != null) {
                props.setLore(new ArrayList<>(meta.getLore()));
            }
            if (meta.hasCustomModelData()) {
                props.setCustomModelData(meta.getCustomModelData());
            }
            Map<Enchantment, Integer> enchants = meta.getEnchants();
            if (enchants != null && !enchants.isEmpty()) {
                props.setEnchantments(new HashMap<>(enchants));
                hasEnchants = true;
            }
            if (material == Material.WRITTEN_BOOK && meta instanceof BookMeta bookMeta) {
                List<String> pages = bookMeta.getPages();
                if (pages != null && !pages.isEmpty()) {
                    props.setPages(new ArrayList<>(pages));
                }
            }
        }

        props.setItemType(hasEnchants ? ItemType.ENCHANTED : ItemType.STANDARD);
        return props;
    }
}

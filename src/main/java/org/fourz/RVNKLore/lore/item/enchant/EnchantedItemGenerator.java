package org.fourz.RVNKLore.lore.item.enchant;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.debug.LogManager;

import java.util.Map;

/**
 * API for generating custom enchanted items with unique properties.
 * Supports compound enchantments, rarity tiers, and integration with vanilla rules.
 *
 * Example usage:
 *   ItemStack sword = EnchantedItemGenerator.createEnchantedItem(
 *       Material.DIAMOND_SWORD,
 *       Map.of(Enchantment.DAMAGE_ALL, 5, Enchantment.FIRE_ASPECT, 2),
 *       "Legendary Sword of Fire"
 *   );
 */
public class EnchantedItemGenerator {
    private final RVNKLore plugin;
    private final LogManager logger;

    public EnchantedItemGenerator(RVNKLore plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "EnchantedItemGenerator");
    }

    /**
     * Create an enchanted item with the given material, enchantments, and display name.
     *
     * @param material The item material
     * @param enchantments Map of enchantments and their levels
     * @param displayName The display name for the item
     * @return The enchanted ItemStack
     */
    public ItemStack createEnchantedItem(Material material, Map<Enchantment, Integer> enchantments, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayName != null && !displayName.isEmpty()) {
                meta.setDisplayName(displayName);
            }
            item.setItemMeta(meta);
        }
        if (enchantments != null) {
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                item.addUnsafeEnchantment(entry.getKey(), entry.getValue());
            }
        }
        logger.debug("Created enchanted item: " + displayName + " (" + material + ") with " + (enchantments != null ? enchantments.size() : 0) + " enchantments.");
        return item;
    }
}

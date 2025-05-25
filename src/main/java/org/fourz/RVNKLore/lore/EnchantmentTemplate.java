package org.fourz.RVNKLore.lore;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import java.util.Map;

/**
 * Template for generating enchantment profiles based on item type and tier.
 * Allows for predefined or random selection of enchantments.
 */
public class EnchantmentTemplate {
    private final Material material;
    private final EnchantmentTier tier;
    private final Map<Enchantment, Integer> baseEnchantments;

    public EnchantmentTemplate(Material material, EnchantmentTier tier, Map<Enchantment, Integer> baseEnchantments) {
        this.material = material;
        this.tier = tier;
        this.baseEnchantments = baseEnchantments;
    }

    public Material getMaterial() {
        return material;
    }

    public EnchantmentTier getTier() {
        return tier;
    }

    public Map<Enchantment, Integer> getBaseEnchantments() {
        return baseEnchantments;
    }
}

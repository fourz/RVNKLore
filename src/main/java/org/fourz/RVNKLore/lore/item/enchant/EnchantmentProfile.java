package org.fourz.RVNKLore.lore.item.enchant;

import org.bukkit.enchantments.Enchantment;

import java.util.Map;

/**
 * Represents a profile of enchantments and their levels for a generated item.
 * Used to encapsulate compound enchantments and rarity logic.
 */
public class EnchantmentProfile {
    private final Map<Enchantment, Integer> enchantments;
    private final EnchantmentTier tier;

    public EnchantmentProfile(Map<Enchantment, Integer> enchantments, EnchantmentTier tier) {
        this.enchantments = enchantments;
        this.tier = tier;
    }

    public Map<Enchantment, Integer> getEnchantments() {
        return enchantments;
    }

    public EnchantmentTier getTier() {
        return tier;
    }
}

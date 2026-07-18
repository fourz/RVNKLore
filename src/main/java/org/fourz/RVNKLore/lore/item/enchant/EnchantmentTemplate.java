package org.fourz.RVNKLore.lore.item.enchant;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    // ── Material-aware tier→enchant-set selection (#1504) ────────────────────────
    // Ported from scripts/minecraft/gear.item.py (GEAR_SETS/TIERS). Curated PRIMARY
    // enchants per material category, ordered most-important first; unbreaking/mending
    // are added by tier. Conflicting pairs (e.g. Infinity+Mending) are avoided.

    /** One curated enchant and its cap. */
    private record Ench(String key, int max) {}

    /** Category detection — MORE SPECIFIC FIRST (pickaxe before axe, crossbow before bow). */
    private static String categoryOf(Material material) {
        String m = material.name().toLowerCase();
        for (String cat : new String[]{"crossbow", "bow", "pickaxe", "axe", "fishing_rod",
                "sword", "shovel", "hoe", "trident", "shears", "helmet", "chestplate",
                "leggings", "boots", "elytra", "shield"}) {
            if (m.contains(cat)) return cat;
        }
        return "";
    }

    private static List<Ench> gearSet(String cat) {
        switch (cat) {
            case "sword":       return List.of(new Ench("sharpness", 5), new Ench("looting", 3),
                                               new Ench("fire_aspect", 2), new Ench("knockback", 2));
            case "axe":         return List.of(new Ench("sharpness", 5), new Ench("efficiency", 5));
            case "pickaxe":
            case "shovel":
            case "hoe":         return List.of(new Ench("efficiency", 5), new Ench("fortune", 3));
            case "bow":         return List.of(new Ench("power", 5), new Ench("flame", 1), new Ench("punch", 2));
            case "crossbow":    return List.of(new Ench("quick_charge", 3), new Ench("multishot", 1),
                                               new Ench("piercing", 4));
            case "trident":     return List.of(new Ench("impaling", 5), new Ench("loyalty", 3),
                                               new Ench("channeling", 1));
            case "fishing_rod": return List.of(new Ench("luck_of_the_sea", 3), new Ench("lure", 3));
            case "shears":      return List.of(new Ench("efficiency", 5));
            case "helmet":      return List.of(new Ench("protection", 4), new Ench("respiration", 3),
                                               new Ench("aqua_affinity", 1));
            case "chestplate":  return List.of(new Ench("protection", 4), new Ench("thorns", 3));
            case "leggings":    return List.of(new Ench("protection", 4), new Ench("swift_sneak", 3));
            case "boots":       return List.of(new Ench("protection", 4), new Ench("feather_falling", 4),
                                               new Ench("depth_strider", 3));
            default:            return List.of(); // elytra, shield, unknown
        }
    }

    private static Enchantment byKey(String key) {
        NamespacedKey nk = NamespacedKey.fromString(key.contains(":") ? key : "minecraft:" + key);
        return nk != null ? Enchantment.getByKey(nk) : null;
    }

    /**
     * Select a curated enchant map for a material + tier, matching gear.item.py --tier.
     * Unknown enchant keys (removed/renamed across versions) are skipped.
     */
    public static Map<Enchantment, Integer> selectEnchants(Material material, EnchantmentTier tier) {
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        List<Ench> primaries = gearSet(categoryOf(material));

        int num; double factor; int unbreaking; boolean mending;
        switch (tier) {
            case COMMON:    num = 1;  factor = 0.4; unbreaking = 1; mending = false; break;
            case UNCOMMON:  num = 1;  factor = 0.7; unbreaking = 2; mending = false; break;
            case RARE:      num = 2;  factor = 1.0; unbreaking = 3; mending = true;  break;
            case EPIC:      num = 3;  factor = 1.0; unbreaking = 3; mending = true;  break;
            case LEGENDARY:
            default:        num = 99; factor = 1.0; unbreaking = 3; mending = true;  break;
        }

        for (int i = 0; i < primaries.size() && i < num; i++) {
            Ench e = primaries.get(i);
            Enchantment ench = byKey(e.key());
            if (ench != null) {
                out.put(ench, Math.max(1, (int) Math.round(e.max() * factor)));
            }
        }
        if (unbreaking > 0) {
            Enchantment u = byKey("unbreaking");
            if (u != null) out.put(u, unbreaking);
        }
        if (mending) {
            Enchantment m = byKey("mending");
            if (m != null) out.put(m, 1);
        }
        return out;
    }
}

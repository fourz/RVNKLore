package org.fourz.RVNKLore.handler;

import org.bukkit.Location;
import org.bukkit.Material;
import org.fourz.RVNKLore.lore.LoreEntry;
import org.fourz.RVNKLore.lore.LoreType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enchanting-table lore entries were rejected on every tier: typed ITEM with no material, so
 * LoreEntryRepository refused them, and a per-material name would collide on the
 * UNIQUE (name, entry_type) key after the first one.
 */
@DisplayName("EnchantedItemLoreHandler entry builder")
class EnchantedItemLoreHandlerTest {

    private static final UUID PLAYER = UUID.fromString("0e94bd8f-a7eb-47b8-9100-38d640249cb7");
    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 9, 18, 19, 16, 15);

    private static Map<String, Integer> pickaxeEnchants() {
        Map<String, Integer> enchants = new LinkedHashMap<>();
        enchants.put("efficiency", 5);
        enchants.put("unbreaking", 3);
        return enchants;
    }

    private static LoreEntry build(String player, LocalDateTime when) {
        return EnchantedItemLoreHandler.buildEnchantmentEntry(player, PLAYER, Material.DIAMOND_PICKAXE,
                pickaxeEnchants(), 30, new Location(null, 10, 64, -20), when);
    }

    @Test
    @DisplayName("entry is typed ENCHANTMENT, not ITEM")
    void typedEnchantment() {
        assertEquals(LoreType.ENCHANTMENT, build("crumpetm32588", WHEN).getType());
    }

    @Test
    @DisplayName("entry carries the material the ITEM guard used to reject")
    void carriesMaterial() {
        assertEquals("DIAMOND_PICKAXE", build("crumpetm32588", WHEN).getMetadata("material"));
    }

    @Test
    @DisplayName("name, description and metadata record who, what, and the cost")
    void recordsPayload() {
        LoreEntry entry = build("crumpetm32588", WHEN);
        assertEquals("crumpetm32588's Diamond Pickaxe (2026-09-18 19:16:15)", entry.getName());
        assertEquals("crumpetm32588 enchanted a Diamond Pickaxe with Efficiency V, Unbreaking III for 30 levels.",
                entry.getDescription());
        assertEquals(PLAYER.toString(), entry.getMetadata("enchanter_uuid"));
        assertEquals("30", entry.getMetadata("exp_cost"));
        assertEquals("efficiency:5,unbreaking:3", entry.getMetadata("enchantments"));
        assertEquals("crumpetm32588", entry.getSubmittedBy());
        assertTrue(entry.isApproved());
        assertNotNull(entry.getLocation());
    }

    @Test
    @DisplayName("two enchants of the same material get different names (UNIQUE name,type)")
    void namesDoNotCollide() {
        assertNotEquals(build("crumpetm32588", WHEN).getName(),
                build("crumpetm32588", WHEN.plusSeconds(11)).getName());
        assertNotEquals(build("crumpetm32588", WHEN).getName(),
                build("BHScreep", WHEN).getName());
    }

    @Test
    @DisplayName("passes the handler's own validation (name + description)")
    void validatesAsDefault() {
        LoreEntry entry = build("crumpetm32588", WHEN);
        assertFalse(entry.getName().isEmpty());
        assertFalse(entry.getDescription().isEmpty());
    }

    @Test
    @DisplayName("pretty names and roman numerals")
    void formatting() {
        assertEquals("Fire Aspect", EnchantedItemLoreHandler.prettyName("fire_aspect"));
        assertEquals("Book", EnchantedItemLoreHandler.prettyName("BOOK"));
        assertEquals("IV", EnchantedItemLoreHandler.roman(4));
        assertEquals("15", EnchantedItemLoreHandler.roman(15));
    }
}

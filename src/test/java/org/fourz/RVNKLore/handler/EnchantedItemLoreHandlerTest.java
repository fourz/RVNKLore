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
        return build(player, when, false);
    }

    private static LoreEntry build(String player, LocalDateTime when, boolean approved) {
        return EnchantedItemLoreHandler.buildEnchantmentEntry(player, PLAYER, Material.DIAMOND_PICKAXE,
                pickaxeEnchants(), 30, new Location(null, 10, 64, -20), when, approved);
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
        assertEquals("crumpetm32588's Diamond Pickaxe (2026-09-18 19:16:15.000)", entry.getName());
        assertEquals("crumpetm32588 enchanted a Diamond Pickaxe with Efficiency V, Unbreaking III for 30 levels.",
                entry.getDescription());
        assertEquals(PLAYER.toString(), entry.getMetadata("enchanter_uuid"));
        assertEquals("30", entry.getMetadata("exp_cost"));
        assertEquals("efficiency:5,unbreaking:3", entry.getMetadata("enchantments"));
        // submitter_uuid is UUID-keyed so attribution survives a rename (PR #21 review);
        // the readable name stays in the description and metadata.
        assertEquals(PLAYER.toString(), entry.getSubmittedBy());
        assertEquals("crumpetm32588", entry.getMetadata("enchanter_name"));
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
    @DisplayName("two enchants inside the SAME second still get different names (PR #21 review)")
    void namesDoNotCollideWithinOneSecond() {
        // At second granularity these two shared a name, so the database's UNIQUE(name, entry_type)
        // rejected the second one and the loss was only logged at DEBUG.
        assertNotEquals(build("crumpetm32588", WHEN).getName(),
                build("crumpetm32588", WHEN.plusNanos(40_000_000L)).getName());
    }

    @Test
    @DisplayName("passes the handler's own validation (name + description)")
    void validatesAsDefault() {
        LoreEntry entry = build("crumpetm32588", WHEN);
        assertFalse(entry.getName().isEmpty());
        assertFalse(entry.getDescription().isEmpty());
    }

    @Test
    @DisplayName("approval follows rvnklore.approve.own, not unconditional")
    void approvalIsPassedThrough() {
        assertFalse(build("crumpetm32588", WHEN, false).isApproved());
        assertTrue(build("crumpetm32588", WHEN, true).isApproved());
    }

    @Test
    @DisplayName("opt-in: only an explicit true counts as consent")
    void optInRequiresExplicitTrue() {
        assertFalse(EnchantChronicle.isOptedIn(null));
        assertFalse(EnchantChronicle.isOptedIn(Map.of()));
        assertFalse(EnchantChronicle.isOptedIn(Map.of(EnchantChronicle.META_KEY, "false")));
        assertFalse(EnchantChronicle.isOptedIn(Map.of(EnchantChronicle.META_KEY, "")));
        assertTrue(EnchantChronicle.isOptedIn(Map.of(EnchantChronicle.META_KEY, "true")));
        assertTrue(EnchantChronicle.isOptedIn(Map.of(EnchantChronicle.META_KEY, "TRUE")));
    }

    @Test
    @DisplayName("gate names match plugin.yml and the prefs metadata key")
    void gateConstants() throws Exception {
        assertEquals("enchant_chronicle", EnchantChronicle.META_KEY);
        try (java.io.InputStream in = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(in, "plugin.yml on the test classpath");
            String yml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(yml.contains("\n  " + EnchantChronicle.PERMISSION + ":\n"),
                    "plugin.yml declares " + EnchantChronicle.PERMISSION);
        }
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

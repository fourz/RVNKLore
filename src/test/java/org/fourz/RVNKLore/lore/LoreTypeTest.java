package org.fourz.RVNKLore.lore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LoreType enum including new MONUMENT type.
 */
@DisplayName("LoreType Enum")
class LoreTypeTest {

    @Test
    @DisplayName("MONUMENT type exists with correct description")
    void monumentTypeExists() {
        LoreType monument = LoreType.valueOf("MONUMENT");
        assertNotNull(monument);
        assertEquals("Monument or memorial marker", monument.getDescription());
    }

    @Test
    @DisplayName("All expected lore types exist")
    void allExpectedTypesExist() {
        Set<String> typeNames = Arrays.stream(LoreType.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertTrue(typeNames.contains("GENERIC"));
        assertTrue(typeNames.contains("PLAYER"));
        assertTrue(typeNames.contains("CITY"));
        assertTrue(typeNames.contains("LANDMARK"));
        assertTrue(typeNames.contains("FACTION"));
        assertTrue(typeNames.contains("PATH"));
        assertTrue(typeNames.contains("ITEM"));
        assertTrue(typeNames.contains("EVENT"));
        assertTrue(typeNames.contains("QUEST"));
        assertTrue(typeNames.contains("ENCHANTMENT"));
        assertTrue(typeNames.contains("MONUMENT"));
        assertTrue(typeNames.contains("HEAD"));
    }

    @Test
    @DisplayName("Total lore type count is 15")
    void totalTypeCount() {
        assertEquals(15, LoreType.values().length);
    }

    @Test
    @DisplayName("Each type has a non-empty description")
    void allTypesHaveDescriptions() {
        for (LoreType type : LoreType.values()) {
            assertNotNull(type.getDescription(), type.name() + " has null description");
            assertFalse(type.getDescription().isEmpty(), type.name() + " has empty description");
        }
    }

    @Test
    @DisplayName("valueOf works for all types")
    void valueOfWorksForAllTypes() {
        for (LoreType type : LoreType.values()) {
            assertEquals(type, LoreType.valueOf(type.name()));
        }
    }
}

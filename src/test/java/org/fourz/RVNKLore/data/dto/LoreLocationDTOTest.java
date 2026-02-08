package org.fourz.RVNKLore.data.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LoreLocationDTO record.
 */
@DisplayName("LoreLocationDTO")
class LoreLocationDTOTest {

    @Test
    @DisplayName("Constructor sets all fields correctly")
    void constructorSetsFields() {
        LoreLocationDTO dto = new LoreLocationDTO(1, "entry-abc", "world", 100.5, 64.0, -200.3, "PRIMARY", "Spawn");

        assertEquals(1, dto.id());
        assertEquals("entry-abc", dto.entryId());
        assertEquals("world", dto.world());
        assertEquals(100.5, dto.x(), 0.001);
        assertEquals(64.0, dto.y(), 0.001);
        assertEquals(-200.3, dto.z(), 0.001);
        assertEquals("PRIMARY", dto.locationType());
        assertEquals("Spawn", dto.label());
    }

    @Test
    @DisplayName("Null entryId throws NullPointerException")
    void nullEntryIdThrows() {
        assertThrows(NullPointerException.class, () ->
                new LoreLocationDTO(0, null, "world", 0, 0, 0, "PRIMARY", null));
    }

    @Test
    @DisplayName("Null world throws NullPointerException")
    void nullWorldThrows() {
        assertThrows(NullPointerException.class, () ->
                new LoreLocationDTO(0, "entry-1", null, 0, 0, 0, "PRIMARY", null));
    }

    @Test
    @DisplayName("Null locationType defaults to PRIMARY")
    void nullLocationTypeDefaultsToPrimary() {
        LoreLocationDTO dto = new LoreLocationDTO(0, "entry-1", "world", 0, 0, 0, null, null);
        assertEquals("PRIMARY", dto.locationType());
    }

    @Test
    @DisplayName("Label can be null")
    void labelCanBeNull() {
        LoreLocationDTO dto = new LoreLocationDTO(0, "entry-1", "world", 0, 0, 0, "SECONDARY", null);
        assertNull(dto.label());
    }

    @Test
    @DisplayName("Record equality works correctly")
    void recordEquality() {
        LoreLocationDTO dto1 = new LoreLocationDTO(1, "e-1", "world", 10, 20, 30, "PRIMARY", "test");
        LoreLocationDTO dto2 = new LoreLocationDTO(1, "e-1", "world", 10, 20, 30, "PRIMARY", "test");
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    @DisplayName("Different DTOs are not equal")
    void differentDtosNotEqual() {
        LoreLocationDTO dto1 = new LoreLocationDTO(1, "e-1", "world", 10, 20, 30, "PRIMARY", "test");
        LoreLocationDTO dto2 = new LoreLocationDTO(2, "e-1", "world", 10, 20, 30, "PRIMARY", "test");
        assertNotEquals(dto1, dto2);
    }
}

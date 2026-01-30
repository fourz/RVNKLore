package org.fourz.RVNKLore.data.dto;

import org.fourz.RVNKLore.lore.LoreType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.sql.Timestamp;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LoreEntryDTO Java Record.
 * Tests immutability, validation, builder pattern, and entity conversion.
 */
class LoreEntryDTOTest {

    @Test
    @DisplayName("Record creation with all fields")
    void testRecordCreation() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Map<String, String> metadata = Map.of("key1", "value1", "key2", "value2");

        LoreEntryDTO dto = new LoreEntryDTO(
            "entry-123",
            LoreType.ITEM,
            "Test Item",
            "A test item description",
            "{nbt:data}",
            "player-uuid",
            true,
            now,
            now,
            metadata
        );

        assertEquals("entry-123", dto.entryId());
        assertEquals(LoreType.ITEM, dto.type());
        assertEquals("Test Item", dto.name());
        assertEquals("A test item description", dto.description());
        assertEquals("{nbt:data}", dto.nbtData());
        assertEquals("player-uuid", dto.submittedBy());
        assertTrue(dto.approved());
        assertEquals(now, dto.createdAt());
        assertEquals(now, dto.updatedAt());
        assertEquals(2, dto.metadata().size());
    }

    @Test
    @DisplayName("Record throws on null entryId")
    void testNullEntryIdThrows() {
        assertThrows(NullPointerException.class, () -> {
            new LoreEntryDTO(null, LoreType.ITEM, "Name", "Desc", null, null, false, null, null, null);
        });
    }

    @Test
    @DisplayName("Record throws on null type")
    void testNullTypeThrows() {
        assertThrows(NullPointerException.class, () -> {
            new LoreEntryDTO("entry-123", null, "Name", "Desc", null, null, false, null, null, null);
        });
    }

    @Test
    @DisplayName("Null metadata becomes empty map (defensive copy)")
    void testNullMetadataBecomesEmptyMap() {
        LoreEntryDTO dto = new LoreEntryDTO(
            "entry-123", LoreType.GENERIC, "Name", "Desc", null, null, false, null, null, null
        );

        assertNotNull(dto.metadata());
        assertTrue(dto.metadata().isEmpty());
    }

    @Test
    @DisplayName("Metadata is immutable (defensive copy)")
    void testMetadataImmutability() {
        Map<String, String> originalMetadata = Map.of("key", "value");
        LoreEntryDTO dto = new LoreEntryDTO(
            "entry-123", LoreType.GENERIC, "Name", "Desc", null, null, false, null, null, originalMetadata
        );

        // Attempting to modify should throw
        assertThrows(UnsupportedOperationException.class, () -> {
            dto.metadata().put("newKey", "newValue");
        });
    }

    @Test
    @DisplayName("Builder creates valid DTO")
    void testBuilderPattern() {
        LoreEntryDTO dto = LoreEntryDTO.builder()
            .entryId("builder-entry")
            .type(LoreType.LANDMARK)
            .name("Builder Test")
            .description("Created via builder")
            .approved(true)
            .build();

        assertEquals("builder-entry", dto.entryId());
        assertEquals(LoreType.LANDMARK, dto.type());
        assertEquals("Builder Test", dto.name());
        assertTrue(dto.approved());
    }

    @Test
    @DisplayName("Builder uses default values")
    void testBuilderDefaults() {
        LoreEntryDTO dto = LoreEntryDTO.builder()
            .entryId("default-test")
            .name("Default Test")
            .build();

        assertEquals(LoreType.GENERIC, dto.type()); // Default type
        assertFalse(dto.approved()); // Default approved
        assertNotNull(dto.createdAt()); // Default timestamp
        assertTrue(dto.metadata().isEmpty()); // Default empty metadata
    }

    @Test
    @DisplayName("Record equality based on values")
    void testRecordEquality() {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        LoreEntryDTO dto1 = new LoreEntryDTO(
            "same-id", LoreType.ITEM, "Same Name", "Same Desc", null, null, false, now, now, Map.of()
        );
        LoreEntryDTO dto2 = new LoreEntryDTO(
            "same-id", LoreType.ITEM, "Same Name", "Same Desc", null, null, false, now, now, Map.of()
        );

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }
}

package org.fourz.RVNKLore.data.dto;

import org.fourz.RVNKLore.lore.LoreSubmission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LoreSubmissionDTO Java Record.
 * Tests immutability, validation, builder pattern, and entity conversion.
 */
class LoreSubmissionDTOTest {

    @Test
    @DisplayName("Record creation with all fields")
    void testRecordCreation() {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        LoreSubmissionDTO dto = new LoreSubmissionDTO(
            1,
            "entry-123",
            "test-slug",
            "PUBLIC",
            "PENDING",
            "submitter-uuid",
            "creator-uuid",
            now,
            "PENDING",
            null,
            null,
            0,
            null,
            now,
            now,
            1,
            true,
            "{\"content\":\"test\"}"
        );

        assertEquals(1, dto.id());
        assertEquals("entry-123", dto.entryId());
        assertEquals("test-slug", dto.slug());
        assertEquals("PUBLIC", dto.visibility());
        assertEquals("PENDING", dto.status());
        assertEquals("submitter-uuid", dto.submitterUuid());
        assertTrue(dto.isCurrentVersion());
        assertEquals("{\"content\":\"test\"}", dto.content());
    }

    @Test
    @DisplayName("Record throws on null entryId")
    void testNullEntryIdThrows() {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        assertThrows(NullPointerException.class, () -> {
            new LoreSubmissionDTO(1, null, "slug", "PUBLIC", "PENDING",
                null, null, now, "PENDING", null, null, 0, null, now, now, 1, true, null);
        });
    }

    @Test
    @DisplayName("Builder creates valid DTO")
    void testBuilderPattern() {
        LoreSubmissionDTO dto = LoreSubmissionDTO.builder()
            .id(42)
            .entryId("builder-entry")
            .slug("builder-slug")
            .content("{\"test\":true}")
            .build();

        assertEquals(42, dto.id());
        assertEquals("builder-entry", dto.entryId());
        assertEquals("builder-slug", dto.slug());
        assertEquals("{\"test\":true}", dto.content());
    }

    @Test
    @DisplayName("Builder uses default values")
    void testBuilderDefaults() {
        LoreSubmissionDTO dto = LoreSubmissionDTO.builder()
            .entryId("default-test")
            .build();

        assertEquals("PUBLIC", dto.visibility()); // Default visibility
        assertEquals("PENDING", dto.status()); // Default status
        assertEquals("PENDING", dto.approvalStatus()); // Default approval status
        assertEquals(1, dto.contentVersion()); // Default version
        assertTrue(dto.isCurrentVersion()); // Default is current
        assertEquals(0, dto.viewCount()); // Default view count
    }

    @Test
    @DisplayName("toEntity creates LoreSubmission with correct values")
    void testToEntity() {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        LoreSubmissionDTO dto = LoreSubmissionDTO.builder()
            .id(1)
            .entryId("entity-test")
            .slug("entity-slug")
            .visibility("PRIVATE")
            .status("ACTIVE")
            .submitterUuid("player-123")
            .createdBy("player-123")
            .submissionDate(now)
            .approvalStatus("APPROVED")
            .contentVersion(2)
            .isCurrentVersion(true)
            .content("{\"data\":\"value\"}")
            .build();

        LoreSubmission entity = dto.toEntity();

        assertEquals(1, entity.getId());
        assertEquals("entity-test", entity.getEntryId());
        assertEquals("entity-slug", entity.getSlug());
        assertEquals("PRIVATE", entity.getVisibility());
        assertEquals("ACTIVE", entity.getStatus());
        assertEquals("player-123", entity.getSubmitterUuid());
        assertEquals("APPROVED", entity.getApprovalStatus());
        assertEquals(2, entity.getContentVersion());
        assertTrue(entity.isCurrentVersion());
        assertEquals("{\"data\":\"value\"}", entity.getContent());
    }

    @Test
    @DisplayName("Record equality based on values")
    void testRecordEquality() {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        LoreSubmissionDTO dto1 = LoreSubmissionDTO.builder()
            .id(1)
            .entryId("same-id")
            .slug("same-slug")
            .createdAt(now)
            .updatedAt(now)
            .build();

        LoreSubmissionDTO dto2 = LoreSubmissionDTO.builder()
            .id(1)
            .entryId("same-id")
            .slug("same-slug")
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    @DisplayName("Record accessor methods use field names (not getXxx)")
    void testRecordAccessorSyntax() {
        LoreSubmissionDTO dto = LoreSubmissionDTO.builder()
            .id(99)
            .entryId("accessor-test")
            .build();

        // Java records use field-name accessors, not getXxx
        assertEquals(99, dto.id());
        assertEquals("accessor-test", dto.entryId());
        assertTrue(dto.isCurrentVersion());
    }
}

package org.fourz.RVNKLore.lore.item.collection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers LoreCollection.setCreatedAt(), which restores the stored creation time on load (#1956).
 */
@DisplayName("LoreCollection createdAt")
class LoreCollectionCreatedAtTest {

    private static final long JAN_2026 = 1_767_225_600_000L;
    private static final long JUN_2026 = 1_780_272_000_000L;

    @Test
    @DisplayName("constructor stamps now, so an unset collection is not zero")
    void constructor_stampsNow() {
        LoreCollection c = new LoreCollection("a", "A", "");
        assertTrue(c.getCreatedAt() > 0, "constructor should stamp a real timestamp");
    }

    @Test
    @DisplayName("setCreatedAt overrides the constructor stamp")
    void setCreatedAt_overrides() {
        LoreCollection c = new LoreCollection("a", "A", "");
        c.setCreatedAt(JAN_2026);
        assertEquals(JAN_2026, c.getCreatedAt());
    }

    @Test
    @DisplayName("non-positive values are ignored, keeping the constructor default")
    void setCreatedAt_ignoresNonPositive() {
        LoreCollection c = new LoreCollection("a", "A", "");
        long original = c.getCreatedAt();
        c.setCreatedAt(0);
        assertEquals(original, c.getCreatedAt(), "0 should not blank the timestamp");
        c.setCreatedAt(-1);
        assertEquals(original, c.getCreatedAt(), "negative should not blank the timestamp");
    }

    @Test
    @DisplayName("newest-first ordering survives a simulated reload")
    void ordering_holdsAfterReload() {
        // Reload order deliberately puts the OLDER collection first, as a database SELECT may.
        // Before the fix both objects were stamped with load time and this ordering was arbitrary.
        LoreCollection older = new LoreCollection("older", "Older", "");
        older.setCreatedAt(JAN_2026);
        LoreCollection newer = new LoreCollection("newer", "Newer", "");
        newer.setCreatedAt(JUN_2026);

        List<LoreCollection> loaded = new ArrayList<>(List.of(older, newer));
        loaded.sort(Comparator.comparingLong(LoreCollection::getCreatedAt).reversed());

        assertEquals("newer", loaded.get(0).getId(), "newest must sort first after a reload");
        assertEquals("older", loaded.get(1).getId());
    }
}

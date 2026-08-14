package org.fourz.RVNKLore.lore.item.collection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers CollectionTheme.parse() and the sentinel problem it exists to solve (#1955).
 */
@DisplayName("CollectionTheme parsing")
class CollectionThemeParseTest {

    @Nested
    @DisplayName("parse distinguishes CUSTOM from unknown")
    class ParseTests {

        @Test
        @DisplayName("CUSTOM resolves to CUSTOM rather than reading as unknown")
        void parse_custom_resolves() {
            // The whole point: `/lore collection list Custom` used to be rejected as an unknown
            // theme because CUSTOM doubled as the not-found sentinel.
            assertEquals(CollectionTheme.CUSTOM, CollectionTheme.parse("Custom"));
            assertEquals(CollectionTheme.CUSTOM, CollectionTheme.parse("CUSTOM"));
            assertEquals(CollectionTheme.CUSTOM, CollectionTheme.parse("custom"));
        }

        @Test
        @DisplayName("unrecognised input returns null, not CUSTOM")
        void parse_unknown_returnsNull() {
            assertNull(CollectionTheme.parse("nonsense_theme"));
            assertNull(CollectionTheme.parse(""));
            assertNull(CollectionTheme.parse("   "));
            assertNull(CollectionTheme.parse(null));
        }

        @Test
        @DisplayName("matches display name and enum name, ignoring case and surrounding space")
        void parse_matchesBothForms() {
            assertEquals(CollectionTheme.MEDIEVAL, CollectionTheme.parse("Medieval"));
            assertEquals(CollectionTheme.MEDIEVAL, CollectionTheme.parse("MEDIEVAL"));
            assertEquals(CollectionTheme.MEDIEVAL, CollectionTheme.parse("  medieval  "));
            assertEquals(CollectionTheme.LEGENDARY, CollectionTheme.parse("legendary"));
        }

        @Test
        @DisplayName("every declared theme round-trips through its own display name")
        void parse_allThemesRoundTrip() {
            for (CollectionTheme theme : CollectionTheme.values()) {
                assertEquals(theme, CollectionTheme.parse(theme.getDisplayName()),
                        "display name should resolve: " + theme.getDisplayName());
                assertEquals(theme, CollectionTheme.parse(theme.name()),
                        "enum name should resolve: " + theme.name());
            }
        }
    }

    @Nested
    @DisplayName("fromDisplayName keeps its lenient contract")
    class FromDisplayNameTests {

        @Test
        @DisplayName("still falls back to CUSTOM, which is why callers needing the difference use parse")
        void fromDisplayName_unknownFallsBackToCustom() {
            assertEquals(CollectionTheme.CUSTOM, CollectionTheme.fromDisplayName("nonsense_theme"));
            assertEquals(CollectionTheme.CUSTOM, CollectionTheme.fromDisplayName(null));
        }
    }
}

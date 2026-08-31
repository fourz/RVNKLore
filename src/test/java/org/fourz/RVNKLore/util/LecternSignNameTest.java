package org.fourz.RVNKLore.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for multi-line lectern sign names (#2021).
 *
 * <p>A sign line holds roughly fifteen rendered characters, so {@code RAVENFORGE WAYBILL} (18)
 * cannot be typed onto one line — the client stops at {@code RAVENFORGE WAYBI}. Names now span
 * lines 2-4, joined with single spaces on read and word-wrapped on write.</p>
 *
 * <p>The property that matters most is the <b>round trip</b>: what
 * {@link LecternSignUtil#wrapNameOntoLines} writes, {@link LecternSignUtil#joinNameLines} must
 * read back byte-identically. Lore items resolve by exact name, so a wrap the reader does not
 * un-wrap identically would silently break every long-named tome lectern — worse than the
 * clipped-but-correct single line it replaces.</p>
 */
@DisplayName("Lectern sign multi-line names")
class LecternSignNameTest {

    /** Builds the four-line array a SignChangeEvent hands over: tag on line 0, payload below. */
    private static String[] sign(String tag, String... payload) {
        String[] lines = { tag, "", "", "" };
        for (int i = 0; i < payload.length && i < 3; i++) {
            lines[i + 1] = payload[i];
        }
        return lines;
    }

    @Nested
    @DisplayName("Round trip")
    class RoundTrip {

        @Test
        @DisplayName("the motivating case survives wrap then join")
        void ravenforgeWaybill() {
            String name = "RAVENFORGE WAYBILL";
            String[] wrapped = LecternSignUtil.wrapNameOntoLines(name);

            assertTrue(wrapped[0].length() <= 15, "line 2 must fit: " + wrapped[0]);
            assertEquals(name, LecternSignUtil.joinNameLines(
                new String[] { LecternSignUtil.TOME_TAG, wrapped[0], wrapped[1], wrapped[2] }));
        }

        @Test
        @DisplayName("every name we have minted round-trips")
        void mintedNamesRoundTrip() {
            for (String name : new String[] {
                "THE CROSSING TALLY", "RAVENFORGE WAYBILL", "THE QUIET WORLD",
                "THE RECORD OF NOCTURNE", "Nickel's Pick", "Ravenforge Shard",
                "SOMETHING STANDS WATCH", "THE KINGDOM LOOKED UP" }) {

                String[] w = LecternSignUtil.wrapNameOntoLines(name);
                assertEquals(name,
                    LecternSignUtil.joinNameLines(
                        new String[] { LecternSignUtil.TOME_TAG, w[0], w[1], w[2] }),
                    "round trip failed for: " + name);
            }
        }

        @Test
        @DisplayName("a name too long for three lines still round-trips rather than truncating")
        void overlongNameKeepsItsTail() {
            String name = "THE VERY LONG AND ENTIRELY UNREASONABLE NAME OF A BOOK NOBODY SHOULD MINT";
            String[] w = LecternSignUtil.wrapNameOntoLines(name);

            assertEquals(name, LecternSignUtil.joinNameLines(
                new String[] { LecternSignUtil.TOME_TAG, w[0], w[1], w[2] }));
        }
    }

    @Nested
    @DisplayName("Joining")
    class Joining {

        @Test
        @DisplayName("a one-line name yields exactly itself — existing signs do not change meaning")
        void singleLineIsUnchanged() {
            assertEquals("ZEAL", LecternSignUtil.joinNameLines(sign(LecternSignUtil.TOME_TAG, "ZEAL")));
        }

        @Test
        @DisplayName("blank lines below the name contribute nothing")
        void blankLinesIgnored() {
            assertEquals("THE BREACH",
                LecternSignUtil.joinNameLines(sign(LecternSignUtil.TOME_TAG, "THE BREACH", "", "  ")));
        }

        @Test
        @DisplayName("a gap in the middle does not produce a double space")
        void gapDoesNotDoubleSpace() {
            assertEquals("ONE TRUE THING",
                LecternSignUtil.joinNameLines(sign(LecternSignUtil.TOME_TAG, "ONE TRUE", "", "THING")));
        }

        @Test
        @DisplayName("nothing below the tag joins to empty, not null")
        void emptyPayloadIsEmptyString() {
            assertEquals("", LecternSignUtil.joinNameLines(sign(LecternSignUtil.TOME_TAG)));
            assertEquals("", LecternSignUtil.joinNameLines(null));
        }

        @Test
        @DisplayName("case and internal spacing survive — resolution is by exact name")
        void casePreserved() {
            assertEquals("Nickel's Pick",
                LecternSignUtil.joinNameLines(sign(LecternSignUtil.TOME_TAG, "Nickel's", "Pick")));
        }
    }

    @Nested
    @DisplayName("Wrapping")
    class Wrapping {

        @Test
        @DisplayName("always returns exactly three lines, blank-padded")
        void alwaysThreeLines() {
            assertEquals(3, LecternSignUtil.wrapNameOntoLines("ZEAL").length);
            assertEquals(3, LecternSignUtil.wrapNameOntoLines(null).length);
            assertEquals(3, LecternSignUtil.wrapNameOntoLines("").length);
        }

        @Test
        @DisplayName("a short name stays on the first payload line")
        void shortNameDoesNotWrap() {
            String[] w = LecternSignUtil.wrapNameOntoLines("ZEAL");
            assertEquals("ZEAL", w[0]);
            assertEquals("", w[1]);
            assertEquals("", w[2]);
        }

        @Test
        @DisplayName("breaks on word boundaries, never mid-word")
        void breaksOnWords() {
            String[] w = LecternSignUtil.wrapNameOntoLines("THE RECORD OF NOCTURNE");
            for (String line : w) {
                assertFalse(line.startsWith(" ") || line.endsWith(" "), "ragged line: '" + line + "'");
            }
            assertEquals("THE RECORD OF NOCTURNE", String.join(" ",
                java.util.Arrays.stream(w).filter(l -> !l.isEmpty()).toList()));
        }

        @Test
        @DisplayName("a single unbreakable word is left to clip rather than corrupted")
        void singleLongWordIsNotSplit() {
            String[] w = LecternSignUtil.wrapNameOntoLines("SUPERCALIFRAGILISTICEXPIALIDOCIOUS");
            assertEquals("SUPERCALIFRAGILISTICEXPIALIDOCIOUS", w[0]);
        }
    }
}

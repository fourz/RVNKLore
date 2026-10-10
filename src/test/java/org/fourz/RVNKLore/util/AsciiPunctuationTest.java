package org.fourz.RVNKLore.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The book-text ASCII mapping (#1500). It must match {@code _NORMALIZE} in
 * {@code scripts/minecraft/_item_common.py} and section 4 of
 * {@code docs/standard/book-item-schema.md}.
 */
@DisplayName("AsciiPunctuation")
class AsciiPunctuationTest {

    @ParameterizedTest(name = "U+{0} -> [{1}]")
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "2014|-",       // em dash
            "2013|-",       // en dash
            "201C|`\"`",    // left curly double quote
            "201D|`\"`",    // right curly double quote
            "2018|'",       // left curly single quote
            "2019|'",       // right curly single quote
            "2026|...",     // ellipsis
            "00A0|` `",     // no-break space
            "2009|` `",     // thin space
            "202F|` `",     // narrow no-break space
            "2022|-",       // bullet
            "00B7|-",       // middle dot
            "00AB|`\"`",    // left guillemet
            "00BB|`\"`",    // right guillemet
    })
    void mapsEachCharacter(String hex, String expected) {
        String input = new String(Character.toChars(Integer.parseInt(hex, 16)));
        assertEquals(expected, AsciiPunctuation.normalize(input));
    }

    @Test
    @DisplayName("normalizes a whole sentence")
    void sentence() {
        String in = "“It’s late…” she said — «the Long Water».";
        assertEquals("\"It's late...\" she said - \"the Long Water\".", AsciiPunctuation.normalize(in));
    }

    @Test
    @DisplayName("leaves colour codes and drawable decorative glyphs alone")
    void passthrough() {
        String in = "§8§o◆ History → Ouroboros";
        assertSame(in, AsciiPunctuation.normalize(in));
    }

    @Test
    @DisplayName("is idempotent and null-safe")
    void idempotentAndNullSafe() {
        assertNull(AsciiPunctuation.normalize(null));
        assertEquals("", AsciiPunctuation.normalize(""));
        String once = AsciiPunctuation.normalize("a—b…c");
        assertEquals(once, AsciiPunctuation.normalize(once));
    }
}

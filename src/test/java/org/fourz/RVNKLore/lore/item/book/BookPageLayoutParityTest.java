package org.fourz.RVNKLore.lore.item.book;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parity between {@link BookPageLayout} and {@code scripts/minecraft/book.item.py} (#1500).
 *
 * <p>Each fixture in {@code src/test/resources/book-parity/} is a source text ({@code <name>.txt})
 * and the {@code legacy_pages} the Python tool emitted for it ({@code <name>.expected.json}).
 * The Java layout must reproduce those pages byte for byte.</p>
 *
 * <p>To regenerate after a deliberate change to the tool (local only, no server is touched):</p>
 * <pre>
 * python3 scripts/minecraft/book.item.py --in repos/RVNKLore/src/test/resources/book-parity/NAME.txt --emit \
 *   | python3 -c "import json,sys; print(json.dumps(json.load(sys.stdin)['legacy_pages'], ensure_ascii=False, indent=1))" \
 *   &gt; repos/RVNKLore/src/test/resources/book-parity/NAME.expected.json
 * </pre>
 */
@DisplayName("BookPageLayout parity with book.item.py")
class BookPageLayoutParityTest {

    private static final String DIR = "/book-parity/";
    private static final String SECTION_HEADER = "§8§o◆ History";

    private static String read(String name) throws IOException {
        try (InputStream in = BookPageLayoutParityTest.class.getResourceAsStream(DIR + name)) {
            assertNotNull(in, "missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> expected(String fixture) throws IOException {
        return new Gson().fromJson(read(fixture + ".expected.json"), new TypeToken<List<String>>() { }.getType());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"prose-smart-punct", "markup-mixed", "section-header", "overflow-cap"})
    @DisplayName("layout() matches the tool's legacy_pages")
    void matchesTool(String fixture) throws IOException {
        List<String> want = expected(fixture);
        List<String> got = BookPageLayout.layout(read(fixture + ".txt"));
        assertEquals(want.size(), got.size(), "page count");
        for (int i = 0; i < want.size(); i++) {
            assertEquals(want.get(i), got.get(i), "page " + (i + 1));
        }
    }

    @Test
    @DisplayName("layoutSection() (the LoreBookManager section path) matches the tool")
    void sectionPathMatchesTool() throws IOException {
        String source = read("section-header.txt");
        String prefix = SECTION_HEADER + "\n\n";
        assertTrue(source.startsWith(prefix), "fixture must open with the section header");
        String body = source.substring(prefix.length());

        assertEquals(expected("section-header"), BookPageLayout.layoutSection(SECTION_HEADER, body));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"prose-smart-punct", "markup-mixed", "section-header", "overflow-cap"})
    @DisplayName("every page fits: <=14 lines, <=114px a line, <=100 pages, ASCII punctuation")
    void pagesFit(String fixture) throws IOException {
        List<String> pages = BookPageLayout.layout(read(fixture + ".txt"));
        assertTrue(pages.size() <= BookPageLayout.MAX_PAGES);
        for (String page : pages) {
            String[] lines = page.split("\n", -1);
            assertTrue(lines.length <= BookPageLayout.LINES_PER_PAGE, "lines on page: " + lines.length);
            for (String line : lines) {
                if (line.startsWith(SECTION_HEADER)) continue; // the header glyph is outside the table
                assertTrue(renderedWidth(line) <= BookPageLayout.PAGE_WIDTH_PX, "too wide: " + line);
            }
            for (char c : new char[] {'—', '–', '“', '”', '‘', '’', '…'}) {
                assertEquals(-1, page.indexOf(c), "smart punctuation survived: " + c);
            }
        }
    }

    @Test
    @DisplayName("overflow input is capped at exactly 100 pages")
    void capsAtHundredPages() throws IOException {
        assertEquals(100, BookPageLayout.layout(read("overflow-cap.txt")).size());
    }

    @Test
    @DisplayName("glyph advances match the tool's _DRAWN table (+1 spacing, +1 bold)")
    void glyphAdvances() {
        assertEquals(4, BookPageLayout.charAdvance(' ', false));
        assertEquals(2, BookPageLayout.charAdvance('i', false));
        assertEquals(3, BookPageLayout.charAdvance('l', false));
        assertEquals(4, BookPageLayout.charAdvance('t', false));
        assertEquals(5, BookPageLayout.charAdvance('f', false));
        assertEquals(6, BookPageLayout.charAdvance('a', false));
        assertEquals(7, BookPageLayout.charAdvance('a', true));
        assertEquals(7, BookPageLayout.charAdvance('@', false));
        assertEquals(7, BookPageLayout.charAdvance('~', false));
        assertEquals(6, BookPageLayout.charAdvance(0x1F30A, false)); // unlisted: default 5 + 1
    }

    @Test
    @DisplayName("empty and blank input produce no pages")
    void emptyInput() {
        assertTrue(BookPageLayout.layout("").isEmpty());
        assertTrue(BookPageLayout.layout("\n\n   \n").isEmpty());
        assertTrue(BookPageLayout.layout(null).isEmpty());
    }

    @Test
    @DisplayName("finalizePages normalizes header text and caps the book at 100 pages")
    void finalizePages() {
        List<String> pages = new ArrayList<>();
        pages.add("§5§lMarrow’s Lantern…");
        for (int i = 0; i < 120; i++) pages.add("p" + i);
        List<String> out = LoreBookManager.finalizePages(pages);
        assertEquals(100, out.size());
        assertEquals("§5§lMarrow's Lantern...", out.get(0));
    }

    /** Pixel width of one rendered legacy line, tracking bold through the section codes. */
    private static int renderedWidth(String line) {
        int width = 0;
        boolean bold = false;
        int[] cps = line.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            if (cps[i] == '§' && i + 1 < cps.length) {
                int code = cps[++i];
                if (code == 'l') bold = true;
                else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r') bold = false;
                continue;
            }
            width += BookPageLayout.charAdvance(cps[i], bold);
        }
        return width;
    }
}

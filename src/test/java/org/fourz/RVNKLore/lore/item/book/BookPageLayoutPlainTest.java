package org.fourz.RVNKLore.lore.item.book;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain mode of {@link BookPageLayout}: database text is wrapped and normalized but never read as
 * markup (operator decision on #1500).
 */
@DisplayName("BookPageLayout plain mode")
class BookPageLayoutPlainTest {

    private static final String HEADER = "§8§o◆ History";

    @Test
    @DisplayName("'B&B Tavern' keeps both B's in the page colour and keeps the '&'")
    void ampersandIsLiteral() {
        assertEquals(List.of("§0B&B Tavern"), BookPageLayout.layoutPlain("B&B Tavern"));
        // Markup mode, for contrast, reads "&B" as an aqua colour code and drops the second B.
        assertEquals(List.of("§0B§b Tavern"), BookPageLayout.layout("B&B Tavern"));
    }

    @Test
    @DisplayName("'*not italic*' and '**not bold**' keep their asterisks")
    void asterisksAreLiteral() {
        assertEquals(List.of("§0*not italic*\n§0**not bold**"),
                BookPageLayout.layoutPlain("*not italic*\n**not bold**"));
    }

    @Test
    @DisplayName("a '---' line does not break the page, and '# ' is not a heading")
    void noPageBreakOrHeading() {
        List<String> pages = BookPageLayout.layoutPlain("# Chapter one\nabove\n---\nbelow");
        assertEquals(List.of("§0# Chapter one\n§0above\n§0---\n§0below"), pages);
    }

    @Test
    @DisplayName("blank lines are still paragraph gaps and punctuation is still normalized")
    void gapsAndNormalization() {
        assertEquals(List.of("§0It's done - mostly...\n\n§0Next."),
                BookPageLayout.layoutPlain("It’s done — mostly…\n\n\n\nNext."));
    }

    @Test
    @DisplayName("wrap counts '&' as a visible 6px glyph")
    void ampersandWidth() {
        // "&a" x10 = 20 glyphs x 6px = 120px > 114: 19 glyphs (114px) fit, the last 'a' wraps.
        String word = "&a".repeat(10);
        assertEquals(List.of("§0" + word.substring(0, 19) + "\n§0a"), BookPageLayout.layoutPlain(word));
    }

    @Test
    @DisplayName("wrap counts '*' as a visible 4px glyph")
    void asteriskWidth() {
        // 30 x 4px = 120px > 114: 28 asterisks (112px) fit on the first line.
        String word = "*".repeat(30);
        assertEquals(List.of("§0" + "*".repeat(28) + "\n§0**"), BookPageLayout.layoutPlain(word));
    }

    @Test
    @DisplayName("a '§x' pair in plain text is passed through, not parsed")
    void sectionSignPassesThrough() {
        // Not interpreted here; the Minecraft client will still read it (documented limit).
        assertEquals(List.of("§0§4red"), BookPageLayout.layoutPlain("§4red"));
    }

    @Test
    @DisplayName("section header keeps its format codes; the body is plain")
    void sectionHeaderFormattedBodyPlain() {
        assertEquals(List.of(HEADER + "\n\n§0B&B *inn*\n§0---"),
                BookPageLayout.layoutSectionPlain(HEADER, "B&B *inn*\n---"));
        assertEquals(List.of(HEADER + "\n\n§7note"),
                BookPageLayout.layoutSectionPlain(HEADER, "note", '7'));
    }

    @Test
    @DisplayName("record rows: dark-gray key, gray value, values plain")
    void recordRows() {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("Owner", "B&B");
        rows.put("Note", "*first*\nsecond");
        assertEquals(List.of("§8§o◆ Record\n\n§8Owner: §7B&B\n§8Note: §7*first*\n§7second"),
                BookPageLayout.layoutRecordPlain("§8§o◆ Record", rows, '8', '7'));
    }

    @Test
    @DisplayName("plain mode still caps at 100 pages and 14 lines a page")
    void limits() {
        List<String> pages = BookPageLayout.layoutPlain("word &* ".repeat(5000));
        assertEquals(BookPageLayout.MAX_PAGES, pages.size());
        for (String page : pages) {
            assertTrue(page.split("\n", -1).length <= BookPageLayout.LINES_PER_PAGE);
        }
    }

    @Test
    @DisplayName("for markup-free text, plain mode equals the tool's output")
    void plainEqualsToolForMarkupFreeText() throws IOException {
        assertEquals(expected("prose-smart-punct"), BookPageLayout.layoutPlain(read("prose-smart-punct.txt")));
        String section = read("section-header.txt");
        String body = section.substring((HEADER + "\n\n").length());
        assertEquals(expected("section-header"), BookPageLayout.layoutSectionPlain(HEADER, body));
    }

    @Test
    @DisplayName("PATH See Also footer lays out to three lines")
    void footerLineCount() {
        String footer = BookPageLayout.layout("§8§oRavenkraft Road Survey\n§8§oOffice of Cartography").get(0);
        assertEquals(3, footer.split("\n", -1).length);
    }

    private static String read(String name) throws IOException {
        try (InputStream in = BookPageLayoutPlainTest.class.getResourceAsStream("/book-parity/" + name)) {
            assertNotNull(in, "missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> expected(String fixture) throws IOException {
        return new Gson().fromJson(read(fixture + ".expected.json"), new TypeToken<List<String>>() { }.getType());
    }
}

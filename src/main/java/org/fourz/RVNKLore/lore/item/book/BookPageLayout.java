package org.fourz.RVNKLore.lore.item.book;

import org.fourz.RVNKLore.util.AsciiPunctuation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pixel-accurate written-book layout, ported from {@code scripts/minecraft/book.item.py} (#1500).
 *
 * <p>Text is wrapped by summed glyph advance widths against the 114px page, at most 14 lines a
 * page and 100 pages a book, and rendered as legacy {@code §}-code page strings. The output for a
 * given input is byte-identical to the {@code legacy_pages} that {@code book.item.py --emit}
 * prints, which {@code BookPageLayoutParityTest} checks against fixtures captured from the tool.
 * Contract: {@code docs/standard/book-item-schema.md}.</p>
 *
 * <p>Input markup understood (same as the tool):</p>
 * <ul>
 *   <li>{@code # Heading} - a bold line in the accent colour (gold)</li>
 *   <li>{@code ---} on its own line - a hard page break</li>
 *   <li>a blank line - a paragraph gap</li>
 *   <li>{@code **bold**} and {@code *italic*} - inline styling toggles</li>
 *   <li>{@code §x} / {@code &x} - legacy colour and format codes</li>
 * </ul>
 *
 * <p><b>Two modes.</b> Markup mode ({@link #layout}, {@link #layoutSection}) is the tool's
 * behaviour and is for authored text. Plain mode ({@link #layoutPlain},
 * {@link #layoutSectionPlain}, {@link #layoutRecordPlain}) is for text that comes from the
 * database (operator decision on #1500): it normalizes punctuation and uses the same pixel wrap,
 * line, page and hard-split rules, but it reads no markup. {@code *}, {@code **}, {@code &x},
 * {@code §x}, {@code # } and {@code ---} stay in the text as ordinary characters and are measured
 * with their own glyph widths. A blank line is still a paragraph gap and a newline still ends a
 * line.</p>
 *
 * <p>Limit of plain mode: a legacy page string cannot hold a literal {@code §}. This class does
 * not interpret one, but the Minecraft client still reads {@code §x} in a page as a format code.
 * The wrap counts such a pair as two visible glyphs, so a colour code only makes the line
 * shorter than it needs to be; a {@code §l} in database text can make a line wider than counted.</p>
 *
 * <p>Keep this class in step with the Python tool. If the tool's width table or wrap rules change,
 * port the change here and regenerate the parity fixtures.</p>
 */
public final class BookPageLayout {

    /** Page content width in pixels. */
    public static final int PAGE_WIDTH_PX = 114;
    /** Lines that fit on one page. */
    public static final int LINES_PER_PAGE = 14;
    /** Pages a written book may hold. */
    public static final int MAX_PAGES = 100;

    /** Default heading colour code (gold), the tool's default {@code --accent}. */
    public static final char DEFAULT_ACCENT = '6';

    private static final char DEFAULT_COLOR = '0'; // book pages render black by default
    private static final int SPACE = ' ';
    private static final int DEFAULT_DRAWN = 5;

    /**
     * Minecraft default-font DRAWN glyph widths in pixels. Advance = drawn + 1 (inter-character
     * spacing); bold adds +1. Anything not listed draws 5px. Mirrors {@code _DRAWN} in the tool.
     */
    private static final Map<Integer, Integer> DRAWN = new HashMap<>();

    static {
        drawn(" ", 3); drawn("!", 1); drawn("\"", 3); drawn("#", 5); drawn("$", 5); drawn("%", 5);
        drawn("&", 5); drawn("'", 1); drawn("(", 3); drawn(")", 3); drawn("*", 3); drawn("+", 5);
        drawn(",", 1); drawn("-", 5); drawn(".", 1); drawn("/", 5); drawn(":", 1); drawn(";", 1);
        drawn("<", 4); drawn("=", 5); drawn(">", 4); drawn("?", 5); drawn("@", 6); drawn("[", 3);
        drawn("\\", 5); drawn("]", 3); drawn("^", 5); drawn("_", 5); drawn("`", 2); drawn("{", 3);
        drawn("|", 1); drawn("}", 3); drawn("~", 6);
        drawn("f", 4); drawn("i", 1); drawn("k", 4); drawn("l", 2); drawn("t", 3); drawn("I", 3);
    }

    private static void drawn(String ch, int px) {
        DRAWN.put(ch.codePointAt(0), px);
    }

    private BookPageLayout() {
    }

    // ── Public entry points ─────────────────────────────────────────────────

    /**
     * Normalize smart punctuation, then lay text out into legacy page strings.
     * Equivalent to {@code book.item.py --text <text> --emit} ({@code legacy_pages}).
     *
     * @param text the source text (may contain the markup listed on the class)
     * @return the pages, at most {@link #MAX_PAGES}; empty when the text has no visible content
     */
    public static List<String> layout(String text) {
        if (text == null) {
            return new ArrayList<>();
        }
        return renderLegacy(buildPages(AsciiPunctuation.normalize(text), DEFAULT_ACCENT));
    }

    /**
     * Lay out a section that opens with a one-line header, then a blank line, then the body.
     *
     * <p>The header is a legacy-coded line such as {@code "§8§o◆ History"}. It is laid out with
     * the body as one text, so the first page holds the header and twelve body lines and the
     * remaining body continues on the following pages.</p>
     *
     * @param header the header line, with its own colour codes
     * @param body   the section body
     * @return the pages, at most {@link #MAX_PAGES}
     */
    public static List<String> layoutSection(String header, String body) {
        return layout(header + "\n\n" + (body == null ? "" : body));
    }

    /**
     * Plain mode: normalize punctuation and wrap {@code text} with no markup parsing, in black.
     *
     * @param text database text, laid out literally
     * @return the pages, at most {@link #MAX_PAGES}
     */
    public static List<String> layoutPlain(String text) {
        if (text == null) {
            return new ArrayList<>();
        }
        return renderLegacy(buildPages(plainBlocks(text, DEFAULT_COLOR)));
    }

    /**
     * A section with a formatted header line, a blank line, then a plain body in black.
     *
     * @param header the header line; its {@code §} codes are read (the plugin builds it)
     * @param body   database text, laid out literally
     * @return the pages, at most {@link #MAX_PAGES}
     */
    public static List<String> layoutSectionPlain(String header, String body) {
        return layoutSectionPlain(header, body, DEFAULT_COLOR);
    }

    /**
     * A section with a formatted header line, a blank line, then a plain body.
     *
     * @param header    the header line; its {@code §} codes are read (the plugin builds it)
     * @param body      database text, laid out literally
     * @param bodyColor legacy colour code for the body, for example {@code '7'} for gray
     * @return the pages, at most {@link #MAX_PAGES}
     */
    public static List<String> layoutSectionPlain(String header, String body, char bodyColor) {
        List<Block> blocks = headerBlocks(header);
        blocks.addAll(plainBlocks(body == null ? "" : body, bodyColor));
        return renderLegacy(buildPages(blocks));
    }

    /**
     * A section with a formatted header line, a blank line, then one {@code Key: value} line per
     * row. Keys and values are database text and are laid out literally; a value that wraps
     * continues on the next line in the value colour.
     *
     * @param header     the header line; its {@code §} codes are read (the plugin builds it)
     * @param rows       label to value, in display order
     * @param keyColor   legacy colour code for the {@code Key: } part
     * @param valueColor legacy colour code for the value
     * @return the pages, at most {@link #MAX_PAGES}
     */
    public static List<String> layoutRecordPlain(String header, Map<String, String> rows,
                                                 char keyColor, char valueColor) {
        List<Block> blocks = headerBlocks(header);
        for (Map.Entry<String, String> row : rows.entrySet()) {
            String key = AsciiPunctuation.normalize(String.valueOf(row.getKey())) + ": ";
            List<String> valueLines = splitLines(AsciiPunctuation.normalize(
                    row.getValue() == null ? "" : row.getValue()));
            List<Seg> first = new ArrayList<>();
            first.add(new Seg(key, new Style(keyColor, false)));
            if (!valueLines.isEmpty() && !valueLines.get(0).isEmpty()) {
                first.add(new Seg(valueLines.get(0), new Style(valueColor, false)));
            }
            blocks.add(new Block(BlockKind.PARA, first));
            for (int i = 1; i < valueLines.size(); i++) {
                blocks.add(plainLine(valueLines.get(i), valueColor));
            }
        }
        return renderLegacy(buildPages(blocks));
    }

    /**
     * The pixel advance of one code point, as the tool computes it.
     *
     * @param codePoint the character
     * @param bold      whether it renders bold
     * @return drawn width + 1px spacing (+1 when bold)
     */
    public static int charAdvance(int codePoint, boolean bold) {
        int drawn = DRAWN.getOrDefault(codePoint, DEFAULT_DRAWN);
        return drawn + (bold ? 1 : 0) + 1;
    }

    // ── Model ───────────────────────────────────────────────────────────────

    /** Mutable text style; copied whenever a segment is captured. Mirrors the tool's Style. */
    static final class Style {
        char color;
        boolean bold;
        boolean italic;
        boolean underline;
        boolean strike;
        boolean obf;

        Style(char color, boolean bold) {
            this.color = color;
            this.bold = bold;
        }

        Style copy() {
            Style s = new Style(color, bold);
            s.italic = italic;
            s.underline = underline;
            s.strike = strike;
            s.obf = obf;
            return s;
        }

        void clearFormats() {
            bold = false;
            italic = false;
            underline = false;
            strike = false;
            obf = false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Style)) return false;
            Style s = (Style) o;
            return color == s.color && bold == s.bold && italic == s.italic
                    && underline == s.underline && strike == s.strike && obf == s.obf;
        }

        @Override
        public int hashCode() {
            return Objects.hash(color, bold, italic, underline, strike, obf);
        }
    }

    /** A run of text in one style. */
    static final class Seg {
        final String text;
        final Style style;

        Seg(String text, Style style) {
            this.text = text;
            this.style = style;
        }
    }

    /** One character with its style, the unit the wrapper works in. */
    private static final class Ch {
        final int cp;
        final Style style;

        Ch(int cp, Style style) {
            this.cp = cp;
            this.style = style;
        }
    }

    private enum BlockKind { PARA, HEADING, GAP, BREAK }

    private static final class Block {
        final BlockKind kind;
        final List<Seg> segs;

        Block(BlockKind kind, List<Seg> segs) {
            this.kind = kind;
            this.segs = segs;
        }
    }

    // ── Inline markup -> styled segments (parse_inline) ─────────────────────

    static List<Seg> parseInline(String text, Style base) {
        int[] cps = text.codePoints().toArray();
        List<Seg> segs = new ArrayList<>();
        Style st = base.copy();
        StringBuilder buf = new StringBuilder();
        int i = 0;
        int n = cps.length;
        while (i < n) {
            int ch = cps[i];
            if ((ch == '§' || ch == '&') && i + 1 < n) {
                int code = Character.toLowerCase(cps[i + 1]);
                if (isColorCode(code)) {
                    flush(segs, buf, st);
                    st.color = (char) code;
                    st.clearFormats();
                    i += 2;
                    continue;
                }
                if (applyFormatCode(code, segs, buf, st)) {
                    i += 2;
                    continue;
                }
                if (code == 'r') {
                    flush(segs, buf, st);
                    st = base.copy();
                    i += 2;
                    continue;
                }
            }
            if (ch == '*' && i + 1 < n && cps[i + 1] == '*') {
                flush(segs, buf, st);
                st.bold = !st.bold;
                i += 2;
                continue;
            }
            if (ch == '*') {
                flush(segs, buf, st);
                st.italic = !st.italic;
                i += 1;
                continue;
            }
            buf.appendCodePoint(ch);
            i += 1;
        }
        flush(segs, buf, st);
        return segs;
    }

    private static boolean isColorCode(int code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private static boolean applyFormatCode(int code, List<Seg> segs, StringBuilder buf, Style st) {
        switch (code) {
            case 'l': flush(segs, buf, st); st.bold = true; return true;
            case 'o': flush(segs, buf, st); st.italic = true; return true;
            case 'n': flush(segs, buf, st); st.underline = true; return true;
            case 'm': flush(segs, buf, st); st.strike = true; return true;
            case 'k': flush(segs, buf, st); st.obf = true; return true;
            default: return false;
        }
    }

    private static void flush(List<Seg> segs, StringBuilder buf, Style st) {
        if (buf.length() > 0) {
            segs.add(new Seg(buf.toString(), st.copy()));
            buf.setLength(0);
        }
    }

    // ── Plain-mode blocks (no markup) ───────────────────────────────────────

    /** Header line parsed as markup (the plugin builds it), followed by one blank line. */
    private static List<Block> headerBlocks(String header) {
        List<Block> blocks = parseBlocks(AsciiPunctuation.normalize(header == null ? "" : header),
                DEFAULT_ACCENT);
        blocks.add(new Block(BlockKind.GAP, List.of()));
        return blocks;
    }

    /** One block per source line: blank lines are gaps, every other line is literal text. */
    private static List<Block> plainBlocks(String text, char color) {
        List<Block> blocks = new ArrayList<>();
        for (String line : splitLines(AsciiPunctuation.normalize(text))) {
            blocks.add(plainLine(line, color));
        }
        return blocks;
    }

    private static Block plainLine(String line, char color) {
        if (pyStrip(line).isEmpty()) {
            return new Block(BlockKind.GAP, List.of());
        }
        List<Seg> segs = new ArrayList<>(1);
        segs.add(new Seg(line, new Style(color, false)));
        return new Block(BlockKind.PARA, segs);
    }

    // ── Block parsing (parse_blocks) ────────────────────────────────────────

    private static List<Block> parseBlocks(String text, char accent) {
        List<Block> blocks = new ArrayList<>();
        for (String line : splitLines(text)) {
            String stripped = pyStrip(line);
            if (stripped.equals("---")) {
                blocks.add(new Block(BlockKind.BREAK, List.of()));
                continue;
            }
            if (stripped.isEmpty()) {
                blocks.add(new Block(BlockKind.GAP, List.of()));
                continue;
            }
            if (line.startsWith("# ")) {
                blocks.add(new Block(BlockKind.HEADING,
                        parseInline(pyStrip(line.substring(2)), new Style(accent, true))));
                continue;
            }
            blocks.add(new Block(BlockKind.PARA, parseInline(line, new Style(DEFAULT_COLOR, false))));
        }
        return blocks;
    }

    /** Python {@code str.splitlines()}: same boundaries, no trailing empty element. */
    static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (isLineBoundary(c)) {
                lines.add(text.substring(start, i));
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
                    i++;
                }
                i++;
                start = i;
                continue;
            }
            i++;
        }
        if (start < n) {
            lines.add(text.substring(start));
        }
        return lines;
    }

    private static boolean isLineBoundary(char c) {
        switch (c) {
            case '\n': case '\r': case '\u000B': case '\u000C':
            case '\u001C': case '\u001D': case '\u001E':
            case '\u0085': case ' ': case ' ':
                return true;
            default:
                return false;
        }
    }

    /** Python {@code str.strip()} with no arguments. */
    static String pyStrip(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && isPySpace(s.charAt(start))) start++;
        while (end > start && isPySpace(s.charAt(end - 1))) end--;
        return s.substring(start, end);
    }

    private static boolean isPySpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u0085';
    }

    // ── Word wrap (wrap_segments) ───────────────────────────────────────────

    static List<List<Seg>> wrapSegments(List<Seg> segments) {
        // Flatten to styled words; every space is its own token.
        List<List<Ch>> words = new ArrayList<>();
        List<Ch> cur = new ArrayList<>();
        for (Seg seg : segments) {
            int[] cps = seg.text.codePoints().toArray();
            for (int cp : cps) {
                if (cp == SPACE) {
                    if (!cur.isEmpty()) {
                        words.add(cur);
                        cur = new ArrayList<>();
                    }
                    List<Ch> space = new ArrayList<>(1);
                    space.add(new Ch(SPACE, seg.style));
                    words.add(space);
                } else {
                    cur.add(new Ch(cp, seg.style));
                }
            }
        }
        if (!cur.isEmpty()) {
            words.add(cur);
        }

        List<List<Seg>> lines = new ArrayList<>();
        List<Ch> line = new ArrayList<>();
        int width = 0;

        for (List<Ch> w : words) {
            boolean isSpace = w.size() == 1 && w.get(0).cp == SPACE;
            int ww = wordWidth(w);
            if (isSpace) {
                if (!line.isEmpty() && width + ww <= PAGE_WIDTH_PX) {
                    line.add(w.get(0));
                    width += ww;
                }
                continue;
            }
            if (width + ww > PAGE_WIDTH_PX && !line.isEmpty()) {
                lines.add(coalesce(line));
                line = new ArrayList<>();
                width = 0;
            }
            if (ww > PAGE_WIDTH_PX) { // hard-split an over-wide word
                for (Ch ch : w) {
                    int cw = charAdvance(ch.cp, ch.style.bold);
                    if (width + cw > PAGE_WIDTH_PX && !line.isEmpty()) {
                        lines.add(coalesce(line));
                        line = new ArrayList<>();
                        width = 0;
                    }
                    line.add(ch);
                    width += cw;
                }
                continue;
            }
            line.addAll(w);
            width += ww;
        }
        if (!line.isEmpty()) {
            lines.add(coalesce(line));
        }
        return lines;
    }

    private static int wordWidth(List<Ch> word) {
        int sum = 0;
        for (Ch ch : word) {
            sum += charAdvance(ch.cp, ch.style.bold);
        }
        return sum;
    }

    /** Trim trailing spaces, then merge adjacent characters that share a style. */
    private static List<Seg> coalesce(List<Ch> line) {
        int end = line.size();
        while (end > 0 && line.get(end - 1).cp == SPACE) {
            end--;
        }
        List<Seg> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        Style curStyle = null;
        for (int i = 0; i < end; i++) {
            Ch ch = line.get(i);
            if (curStyle != null && !curStyle.equals(ch.style)) {
                out.add(new Seg(buf.toString(), curStyle));
                buf.setLength(0);
            }
            curStyle = ch.style;
            buf.appendCodePoint(ch.cp);
        }
        if (curStyle != null) {
            out.add(new Seg(buf.toString(), curStyle));
        }
        return out;
    }

    // ── Pages (build_pages) ─────────────────────────────────────────────────

    static List<List<List<Seg>>> buildPages(String text, char accent) {
        return buildPages(parseBlocks(text, accent));
    }

    private static List<List<List<Seg>>> buildPages(List<Block> blocks) {
        List<List<List<Seg>>> pages = new ArrayList<>();
        List<List<Seg>> cur = new ArrayList<>();

        for (Block block : blocks) {
            if (block.kind == BlockKind.BREAK) {
                cur = newPage(pages, cur);
                continue;
            }
            if (block.kind == BlockKind.GAP) {
                // blank line only inside a non-full page that has visible content
                if (!cur.isEmpty() && cur.size() < LINES_PER_PAGE && !cur.get(cur.size() - 1).isEmpty()) {
                    cur.add(new ArrayList<>());
                }
                continue;
            }
            for (List<Seg> ln : wrapSegments(block.segs)) {
                if (cur.size() >= LINES_PER_PAGE) {
                    cur = newPage(pages, cur);
                }
                cur.add(ln);
            }
        }
        newPage(pages, cur);
        return pages.size() > MAX_PAGES ? new ArrayList<>(pages.subList(0, MAX_PAGES)) : pages;
    }

    /** Close the current page (dropping trailing blank lines) and return a fresh one. */
    private static List<List<Seg>> newPage(List<List<List<Seg>>> pages, List<List<Seg>> cur) {
        while (!cur.isEmpty() && cur.get(cur.size() - 1).isEmpty()) {
            cur.remove(cur.size() - 1);
        }
        if (!cur.isEmpty()) {
            pages.add(cur);
            return new ArrayList<>();
        }
        return cur;
    }

    // ── Legacy renderer (render_legacy) ─────────────────────────────────────

    static List<String> renderLegacy(List<List<List<Seg>>> pages) {
        List<String> out = new ArrayList<>(pages.size());
        for (List<List<Seg>> page : pages) {
            StringBuilder sb = new StringBuilder();
            for (int li = 0; li < page.size(); li++) {
                if (li > 0) {
                    sb.append('\n');
                }
                for (Seg seg : page.get(li)) {
                    Style st = seg.style;
                    sb.append('§').append(st.color);
                    if (st.bold) sb.append("§l");
                    if (st.italic) sb.append("§o");
                    if (st.underline) sb.append("§n");
                    if (st.strike) sb.append("§m");
                    if (st.obf) sb.append("§k");
                    sb.append(seg.text);
                }
            }
            out.add(sb.toString());
        }
        return out;
    }
}

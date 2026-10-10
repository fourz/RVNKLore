package org.fourz.RVNKLore.util;

/**
 * Maps typographic punctuation to plain ASCII for text players read in a written book.
 *
 * <p>The Minecraft default book font has no glyphs for smart punctuation; it draws a
 * ?-in-a-diamond box instead. This is the same mapping as {@code _NORMALIZE} in
 * {@code scripts/minecraft/_item_common.py} (used by {@code book.item.py}) and the table in
 * {@code docs/standard/book-item-schema.md} section 4, so a book built by the plugin and a book
 * built by the tool contain the same characters (#1500).</p>
 *
 * <p>Characters outside this table pass through unchanged. Decorative glyphs the font does draw
 * (for example the {@code ◆} section marker and {@code →}) are deliberately left alone, and so is
 * the {@code §} colour-code prefix.</p>
 */
public final class AsciiPunctuation {

    private AsciiPunctuation() {
    }

    /**
     * Replace smart punctuation with its ASCII equivalent.
     *
     * @param text the text to normalize; {@code null} is returned as {@code null}
     * @return the normalized text
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String replacement = replacementFor(c);
            if (replacement == null) {
                if (out != null) {
                    out.append(c);
                }
                continue;
            }
            if (out == null) {
                out = new StringBuilder(text.length() + 8);
                out.append(text, 0, i);
            }
            out.append(replacement);
        }
        return out == null ? text : out.toString();
    }

    /** The ASCII replacement for {@code c}, or {@code null} when it needs none. */
    private static String replacementFor(char c) {
        switch (c) {
            case '—': // em dash
            case '–': // en dash
                return "-";
            case '“': // left curly double quote
            case '”': // right curly double quote
                return "\"";
            case '‘': // left curly single quote
            case '’': // right curly single quote
                return "'";
            case '…': // ellipsis
                return "...";
            case ' ': // no-break space
            case ' ': // thin space
            case ' ': // narrow no-break space
                return " ";
            case '•': // bullet
            case '·': // middle dot
                return "-";
            case '«': // left guillemet
            case '»': // right guillemet
                return "\"";
            default:
                return null;
        }
    }
}

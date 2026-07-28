package org.fourz.RVNKLore.util;

import java.util.regex.Pattern;

/**
 * Small, self-contained helper for deriving a "lineage base name" from an item's
 * display name for the {@code [Forge]} feature.
 *
 * <p>A forged item's lineage is keyed off its base name: re-forging a same-base-name
 * item you already authored content-versions the existing catalog entry rather than
 * minting a new one. To make {@code Monster Hunter}, {@code Monster Hunter I} and
 * {@code Monster Hunter II} share one lineage, this strips a trailing version suffix:</p>
 * <ul>
 *   <li>a trailing Roman numeral — {@code Monster Hunter II} → {@code Monster Hunter}</li>
 *   <li>a trailing integer — {@code Super Pick 7} → {@code Super Pick}</li>
 *   <li>otherwise the (trimmed) input is returned unchanged</li>
 * </ul>
 *
 * <p>Intentionally has no Bukkit or plugin dependencies so it stays unit-testable.</p>
 */
public final class ForgeNaming {

    // Version-suffix Roman numerals only — I..XXXIX (X/V/I letters). Deliberately excludes the
    // larger letters (M/D/C/L) so common words that happen to be valid full Roman numerals do NOT
    // get stripped: "Trail Mix" (MIX=1009), "Vitamin C" (100), "Plan D" (500) keep their suffix,
    // while realistic item versions ("Sword II", "Gear XV", "Blade IX") are matched. Empty string
    // also matches, so callers must pass a non-empty suffix (the whitespace split guarantees this).
    private static final Pattern ROMAN = Pattern.compile("(?i)^(X{0,3})(IX|IV|V?I{0,3})$");

    private static final Pattern INTEGER = Pattern.compile("^\\d+$");

    private ForgeNaming() {}

    /**
     * Strip a trailing Roman-numeral or integer version suffix from a display name.
     *
     * @param display the item display name (may be null)
     * @return the lineage base name; empty string when {@code display} is null/blank
     */
    public static String baseName(String display) {
        if (display == null) return "";
        String trimmed = display.trim();
        if (trimmed.isEmpty()) return trimmed;

        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace <= 0) return trimmed;

        String suffix = trimmed.substring(lastSpace + 1);
        String head = trimmed.substring(0, lastSpace).trim();
        if (head.isEmpty() || suffix.isEmpty()) return trimmed;

        if (INTEGER.matcher(suffix).matches() || ROMAN.matcher(suffix).matches()) {
            return head;
        }
        return trimmed;
    }
}

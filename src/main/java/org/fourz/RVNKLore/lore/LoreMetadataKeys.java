package org.fourz.RVNKLore.lore;

import java.util.Set;

/**
 * Canonical vocabulary for {@code lore_metadata} keys (#1367).
 *
 * <p>The {@code lore_metadata} table ({@code lore_id, meta_key, meta_value}) lets ingested
 * location/event lore carry extended fields <b>without schema changes</b>. This class names the
 * canonical keys so writers reference constants instead of magic strings, and provides light
 * validation for the two keys that have a constrained value set.</p>
 *
 * <p><b>Forward-compatible:</b> unknown keys are still accepted (callers log them at debug), so new
 * fields can be ingested before this vocabulary is extended. Only {@link #CONFIDENCE} and
 * {@link #WORLD_STATUS} constrain their values.</p>
 */
public final class LoreMetadataKeys {

    private LoreMetadataKeys() {
    }

    // ── Provenance ────────────────────────────────────────────────────────────────
    /** MCA file / scan source, e.g. {@code koz_r.0.-1.mca}. */
    public static final String SOURCE_REGION = "source_region";
    /** ISO date of the scan, e.g. {@code 2026-06-21}. */
    public static final String SCAN_DATE = "scan_date";
    /** Sign-confirmed vs inferred: {@code high} / {@code medium} / {@code low} (constrained). */
    public static final String CONFIDENCE = "confidence";
    /** Whether the world resolved via Bukkit at write time: {@code loaded} / {@code unloaded}
     *  (constrained). Set by the unloaded-world storage fix (#1366). */
    public static final String WORLD_STATUS = "world_status";

    // ── Attribution ───────────────────────────────────────────────────────────────
    /** Player IGN of the builder (may be {@code Unknown}), e.g. {@code mctmoz}. */
    public static final String BUILDER = "builder";
    /** World era, e.g. {@code Bankrekka} / {@code Zothique}. */
    public static final String ERA = "era";
    /** Human label vs slug, e.g. {@code Kingdom of Zeal}. */
    public static final String WORLD_DISPLAY_NAME = "world_display_name";

    // ── Chatbot hint ──────────────────────────────────────────────────────────────
    /** Persona-facing teaser text for Shadowmelt. */
    public static final String SHADOWMELT_HINT = "shadowmelt_hint";
    /** Persona-facing factual locator text for Lenore. */
    public static final String LENORE_HINT = "lenore_hint";

    // ── Cross-ref ─────────────────────────────────────────────────────────────────
    /** Graph Memory entity name, e.g. {@code koz:wondercraft_base}. */
    public static final String GRAPH_ENTITY = "graph_entity";
    /** Wiki article title, e.g. {@code Wondercraft Base}. */
    public static final String WIKI_PAGE = "wiki_page";
    /** Comma-separated IGNs, e.g. {@code mctmoz,grady}. */
    public static final String RELATED_PLAYERS = "related_players";

    /** All canonical keys. Keys outside this set are accepted but logged at debug. */
    public static final Set<String> KNOWN_KEYS = Set.of(
        SOURCE_REGION, SCAN_DATE, CONFIDENCE, WORLD_STATUS,
        BUILDER, ERA, WORLD_DISPLAY_NAME,
        SHADOWMELT_HINT, LENORE_HINT,
        GRAPH_ENTITY, WIKI_PAGE, RELATED_PLAYERS);

    /** Allowed values for {@link #CONFIDENCE}. */
    public static final Set<String> CONFIDENCE_VALUES = Set.of("high", "medium", "low");
    /** Allowed values for {@link #WORLD_STATUS}. */
    public static final Set<String> WORLD_STATUS_VALUES = Set.of("loaded", "unloaded");

    /** @return true if {@code key} is a canonical key. */
    public static boolean isKnown(String key) {
        return key != null && KNOWN_KEYS.contains(key);
    }

    /**
     * Validate a constrained key's value. Keys without a constrained value set always pass, as do
     * null inputs (length/null are enforced by the caller).
     *
     * @return {@code null} if valid; otherwise a human-readable error message.
     */
    public static String validate(String key, String value) {
        if (key == null || value == null) {
            return null;
        }
        if (CONFIDENCE.equals(key) && !CONFIDENCE_VALUES.contains(value.toLowerCase())) {
            return "metadata '" + CONFIDENCE + "' must be one of " + CONFIDENCE_VALUES + " (got '" + value + "')";
        }
        if (WORLD_STATUS.equals(key) && !WORLD_STATUS_VALUES.contains(value.toLowerCase())) {
            return "metadata '" + WORLD_STATUS + "' must be one of " + WORLD_STATUS_VALUES + " (got '" + value + "')";
        }
        return null;
    }
}

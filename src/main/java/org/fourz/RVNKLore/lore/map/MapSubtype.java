package org.fourz.RVNKLore.lore.map;

/**
 * Subtypes of lore maps stored in lore_map.
 *
 * TREASURE  — QUEST-type entries: partial, hand-drawn style with X marking location.
 * ATLAS     — CITY/LANDMARK/FACTION entries: overview map marking the entry location.
 * PIXEL     — Standalone 128×128 ARGB image stored as base64 pixel_data (no linked entry required).
 */
public enum MapSubtype {
    TREASURE,
    ATLAS,
    PIXEL;

    public static MapSubtype fromString(String s) {
        if (s == null) return ATLAS;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ATLAS;
        }
    }
}

package org.fourz.RVNKLore.handler;

import java.util.Map;

/**
 * Gate for recording enchanting-table lore (#2099).
 *
 * <p>ENCHANTMENT is an admin-only type in {@link org.fourz.RVNKLore.lore.LoreTypePermission}, so a
 * notable enchant is recorded only when the enchanter holds {@link #PERMISSION} AND has opted in
 * with {@code /lore prefs chronicle on}. The opt-in lives in the player's RVNKCore preference
 * metadata; notification types cannot express it because they default to enabled.
 */
public final class EnchantChronicle {

    public static final String PERMISSION = "rvnklore.enchant.chronicle";
    public static final String META_KEY = "enchant_chronicle";

    private EnchantChronicle() {}

    /** Off unless the player explicitly turned it on - a missing key is not consent. */
    public static boolean isOptedIn(Map<String, String> metadata) {
        return metadata != null && "true".equalsIgnoreCase(metadata.get(META_KEY));
    }
}
